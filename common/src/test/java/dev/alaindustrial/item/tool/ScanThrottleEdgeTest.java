package dev.alaindustrial.item.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The Network Analyzer's scan throttle at its edges (MOD-665): a second scan on the same tick, the table prune
 * that starts only above its size, the entries from a clock that went backwards, and a reset.
 *
 * @implements MOD-976 the mutation gate kills the throttle's prune and reset
 */
class ScanThrottleEdgeTest {

	private static final int PRUNE_ABOVE = 64;

	@Test
	void aSecondScanOnTheSameTickIsRefused() {
		ScanThrottle t = new ScanThrottle();
		UUID p = UUID.randomUUID();
		assertTrue(t.tryScan(p, 1L, 500L));
		assertFalse(t.tryScan(p, 2L, 500L));
	}

	@Test
	void aFullTableIsNotPrunedUntilItGrowsPastItsSize() {
		ScanThrottle t = new ScanThrottle();
		UUID future = UUID.randomUUID();
		assertTrue(t.tryScan(future, 1L, 1000L)); // a clock that later goes back
		for (int i = 1; i < PRUNE_ABOVE; i++) {
			assertTrue(t.tryScan(UUID.randomUUID(), 1L, 0L));
		}
		// exactly PRUNE_ABOVE entries: nothing pruned, so the future entry still throttles
		assertFalse(t.tryScan(future, 2L, 1005L));
	}

	@Test
	void growingPastItsSizeDropsEntriesFromABackwardClock() {
		ScanThrottle t = new ScanThrottle();
		UUID future = UUID.randomUUID();
		assertTrue(t.tryScan(future, 1L, 1000L));
		for (int i = 0; i < PRUNE_ABOVE; i++) {
			assertTrue(t.tryScan(UUID.randomUUID(), 1L, 0L));
		}
		// the 65th entry pruned the one stamped in the future, so it no longer throttles
		assertTrue(t.tryScan(future, 2L, 1005L));
	}

	@Test
	void resetForgetsEveryone() {
		ScanThrottle t = new ScanThrottle();
		UUID p = UUID.randomUUID();
		assertTrue(t.tryScan(p, 1L, 100L));
		t.reset();
		assertTrue(t.tryScan(p, 1L, 101L));
	}
}
