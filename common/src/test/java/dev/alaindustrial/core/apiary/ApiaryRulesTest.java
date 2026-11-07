package dev.alaindustrial.core.apiary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the apiary station's arithmetic (MOD-923, slice 2): the owner's prices of 2026-11-07, the leg and
 * arc formulas, the bay choice with its tie order, the reserves held back for drones already out and the docking's
 * drain steps.
 */
class ApiaryRulesTest {

	@Test
	void flightPriceIsTheOwners() {
		assertEquals(160, ApiaryRules.launchCost(160, 4, 0.0));
		assertEquals(164, ApiaryRules.launchCost(160, 4, 1.0));
		assertEquals(220, ApiaryRules.launchCost(160, 4, 15.0), "15 blocks cost 220 EU (spec)");
		assertEquals(220, ApiaryRules.launchCost(160, 4, 14.2), "a started block counts");
		assertEquals(220, ApiaryRules.launchCost(160, 4, 15.0 - 1.0e-12), "rounding noise is not a block");
	}

	@Test
	void legIsElevenTicksABlockButNeverUnderTwentySix() {
		assertEquals(26, ApiaryRules.legTicks(0.0));
		assertEquals(26, ApiaryRules.legTicks(2.0));
		assertEquals(33, ApiaryRules.legTicks(3.0));
		assertEquals(165, ApiaryRules.legTicks(15.0));
	}

	@Test
	void arcGrowsWithDistanceBetweenItsBounds() {
		assertEquals(1.5, ApiaryRules.arcHeight(0.0), 1.0e-9);
		assertEquals(2.5, ApiaryRules.arcHeight(5.0), 1.0e-9);
		assertEquals(4.5, ApiaryRules.arcHeight(15.0), 1.0e-9);
		assertEquals(4.5, ApiaryRules.arcHeight(40.0), 1.0e-9);
	}

	@Test
	void rangeIncludesItsEdge() {
		assertTrue(ApiaryRules.inRange(15.0, 15));
		assertFalse(ApiaryRules.inRange(15.01, 15));
	}

	@Test
	void nearestFreeBayFliesAndTiesGoNorthEastSouthWest() {
		boolean[] all = {true, true, true, true};
		assertEquals(2, ApiaryRules.chooseBay(new double[] {9, 9, 4, 9}, all));
		assertEquals(1, ApiaryRules.chooseBay(new double[] {9, 4, 9, 4}, all), "east before west on a tie");
		assertEquals(0, ApiaryRules.chooseBay(new double[] {4, 4, 4, 4}, all), "north wins a four-way tie");
		assertEquals(3, ApiaryRules.chooseBay(new double[] {1, 2, 3, 4}, new boolean[] {false, false, false, true}),
				"a busy or empty bay is skipped however near it is");
		assertEquals(-1, ApiaryRules.chooseBay(new double[] {1, 2, 3, 4}, new boolean[4]));
	}

	@Test
	void reservesHoldBackWhatTheDronesOutMayNeed() {
		assertTrue(ApiaryRules.energyCovers(400, 0, 220));
		assertFalse(ApiaryRules.energyCovers(400, 220, 220), "one flight's price is held for the drone already out");
		assertTrue(ApiaryRules.energyCovers(440, 220, 220));

		assertTrue(ApiaryRules.tankHasRoom(10_000, 9_750, 0, 250));
		assertFalse(ApiaryRules.tankHasRoom(10_000, 9_750, 1, 250), "the drone out may bring a full hive back");
		assertFalse(ApiaryRules.tankHasRoom(10_000, 9_800, 0, 250));

		assertTrue(ApiaryRules.combSlotHasRoom(63, 64, 0));
		assertFalse(ApiaryRules.combSlotHasRoom(63, 64, 1));
		assertFalse(ApiaryRules.combSlotHasRoom(64, 64, 0));
	}

	@Test
	void combChanceZeroNeverOneAlways() {
		assertFalse(ApiaryRules.combWon(0.0, 0.0));
		assertTrue(ApiaryRules.combWon(1.0, 0.9999));
		assertTrue(ApiaryRules.combWon(0.1, 0.05));
		assertFalse(ApiaryRules.combWon(0.1, 0.1));
	}

	@Test
	void dockingStepsTheJarFiveTimesWhilePumping() {
		int dock = 40;
		assertEquals(8, ApiaryRules.stingTicks(dock));
		assertEquals(0, ApiaryRules.drainStepsDone(0, dock));
		assertEquals(0, ApiaryRules.drainStepsDone(8, dock), "nothing while the sting goes down");
		int previous = 0;
		for (int t = 0; t <= dock; t++) {
			int done = ApiaryRules.drainStepsDone(t, dock);
			assertTrue(done >= previous && done <= previous + 1, "one step at a time at tick " + t);
			previous = done;
		}
		assertEquals(5, ApiaryRules.drainStepsDone(32, dock), "all five by the time the sting comes up");
		assertEquals(5, ApiaryRules.drainStepsDone(dock, dock));
	}

	@Test
	void stingAndBellyFollowTheDocking() {
		int dock = 40;
		assertEquals(0.0F, ApiaryRules.stingPose(0, dock), 1.0e-6);
		assertEquals(1.0F, ApiaryRules.stingPose(8, dock), 1.0e-6);
		assertEquals(1.0F, ApiaryRules.stingPose(20, dock), 1.0e-6);
		assertEquals(0.0F, ApiaryRules.stingPose(40, dock), 1.0e-6);
		assertEquals(0.0F, ApiaryRules.bellyWhileDocking(8, dock), 1.0e-6);
		assertEquals(0.5F, ApiaryRules.bellyWhileDocking(20, dock), 1.0e-6);
		assertEquals(1.0F, ApiaryRules.bellyWhileDocking(32, dock), 1.0e-6);
	}
}
