package dev.alaindustrial.client.render;

import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.tool.DrillBore;
import dev.alaindustrial.item.tool.ElectricDrillItem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The zone an electric drill with bore modules will take (MOD-967): while the player holds the drill and looks
 * at a block, every extra block of the stroke (the column bore's blocks above and below, the row bore's to the
 * left and right) is outlined exactly as the game outlines the aimed block.
 *
 * <p><b>The game's own outline, not a look-alike.</b> This is one entry of
 * {@code ClientContentManifest.BLOCK_OUTLINES}: it only swaps the shape of the hovered block's extracted
 * outline, and the game draws that shape itself, with its colour, line width, render type, translucency pass
 * and the high-contrast accessibility option. Whenever the game draws no outline (a hidden HUD, say) the zone
 * is not drawn either, nor for a player who may not build, and a zone outline behind a wall stays hidden like
 * any other. Owner's decision 2026-11-14: the extra blocks look the same as the aimed one, no colour of their
 * own.
 *
 * <p><b>One outline per block.</b> The game draws the edges of one {@link VoxelShape}, and a shape that is a
 * union of touching cubes has no edges where they touch: a column of three unit cubes outlines as one tall
 * box. So each box of an extra block's own selection shape is grown by {@link DrillBoreOutlinePlan#INSET}
 * before it joins the aimed block's shape: the grown box keeps all its edges, a hair outside the block's
 * faces, and the zone reads as a grid of blocks.
 *
 * <p><b>What is outlined is what breaks.</b> The positions are {@link ElectricDrillItem#plannedExtrasShown},
 * the plan the cracks and the predicted removal are drawn from: air, unbreakable and non-pickaxe blocks, a
 * switched-off module, a drill that cannot pay for the whole stroke and creative mode all leave the plan empty
 * or shorter, and then the aimed block keeps its own outline alone. {@link DrillBoreOutlinePlan} holds the
 * built shape between frames.
 */
public final class DrillBoreOutline {

	/** The zone shape of the held plan (empty: no extras), built once per plan rather than once per frame. */
	private static final DrillBoreOutlinePlan<VoxelShape> PLAN = new DrillBoreOutlinePlan<>();

	private DrillBoreOutline() {
	}

	/**
	 * The aimed block's own outline joined with the outlines of the extra blocks the held drill will take, relative
	 * to {@code pos}; {@code null} (keep the game's outline) when the player holds no drill or nothing extra is
	 * planned.
	 */
	public static @Nullable VoxelShape shape(BlockGetter getter, BlockPos pos, BlockState state) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		// The game also outlines a block for a spectator or an adventure player who may not build (an interactable
		// block, an item allowed to break it); the zone stays with players who may build, as before.
		if (player == null || mc.getCameraEntity() != player || !player.getAbilities().mayBuild
				|| !(getter instanceof Level level)) {
			return null;
		}
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof ElectricDrillItem)) {
			return null;
		}
		DrillBoreOutlinePlan.Key key = new DrillBoreOutlinePlan.Key(pos.asLong(), Block.getId(state),
				player.getDirection().ordinal(), ItemEnergy.shownGet(stack), DrillBore.boreMode(stack).ordinal(),
				BuiltInRegistries.ITEM.getId(stack.getItem()), player.tickCount);
		List<VoxelShape> zone = PLAN.get(key, () -> zoneOf(level, pos, state, player,
				ElectricDrillItem.plannedExtrasShown(stack, level, pos, state, player)));
		return zone.isEmpty() ? null : zone.get(0);
	}

	/** The aimed block's shape, as the game computes it for its outline, joined with every grown extra. */
	private static List<VoxelShape> zoneOf(Level level, BlockPos aim, BlockState state, LocalPlayer player,
			List<BlockPos> extras) {
		if (extras.isEmpty()) {
			return List.of();
		}
		CollisionContext context = CollisionContext.of(player);
		VoxelShape zone = state.getShape(level, aim, context);
		for (BlockPos extra : extras) {
			int dx = extra.getX() - aim.getX();
			int dy = extra.getY() - aim.getY();
			int dz = extra.getZ() - aim.getZ();
			for (AABB box : level.getBlockState(extra).getShape(level, extra, context).toAabbs()) {
				double[] f = DrillBoreOutlinePlan.frame(box.minX + dx, box.minY + dy, box.minZ + dz,
						box.maxX + dx, box.maxY + dy, box.maxZ + dz);
				zone = Shapes.or(zone, Shapes.box(f[0], f[1], f[2], f[3], f[4], f[5]));
			}
		}
		return List.of(zone);
	}
}
