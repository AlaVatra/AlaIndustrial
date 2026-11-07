package dev.alaindustrial.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link SecondaryChance} (MOD-960): the roll that decides whether an operation pays the
 * recipe's bonus output. Pure math, no Minecraft on the classpath.
 */
class SecondaryChanceTest {

	/** A source that fails the test when it is drawn from at all. */
	private static final DoubleSupplier NEVER_DRAWN = () -> {
		throw new AssertionError("a chance of 1 or more must not draw from the random source");
	};

	@Test
	void chanceOfOneOrMoreIsGrantedWithoutDrawing() {
		assertTrue(SecondaryChance.granted(1.0, NEVER_DRAWN));
		assertTrue(SecondaryChance.granted(1.5, NEVER_DRAWN));
	}

	@Test
	void drawBelowTheChanceGrants() {
		assertTrue(SecondaryChance.granted(0.25, () -> 0.0));
		assertTrue(SecondaryChance.granted(0.25, () -> 0.2499));
	}

	/** The threshold itself does not pay: a draw in [0, 1) below 0.25 is a quarter of the range. */
	@Test
	void drawAtOrAboveTheChanceDoesNotGrant() {
		assertFalse(SecondaryChance.granted(0.25, () -> 0.25));
		assertFalse(SecondaryChance.granted(0.25, () -> 0.9999));
	}

	@Test
	void chanceOfZeroOrLessNeverGrants() {
		assertFalse(SecondaryChance.granted(0.0, () -> 0.0));
		assertFalse(SecondaryChance.granted(-1.0, () -> 0.0));
	}

	@Test
	void tinyChancePaysOnlyBelowItsThreshold() {
		assertTrue(SecondaryChance.granted(0.0001, () -> 0.00005));
		assertFalse(SecondaryChance.granted(0.0001, () -> 0.0001));
	}
}
