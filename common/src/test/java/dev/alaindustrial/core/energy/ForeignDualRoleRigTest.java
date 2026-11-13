package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A foreign block that takes and gives on one face, on a line of our copper cables (MOD-940): the stands measured on
 * a copy of server 59 ({@code docs/tasks/MOD-940-foreign-mod-energy-compat/research.md}, sections 4.1, 4.4, 4.6).
 * The network sees such a block as a generator and as a consumer of unknown need at once; the line plan must not
 * route its own output back into it (ADR-055), or the cable beside it carries nothing else. A copper cable passes 12
 * EU a tick (ADR-001), so 12 EU/t is the full speed of every stand here.
 *
 * <p>The foreign blocks: an Energized Power battery (65 536 EU, 2048 a call, offers what it holds to every
 * neighbour each tick); a Tech Reborn copper cable (128 a call, hands everything on along the foreign cables behind
 * it and offers the rest back to ours).
 *
 * @implements MOD-940-SELF — a block with both roles on one face is never fed out of itself by the line plan
 */
class ForeignDualRoleRigTest {

	private static final int Z = 3;
	private static final int WARM_UP = 200;
	private static final int WINDOW = 400;
	/** Full speed of a copper line: its segment buffer a tick, less nothing (no loss on these rigs). */
	private static final long FULL = LineRig.CABLE * WINDOW;

	private static void warmUp(LineRig r) {
		for (int t = 0; t < WARM_UP; t++) {
			r.tick();
		}
	}

	/** Section 4.1, row "Ala -> Ala -> EP battery": measured 6.0 EU/t where every other source gave 12. */
	@Test
	void ourSourceFillsADualRoleBatteryThroughOurCablesAtFullSpeed() {
		LineRig r = new LineRig("ala-to-ep-battery", 0.0).source(0, Z, 512).run(1, Z, 3, Z)
				.dual("EP", 4, Z, 65_536, 2048, 0, 0, true);
		warmUp(r);
		long before = r.end("EP");
		for (int t = 0; t < WINDOW; t++) {
			r.tick();
		}
		assertEquals(FULL, r.end("EP") - before, "the battery charges at the cable's 12 EU/t, gained "
				+ (r.end("EP") - before) + " in " + WINDOW + " ticks");
	}

	/** Section 4.6: a charged EP battery -> two copper cables -> the input face of our Battery Box: measured 0. */
	@Test
	void aDualRoleBatteryFeedsOurBoxThroughOurCables() {
		LineRig r = new LineRig("ep-battery-to-box", 0.0).dual("EP", 0, Z, 65_536, 2048, 40_000, 0, true)
				.run(1, Z, 2, Z).box("BOX", 3, Z, 1_000_000);
		warmUp(r);
		long before = r.end("BOX");
		for (int t = 0; t < WINDOW; t++) {
			r.tick();
		}
		assertEquals(FULL, r.end("BOX") - before, "our box charges at the cable's 12 EU/t, gained "
				+ (r.end("BOX") - before) + " in " + WINDOW + " ticks");
	}

	/** Section 4.4: our source -> our cables -> a Tech Reborn cable (and on to a battery): measured 0. */
	@Test
	void ourCablesHandOnToAForeignCable() {
		LineRig r = new LineRig("ala-to-tr-cable", 0.0).source(0, Z, 512).run(1, Z, 2, Z)
				.dual("TR", 3, Z, 128, 128, 0, 128, true);
		warmUp(r);
		long before = r.forwarded;
		for (int t = 0; t < WINDOW; t++) {
			r.tick();
		}
		assertEquals(FULL, r.forwarded - before, "the foreign cable hands on the cable's 12 EU/t, handed on "
				+ (r.forwarded - before) + " in " + WINDOW + " ticks");
	}

	/**
	 * Section 4.4 with the Tech Reborn cable network as it ticks: it pulls from our cable beside it, then shares what
	 * it holds between the battery behind it and our cable (measured: 0 into the battery).
	 */
	@Test
	void ourCablesHandOnToATechRebornCableNetwork() {
		LineRig r = new LineRig("ala-to-tr-network", 0.0).source(0, Z, 512).run(1, Z, 2, Z)
				.foreignCable("TR", 3, Z, 256, 128);
		warmUp(r);
		long before = r.forwarded;
		for (int t = 0; t < WINDOW; t++) {
			r.tick();
		}
		assertEquals(FULL, r.forwarded - before, "the cable network hands on the cable's 12 EU/t, handed on "
				+ (r.forwarded - before) + " in " + WINDOW + " ticks");
	}

	/** The same stand with a foreign cable that never pushes back: the plan alone, no foreign tick in the way. */
	@Test
	void ourCablesHandOnToAForeignCableThatDoesNotPushBack() {
		LineRig r = new LineRig("ala-to-quiet-cable", 0.0).source(0, Z, 512).run(1, Z, 2, Z)
				.dual("Q", 3, Z, 128, 128, 0, 128, false);
		warmUp(r);
		long before = r.forwarded;
		for (int t = 0; t < WINDOW; t++) {
			r.tick();
		}
		assertTrue(r.forwarded - before >= FULL, "handed on " + (r.forwarded - before) + " in " + WINDOW + " ticks");
	}

	/** A generator and a dual-role battery beside ONE cable: the battery is fed by the generator, not by itself. */
	@Test
	void aDualRoleBlockBesideTheGeneratorsCableDoesNotStandInForIt() {
		LineRig r = new LineRig("shared-cable", 0.0).source(0, Z, 512).cable(1, Z)
				.dual("EP", 2, Z, 65_536, 2048, 0, 0, false);
		warmUp(r);
		long before = r.end("EP");
		for (int t = 0; t < WINDOW; t++) {
			r.tick();
		}
		assertEquals(FULL, r.end("EP") - before, "gained " + (r.end("EP") - before) + " in " + WINDOW + " ticks");
	}

	/**
	 * Our blocks with both roles on a face that are not storages (MOD-940, point b): the cables are part of the line,
	 * not its ends, and the crystal farm controller is {@code BOTH} with no output ({@code maxExtract = 0}). Such a
	 * block on a line with a machine and a box behaves tick for tick like the same buffer as a plain consumer.
	 */
	@Test
	void aBlockWithBothRolesAndNothingToGiveIsAPlainConsumer() {
		LineRig both = new LineRig("both-no-output", 0.0).source(0, Z, 20).run(1, Z, 4, Z).machine("M", 5, Z, 2)
				.box("B", 3, Z + 1, 1_000_000).dual("F", 2, Z + 1, 1000, 32, 0, 0, 0, false);
		LineRig plain = new LineRig("plain-consumer", 0.0).source(0, Z, 20).run(1, Z, 4, Z).machine("M", 5, Z, 2)
				.box("B", 3, Z + 1, 1_000_000).foreign("F", 2, Z + 1, 0, 1000, 32);
		for (int t = 0; t < 600; t++) {
			both.tick();
			plain.tick();
			both.work();
			plain.work();
			for (String id : new String[] {"M", "B", "F"}) {
				assertEquals(plain.end(id), both.end(id), id + " at tick " + t);
			}
		}
		assertEquals(0L, both.dualOut, "nothing was drawn out of a block with no output");
	}
}
