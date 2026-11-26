package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The edges {@link CascadeShareTest} leaves alone (MOD-314): a store with no capacity next to one that holds
 * charge, and the floating-point fallback taken when the exact cross products overflow a long. The fallback
 * runs only for capacities an operator can reach by hand-editing the config, which is exactly why nothing
 * else would notice it computing the wrong direction or the wrong amount.
 *
 * @implements MOD-976 the mutation gate kills the cascade's zero-capacity and overflow branches
 */
class CascadeShareEdgeTest {

	private static final long E18 = 1_000_000_000_000_000_000L;

	@Test
	void aStoreWithNoCapacityIsNeitherFullerNorEmptierEvenBesideCharge() {
		assertEquals(0, CascadeShare.compareFill(5, 0, 1, 10), "zero capacity on the left");
		assertEquals(0, CascadeShare.compareFill(1, 10, 5, 0), "zero capacity on the right");
		assertEquals(0L, CascadeShare.equalisingTransfer(10, 0, 0, 10), "a donor with no capacity gives nothing");
	}

	@Test
	void overflowingFractionsStillCompareTheRightWayRound() {
		// 0.5 against 0.333…: the cross products (1e18·9e18) do not fit a long.
		assertEquals(1, CascadeShare.compareFill(E18, 2 * E18, 3 * E18, 9 * E18));
		// 0.111… against 0.75: the same overflow, the other sign.
		assertEquals(-1, CascadeShare.compareFill(E18, 9 * E18, 3 * E18, 4 * E18));
	}

	@Test
	void overflowingTransferLevelsBothStoresToOneFraction() {
		// Donor full at 8e18, sink a quarter full at 8e18: equal capacities meet half way, at 3e18 moved.
		long x = CascadeShare.equalisingTransfer(8 * E18, 8 * E18, 2 * E18, 8 * E18);
		assertTrue(Math.abs(x - 3 * E18) < 1_000_000L, () -> "expected about 3e18, got " + x);
		// Different capacities: donor 6e18 of 8e18, sink 0 of 4e18 -> x = 6e18·4e18 / 12e18 = 2e18.
		long y = CascadeShare.equalisingTransfer(6 * E18, 8 * E18, 0, 4 * E18);
		assertTrue(Math.abs(y - 2 * E18) < 1_000_000L, () -> "expected about 2e18, got " + y);
		assertEquals(0L, CascadeShare.equalisingTransfer(2 * E18, 8 * E18, 8 * E18, 8 * E18),
				"an emptier donor gives nothing, on the overflow path too");
	}
}
