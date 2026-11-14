package dev.alaindustrial.client.render;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The game-free core of {@link DrillBoreOutline} (MOD-967): which plan the zone outline is drawn from, and how far
 * an extra block's outline box is grown. No Minecraft type appears here, so it is unit-testable on the L1 lane.
 *
 * <p><b>One plan, held per key.</b> The blocks a drill stroke takes come from
 * {@code ElectricDrillItem.plannedExtrasShown}, the planner the cracks and the predicted removal already use; this
 * class never computes a position itself. It only keeps the last plan and asks for a new one when anything the
 * plan depends on has changed: the aimed cell and its state, the player's horizontal facing, the shown charge, the
 * bore modules' mode, the drill item, and the client tick, which brings in the neighbour blocks, the game mode and
 * the synced prices with at most one tick of lag. The tick is the player's own count, not the level's game time:
 * under {@code /tick freeze} the game time stands still while the player still ticks and blocks still change.
 * Between those changes a frame reuses the plan instead of re-reading the world.
 *
 * @param <T> the position type the planner returns ({@code BlockPos} in game, anything in a test)
 */
public final class DrillBoreOutlinePlan<T> {

	/**
	 * How far an extra block's outline box is grown on every side, in blocks. The game outlines the edges of one
	 * shape, and touching cubes joined into one shape lose the edges where they touch; grown, every extra block keeps
	 * its own twelve edges, a hair outside its faces, without visibly growing the outline.
	 */
	public static final double INSET = 0.004;

	/**
	 * Everything the plan depends on, as plain numbers.
	 *
	 * @param aimPos   the aimed cell, packed
	 * @param aimState the aimed block state's id
	 * @param facing   the player's horizontal facing (the row runs across it)
	 * @param charge   the drill's shown charge
	 * @param mode     the bore modules' mode (installed and switched on)
	 * @param item     the drill item's id (each tier has its own prices)
	 * @param tick     the player's tick count (advances every client tick, also under {@code /tick freeze})
	 */
	public record Key(long aimPos, int aimState, int facing, long charge, int mode, int item, long tick) {
	}

	private Key key;
	private List<T> plan = List.of();

	/** The plan for {@code key}: the held one while the key is unchanged, else a fresh one from {@code planner}. */
	public List<T> get(Key key, Supplier<List<T>> planner) {
		Objects.requireNonNull(key, "key");
		if (!key.equals(this.key)) {
			plan = List.copyOf(planner.get());
			this.key = key;
		}
		return plan;
	}

	/**
	 * The outline box of one box of an extra block's shape: that box grown by {@link #INSET} on every side, as
	 * {@code {minX, minY, minZ, maxX, maxY, maxZ}}.
	 */
	public static double[] frame(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		return new double[] {minX - INSET, minY - INSET, minZ - INSET, maxX + INSET, maxY + INSET, maxZ + INSET};
	}
}
