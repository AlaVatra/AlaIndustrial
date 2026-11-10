package dev.alaindustrial.core.forge;

import dev.alaindustrial.item.energy.ItemEnergyBridge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * {@link ItemEnergyBridge} on MinecraftForge (MOD-084): the worn Energy Pack charges another mod's powered item
 * through {@link ForgeCapabilities#ENERGY} on the stack. One real call, no transaction: the caller debits the pack
 * for exactly what this returns. {@code slot} is a vanilla {@code Inventory} index, which {@code getItem} reads.
 */
public final class ForgeItemEnergyBridge implements ItemEnergyBridge {

	@Override
	public long chargeSlot(Player player, int slot, long maxEu) {
		if (maxEu <= 0) {
			return 0L;
		}
		ItemStack stack = player.getInventory().getItem(slot);
		if (stack.isEmpty()) {
			return 0L;
		}
		IEnergyStorage target = stack.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
		if (target == null || !target.canReceive()) {
			return 0L;
		}
		return Math.max(0, target.receiveEnergy(PortAsEnergyStorage.toInt(maxEu), false));
	}
}
