package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link GeothermalLava}: the geothermal generator's lava folded into one mB volume for its
 * dropped item and unfolded again on placement (MOD-998).
 *
 * @implements MOD-998 — the whole lava (tank + burn reserve) rides the item as one volume in mB
 */
class GeothermalLavaTest {
	private static final int BURN = 1000;
	private static final int RESERVE_CAP = 10 * BURN;
	private static final long TANK_CAP = 10_000L;

	@Test
	void reserveIsCountedInMillibucketsRoundedDown() {
		assertEquals(1000L, GeothermalLava.reserveMb(1000, 1000), "one bucket of burn is 1000 mB");
		assertEquals(666L, GeothermalLava.reserveMb(999, 1500), "a part bucket rounds down");
		assertEquals(0L, GeothermalLava.reserveMb(-5, 1000), "a negative reserve counts as none");
		assertEquals(5000L, GeothermalLava.reserveMb(5, 1), "a burn of one tick per bucket");
	}

	@Test
	void totalIsTankPlusReserve() {
		assertEquals(7250L, GeothermalLava.totalMb(3000L, 4250, BURN), "tank 3000 + reserve 4250");
		assertEquals(0L, GeothermalLava.totalMb(0L, 0, BURN), "an empty generator holds nothing");
	}

	@Test
	void splitFillsTheReserveFirstThenTheTank() {
		GeothermalLava.Split split = GeothermalLava.split(14_500L, RESERVE_CAP, TANK_CAP, BURN);
		assertEquals(RESERVE_CAP, split.lavaTicks(), "the reserve fills to its cap first");
		assertEquals(4_500L, split.tankMb(), "the rest goes into the tank");

		GeothermalLava.Split small = GeothermalLava.split(750L, RESERVE_CAP, TANK_CAP, BURN);
		assertEquals(750, small.lavaTicks(), "a small volume sits in the reserve only");
		assertEquals(0L, small.tankMb(), "and leaves the tank empty");
	}

	@Test
	void splitDropsWhatDoesNotFitLikeThePortableTank() {
		GeothermalLava.Split split = GeothermalLava.split(25_000L, RESERVE_CAP, TANK_CAP, BURN);
		assertEquals(RESERVE_CAP, split.lavaTicks(), "the reserve is full");
		assertEquals(TANK_CAP, split.tankMb(), "the tank is clamped to its capacity");
	}

	@Test
	void roundTripNeverCreatesLava() {
		for (int burn : new int[] {1, 7, 999, 1000, 1001, 1500, 4000}) {
			int cap = 10 * burn;
			for (int ticks = 0; ticks <= cap; ticks += Math.max(1, cap / 97)) {
				for (long tank = 0; tank <= TANK_CAP; tank += 2_500L) {
					long before = GeothermalLava.totalMb(tank, ticks, burn);
					GeothermalLava.Split split = GeothermalLava.split(before, cap, TANK_CAP, burn);
					long after = GeothermalLava.totalMb(split.tankMb(), split.lavaTicks(), burn);
					assertTrue(after <= before, "burn=" + burn + " ticks=" + ticks + " tank=" + tank
							+ ": a round trip made lava " + before + " -> " + after);
					assertTrue(split.lavaTicks() <= cap, "the reserve never exceeds its cap");
				}
			}
		}
	}

	@Test
	void roundTripIsExactAtTheDefaultBurn() {
		for (int ticks = 0; ticks <= RESERVE_CAP; ticks += 333) {
			long before = GeothermalLava.totalMb(6_000L, ticks, BURN);
			GeothermalLava.Split split = GeothermalLava.split(before, RESERVE_CAP, TANK_CAP, BURN);
			assertEquals(before, GeothermalLava.totalMb(split.tankMb(), split.lavaTicks(), BURN),
					"at 1000 ticks per bucket one tick is one mB, so nothing is lost");
		}
	}
}
