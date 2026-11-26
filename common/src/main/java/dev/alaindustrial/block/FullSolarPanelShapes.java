package dev.alaindustrial.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shapes of the full-block solar panels, T3 to T8 of both branches (MOD-1031): one model, so one answer.
 *
 * <p>The block is a full cube to stand on and to hit. What it hides of its neighbours is only the housing, sixteen
 * by fifteen pixels: the top pixel is a frame around a recessed glass cell, and a neighbour's face up there must
 * still be drawn. An occlusion shape of the whole cube would also make the block solid-rendered — the recess
 * inside it would be lit as the inside of a stone and draw black.
 */
public final class FullSolarPanelShapes {
	/** The housing under the frame: what the block hides of its neighbours. */
	public static final VoxelShape HOUSING = Block.box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0);

	private FullSolarPanelShapes() {
	}
}
