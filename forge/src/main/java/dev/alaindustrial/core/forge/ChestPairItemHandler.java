package dev.alaindustrial.core.forge;

import dev.alaindustrial.block.AbstractModChestBlock;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.InvWrapper;

/**
 * A chest of ours as one inventory to automation (MOD-391 on Forge): the two halves of a double chest are joined,
 * whichever half is asked, in the order the double chest's menu shows. The pair is resolved on every call, so the
 * handler never serves a stale pair; a chest is a plain container, so the face does not matter.
 *
 * <p>Our chests extend {@code BaseContainerBlockEntity}, not vanilla {@code ChestBlockEntity}, so Forge's own
 * double-chest handling does not apply to them.
 */
final class ChestPairItemHandler implements IItemHandlerModifiable {

	private final BlockEntity chest;

	ChestPairItemHandler(BlockEntity chest) {
		this.chest = chest;
	}

	private InvWrapper view() {
		BlockState state = chest.getBlockState();
		Container joined = chest.getLevel() != null && state.getBlock() instanceof AbstractModChestBlock block
				? block.combinedContainer(state, chest.getLevel(), chest.getBlockPos())
				: null;
		return new InvWrapper(joined != null ? joined : (Container) chest);
	}

	@Override
	public int getSlots() {
		return view().getSlots();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return view().getStackInSlot(slot);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		return view().insertItem(slot, stack, simulate);
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		return view().extractItem(slot, amount, simulate);
	}

	@Override
	public int getSlotLimit(int slot) {
		return view().getSlotLimit(slot);
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return view().isItemValid(slot, stack);
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		view().setStackInSlot(slot, stack);
	}
}
