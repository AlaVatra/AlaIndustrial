package dev.alaindustrial.core.forge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyTransactions;
import org.junit.jupiter.api.Test;

/** The Forge journal against the real {@link EnergyBuffer} and a scripted foreign storage (MOD-962). */
class ForgeTxnTest {

	private final ForgeEnergyTransactions transactions = new ForgeEnergyTransactions();

	@Test
	void dryRunRestoresOurBufferAndCommitKeepsIt() {
		int[] commits = new int[1];
		EnergyBuffer buffer = new EnergyBuffer(10_000, 1_000, 1_000, () -> commits[0]++);

		long simulated = transactions.simulate(txn -> buffer.insert(800, txn) + buffer.insert(800, txn));
		assertEquals(1_600, simulated, "the cap is per call; both calls land inside one transaction");
		assertEquals(0, buffer.getAmount());
		assertEquals(0, commits[0]);

		transactions.runCommitting(txn -> buffer.insert(800, txn));
		assertEquals(800, buffer.getAmount());
		assertEquals(1, commits[0], "onFinalCommit fires once per committed transaction");
	}

	@Test
	void exceptionInsideCommittingBodyRollsBack() {
		EnergyBuffer buffer = new EnergyBuffer(10_000, 1_000, 1_000, () -> { });
		assertThrows(IllegalStateException.class, () -> transactions.runCommitting(txn -> {
			buffer.insert(500, txn);
			throw new IllegalStateException("boom");
		}));
		assertEquals(0, buffer.getAmount());
	}

	@Test
	void foreignInsertIsDeferredAndSharedAcrossLookups() {
		ScriptedStorage storage = new ScriptedStorage(1_000, 700);
		transactions.runCommitting(txn -> {
			ForeignLedger first = ledger(txn, storage);
			ForeignLedger second = ledger(txn, storage);
			assertEquals(500, first.insert(500));
			assertEquals(200, second.insert(500), "a second lookup cannot promise room already promised");
			assertEquals(0, storage.stored, "nothing reaches the storage before commit");
		});
		assertEquals(700, storage.stored);
	}

	/** T-J1: the owner's rule, a shortfall goes back to whoever paid for it. */
	@Test
	void foreignShortfallReturnsToPayingBuffer() {
		int[] commits = new int[1];
		EnergyBuffer buffer = charged(1_000, () -> commits[0]++);
		ScriptedStorage storage = new ScriptedStorage(1_000, 700);
		long lostBefore = ForgeTransferLosses.get(ForgeTransferLosses.Kind.ENERGY);
		transactions.runCommitting(txn -> {
			assertEquals(600, buffer.extract(600, txn));
			assertEquals(600, ledger(txn, storage).insert(600));
			storage.room = 250; // the storage changes its mind between the promise and the commit
		});
		assertEquals(250, storage.stored);
		assertEquals(750, buffer.getAmount(), "the 350 the storage refused are back in the buffer that paid");
		assertEquals(0, ForgeTransferLosses.get(ForgeTransferLosses.Kind.ENERGY) - lostBefore);
		assertEquals(1, commits[0]);
	}

	/** T-J2: a refund never exceeds the payer's own debit; the rest goes back to a foreign source we drained. */
	@Test
	void refundNeverExceedsPayerDebit() {
		assertEquals(0, refundScenario(true), "the drained foreign source takes its 500 back");
		assertEquals(500, refundScenario(false), "a source that refuses leaves 500 counted as lost");
	}

	private long refundScenario(boolean sourceTakesBack) {
		EnergyBuffer payer = charged(1_000, () -> { });
		ScriptedStorage source = new ScriptedStorage(1_000, 1_000);
		source.stored = 500;
		ScriptedStorage target = new ScriptedStorage(1_000, 700);
		long lostBefore = ForgeTransferLosses.get(ForgeTransferLosses.Kind.ENERGY);
		transactions.runCommitting(txn -> {
			payer.extract(100, txn);
			assertEquals(500, ledger(txn, source).extract(500));
			assertEquals(600, ledger(txn, target).insert(600));
			target.room = 0;
			if (!sourceTakesBack) {
				source.room = 0;
			}
		});
		assertEquals(1_000, payer.getAmount(), "the payer is back where it started, not above");
		assertEquals(0, target.stored);
		assertEquals(sourceTakesBack ? 500 : 0, source.stored);
		return ForgeTransferLosses.get(ForgeTransferLosses.Kind.ENERGY) - lostBefore;
	}

	/** T-J3: with nobody to give it back to, a shortfall is a counted loss, never a gain anywhere. */
	@Test
	void shortfallWithoutPayerIsLostNotCreated() {
		ScriptedStorage storage = new ScriptedStorage(1_000, 300);
		long lostBefore = ForgeTransferLosses.get(ForgeTransferLosses.Kind.ENERGY);
		transactions.runCommitting(txn -> {
			ledger(txn, storage).insert(300);
			storage.room = 100;
		});
		assertEquals(100, storage.stored);
		assertEquals(200, ForgeTransferLosses.get(ForgeTransferLosses.Kind.ENERGY) - lostBefore);
	}

	/** T-J4: a buffer that gained in the transaction did not pay and receives nothing. */
	@Test
	void bufferThatGainedIsNotAPayer() {
		EnergyBuffer consumer = new EnergyBuffer(10_000, 1_000, 1_000, () -> { });
		ScriptedStorage storage = new ScriptedStorage(1_000, 300);
		long lostBefore = ForgeTransferLosses.get(ForgeTransferLosses.Kind.ENERGY);
		transactions.runCommitting(txn -> {
			consumer.insert(500, txn);
			ledger(txn, storage).insert(300);
			storage.room = 0;
		});
		assertEquals(500, consumer.getAmount());
		assertEquals(300, ForgeTransferLosses.get(ForgeTransferLosses.Kind.ENERGY) - lostBefore);
	}

	/** T-J5: with two payers, the most recent debit is refunded first. */
	@Test
	void refundGoesMostRecentDebitFirst() {
		EnergyBuffer first = charged(1_000, () -> { });
		EnergyBuffer second = charged(1_000, () -> { });
		ScriptedStorage storage = new ScriptedStorage(1_000, 1_000);
		transactions.runCommitting(txn -> {
			first.extract(200, txn);
			second.extract(200, txn);
			ledger(txn, storage).insert(400);
			storage.room = 100;
		});
		assertEquals(1_000, second.getAmount(), "the later payer gets its 200 back first");
		assertEquals(900, first.getAmount(), "the earlier payer gets the remaining 100");
		assertEquals(100, storage.stored);
	}

	/** T-J6: one root per thread, and the guard is released whichever way a transaction ends. */
	@Test
	void nestedRootOnSameThreadThrows() {
		ForgeTxn root = ForgeTxn.openRoot(false);
		assertThrows(IllegalStateException.class, () -> ForgeTxn.openRoot(true));
		root.commit();
		assertDoesNotThrow(() -> ForgeTxn.openRoot(true).abort());
		assertThrows(IllegalStateException.class, () -> transactions.runCommitting(txn -> {
			throw new IllegalStateException("body failed");
		}));
		assertDoesNotThrow(() -> transactions.runCommitting(txn -> { }));
	}

	/** T-J7: another mod calling into our face from inside our own transaction gets nothing. */
	@Test
	void reverseAdapterRefusesReentrantCall() {
		EnergyTransactions.install(transactions);
		EnergyBuffer buffer = new EnergyBuffer(10_000, 1_000, 1_000, () -> { });
		PortAsEnergyStorage face = new PortAsEnergyStorage(() -> buffer);
		transactions.runCommitting(txn -> assertEquals(0, face.receiveEnergy(100, false)));
		assertEquals(0, buffer.getAmount());
		assertEquals(100, face.receiveEnergy(100, false), "outside a transaction the same call goes through");
		assertEquals(100, buffer.getAmount());
	}

	/** T-J8. */
	@Test
	void commitTwiceThrows() {
		ForgeTxn txn = ForgeTxn.openRoot(false);
		txn.commit();
		assertThrows(IllegalStateException.class, txn::commit);
	}

	/** T-J8. */
	@Test
	void abortAfterCommitIsNoop() {
		EnergyBuffer buffer = new EnergyBuffer(10_000, 1_000, 1_000, () -> { });
		ForgeTxn txn = ForgeTxn.openRoot(false);
		buffer.insert(300, txn);
		txn.commit();
		txn.abort();
		assertEquals(300, buffer.getAmount());
	}

	/** T-J9: the refund writes {@code createSnapshot() + r}; that is only right while a snapshot is the amount. */
	@Test
	void energyBufferSnapshotIsItsAmount() {
		EnergyBuffer buffer = charged(1_234, () -> { });
		assertEquals(1_234, buffer.createSnapshot());
		buffer.readSnapshot(77);
		assertEquals(77, buffer.getAmount());
	}

	@Test
	void foreignExtractIsRealAndGivenBackOnAbort() {
		ScriptedStorage storage = new ScriptedStorage(1_000, 1_000);
		storage.stored = 400;
		assertThrows(IllegalStateException.class, () -> transactions.runCommitting(txn -> {
			assertEquals(300, ledger(txn, storage).extract(300));
			assertEquals(100, storage.stored, "extraction is immediate, so a lying simulate cannot create energy");
			throw new IllegalStateException("abort");
		}));
		assertEquals(400, storage.stored, "aborted extraction is offered back");
	}

	/** T-E3: two dry-run extractions from one storage cannot both see the full stock. */
	@Test
	void dryRunExtractIsCumulative() {
		ScriptedStorage storage = new ScriptedStorage(1_000, 1_000);
		storage.stored = 400;
		transactions.simulate(txn -> {
			ForeignLedger ledger = ledger(txn, storage);
			assertEquals(300, ledger.extract(300));
			assertEquals(100, ledger.extract(300));
			return null;
		});
		assertEquals(400, storage.stored);
		assertEquals(0, storage.realCalls);
	}

	/** T-E4: what a storage refuses to take back after an abort is counted, not silently dropped. */
	@Test
	void abortGivesBackRealExtractionRefusedPartCounted() {
		ScriptedStorage storage = new ScriptedStorage(1_000, 1_000);
		storage.stored = 400;
		long lostBefore = ForgeTransferLosses.get(ForgeTransferLosses.Kind.ENERGY);
		assertThrows(IllegalStateException.class, () -> transactions.runCommitting(txn -> {
			ledger(txn, storage).extract(300);
			storage.room = 50;
			throw new IllegalStateException("abort");
		}));
		assertEquals(150, storage.stored);
		assertEquals(250, ForgeTransferLosses.get(ForgeTransferLosses.Kind.ENERGY) - lostBefore);
	}

	/** T-E5: long amounts saturate to {@code int}; a promise at the ceiling stops growing without overflow. */
	@Test
	void intSaturation() {
		ScriptedStorage storage = new ScriptedStorage(Integer.MAX_VALUE, Integer.MAX_VALUE);
		transactions.simulate(txn -> {
			ForeignLedger ledger = ledger(txn, storage);
			assertEquals(Integer.MAX_VALUE, ledger.insert(Long.MAX_VALUE));
			assertEquals(Integer.MAX_VALUE, storage.lastAsked);
			assertEquals(0, ledger.insert(10));
			assertTrue(storage.lastAsked > 0, "no overflow into a negative request");
			return null;
		});
	}

	/** A second unbounded request on a ledger that already promised something must not wrap below zero. */
	@Test
	void unboundedRequestAfterAPromiseDoesNotOverflow() {
		ScriptedStorage storage = new ScriptedStorage(1_000, 1_000);
		storage.stored = 400;
		transactions.simulate(txn -> {
			ForeignLedger ledger = ledger(txn, storage);
			assertEquals(100, ledger.insert(100));
			assertEquals(500, ledger.insert(Long.MAX_VALUE), "the rest of the room (600), not 0 from a wrapped sum");
			assertEquals(100, ledger.extract(100));
			assertEquals(300, ledger.extract(Long.MAX_VALUE));
			return null;
		});
	}

	/**
	 * A foreign storage that throws while the commit delivers: the payers of ledgers not yet settled get their
	 * whole promise back, the thrower's promise is counted lost (its delivery is unknown), no further foreign call
	 * is made, every participant still gets its final commit, the thread is free and the exception reaches the
	 * caller.
	 */
	@Test
	void foreignStorageThrowingInCommitLeavesNothingHalfApplied() {
		int[] commits = new int[2];
		EnergyBuffer first = charged(1_000, () -> commits[0]++);
		EnergyBuffer second = charged(1_000, () -> commits[1]++);
		ScriptedStorage thrower = new ScriptedStorage(1_000, 1_000);
		thrower.throwOnRealInsert = true;
		ScriptedStorage later = new ScriptedStorage(1_000, 1_000);
		long lostBefore = ForgeTransferLosses.get(ForgeTransferLosses.Kind.ENERGY);
		IllegalStateException thrown = assertThrows(IllegalStateException.class,
				() -> transactions.runCommitting(txn -> {
			first.extract(300, txn);
			ledger(txn, thrower).insert(300);
			second.extract(200, txn);
			ledger(txn, later).insert(200);
		}));
		assertEquals("foreign storage failed", thrown.getMessage());
		assertEquals(1_000, second.getAmount(), "the ledger after the thrower never delivered: its payer is refunded");
		assertEquals(0, later.stored);
		assertEquals(0, later.realCalls, "no foreign call after the failure");
		assertEquals(700, first.getAmount(), "the thrower's promise is not refunded: it may have been delivered");
		assertEquals(300, ForgeTransferLosses.get(ForgeTransferLosses.Kind.ENERGY) - lostBefore);
		assertEquals(1, commits[0], "final commit still fires");
		assertEquals(1, commits[1], "final commit still fires");
		assertDoesNotThrow(() -> transactions.runCommitting(txn -> { }), "the thread's root was released");
	}

	/** Phase two runs with the root already released, as on NeoForge: a hook may open the next transaction. */
	@Test
	void finalCommitHookMayOpenTheNextRoot() {
		boolean[] openDuringHook = {true};
		long[] movedByHook = new long[1];
		EnergyBuffer other = new EnergyBuffer(10_000, 1_000, 1_000, () -> { });
		EnergyBuffer buffer = new EnergyBuffer(10_000, 1_000, 1_000, () -> {
			openDuringHook[0] = ForgeTxn.isOpenOnThisThread();
			movedByHook[0] = transactions.simulate(txn -> other.insert(50, txn));
		});
		transactions.runCommitting(txn -> buffer.insert(100, txn));
		assertEquals(100, buffer.getAmount());
		assertEquals(false, openDuringHook[0], "no transaction is open while onFinalCommit runs");
		assertEquals(50, movedByHook[0]);
	}

	@Test
	void foreignDryRunNeverTouchesTheStorage() {
		ScriptedStorage storage = new ScriptedStorage(1_000, 1_000);
		storage.stored = 400;
		long moved = transactions.simulate(txn -> {
			ForeignLedger ledger = ledger(txn, storage);
			return ledger.extract(300) + ledger.insert(100);
		});
		assertEquals(400, moved);
		assertEquals(400, storage.stored);
		assertEquals(0, storage.realCalls);
	}

	@Test
	void closedTransactionRefusesNewParticipants() {
		ForgeTxn txn = ForgeTxn.openRoot(false);
		txn.commit();
		EnergyPort.Participant participant = new EnergyBuffer(10, 10, 10, () -> { });
		assertThrows(IllegalStateException.class, () -> txn.enlist(participant));
	}

	private static ForeignLedger ledger(EnergyPort.Txn txn, ScriptedStorage storage) {
		ForgeTxn forge = (ForgeTxn) txn;
		return forge.ledger(storage, ForeignLedger.ENERGY, () -> ForeignLedger.energy(storage, forge.simulation()));
	}

	private static EnergyBuffer charged(long amount, Runnable onCommit) {
		EnergyBuffer buffer = new EnergyBuffer(10_000, 1_000, 1_000, onCommit);
		buffer.setAmountUntracked(amount);
		return buffer;
	}

	/** A foreign {@code IEnergyStorage} stand-in whose free room the test can change mid-transaction. */
	static final class ScriptedStorage implements ForeignLedger.Storage {
		final int capacity;
		int room;
		int stored;
		int realCalls;
		int lastAsked;

		ScriptedStorage(int capacity, int room) {
			this.capacity = capacity;
			this.room = room;
		}

		boolean throwOnRealInsert;

		@Override
		public int insert(int amount, boolean simulate) {
			lastAsked = amount;
			if (!simulate && throwOnRealInsert) {
				throw new IllegalStateException("foreign storage failed");
			}
			int accepted = Math.min(amount, Math.min(room, capacity - stored));
			if (!simulate) {
				realCalls++;
				stored += accepted;
				room -= accepted;
			}
			return accepted;
		}

		@Override
		public int extract(int amount, boolean simulate) {
			int taken = Math.min(amount, stored);
			if (!simulate) {
				realCalls++;
				stored -= taken;
				room += taken;
			}
			return taken;
		}
	}
}
