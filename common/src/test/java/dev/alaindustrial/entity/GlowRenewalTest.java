package dev.alaindustrial.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage of the Mob Beacon's glow rule (MOD-968): length and threshold follow the scan interval, the outline
 * never lapses between two searches, and a glow is only ever lengthened.
 */
class GlowRenewalTest {

	@Test
	void lengthAndThresholdFollowTheInterval() {
		assertEquals(80, GlowRenewal.glowTicks(20), "4 intervals of 20 ticks (spec: 4 s)");
		assertEquals(40, GlowRenewal.renewAtTicks(20), "2 intervals of 20 ticks (spec: 2 s)");
		assertEquals(4, GlowRenewal.glowTicks(0), "an interval under 1 tick counts as 1");
		assertEquals(2, GlowRenewal.renewAtTicks(-5), "an interval under 1 tick counts as 1");
	}

	@Test
	void aMobWithNoGlowGetsIt() {
		assertTrue(GlowRenewal.shouldRenew(false, false, 0, 20));
	}

	@Test
	void renewsAtTheThresholdAndNotAbove() {
		assertTrue(GlowRenewal.shouldRenew(true, false, 40, 20), "at the threshold");
		assertTrue(GlowRenewal.shouldRenew(true, false, 1, 20), "about to end");
		assertFalse(GlowRenewal.shouldRenew(true, false, 41, 20), "one tick above the threshold");
		assertFalse(GlowRenewal.shouldRenew(true, false, 600, 20), "a spectral arrow's longer glow is left alone");
	}

	@Test
	void anInfiniteGlowIsNeverTouched() {
		assertFalse(GlowRenewal.shouldRenew(true, true, -1, 20));
		assertFalse(GlowRenewal.shouldRenew(true, true, 0, 20));
	}

	@Test
	void theOutlineNeverLapsesBetweenSearchesAndIsNeverShortened() {
		for (int interval = 1; interval <= 200; interval++) {
			int glow = GlowRenewal.glowTicks(interval);
			int threshold = GlowRenewal.renewAtTicks(interval);
			// A glow just above the threshold is skipped; one interval later it must still be lit and renewable.
			assertTrue(threshold + 1 - interval > 0, "lapses between searches at interval " + interval);
			assertTrue(GlowRenewal.shouldRenew(true, false, threshold + 1 - interval, interval),
					"not renewed on the next search at interval " + interval);
			// Renewing only at or under the threshold gives the full length, which is longer than what is left.
			assertTrue(glow > threshold, "a renewal would shorten the glow at interval " + interval);
		}
	}
}
