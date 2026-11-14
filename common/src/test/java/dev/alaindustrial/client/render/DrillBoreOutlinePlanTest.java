package dev.alaindustrial.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 — the drill bore zone's outline (MOD-967): it is drawn from the planner's own answer, re-asked whenever
 * anything the plan depends on changes, and each extra block's outline box is grown just off its block.
 */
class DrillBoreOutlinePlanTest {

	private static final DrillBoreOutlinePlan.Key BASE = new DrillBoreOutlinePlan.Key(42L, 7, 2, 1_000L, 0, 99, 500L);

	/**
	 * @implements MOD-967 — the frames are exactly the planner's positions: nothing added, nothing dropped, in its
	 *             order (the planner is the one {@code plannedExtrasShown} the cracks use)
	 * @covers MOD-967
	 */
	@Test
	void framesAreThePlannersPositions() {
		DrillBoreOutlinePlan<String> plan = new DrillBoreOutlinePlan<>();
		assertEquals(List.of("above", "below", "right"), plan.get(BASE, () -> List.of("above", "below", "right")));
		assertEquals(List.of(), new DrillBoreOutlinePlan<String>().get(BASE, List::of));
	}

	/**
	 * @implements MOD-967 — an unchanged key reuses the plan; every part of the key asks for a new one. The first
	 *             assertion is the negative control: were the key ignored, it would count two calls, not one.
	 * @covers MOD-967
	 */
	@Test
	void anyChangeOfTheKeyAsksThePlannerAgain() {
		DrillBoreOutlinePlan<Integer> plan = new DrillBoreOutlinePlan<>();
		List<Integer> calls = new ArrayList<>();
		plan.get(BASE, () -> answer(calls));
		plan.get(new DrillBoreOutlinePlan.Key(42L, 7, 2, 1_000L, 0, 99, 500L), () -> answer(calls));
		assertEquals(1, calls.size(), "an equal key must reuse the held plan");

		List<DrillBoreOutlinePlan.Key> changed = List.of(
				new DrillBoreOutlinePlan.Key(43L, 7, 2, 1_000L, 0, 99, 500L), // another aimed cell
				new DrillBoreOutlinePlan.Key(43L, 8, 2, 1_000L, 0, 99, 500L), // its block changed
				new DrillBoreOutlinePlan.Key(43L, 8, 3, 1_000L, 0, 99, 500L), // the player turned
				new DrillBoreOutlinePlan.Key(43L, 8, 3, 900L, 0, 99, 500L), // the charge dropped
				new DrillBoreOutlinePlan.Key(43L, 8, 3, 900L, 1, 99, 500L), // a module switched
				new DrillBoreOutlinePlan.Key(43L, 8, 3, 900L, 1, 98, 500L), // another drill in hand
				new DrillBoreOutlinePlan.Key(43L, 8, 3, 900L, 1, 98, 501L)); // the next tick
		for (DrillBoreOutlinePlan.Key key : changed) {
			int before = calls.size();
			List<Integer> got = plan.get(key, () -> answer(calls));
			assertEquals(before + 1, calls.size(), "a changed key must re-plan: " + key);
			assertEquals(List.of(calls.size()), got, "and serve the new plan: " + key);
		}
	}

	/**
	 * @implements MOD-967 — an extra block's outline box encloses the box it outlines and is grown a small gap on
	 *             every side, so it keeps its own edges where it touches the aimed block
	 */
	@Test
	void frameStandsJustOffItsCell() {
		double[] cell = {-3, 64, 10, -2, 64.5, 11};
		double[] f = DrillBoreOutlinePlan.frame(cell[0], cell[1], cell[2], cell[3], cell[4], cell[5]);
		assertTrue(DrillBoreOutlinePlan.INSET > 0.0 && DrillBoreOutlinePlan.INSET < 0.05,
				"the gap keeps the line off the face without visibly growing the frame");
		for (int axis = 0; axis < 3; axis++) {
			assertEquals(cell[axis] - DrillBoreOutlinePlan.INSET, f[axis], 1e-12);
			assertEquals(cell[axis + 3] + DrillBoreOutlinePlan.INSET, f[axis + 3], 1e-12);
		}
	}

	private static List<Integer> answer(List<Integer> calls) {
		calls.add(calls.size() + 1);
		return List.of(calls.size());
	}
}
