package dev.alaindustrial.client.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A click on the blueprint scene lands on the block drawn under the cursor, the front one first (MOD-933). */
class BlueprintPickerTest {

	private static final BlueprintCamera CAMERA = BlueprintCamera.DEFAULT;
	private static final double SCALE = 40;

	@Test
	void theDefaultCameraSeesTheTopSouthAndWestFacesFirst() {
		assertTrue(CAMERA.depth(0, 1, 0) > CAMERA.depth(0, 0, 0), "a point above is nearer");
		assertTrue(CAMERA.depth(0, 0, 1) > CAMERA.depth(0, 0, 0), "a point to the south is nearer");
		assertTrue(CAMERA.depth(-1, 0, 0) > CAMERA.depth(0, 0, 0), "a point to the west is nearer");
	}

	@Test
	void aClickOnABlockCentrePicksIt() {
		List<Placement> row = List.of(block("a", 0, 0, 0), block("b", 2, 0, 0), block("c", 4, 0, 0));
		BlueprintBounds bounds = bounds(row);
		for (int i = 0; i < row.size(); i++) {
			double[] at = centre(row.get(i), bounds);
			assertEquals(i, BlueprintPicker.pick(row, bounds, CAMERA, SCALE, at[0], at[1]));
		}
	}

	@Test
	void aClickOnEmptyPaperPicksNothing() {
		List<Placement> one = List.of(block("a", 0, 0, 0));
		assertEquals(-1, BlueprintPicker.pick(one, bounds(one), CAMERA, SCALE, 10 * SCALE, 10 * SCALE));
	}

	@Test
	void ofTwoBlocksUnderTheCursorTheFrontOneWins() {
		// A block straight above another, seen from above: both centres project close; the top one is nearer.
		List<Placement> stack = List.of(block("low", 0, 0, 0), block("high", 0, 1, 0));
		BlueprintBounds bounds = bounds(stack);
		double[] low = centre(stack.get(0), bounds);
		double[] high = centre(stack.get(1), bounds);
		double midX = (low[0] + high[0]) / 2;
		double midY = (low[1] + high[1]) / 2;
		assertEquals(1, BlueprintPicker.pick(stack, bounds, CAMERA, SCALE, midX, midY));
	}

	@Test
	void airIsNeverPicked() {
		List<Placement> air = List.of(new Placement("minecraft:air", 0, 0, 0, Map.of()));
		double[] at = centre(air.get(0), bounds(air));
		assertEquals(-1, BlueprintPicker.pick(air, bounds(air), CAMERA, SCALE, at[0], at[1]));
	}

	private static Placement block(String name, int x, int y, int z) {
		return new Placement("alaindustrial:" + name, x, y, z, Map.of());
	}

	private static BlueprintBounds bounds(List<Placement> blocks) {
		return BlueprintBounds.of(new BlueprintDef("t", List.of(), List.of(new Step(blocks, -1, 0, -1, -1,
				Verdict.NONE))));
	}

	private static double[] centre(Placement p, BlueprintBounds b) {
		double[] at = CAMERA.project(p.x() + 0.5 - b.centerX(), p.y() + 0.5 - b.centerY(), p.z() + 0.5 - b.centerZ());
		return new double[] {at[0] * SCALE, at[1] * SCALE};
	}
}
