package dev.alaindustrial.core.elevator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.core.elevator.ElevatorFare.Tariff;
import org.junit.jupiter.api.Test;

/** What an elevator trip costs (MOD-769): the whole trip paid up front, a descent pays the brake only. */
class ElevatorFareTest {

	/** The defaults of the config knobs, written out so a change to them is a visible change here. */
	private static final Tariff TARIFF = new Tariff(50, 8, 4, 0.25D);

	@Test
	void sixtyFourBlocksUpWithOnePassengerCostEightHundredEighteen() {
		// The research's worked example: 50 + 64 x (8 + 4).
		assertEquals(818L, ElevatorFare.fare(TARIFF, 64, true, 1));
	}

	@Test
	void anEmptyCabinPaysOnlyForItsOwnWeight() {
		assertEquals(50L + 8L * 10L, ElevatorFare.fare(TARIFF, 10, true, 0));
		assertEquals(50L + 16L * 10L, ElevatorFare.fare(TARIFF, 10, true, 2));
	}

	@Test
	void aDescentPaysTheLaunchAndTheBrake() {
		assertEquals(50L + 192L, ElevatorFare.fare(TARIFF, 64, false, 1));
	}

	@Test
	void theBrakeRoundsUp() {
		// 3 blocks x 8 EU = 24, a quarter is 6; 2 blocks x 5 EU = 10, a quarter is 2.5, paid as 3.
		assertEquals(56L, ElevatorFare.fare(TARIFF, 3, false, 0));
		assertEquals(53L, ElevatorFare.fare(new Tariff(50, 5, 0, 0.25D), 2, false, 0));
	}
}
