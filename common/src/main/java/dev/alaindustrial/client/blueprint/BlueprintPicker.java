package dev.alaindustrial.client.blueprint;

import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import java.util.List;

/**
 * Which block of a blueprint scene is under the cursor (MOD-933): plain arithmetic over the same projection the
 * screen draws with, so it is checked on the L1 lane. A block counts as under the cursor when the cursor is within
 * half a block of its projected centre; of several, the one nearest the viewer wins.
 */
public final class BlueprintPicker {

	/** How far from a block's projected centre a click still lands on it, in blocks on screen. */
	static final double REACH = 0.5;

	private BlueprintPicker() {
	}

	/**
	 * The index into {@code blocks} of the block under screen offset ({@code dx}, {@code dy}) from the picture's
	 * centre, in pixels, or -1 for none. Air never counts.
	 *
	 * @param pixelsPerBlock the picture's scale
	 */
	public static int pick(List<Placement> blocks, BlueprintBounds bounds, BlueprintCamera camera,
			double pixelsPerBlock, double dx, double dy) {
		int best = -1;
		double bestDepth = -Double.MAX_VALUE;
		double reach = REACH * pixelsPerBlock;
		for (int i = 0; i < blocks.size(); i++) {
			Placement p = blocks.get(i);
			if (p.blockId().endsWith(":air")) {
				continue;
			}
			double x = p.x() + 0.5 - bounds.centerX();
			double y = p.y() + 0.5 - bounds.centerY();
			double z = p.z() + 0.5 - bounds.centerZ();
			double[] at = camera.project(x, y, z);
			double ox = at[0] * pixelsPerBlock - dx;
			double oy = at[1] * pixelsPerBlock - dy;
			if (ox * ox + oy * oy > reach * reach) {
				continue;
			}
			double depth = camera.depth(x, y, z);
			if (depth > bestDepth) {
				best = i;
				bestDepth = depth;
			}
		}
		return best;
	}
}
