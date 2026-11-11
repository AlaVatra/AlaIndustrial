package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.core.energy.EnergyPort;
import java.util.function.Predicate;

/**
 * What one {@link ForgeTxn} has asked of one foreign storage for one resource — a block of another mod that only
 * speaks {@code int} calls with a {@code simulate} flag ({@code IEnergyStorage}, {@code IFluidHandler}). Energy and
 * fluid share this one algorithm; they differ only in the two calls, the resource key and who may take a refund.
 *
 * <p>The rule is asymmetric on purpose, so that a mismatch can only lose, never create:
 * <ul>
 *   <li><b>Insert is deferred.</b> Inside the transaction it is answered by {@code simulate=true} against the
 *   total promised so far; the real call happens once, in {@link #settle()}, during the first phase of
 *   {@link ForgeTxn#commit()}. What the storage then refuses is the shortfall the transaction hands back to the
 *   participants that paid for it.</li>
 *   <li><b>Extract is immediate</b> in a committing transaction: the resource is really taken out before our side
 *   is credited, so a storage that lied in simulation cannot hand us what it does not have. On abort it is offered
 *   back; what the storage refuses is counted in {@link ForgeTransferLosses}.</li>
 *   <li>In a dry run only {@code simulate=true} calls are made, and repeated extractions add up
 *   ({@code pendingSimExtract}), so two dry-run extractions cannot both see the full stock.</li>
 * </ul>
 * Amounts are {@code long} on our side and {@code int} on the foreign one: every call is clamped to
 * {@link Integer#MAX_VALUE}; a promise that reaches the ceiling simply stops growing.
 */
final class ForeignLedger implements EnergyPort.Participant {

	/** The two calls of the foreign storage, bound to one resource; keeps this class free of Forge types. */
	interface Storage {
		int insert(int amount, boolean simulate);

		int extract(int amount, boolean simulate);
	}

	/** The resource key of every energy ledger: energy has no variants. */
	static final Object ENERGY = new Object() {
		@Override
		public String toString() {
			return "energy";
		}
	};

	private final ForgeTransferLosses.Kind kind;
	private final Object resource;
	private final Storage storage;
	private final Predicate<EnergyPort.Participant> payer;
	private final boolean simulation;
	private long pendingInsert;
	private long pendingSimExtract;
	private long extractedForReal;

	private ForeignLedger(ForgeTransferLosses.Kind kind, Object resource, Storage storage,
			Predicate<EnergyPort.Participant> payer, boolean simulation) {
		this.kind = kind;
		this.resource = resource;
		this.storage = storage;
		this.payer = payer;
		this.simulation = simulation;
	}

	/** An energy ledger: a refund may go to any of our {@link EnergyBuffer}s that paid in this transaction. */
	static ForeignLedger energy(Storage storage, boolean simulation) {
		return new ForeignLedger(ForgeTransferLosses.Kind.ENERGY, ENERGY, storage, p -> p instanceof EnergyBuffer,
				simulation);
	}

	/** A fluid ledger for one fluid; {@code payer} says which of our participants held that fluid. */
	static ForeignLedger fluid(Storage storage, Object fluid, Predicate<EnergyPort.Participant> payer,
			boolean simulation) {
		return new ForeignLedger(ForgeTransferLosses.Kind.FLUID, fluid, storage, payer, simulation);
	}

	ForgeTransferLosses.Kind kind() {
		return kind;
	}

	Object resource() {
		return resource;
	}

	long insert(long maxAmount) {
		if (maxAmount <= 0) {
			return 0;
		}
		long asked = clamp(pendingInsert + clamp(maxAmount)); // both <= int max: the sum cannot wrap
		long acceptedTotal = storage.insert((int) asked, true);
		long accepted = Math.max(0, Math.min(maxAmount, acceptedTotal - pendingInsert));
		pendingInsert += accepted;
		return accepted;
	}

	long extract(long maxAmount) {
		if (maxAmount <= 0) {
			return 0;
		}
		if (simulation) {
			long asked = clamp(pendingSimExtract + clamp(maxAmount));
			long total = storage.extract((int) asked, true);
			long got = Math.max(0, Math.min(maxAmount, total - pendingSimExtract));
			pendingSimExtract += got;
			return got;
		}
		long taken = Math.max(0, Math.min(maxAmount, storage.extract((int) clamp(maxAmount), false)));
		extractedForReal += taken;
		return taken;
	}

	/** Phase one of the commit: the deferred real insert. Returns what the storage did NOT take. */
	long settle() {
		if (pendingInsert <= 0) {
			return 0;
		}
		long promised = pendingInsert;
		long received = Math.max(0, Math.min(promised, storage.insert((int) clamp(promised), false)));
		pendingInsert = 0; // only once the call returned: a storage that throws leaves its promise to dropPending
		return promised - received;
	}

	/**
	 * The promise that was never (or not knowingly) delivered, cleared without a call: after a foreign storage
	 * threw during the commit, {@link ForgeTxn} refunds a ledger not yet settled from this and counts the throwing
	 * one's as lost.
	 */
	long dropPending() {
		long promised = pendingInsert;
		pendingInsert = 0;
		return promised;
	}

	/** May {@code own} — one of our participants with a net debit in this transaction — take a refund of mine? */
	boolean isPayer(EnergyPort.Participant own) {
		return payer.test(own);
	}

	/** Drained for real in this transaction and not yet given back. */
	long extractedForReal() {
		return extractedForReal;
	}

	/** A real re-insert into this storage, at most {@link #extractedForReal()}; returns what it took. */
	long giveBack(long max) {
		long offered = Math.min(max, extractedForReal);
		if (offered <= 0) {
			return 0;
		}
		long taken = Math.max(0, Math.min(offered, storage.insert((int) clamp(offered), false)));
		extractedForReal -= taken;
		return taken;
	}

	@Override
	public long createSnapshot() {
		return 0L; // a ledger is born inside its transaction, empty
	}

	@Override
	public void readSnapshot(long snapshot) {
		pendingInsert = 0;
		pendingSimExtract = 0;
		if (extractedForReal > 0) {
			ForgeTransferLosses.add(kind, extractedForReal - giveBack(extractedForReal));
			extractedForReal = 0;
		}
	}

	@Override
	public void onFinalCommit() {
		extractedForReal = 0; // settled in phase one; what was drained is ours now
	}

	private static long clamp(long amount) {
		return Math.max(0, Math.min(amount, Integer.MAX_VALUE));
	}
}
