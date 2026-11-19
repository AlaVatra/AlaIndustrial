package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link FuelTally} (MOD-992): the burnt fuel counted by kind, the kinds shown most first with
 * the rest as one figure, and the ceiling that keeps a save from growing with every modded fuel.
 */
class FuelTallyTest {

	private static void burn(FuelTally tally, String id, int pieces) {
		for (int i = 0; i < pieces; i++) {
			tally.record(id);
		}
	}

	/** @implements MOD-992-TALLY — a new tally holds nothing, so a block writes no key for it */
	@Test
	void newTallyIsEmpty() {
		FuelTally tally = new FuelTally();
		assertTrue(tally.isEmpty());
		assertEquals(0L, tally.total());
		assertEquals(new FuelTally.Top(List.of(), 0L), tally.top(FuelTally.SHOWN_KINDS));
	}

	/** @implements MOD-992-TALLY — the owner's example: 34 coal and 12 planks read as such, coal first */
	@Test
	void countsByKindMostFirst() {
		FuelTally tally = new FuelTally();
		burn(tally, "minecraft:oak_planks", 12);
		burn(tally, "minecraft:coal", 34);
		FuelTally.Top top = tally.top(FuelTally.SHOWN_KINDS);
		assertEquals(List.of(new FuelTally.Entry("minecraft:coal", 34L),
				new FuelTally.Entry("minecraft:oak_planks", 12L)), top.kinds());
		assertEquals(0L, top.rest());
		assertEquals(46L, top.total());
	}

	/** @implements MOD-992-TALLY — beyond the shown kinds the rest is one figure, and a tie goes by id */
	@Test
	void topFoldsTheRestIntoOneFigure() {
		FuelTally tally = new FuelTally();
		burn(tally, "minecraft:stick", 3);
		burn(tally, "minecraft:coal", 10);
		burn(tally, "minecraft:bamboo", 3);
		burn(tally, "minecraft:charcoal", 7);
		FuelTally.Top top = tally.top(2);
		assertEquals(List.of(new FuelTally.Entry("minecraft:coal", 10L),
				new FuelTally.Entry("minecraft:charcoal", 7L)), top.kinds());
		assertEquals(6L, top.rest(), "3 sticks and 3 bamboo");
		assertEquals(tally.total(), top.total());
		FuelTally.Top three = tally.top(3);
		assertEquals("minecraft:bamboo", three.kinds().get(2).id(), "3 bamboo and 3 sticks tie: bamboo by id");
	}

	/** @implements MOD-992-TALLY — the table keeps MAX_KINDS kinds; a further kind counts under "other" */
	@Test
	void ceilingSendsNewKindsToOther() {
		FuelTally tally = new FuelTally();
		for (int i = 0; i < FuelTally.MAX_KINDS; i++) {
			tally.record("mod:fuel_" + i);
		}
		burn(tally, "mod:late_fuel", 5);
		burn(tally, "mod:fuel_0", 2);
		assertEquals(FuelTally.MAX_KINDS, tally.counts().size(), "the table does not grow past the ceiling");
		assertEquals(5L, tally.other(), "the late kind counts, unnamed");
		assertEquals(3L, tally.counts().get("mod:fuel_0"), "a kind already kept goes on counting by name");
		assertEquals(FuelTally.MAX_KINDS + 5L + 2L, tally.total(), "the total stays exact");
		assertEquals(tally.total(), tally.top(FuelTally.SHOWN_KINDS).total());
	}

	/** @implements MOD-992-TALLY — a save round-trips; an oversized or broken table is cut back to the ceiling */
	@Test
	void restoreKeepsTheBiggestKindsAndTheTotal() {
		FuelTally tally = new FuelTally();
		burn(tally, "minecraft:coal", 4);
		FuelTally copy = new FuelTally();
		copy.restore(tally.counts(), tally.other());
		assertEquals(tally.top(FuelTally.SHOWN_KINDS), copy.top(FuelTally.SHOWN_KINDS));

		Map<String, Long> oversized = new LinkedHashMap<>();
		long sum = 0L;
		for (int i = 0; i < FuelTally.MAX_KINDS + 4; i++) {
			oversized.put("mod:fuel_" + i, (long) (i + 1));
			sum += i + 1;
		}
		oversized.put("mod:broken", -3L);
		FuelTally cut = new FuelTally();
		cut.restore(oversized, 2L);
		assertEquals(FuelTally.MAX_KINDS, cut.counts().size());
		assertEquals(sum + 2L, cut.total(), "nothing counted is lost, the negative entry is dropped");
		assertEquals(1L + 2L + 3L + 4L + 2L, cut.other(), "the four smallest kinds move under other");
		assertTrue(cut.counts().containsKey("mod:fuel_" + (FuelTally.MAX_KINDS + 3)), "the biggest kind is kept");

		cut.restore(Map.of(), 0L);
		assertTrue(cut.isEmpty(), "an old save without the key starts from zero");
	}
}
