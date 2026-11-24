package dev.alaindustrial.core.elevator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The elevator cabin's speed profile (MOD-769): a trapezoid, the same on the server and on every client. */
class ElevatorTripTest {

	private static final double EPS = 1.0E-9D;

	@Test
	void aLongTripRampsUpCruisesAndRampsDown() {
		// 10 blocks at 0.2 b/t with a 10-tick ramp: 1 block on each ramp, 8 blocks of cruise = 40 ticks; 60 in all.
		ElevatorTrip trip = new ElevatorTrip(100L, 64.0D, 74.0D, 0.2D, 10, false);
		assertEquals(60, trip.ticks());
		assertEquals(160L, trip.end());
		assertEquals(64.0D, trip.heightAt(100.0D), EPS);
		assertEquals(65.0D, trip.heightAt(110.0D), EPS, "the ramp covers half of speed x ramp ticks");
		assertEquals(69.0D, trip.heightAt(130.0D), EPS, "cruise at full speed");
		assertEquals(73.0D, trip.heightAt(150.0D), EPS);
		assertEquals(74.0D, trip.heightAt(160.0D), EPS);
		assertEquals(74.0D, trip.heightAt(9999.0D), EPS, "a finished trip stays at its end");
		assertEquals(0.2D, trip.velocityAt(130.0D), EPS);
	}

	@Test
	void theCabinNeverMovesFasterThanItsTopSpeed() {
		ElevatorTrip trip = new ElevatorTrip(0L, 10.0D, 70.0D, 0.2D, 10, false);
		for (int t = -5; t < trip.ticks() + 5; t++) {
			double v = trip.velocityAt(t);
			assertTrue(v >= -EPS && v <= 0.2D + EPS, "speed at tick " + t + " was " + v);
		}
	}

	@Test
	void aShortTripIsATriangleThatStillEndsWhereItShould() {
		// 1 block at 0.2 b/t with a 10-tick ramp never reaches full speed: peak sqrt(1 * 0.02), 2 x peak / a ticks.
		ElevatorTrip trip = new ElevatorTrip(0L, 5.0D, 6.0D, 0.2D, 10, false);
		double expected = 2.0D * Math.sqrt(0.02D) / 0.02D;
		assertEquals((int) Math.ceil(expected), trip.ticks());
		assertEquals(5.5D, trip.heightAt(expected / 2.0D), 1.0E-6D, "the triangle is symmetric");
		assertEquals(6.0D, trip.heightAt(trip.ticks()), EPS);
	}

	@Test
	void aDescentGoesDown() {
		ElevatorTrip trip = new ElevatorTrip(0L, 74.0D, 64.0D, 0.2D, 10, false);
		assertFalse(trip.up());
		assertEquals(73.0D, trip.heightAt(10.0D), EPS);
		assertTrue(trip.velocityAt(30.0D) < 0.0D);
		assertEquals(64.0D, trip.heightAt(trip.end()), EPS);
	}

	@Test
	void theCatchesRunAtConstantSpeed() {
		ElevatorTrip trip = new ElevatorTrip(0L, 10.5D, 8.0D, 0.05D, 0, true);
		assertEquals(50, trip.ticks());
		assertEquals(10.0D, trip.heightAt(10.0D), EPS);
		assertEquals(-0.05D, trip.velocityAt(1.0D), EPS);
	}

	@Test
	void aTripOfNoLengthIsOverAtOnce() {
		ElevatorTrip trip = new ElevatorTrip(7L, 3.0D, 3.0D, 0.2D, 10, false);
		assertEquals(0, trip.ticks());
		assertTrue(trip.arrivedBy(7.0D));
	}

	@Test
	void aStandingSpeedIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> new ElevatorTrip(0L, 1.0D, 2.0D, 0.0D, 10, false));
	}
}
