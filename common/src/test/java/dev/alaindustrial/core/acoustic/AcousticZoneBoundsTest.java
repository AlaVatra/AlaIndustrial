package dev.alaindustrial.core.acoustic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.acoustic.AcousticZones.Zone;
import org.junit.jupiter.api.Test;

/**
 * The exact edges of one absorber group's zone (MOD-804): {@link Zone#contains} reaches from the min corner to
 * the far face of the max block, grown by the tier's radius, inclusive on both sides; {@link Zone#boxContains}
 * is the bare block box the HUD looks a focused block up in. A zone one block short on a face is a room the
 * player treated and still hears, so every face is checked on its own.
 *
 * @implements MOD-976 the mutation gate kills the zone bounds
 */
class AcousticZoneBoundsTest {

	/** Box x 10..12, y 20..21, z -5..-3, tier I (radius 5). */
	private static final Zone ZONE = new Zone(2, AbsorberTier.I, 10, 20, -5, 12, 21, -3);

	private static final double STEP = 0.001;

	@Test
	void zoneReachesTheRadiusPastEachFaceAndNoFurther() {
		int r = AbsorberTier.I.radius();
		double cx = 11.5;
		double cy = 21.0;
		double cz = -4.0;
		// x faces
		assertTrue(ZONE.contains(10 - r, cy, cz));
		assertFalse(ZONE.contains(10 - r - STEP, cy, cz));
		assertTrue(ZONE.contains(12 + 1 + r, cy, cz));
		assertFalse(ZONE.contains(12 + 1 + r + STEP, cy, cz));
		// y faces
		assertTrue(ZONE.contains(cx, 20 - r, cz));
		assertFalse(ZONE.contains(cx, 20 - r - STEP, cz));
		assertTrue(ZONE.contains(cx, 21 + 1 + r, cz));
		assertFalse(ZONE.contains(cx, 21 + 1 + r + STEP, cz));
		// z faces
		assertTrue(ZONE.contains(cx, cy, -5 - r));
		assertFalse(ZONE.contains(cx, cy, -5 - r - STEP));
		assertTrue(ZONE.contains(cx, cy, -3 + 1 + r));
		assertFalse(ZONE.contains(cx, cy, -3 + 1 + r + STEP));
	}

	@Test
	void boxHoldsExactlyTheGroupsBlocks() {
		assertTrue(ZONE.boxContains(10, 20, -5), "min corner");
		assertTrue(ZONE.boxContains(12, 21, -3), "max corner");
		assertTrue(ZONE.boxContains(11, 20, -4));
		assertFalse(ZONE.boxContains(9, 20, -5));
		assertFalse(ZONE.boxContains(13, 20, -5));
		assertFalse(ZONE.boxContains(10, 19, -5));
		assertFalse(ZONE.boxContains(10, 22, -5));
		assertFalse(ZONE.boxContains(10, 20, -6));
		assertFalse(ZONE.boxContains(10, 20, -2));
	}
}
