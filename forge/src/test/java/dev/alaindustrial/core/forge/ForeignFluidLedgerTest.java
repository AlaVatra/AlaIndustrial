package dev.alaindustrial.core.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.forge.ForgeTxnTest.ScriptedStorage;
import org.junit.jupiter.api.Test;

/**
 * The journal's fluid side (MOD-962): the same ledger algorithm as energy, keyed by fluid, with the refund limited
 * to our tanks that held that fluid. The fluid is an opaque key here and the tanks are fakes, so no Minecraft
 * class is needed; {@code FluidTank}'s own snapshot contract is checked in the live-world probe (P4).
 */
class ForeignFluidLedgerTest {

	private static final Object WATER = "water";
	private static final Object LAVA = "lava";

	private final ForgeEnergyTransactions transactions = new ForgeEnergyTransactions();
	private final FakeTank waterTank = new FakeTank(4_000);
	private final FakeTank lavaTank = new FakeTank(4_000);

	/** T-F1: a fill is deferred to commit and one promise is shared by every lookup of the same handler. */
	@Test
	void deferredFillSharedAcrossLookups() {
		ScriptedStorage handler = new ScriptedStorage(8_000, 1_500);
		transactions.runCommitting(txn -> {
			assertEquals(1_000, ledger(txn, handler, WATER).insert(1_000));
			assertEquals(500, ledger(txn, handler, WATER).insert(1_000), "the second promise sees the first");
			assertEquals(0, handler.stored);
		});
		assertEquals(1_500, handler.stored);
	}

	/** T-F2: a shortfall returns to the tank of that fluid only. */
	@Test
	void shortfallReturnsToTankOfThatFluidOnly() {
		ScriptedStorage handler = new ScriptedStorage(8_000, 8_000);
		long lostBefore = ForgeTransferLosses.get(ForgeTransferLosses.Kind.FLUID);
		transactions.runCommitting(txn -> {
			lavaTank.drain(txn, 1_000);
			waterTank.drain(txn, 1_000);
			ledger(txn, handler, WATER).insert(1_000);
			handler.room = 400;
		});
		assertEquals(400, handler.stored);
		assertEquals(3_600, waterTank.amount, "the water tank paid and gets the 600 back");
		assertEquals(3_000, lavaTank.amount, "the lava tank paid for nothing in this ledger");
		assertEquals(0, ForgeTransferLosses.get(ForgeTransferLosses.Kind.FLUID) - lostBefore);
	}

	/** T-F3: an aborted drain is given back; what the handler refuses is counted as a fluid loss. */
	@Test
	void abortReturnsRealDrainRefusedPartCounted() {
		ScriptedStorage handler = new ScriptedStorage(8_000, 8_000);
		handler.stored = 2_000;
		long lostBefore = ForgeTransferLosses.get(ForgeTransferLosses.Kind.FLUID);
		assertThrows(IllegalStateException.class, () -> transactions.runCommitting(txn -> {
			assertEquals(1_000, ledger(txn, handler, WATER).extract(1_000));
			handler.room = 300;
			throw new IllegalStateException("abort");
		}));
		assertEquals(1_300, handler.stored);
		assertEquals(700, ForgeTransferLosses.get(ForgeTransferLosses.Kind.FLUID) - lostBefore);
	}

	/** T-F4: a dry run makes no real call, and its drains add up. */
	@Test
	void dryRunMakesNoRealCallAndDrainsAddUp() {
		ScriptedStorage handler = new ScriptedStorage(8_000, 8_000);
		handler.stored = 1_500;
		transactions.simulate(txn -> {
			ForeignLedger ledger = ledger(txn, handler, WATER);
			assertEquals(1_000, ledger.extract(1_000));
			assertEquals(500, ledger.extract(1_000));
			assertEquals(1_000, ledger.insert(1_000));
			return null;
		});
		assertEquals(0, handler.realCalls);
		assertEquals(1_500, handler.stored);
	}

	private ForeignLedger ledger(EnergyPort.Txn txn, ScriptedStorage handler, Object fluid) {
		ForgeTxn forge = (ForgeTxn) txn;
		FakeTank payer = fluid == WATER ? waterTank : lavaTank;
		return forge.ledger(handler, fluid,
				() -> ForeignLedger.fluid(handler, fluid, p -> p == payer, forge.simulation()));
	}

	/** One of our tanks as the journal sees it: a snapshot that is its amount. */
	private static final class FakeTank implements EnergyPort.Participant {
		long amount;

		FakeTank(long amount) {
			this.amount = amount;
		}

		void drain(EnergyPort.Txn txn, long mb) {
			txn.enlist(this);
			amount -= mb;
		}

		@Override
		public long createSnapshot() {
			return amount;
		}

		@Override
		public void readSnapshot(long snapshot) {
			amount = snapshot;
		}

		@Override
		public void onFinalCommit() {
		}
	}
}
