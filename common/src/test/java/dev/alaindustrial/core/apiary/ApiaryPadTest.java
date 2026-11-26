package dev.alaindustrial.core.apiary;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The apiary station's geometry and its exact edges (MOD-923): where each bay's drone parks and which way it
 * faces, the range and the nearest-bay tie at the tolerance itself, and the sting's pose while it goes down and
 * comes up.
 *
 * @implements MOD-976 the mutation gate kills the apiary pad geometry and the tolerance edges
 */
class ApiaryPadTest {

	private static final double EPS = 1.0e-12;

	@Test
	void eachBayParksOnItsOwnSideAtItsOwnHeight() {
		double reach = ApiaryRules.PAD_REACH;
		double low = 1.0 + ApiaryRules.PAD_TOP_LOW;
		double high = 1.0 + ApiaryRules.PAD_TOP_HIGH;
		assertArrayEquals(new double[] {0.5, low, 0.5 - reach}, ApiaryRules.padSpot(0), EPS, "north, low");
		assertArrayEquals(new double[] {0.5 + reach, high, 0.5}, ApiaryRules.padSpot(1), EPS, "east, high");
		assertArrayEquals(new double[] {0.5, low, 0.5 + reach}, ApiaryRules.padSpot(2), EPS, "south, low");
		assertArrayEquals(new double[] {0.5 - reach, high, 0.5}, ApiaryRules.padSpot(3), EPS, "west, high");
		assertArrayEquals(ApiaryRules.padSpot(3), ApiaryRules.padSpot(-1), EPS, "a bay index wraps round");
		assertArrayEquals(ApiaryRules.padSpot(0), ApiaryRules.padSpot(4), EPS);
	}

	@Test
	void eachDroneFacesOutOfItsBay() {
		assertEquals(0.0F, ApiaryRules.padYawDegrees(0), 1.0e-6F);
		assertEquals(-90.0F, ApiaryRules.padYawDegrees(1), 1.0e-6F);
		assertEquals(-180.0F, ApiaryRules.padYawDegrees(2), 1.0e-6F);
		assertEquals(-270.0F, ApiaryRules.padYawDegrees(3), 1.0e-6F);
		assertEquals(-90.0F, ApiaryRules.padYawDegrees(5), 1.0e-6F);
	}

	@Test
	void rangeAndNearestBayAreDecidedAtTheToleranceItself() {
		assertTrue(ApiaryRules.inRange(5 + 1.0e-9, 5), "a jar at the range plus the tolerance is in range");
		assertFalse(ApiaryRules.inRange(5 + 1.0e-6, 5));
		// The second bay is nearer by exactly the tolerance: that is still a tie, and the tie goes to the first.
		assertEquals(0, ApiaryRules.chooseBay(new double[] {1.0, 1.0 - 1.0e-9}, new boolean[] {true, true}));
		assertEquals(1, ApiaryRules.chooseBay(new double[] {1.0, 0.5}, new boolean[] {true, true}));
	}

	@Test
	void stingGoesDownAndComesUpOverItsOwnTicks() {
		int dock = 50; // sting 10 ticks each way
		assertEquals(0.5F, ApiaryRules.stingPose(5, dock), 1.0e-6F, "half way down");
		assertEquals(1.0F, ApiaryRules.stingPose(25, dock), 1.0e-6F, "in the funnel while pumping");
		assertEquals(0.5F, ApiaryRules.stingPose(45, dock), 1.0e-6F, "half way up");
		assertEquals(0.0F, ApiaryRules.stingPose(50, dock), 1.0e-6F, "tucked away at the end");
	}
}
