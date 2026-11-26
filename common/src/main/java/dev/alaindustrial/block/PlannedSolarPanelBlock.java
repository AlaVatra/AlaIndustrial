package dev.alaindustrial.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A full-block solar panel of a rung that has no function yet (MOD-1031): T4 to T8 of both branches. The ids, the
 * names and the look are fixed now so they never change; the block itself is inert — no block entity, no energy,
 * no recipe — and is kept out of every creative tab and recipe viewer until its rung is built.
 */
public class PlannedSolarPanelBlock extends Block {
	public PlannedSolarPanelBlock(Properties properties) {
		super(properties);
	}

	/** The same body as the working panels; see {@link FullSolarPanelShapes}. */
	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return FullSolarPanelShapes.HOUSING;
	}
}
