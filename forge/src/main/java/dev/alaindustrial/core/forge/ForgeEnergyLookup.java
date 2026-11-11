package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyPortHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * {@link EnergyLookup} on MinecraftForge. Our own block entities are asked directly, as on the other two
 * loaders, so mod-to-mod transfer between our blocks never goes through the {@code int}, non-transactional
 * capability; anything else is reached through {@link ForgeCapabilities#ENERGY}.
 */
public final class ForgeEnergyLookup implements EnergyLookup {

	@Override
	public EnergyPort find(Level level, BlockPos pos, Direction side) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null) {
			return null;
		}
		if (be instanceof EnergyPortHost host) {
			return host.energyPort(side);
		}
		return be.getCapability(ForgeCapabilities.ENERGY, side).map(ForeignEnergyPort::of).orElse(null);
	}
}
