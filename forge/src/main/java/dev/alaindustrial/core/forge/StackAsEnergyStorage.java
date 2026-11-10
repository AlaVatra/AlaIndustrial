package dev.alaindustrial.core.forge;

import dev.alaindustrial.item.energy.ItemEnergy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * One of our powered items seen by another mod's charger as {@link IEnergyStorage} (MOD-084 on Forge), the twin of
 * NeoForge's {@code StackAsEnergyHandler}: insert-only, at most the item's own input rate per call, one item per
 * stack, written through {@link ItemEnergy#set} so a pack's worn look follows its charge. 1 EU = 1 FE.
 *
 * <p>A crystal blank is charged but not turned into its crystal here: {@code IEnergyStorage} sees only the stack,
 * not the slot holding it, and an {@code ItemStack} cannot change its item in place, so the blank cannot be swapped
 * inside a foreign slot on Forge (Fabric and NeoForge can: their item context replaces the stack). The full blank
 * stays a blank in the foreign charger and becomes its crystal at the next of our own charging sites, or as soon
 * as a player carries it ({@code CrystalBlankItem.inventoryTick}) — MOD-962.
 */
final class StackAsEnergyStorage implements IEnergyStorage {

	private final ItemStack stack;
	private final Item validItem;

	StackAsEnergyStorage(ItemStack stack) {
		this.stack = stack;
		this.validItem = stack.getItem();
	}

	@Override
	public int receiveEnergy(int maxReceive, boolean simulate) {
		if (maxReceive <= 0 || stack.getCount() != 1 || !stack.is(validItem)) {
			return 0;
		}
		long stored = ItemEnergy.get(stack);
		long accepted = Math.min(Math.min(maxReceive, ItemEnergy.inputRate(stack)),
				ItemEnergy.capacity(stack) - stored);
		if (accepted <= 0) {
			return 0;
		}
		if (!simulate) {
			ItemEnergy.set(stack, stored + accepted);
		}
		return (int) accepted;
	}

	@Override
	public int extractEnergy(int maxExtract, boolean simulate) {
		return 0;
	}

	@Override
	public int getEnergyStored() {
		return stack.is(validItem) ? PortAsEnergyStorage.toInt(ItemEnergy.get(stack)) : 0;
	}

	@Override
	public int getMaxEnergyStored() {
		return stack.is(validItem) ? PortAsEnergyStorage.toInt(ItemEnergy.capacity(stack)) : 0;
	}

	@Override
	public boolean canExtract() {
		return false;
	}

	@Override
	public boolean canReceive() {
		return true;
	}
}
