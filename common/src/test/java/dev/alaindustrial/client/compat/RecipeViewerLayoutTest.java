package dev.alaindustrial.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RecipeViewerLayoutTest {

	@Test
	void slotPositionsAreOrderedAndSharedForOneToThreeSlots() {
		assertEquals(java.util.List.of(38), RecipeViewerLayout.inputXs(1));
		assertEquals(java.util.List.of(27, 49), RecipeViewerLayout.inputXs(2));
		// MOD-064: the alloy smelter needs three, and they must stay clear of the arrow.
		assertEquals(java.util.List.of(4, 25, 46), RecipeViewerLayout.inputXs(3));
		assertEquals(java.util.List.of(95), RecipeViewerLayout.outputXs(1));
		assertEquals(java.util.List.of(84, 106), RecipeViewerLayout.outputXs(2));
	}

	@Test
	void slotCountsOutsideTheSupportedContractFailLoudly() {
		assertThrows(IllegalArgumentException.class, () -> RecipeViewerLayout.inputXs(0));
		assertThrows(IllegalArgumentException.class, () -> RecipeViewerLayout.inputXs(4));
		assertThrows(IllegalArgumentException.class, () -> RecipeViewerLayout.outputXs(0));
		assertThrows(IllegalArgumentException.class, () -> RecipeViewerLayout.outputXs(3));
	}

	@Test
	void threeInputSlotsFitLeftOfTheArrow() {
		// A slot is 18 px wide; the last one may touch ARROW_X but must not pass it, or the third
		// component would be drawn underneath the progress arrow.
		int lastSlotRight = RecipeViewerLayout.inputXs(3).getLast() + 18;
		org.junit.jupiter.api.Assertions.assertTrue(lastSlotRight <= RecipeViewerLayout.ARROW_X,
				"three input slots overflow into the arrow: right edge " + lastSlotRight
						+ " > ARROW_X " + RecipeViewerLayout.ARROW_X);
	}

	@Test
	void labelsHaveOneLoaderNeutralFormat() {
		assertEquals("400 EU · 10 s", RecipeViewerLayout.costLabel(400, 200));
		assertEquals("401 EU · 10.1 s", RecipeViewerLayout.costLabel(401, 201));
		assertEquals("400 EU · 10 s · 1000 mB", RecipeViewerLayout.fluidCostLabel(400, 200, 1000));
		assertEquals("400 EU · 10 s · 45 %",
				RecipeViewerLayout.withChance(RecipeViewerLayout.costLabel(400, 200), 0.45));
		assertEquals("400 EU · 10 s",
				RecipeViewerLayout.withChance(RecipeViewerLayout.costLabel(400, 200), -1.0));
	}

	/** MOD-960: the bonus-slot tooltip states a gamble only, and never shows a real chance as 0 or 100. */
	@Test
	void secondaryChanceIsStatedOnlyBelowOneAndNeverRoundedToCertainty() {
		org.junit.jupiter.api.Assertions.assertFalse(RecipeViewerLayout.showsSecondaryChance(1.0));
		org.junit.jupiter.api.Assertions.assertTrue(RecipeViewerLayout.showsSecondaryChance(0.25));
		assertEquals("25", RecipeViewerLayout.secondaryChancePercent(0.25));
		assertEquals("1", RecipeViewerLayout.secondaryChancePercent(0.01));
		assertEquals("99", RecipeViewerLayout.secondaryChancePercent(0.999));
		assertEquals("0.5", RecipeViewerLayout.secondaryChancePercent(0.005));
		assertEquals("0.01", RecipeViewerLayout.secondaryChancePercent(0.0001));
		assertEquals("0", RecipeViewerLayout.secondaryChancePercent(0.0));
	}
}
