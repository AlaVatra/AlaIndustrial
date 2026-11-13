package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link SmeltingExperience} (MOD-971): the vanilla furnace's rounding of banked experience. Each
 * case fails on a different wrong rule: dropping the fraction, always rounding it up, or paying it on
 * {@code roll >= fraction}.
 */
class SmeltingExperienceTest {

	@Test
	void fractionIsPaidWhenTheRollIsBelowIt() {
		// 3 x 0.7 = 2.1: two whole points, the third with probability 0.1.
		assertEquals(3, SmeltingExperience.points(3, 0.7F, 0.05F));
		assertEquals(2, SmeltingExperience.points(3, 0.7F, 0.5F));
	}

	@Test
	void wholeTotalIgnoresTheRoll() {
		assertEquals(1, SmeltingExperience.points(2, 0.5F, 0.0F));
		assertEquals(1, SmeltingExperience.points(2, 0.5F, 0.999F));
		assertEquals(4, SmeltingExperience.points(4, 1.0F, 0.0F));
	}

	@Test
	void nothingSmeltedPaysNothing() {
		assertEquals(0, SmeltingExperience.points(0, 0.7F, 0.0F));
		assertEquals(0, SmeltingExperience.points(5, 0.0F, 0.0F));
	}

	@Test
	void theFractionBoundaryIsStrict() {
		// 1 x 0.1: the point is paid only for a roll strictly below 0.1.
		assertEquals(1, SmeltingExperience.points(1, 0.1F, 0.0999F));
		assertEquals(0, SmeltingExperience.points(1, 0.1F, 0.1F));
	}
}
