package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Base of the working full-block solar panels (MOD-1031): the third rung of both branches. Everything the slab
 * panels share stays — the hum, the inert top face, the cable contract of {@link AbstractSolarPanelBlock} — and
 * only the body changes: a full cube with a recessed glass cell, see {@link FullSolarPanelShapes}.
 */
public abstract class AbstractFullSolarPanelBlock extends AbstractSolarPanelBlock {

	protected AbstractFullSolarPanelBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.block();
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return FullSolarPanelShapes.HOUSING;
	}
}
