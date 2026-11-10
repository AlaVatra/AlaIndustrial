package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.item.ItemPort;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * An {@link IItemHandler} as the common pipe port. The move is {@link ItemMoveKernel}'s; what a lying target and an
 * output-only source leave behind is dropped at this port's block, never deleted.
 */
public final class ForgeItemPort implements ItemPort {

	private static final ItemMoveKernel.Stacks<ItemStack> STACKS = new ItemMoveKernel.Stacks<>() {
		@Override
		public boolean isEmpty(ItemStack stack) {
			return stack.isEmpty();
		}

		@Override
		public int count(ItemStack stack) {
			return stack.getCount();
		}

		@Override
		public boolean sameItem(ItemStack a, ItemStack b) {
			return ItemStack.isSameItemSameComponents(a, b);
		}
	};

	private final IItemHandler handler;
	private final Level level;
	private final BlockPos pos;

	private ForgeItemPort(IItemHandler handler, Level level, BlockPos pos) {
		this.handler = handler;
		this.level = level;
		this.pos = pos;
	}

	public static ItemPort of(IItemHandler handler, Level level, BlockPos pos) {
		return handler == null ? null : new ForgeItemPort(handler, level, pos);
	}

	@Override
	public int moveTo(ItemPort target, int maxAmount, EnergyPort.Txn txn) {
		if (!(target instanceof ForgeItemPort forgeTarget)) {
			throw new IllegalArgumentException("Cannot move Forge items into a foreign item-port implementation");
		}
		return ItemMoveKernel.move(calls(handler), calls(forgeTarget.handler), STACKS, maxAmount, ForgeTxn.require(txn),
				stack -> Block.popResource(level, pos, stack));
	}

	private static ItemMoveKernel.Handler<ItemStack> calls(IItemHandler handler) {
		return new ItemMoveKernel.Handler<>() {
			@Override
			public int slots() {
				return handler.getSlots();
			}

			@Override
			public ItemStack extract(int slot, int amount, boolean simulate) {
				return handler.extractItem(slot, amount, simulate);
			}

			@Override
			public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
				return handler.insertItem(slot, stack, simulate);
			}

			@Override
			public ItemStack insertStacked(ItemStack stack, boolean simulate) {
				return ItemHandlerHelper.insertItemStacked(handler, stack, simulate);
			}
		};
	}
}
