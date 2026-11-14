package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Our copper cable next to another mod's cable (MOD-940, round 2): the rows of section 4.4 of
 * {@code docs/tasks/MOD-940-foreign-mod-energy-compat/research.md}, measured on a copy of server 59 with the released
 * 0.1.234, played on {@link ForeignJunctionRow} — our networks through the production kernel, the foreign blocks by
 * the contracts read from their jars, in the server's tick order.
 *
 * <p>These tests pin TODAY's behaviour, which is the defect: the rig gives the very numbers the server gave (12 EU/t is
 * the full speed of a copper line, ADR-001). A change to how our cable meets a foreign block is expected to move them —
 * then this class is updated with the decision, not loosened. {@link #variantInboundOnly()} records what the variant
 * the research recommends would give on the same rows; it is a model prediction until the server is measured again.
 */
class ForeignCableJunctionRigTest {

	private static final int WARM_UP = 100;
	private static final int WINDOW = 400;

	/** EU a tick the last block of {@code row} gained over the window, after the warm-up — the server's method. */
	private static double rate(String row, ForeignJunctionRow.OurFace face) {
		ForeignJunctionRow r = new ForeignJunctionRow(row, face);
		for (int t = 0; t < WARM_UP; t++) {
			r.tick();
		}
		long before = r.sink();
		for (int t = 0; t < WINDOW; t++) {
			r.tick();
		}
		return (r.sink() - before) / (double) WINDOW;
	}

	private static void today(String row, double server) {
		assertEquals(server, rate(row, ForeignJunctionRow.OurFace.BOTH), 0.05,
				row + " on server 59: " + server + " EU/t");
	}

	/** Control, section 4.1: our source, three of our cables, the Energized Power battery — 12 since ADR-055. */
	@Test
	void ourLineIntoABatteryRunsAtFullSpeed() {
		today("SaaaX", 12.0);
	}

	/** A foreign cable network feeding our cable works: Tech Reborn fills our first segment every tick. */
	@Test
	void techRebornCablesIntoOurCablesWork() {
		today("PTTaaX", 12.0);
	}

	/**
	 * Our cable into a Tech Reborn cable: 0. The Tech Reborn network pulls our segment empty, then hands an even share
	 * back to every target, least room first — our segment is first and gets back all it gave while the network holds
	 * twice our buffer; the battery behind is pulled and refilled the same way and settles. The face it moved
	 * anything through stays closed until its next tick, so our network, which ticks last, can neither push into nor
	 * pull from it.
	 */
	@Test
	void ourCablesIntoTechRebornCablesCarryNothing() {
		today("SaaTTX", 0.0);
		today("SaTaTX", 0.0);
	}

	/**
	 * The same into a Tech Reborn storage unit, which takes and never gives: the even share sends it half of what the
	 * network pulled out of our segment. Our lone segment between two Tech Reborn cables is averaged with the full one
	 * before it (x = (12 + x / 2) / 2, x = 8), and half of 8 is the 4 EU/t the server measured.
	 */
	@Test
	void ourCablesIntoAStorageUnitGetHalfOfTheAverage() {
		today("SaTaTU", 4.0);
	}

	/**
	 * Tech Reborn cables with lone segments of ours between them: 0. A lone segment has no producer this network can
	 * use — the Tech Reborn faces beside it are closed for the tick — so {@code EnergyNetwork.tick} leaves what it
	 * holds where it is; at the battery, the segment our network empties is refilled by the battery's own push.
	 */
	@Test
	void loneSegmentsBetweenTechRebornCablesCarryNothing() {
		today("PTaTaX", 0.0);
		today("PTaTaB", 0.0);
	}

	/**
	 * Energized Power cables mixed with ours: 0. From outside such a cable only takes; its network pulls from every
	 * neighbour that gives — our segment too — and shares what it holds evenly between every neighbour that takes.
	 */
	@Test
	void energizedPowerCablesMixedWithOursCarryNothing() {
		today("CeaeaX", 0.0);
		today("SaeaeX", 0.0);
	}

	/**
	 * Model prediction for the recommended variant (research.md, 2026-11-14): our cable takes from a foreign block but
	 * gives it nothing on request. Every Tech Reborn row reaches full speed except the lone segment before the
	 * battery; the Energized Power rows stay at 0; the foreign-into-ours direction keeps its 12.
	 */
	@Test
	void variantInboundOnly() {
		ForeignJunctionRow.OurFace in = ForeignJunctionRow.OurFace.IN;
		for (String row : new String[] {"SaaaX", "PTTaaX", "SaaTTX", "SaTaTX", "SaTaTU", "PTaTaB"}) {
			assertEquals(12.0, rate(row, in), 0.05, row);
		}
		for (String row : new String[] {"PTaTaX", "CeaeaX", "SaeaeX"}) {
			assertEquals(0.0, rate(row, in), 0.05, row);
		}
	}
}
