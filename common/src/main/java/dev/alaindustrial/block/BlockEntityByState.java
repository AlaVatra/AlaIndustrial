package dev.alaindustrial.block;

import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An {@link EntityBlock} whose states do not all carry a block entity (MOD-1025). Vanilla decides
 * {@code BlockState.hasBlockEntity()} by the block alone ({@code getBlock() instanceof EntityBlock}), so every state
 * of such a block would count as a block-entity block — pistons refuse it, the quarry and the town drones skip it.
 * {@code BlockStateBlockEntityByStateMixin} asks this instead, for this block's states only.
 *
 * <p>The block must also drop the block entity when a state change takes it away: the chunk removes a block entity
 * only when the BLOCK changes, not when one state of the same block gives way to another.
 */
public interface BlockEntityByState extends EntityBlock {

	/** Whether {@code state} carries a block entity. */
	boolean hasBlockEntity(BlockState state);
}
