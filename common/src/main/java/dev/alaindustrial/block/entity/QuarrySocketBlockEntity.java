package dev.alaindustrial.block.entity;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity of the quarry's socket foundation, present only while the foundation is the socket (MOD-1025,
 * {@code QuarryFoundationBlock.PORT}). It holds nothing and does nothing: Forge attaches capabilities to block
 * entities only, and this is where its loader layer hangs the controller's lent inventory and tank, so another mod's
 * pipe finds them there as it does on Fabric and NeoForge, where the foundation lends them as a block capability.
 */
public class QuarrySocketBlockEntity extends BlockEntity {

	public QuarrySocketBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.QUARRY_SOCKET_BE.get(), pos, state);
	}
}
