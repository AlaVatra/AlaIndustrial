package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jspecify.annotations.Nullable;

/**
 * {@link ItemFluidBridge} on MinecraftForge (MOD-107): a bucket or a capsule in a machine slot or in the hand
 * against one of our tanks, one bucket per call, all or nothing.
 *
 * <p>The container item is resolved on a one-item copy: its {@link ForgeCapabilities#FLUID_HANDLER_ITEM} (our
 * capsule, any mod that attaches one), else a bucket through {@link BucketFluidHandlerItem}, since Forge 26.3 gives
 * buckets no item capability. Never {@code FluidUtil.getFluidHandler(ItemStack)}: on Forge 26.3 it always returns
 * empty.
 *
 * <p>The slot exchange never loses an item: the item side runs on the copy, the output slot is checked without
 * writing, the tank side is one transaction that aborts unless the whole amount moves, and only then are the slots
 * changed — a step that cannot fail any more.
 */
public final class ForgeItemFluidBridge implements ItemFluidBridge {

	@Override
	public long drainSlotIntoTank(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb) {
		return exchange(container, inSlot, outSlot, tank, maxMb, true);
	}

	@Override
	public long fillSlotFromTank(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb) {
		return exchange(container, inSlot, outSlot, tank, maxMb, false);
	}

	/** Asked by {@code canPlaceItem}; works on a copy, so the stack asked about is never changed. */
	@Override
	public boolean isFluidContainer(ItemStack stack) {
		return handler(stack) != null;
	}

	@Override
	public boolean takeFromHand(Player player, InteractionHand hand, Fluid fluid, long mb, boolean simulate) {
		ItemStack held = player.getItemInHand(hand);
		IFluidHandlerItem item = handler(held); // one item: a hand of four capsules reads as one (WSH06)
		if (item == null) {
			return false;
		}
		FluidStack wanted = new FluidStack(fluid, PortAsEnergyStorage.toInt(mb));
		if (wanted.isEmpty() || item.drain(wanted, FluidAction.SIMULATE).getAmount() != wanted.getAmount()) {
			return false;
		}
		if (simulate) {
			return true;
		}
		item.drain(wanted, FluidAction.EXECUTE);
		player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, item.getContainer()));
		return true;
	}

	private static long exchange(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb,
			boolean drain) {
		IFluidHandlerItem item = handler(container.getItem(inSlot));
		int amount = PortAsEnergyStorage.toInt(maxMb);
		if (item == null || amount <= 0) {
			return 0L;
		}
		FluidHolder fluid;
		if (drain) {
			FluidStack offered = item.drain(amount, FluidAction.SIMULATE);
			if (offered.getAmount() != amount) {
				return 0L;
			}
			fluid = FluidHolder.of(offered.getFluid());
			item.drain(amount, FluidAction.EXECUTE);
		} else {
			fluid = tank.fluid();
			FluidStack wanted = fluid.isEmpty() ? FluidStack.EMPTY : new FluidStack(fluid.fluid(), amount);
			if (wanted.isEmpty() || item.fill(wanted, FluidAction.SIMULATE) != amount) {
				return 0L;
			}
			item.fill(wanted, FluidAction.EXECUTE);
		}
		ItemStack result = item.getContainer();
		if (!fitsOutput(container, outSlot, result) || !moveTank(tank, fluid, amount, drain)) {
			return 0L;
		}
		container.removeItem(inSlot, 1);
		if (!result.isEmpty()) {
			ItemStack out = container.getItem(outSlot);
			if (out.isEmpty()) {
				container.setItem(outSlot, result);
			} else {
				out.grow(result.getCount());
			}
		}
		container.setChanged();
		return amount;
	}

	/** Whether the swapped container can go to the output slot, without writing anything. */
	private static boolean fitsOutput(Container container, int outSlot, ItemStack result) {
		if (result.isEmpty()) {
			return true;
		}
		ItemStack out = container.getItem(outSlot);
		boolean room = out.isEmpty() || (ItemStack.isSameItemSameComponents(out, result)
				&& out.getCount() + result.getCount() <= Math.min(out.getMaxStackSize(), container.getMaxStackSize()));
		return room && container.canPlaceItem(outSlot, result);
	}

	/** The tank side, in one transaction that commits only if the whole amount moved. */
	private static boolean moveTank(FluidPort tank, FluidHolder fluid, int amount, boolean intoTank) {
		ForgeTxn txn = ForgeTxn.openRoot(false);
		long moved;
		try {
			moved = intoTank ? tank.insert(fluid, amount, txn) : tank.extract(fluid, amount, txn);
		} catch (RuntimeException | Error e) {
			txn.abort();
			throw e;
		}
		if (moved != amount) {
			txn.abort();
			return false;
		}
		txn.commit();
		return true;
	}

	/** The fluid handler of one item of {@code stack}, on a copy; {@code null} if it holds no fluid. */
	private static @Nullable IFluidHandlerItem handler(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		ItemStack one = stack.copyWithCount(1);
		IFluidHandlerItem attached = one.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElse(null);
		if (attached != null) {
			return attached;
		}
		return BucketFluidHandlerItem.isBucket(one) ? BucketFluidHandlerItem.bucket(one) : null;
	}
}
