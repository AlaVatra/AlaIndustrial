package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.fluid.FluidHostRedirect;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * {@link FluidLookup} on MinecraftForge. Our own block entities are asked directly, as on the other two loaders;
 * anything else is reached through {@link ForgeCapabilities#FLUID_HANDLER}.
 *
 * <p>A block that lends another block's tank ({@link FluidHostRedirect}, the quarry's socket) is asked by our lookup
 * itself. Other mods find the lent tank through the socket's block entity, where {@code BlockCapabilitiesForge} hangs
 * it (MOD-1025); on Fabric and NeoForge it is a block capability.
 */
public final class ForgeFluidLookup implements FluidLookup {

	@Override
	public FluidPort find(Level level, BlockPos pos, Direction side) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof FluidHostRedirect) {
			return FluidHostRedirect.lent(level, pos, state, side);
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null) {
			return null;
		}
		if (be instanceof FluidPortHost host) {
			return host.fluidPort(side);
		}
		return be.getCapability(ForgeCapabilities.FLUID_HANDLER, side).map(ForeignFluidPort::of).orElse(null);
	}
}
