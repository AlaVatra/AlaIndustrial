package dev.alaindustrial.core.elevator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The room a body has in the elevator's tube (MOD-769, the owner's round 2 of 2026-11-24): a player fits through the
 * doorway with room to spare, the guide puts them on a line that clears the jambs before they reach them, and the
 * clamp keeps a rider off the walls.
 */
class ElevatorClearanceTest {

	/** Vanilla's player width in every pose ({@code Avatar.POSES}); the gametest reads it from the game itself. */
	private static final double PLAYER = 0.6D;
	/** The longest walk or sprint step a player takes in one tick on the ground. */
	private static final double SPRINT_STEP = 0.29D;

	@Test
	void aPlayerFitsThroughTheDoorwayWithAMargin() {
		assertEquals(0.75D, ElevatorClearance.OPENING, 1.0E-9D);
		assertTrue(ElevatorClearance.OPENING - PLAYER >= 0.1D, "the doorway is a tenth of a block wider than a player");
		assertTrue(ElevatorClearance.fits(PLAYER));
		assertFalse(ElevatorClearance.fits(0.9D), "a cow does not fit and is left alone");
	}

	@Test
	void theCabinsFloorPlateLeavesRoomForAStandingPlayerUnderTheStationsTop() {
		// The station is two blocks high and has no lintel in its collision: 1.8 tall on a 1 px plate.
		assertTrue(ElevatorClearance.FLOOR_TOP + 1.8D < 2.0D);
	}

	@Test
	void insideKeepsTheBoxOffBothWalls() {
		double slack = ElevatorClearance.slack(PLAYER);
		assertEquals(slack, ElevatorClearance.inside(0.3D, PLAYER), 1.0E-12D);
		assertEquals(-slack, ElevatorClearance.inside(-0.4D, PLAYER), 1.0E-12D);
		assertEquals(0.02D, ElevatorClearance.inside(0.02D, PLAYER), 1.0E-12D);
		double half = PLAYER / 2.0D;
		assertTrue(slack + half <= 0.5D - ElevatorClearance.WALL, "the clamped box stays inside the walls");
		assertEquals(0.7D, ElevatorClearance.inside(0.7D, 0.9D), 1.0E-12D);
	}

	@Test
	void theGuideLinesAWalkerUpBeforeTheJamb() {
		// The worst case the guide acts on: the centre at the edge of the doorway, entering the approach zone.
		for (double start : new double[] {0.49D, -0.49D, 0.2D, 0.1D}) {
			double offset = start;
			double gap = ElevatorClearance.APPROACH;
			while (gap > 0.0D) {
				offset = ElevatorClearance.guideStep(offset, PLAYER, gap);
				gap -= SPRINT_STEP;
			}
			// The step that crosses the plane is taken from the last guided offset: it must clear the jamb.
			assertTrue(Math.abs(offset) <= ElevatorClearance.slack(PLAYER) + 1.0E-12D,
					"from " + start + " the walker is still " + offset + " off the middle at the doorway");
		}
	}

	@Test
	void theGuideLeavesALinedUpWalkerAloneAndNeverOvershoots() {
		assertEquals(0.03D, ElevatorClearance.guideStep(0.03D, PLAYER, 0.5D), 1.0E-12D);
		double step = ElevatorClearance.guideStep(0.45D, PLAYER, 0.55D);
		assertTrue(step < 0.45D && step >= ElevatorClearance.slack(PLAYER), "one step of the way, got " + step);
		assertEquals(ElevatorClearance.slack(PLAYER), ElevatorClearance.guideStep(0.45D, PLAYER, 0.1D), 1.0E-12D);
	}
}
