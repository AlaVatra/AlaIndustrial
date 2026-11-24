package dev.alaindustrial.core.escalator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests of the escalator's arithmetic (MOD-969): the physical steps, the draw and the band's push.
 *
 * @implements MOD-969-L1 escalator steps, draw and velocity rule
 */
class EscalatorRulesTest {

	private static final double EPS = 1e-9;

	@Test
	void stepsRiseTwoPixelsAndStayInsideTheCell() {
		assertEquals(1, EscalatorRules.stepTopPixels(0), "the entry step is 1 px off the floor");
		assertEquals(15, EscalatorRules.stepTopPixels(EscalatorRules.SUBSTEPS - 1),
				"the highest step stays below 16 px, so a rider's feet are inside the section");
		for (int k = 1; k < EscalatorRules.SUBSTEPS; k++) {
			assertEquals(2, EscalatorRules.stepTopPixels(k) - EscalatorRules.stepTopPixels(k - 1));
		}
		// The next section starts 16 px higher: 15 -> 17 is the same 2 px riser.
		assertEquals(2, 16 + EscalatorRules.stepTopPixels(0)
				- EscalatorRules.stepTopPixels(EscalatorRules.SUBSTEPS - 1));
		assertThrows(IllegalArgumentException.class, () -> EscalatorRules.stepTopPixels(EscalatorRules.SUBSTEPS));
	}

	@Test
	void drawIsFixedPartPlusHalfAnEuPerSection() {
		assertEquals(5000L, EscalatorRules.milliEuPerTick(1, 500, 8), "8 LV sections: 5 EU/t");
		assertEquals(10000L, EscalatorRules.milliEuPerTick(2, 500, 16), "16 MV sections: 10 EU/t");
		assertEquals(1000L, EscalatorRules.milliEuPerTick(1, 500, -3), "no chain: the fixed part only");
	}

	@Test
	void standingRiderIsRaisedToBandSpeedAlongTheSlope() {
		double[] v = EscalatorRules.carry(0, 0, 0, 1, 0, false, 0.1);
		assertArrayEquals(new double[] {0.1, 0.1, 0.0}, v, EPS, "up east: 0.1 east, 0.1 up");
		v = EscalatorRules.carry(0, 0, 0, 0, -1, true, 0.1);
		assertArrayEquals(new double[] {0.0, -0.1, 0.1}, v, EPS, "facing north, running down: south and down");
	}

	@Test
	void walkingWithTheBandIsFasterAndNeverSlowed() {
		double[] v = EscalatorRules.carry(0.2, 0, 0, 1, 0, false, 0.1);
		assertEquals(0.2, v[0], EPS, "a rider walking faster than the band keeps his speed");
		assertEquals(0.2, v[1], EPS, "and climbs the 1:1 slope at it");
		v = EscalatorRules.carry(-0.2, 0, 0, 1, 0, false, 0.1);
		assertEquals(0.1, v[0], EPS, "against a moving band the rider is held to the band's speed");
	}

	@Test
	void sideSpeedIsKeptSoTheRiderCanStepOff() {
		double[] v = EscalatorRules.carry(0, 0, 0.3, 1, 0, false, 0.1);
		assertEquals(0.3, v[2], EPS);
		assertTrue(v[0] >= 0.1 - EPS);
	}

	@Test
	void jumpingIsNotCutAndFallingFasterIsNotSlowed() {
		assertEquals(0.4, EscalatorRules.carry(0, 0.4, 0, 1, 0, false, 0.1)[1], EPS);
		assertEquals(-0.5, EscalatorRules.carry(0, -0.5, 0, 1, 0, true, 0.1)[1], EPS);
	}
}
