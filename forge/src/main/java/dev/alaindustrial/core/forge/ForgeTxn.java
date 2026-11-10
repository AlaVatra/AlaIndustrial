package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.energy.EnergyPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A root transaction for a loader that has none (MinecraftForge 26.x: no {@code Transaction}, no
 * {@code SnapshotJournal}, only capabilities with a {@code simulate} flag).
 *
 * <p>For the mod's own buffers and tanks this is the full model the two other loaders provide: every participant
 * is snapshotted once, on its first {@link #enlist}, before it changes; {@link #abort()} restores the snapshots in
 * reverse order and {@link #commit()} fires {@code onFinalCommit()} once per participant. One root per thread, as
 * on the other loaders ({@link #openRoot}); the common code never nests them.
 *
 * <p>Foreign storages cannot be snapshotted; they keep their state in {@link ForeignLedger}s this transaction
 * owns, one per foreign handler object and resource, so two lookups of the same block inside one transaction
 * share one ledger and cannot promise the same room twice.
 *
 * <p><b>Two-phase commit.</b> Phase one settles every ledger's deferred insert. A storage that takes less than it
 * promised leaves a shortfall, which goes back, in this order, to: our own participants that paid in this
 * transaction (a net debit of the same kind and fluid, most recent first, see {@link ForeignLedger#isPayer}),
 * then the foreign storages of the same kind and resource we really drained in this transaction; only the rest is
 * counted in {@link ForgeTransferLosses}. A refund restores part of a participant's own transaction-start state,
 * so it can never exceed what it paid, never overfill it and never create anything; it bypasses the per-call
 * insert limits on purpose (it undoes a debit, it is not an insert). The payer is inferred: {@code insert} carries
 * none in the common contract, so with several payers in one transaction the most recent debtors are refunded
 * first. Phase two fires {@code onFinalCommit()}.
 */
public final class ForgeTxn implements EnergyPort.Txn {

	private static final ThreadLocal<ForgeTxn> OPEN = new ThreadLocal<>();

	private final boolean simulation;
	private final Map<EnergyPort.Participant, Long> snapshots = new IdentityHashMap<>();
	private final List<EnergyPort.Participant> order = new ArrayList<>();
	private final Map<Object, Map<Object, ForeignLedger>> ledgers = new IdentityHashMap<>();
	private final List<ForeignLedger> ledgerOrder = new ArrayList<>();
	private boolean settling;
	private boolean closed;

	private ForgeTxn(boolean simulation) {
		this.simulation = simulation;
	}

	/** Opens the root transaction of this thread; throws if one is already open here. */
	static ForgeTxn openRoot(boolean simulation) {
		if (OPEN.get() != null) {
			throw new IllegalStateException("A ForgeTxn is already open on this thread");
		}
		ForgeTxn txn = new ForgeTxn(simulation);
		OPEN.set(txn);
		return txn;
	}

	/**
	 * True while a transaction is open on this thread. The adapters that let another mod call into our blocks
	 * refuse such a call (return nothing) rather than nest a second journal over the same buffers: it can only be
	 * a foreign callback made from inside one of our own real calls.
	 */
	public static boolean isOpenOnThisThread() {
		return OPEN.get() != null;
	}

	/** The Forge journal behind a common transaction handle; anything else is a wiring error. */
	static ForgeTxn require(EnergyPort.Txn txn) {
		if (!(txn instanceof ForgeTxn forgeTxn)) {
			throw new IllegalArgumentException("Expected a Forge transaction handle, got: " + txn);
		}
		return forgeTxn;
	}

	/** True for a dry run: nothing may reach a foreign storage except a {@code simulate=true} call. */
	public boolean simulation() {
		return simulation;
	}

	@Override
	public void enlist(EnergyPort.Participant participant) {
		requireOpen();
		if (!snapshots.containsKey(participant)) {
			snapshots.put(participant, participant.createSnapshot());
			order.add(participant);
		}
	}

	/** The ledger of one foreign handler and resource for this transaction, created and enlisted on first use. */
	ForeignLedger ledger(Object handler, Object resource, Supplier<ForeignLedger> factory) {
		requireOpen();
		Map<Object, ForeignLedger> byResource = ledgers.computeIfAbsent(handler, h -> new HashMap<>());
		ForeignLedger ledger = byResource.get(resource);
		if (ledger == null) {
			ledger = factory.get();
			enlist(ledger);
			byResource.put(resource, ledger);
			ledgerOrder.add(ledger);
		}
		return ledger;
	}

	/**
	 * Settles the ledgers (phase one), releases this thread's root, then fires {@code onFinalCommit()} (phase two),
	 * so a hook may open the next root and a foreign mod reached from a hook is not refused — the order of NeoForge,
	 * which lowers its depth before its root-commit callbacks.
	 *
	 * <p>A foreign storage that throws in phase one cannot leave the transaction half-applied: what its own call
	 * delivered is unknown, so its promise counts as lost (never refunded — that could create); every ledger not yet
	 * settled made no real call, so its whole promise goes back to the payers; no further foreign call is made. Every
	 * participant still gets {@code onFinalCommit()}, and the first exception is rethrown at the end, later ones
	 * suppressed.
	 */
	public void commit() {
		requireOpen();
		Throwable failure = null;
		try {
			settling = true;
			failure = settleLedgers();
		} catch (RuntimeException | Error e) {
			failure = e; // our own refund failed; the participants still get their final commit
		} finally {
			close();
		}
		for (EnergyPort.Participant participant : order) {
			try {
				participant.onFinalCommit();
			} catch (RuntimeException | Error e) {
				failure = addFailure(failure, e);
			}
		}
		if (failure instanceof RuntimeException runtime) {
			throw runtime;
		}
		if (failure instanceof Error error) {
			throw error;
		}
	}

	/** Phase one; returns the first exception a foreign storage threw, or null. */
	private @Nullable Throwable settleLedgers() {
		Map<EnergyPort.Participant, Long> atSettle = new IdentityHashMap<>();
		for (EnergyPort.Participant participant : order) {
			if (!(participant instanceof ForeignLedger)) {
				atSettle.put(participant, participant.createSnapshot());
			}
		}
		Map<EnergyPort.Participant, Long> refunded = new IdentityHashMap<>();
		Throwable failure = null;
		for (ForeignLedger ledger : ledgerOrder) {
			long shortfall;
			if (failure == null) {
				try {
					shortfall = ledger.settle();
				} catch (RuntimeException | Error e) {
					failure = e;
					ForgeTransferLosses.add(ledger.kind(), ledger.dropPending());
					continue;
				}
			} else {
				shortfall = ledger.dropPending();
			}
			shortfall = refundToOwnPayers(ledger, shortfall, atSettle, refunded);
			if (failure == null) {
				try {
					shortfall = refundToForeignSources(ledger, shortfall);
				} catch (RuntimeException | Error e) {
					failure = e;
				}
			}
			ForgeTransferLosses.add(ledger.kind(), shortfall);
		}
		return failure;
	}

	private static Throwable addFailure(@Nullable Throwable first, Throwable next) {
		if (first == null) {
			return next;
		}
		first.addSuppressed(next);
		return first;
	}

	public void abort() {
		if (closed) {
			return;
		}
		try {
			closed = true;
			for (int i = order.size() - 1; i >= 0; i--) {
				EnergyPort.Participant participant = order.get(i);
				participant.readSnapshot(snapshots.get(participant));
			}
		} finally {
			close();
		}
	}

	/** How many participants this transaction touched; for the spike's measurements. */
	public int participantCount() {
		return order.size();
	}

	private long refundToOwnPayers(ForeignLedger ledger, long shortfall, Map<EnergyPort.Participant, Long> atSettle,
			Map<EnergyPort.Participant, Long> refunded) {
		for (int i = order.size() - 1; i >= 0 && shortfall > 0; i--) {
			EnergyPort.Participant own = order.get(i);
			if (own instanceof ForeignLedger || !ledger.isPayer(own)) {
				continue;
			}
			long debit = snapshots.get(own) - atSettle.get(own) - refunded.getOrDefault(own, 0L);
			if (debit <= 0) {
				continue;
			}
			long refund = Math.min(shortfall, debit);
			own.readSnapshot(own.createSnapshot() + refund);
			refunded.merge(own, refund, Long::sum);
			shortfall -= refund;
		}
		return shortfall;
	}

	private long refundToForeignSources(ForeignLedger ledger, long shortfall) {
		for (ForeignLedger source : ledgerOrder) {
			if (shortfall <= 0) {
				break;
			}
			if (source != ledger && source.kind() == ledger.kind() && source.resource() == ledger.resource()
					&& source.extractedForReal() > 0) {
				shortfall -= source.giveBack(shortfall);
			}
		}
		return shortfall;
	}

	private void close() {
		closed = true;
		if (OPEN.get() == this) {
			OPEN.remove();
		}
	}

	private void requireOpen() {
		if (closed || settling) {
			throw new IllegalStateException("Transaction already closed");
		}
	}
}
