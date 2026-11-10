package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.core.fluid.FluidPortHost;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullSupplier;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.EmptyHandler;
import org.jspecify.annotations.Nullable;

/**
 * The energy, fluid and item capabilities of one of our block entities, attached by {@link BlockCapabilitiesForge}
 * from {@code BlockCapabilityRoster} (the common block entity names no Forge type).
 *
 * <p><b>Built inside the block entity's constructor.</b> Forge fires the attach event from {@code BlockEntity}'s
 * own constructor, before the subclass has set its buffer, tank or inventory; this class therefore touches the
 * block entity only when a capability is asked for, never when it is created.
 *
 * <p><b>Presence follows the current answer.</b> Each capability keeps one {@link LazyOptional} per face (and one
 * for no face), tagged with a key read on every query: present or absent for energy and fluid, the block state for
 * a chest (its pair changes with it). When the key changes, the old optional is invalidated, so another mod that
 * cached it hears about it, and a new one is handed out; rotation, a breaker opened, a role change or a chest
 * pairing need no hook of their own. The handlers resolve their port on every call besides, and answer nothing
 * once the block entity is removed: a foreign mod that kept the raw handler past the invalidation must not pull the
 * contents of a broken block whose drop already carries them (a dupe).
 *
 * <p>Only these optionals are ever invalidated. {@code BlockEntity.invalidateCaps()} would switch off every
 * capability of the block entity until it is revived, so it is never called here.
 */
public final class BlockCapabilityProviderForge implements ICapabilityProvider {

	/** Finds the provider of a block entity (for the chest-pair hook and the item mixin) without a side table. */
	public static final Capability<BlockCapabilityProviderForge> SELF = CapabilityManager.get(new CapabilityToken<>() {
	});

	private final BlockEntity be;
	private final BlockCapabilitiesForge.Flags flags;
	private final LazyOptional<BlockCapabilityProviderForge> self = LazyOptional.of(() -> this);
	private final FaceCache<PortAsEnergyStorage> energy = new FaceCache<>();
	private final FaceCache<TankAsFluidHandler> fluid = new FaceCache<>();
	private final FaceCache<IItemHandler> item = new FaceCache<>();

	BlockCapabilityProviderForge(BlockEntity be, BlockCapabilitiesForge.Flags flags) {
		this.be = be;
		this.flags = flags;
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == SELF) {
			return self.cast();
		}
		if (cap == ForgeCapabilities.ENERGY && flags.energy()) {
			EnergyPortHost host = (EnergyPortHost) be;
			return energy.get(side, host.energyPortForLookup(side) != null ? Boolean.TRUE : null,
					() -> new PortAsEnergyStorage(() -> be.isRemoved() ? null : host.energyPortForLookup(side))).cast();
		}
		if (cap == ForgeCapabilities.FLUID_HANDLER && flags.fluid()) {
			FluidPortHost host = (FluidPortHost) be;
			return fluid.get(side, host.fluidPortForLookup(side) != null ? Boolean.TRUE : null,
					() -> new TankAsFluidHandler(() -> be.isRemoved() ? null : host.fluidPortForLookup(side))).cast();
		}
		if (cap == ForgeCapabilities.ITEM_HANDLER && flags.item()) {
			return item(side).cast();
		}
		return LazyOptional.empty();
	}

	/** The item handler of a face; also answered by {@code BaseContainerItemCapabilityMixin}. */
	public LazyOptional<IItemHandler> item(@Nullable Direction side) {
		if (flags.chest()) {
			return item.get(side, be.getBlockState(), () -> new LiveItemHandler(be, new ChestPairItemHandler(be)));
		}
		return item.get(side, Boolean.TRUE, () -> new LiveItemHandler(be, ForgeItemLookup.wrap((Container) be, side)));
	}

	/**
	 * An item handler that answers nothing once its block entity is removed: another mod may keep the raw handler
	 * past the optional's invalidation, and the broken block's drop already carries what it held.
	 */
	private static final class LiveItemHandler implements IItemHandlerModifiable {
		private final BlockEntity be;
		private final IItemHandler handler;

		LiveItemHandler(BlockEntity be, IItemHandler handler) {
			this.be = be;
			this.handler = handler;
		}

		private IItemHandler live() {
			return be.isRemoved() ? EmptyHandler.INSTANCE : handler;
		}

		@Override
		public int getSlots() {
			return live().getSlots();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return live().getStackInSlot(slot);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return live().insertItem(slot, stack, simulate);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return live().extractItem(slot, amount, simulate);
		}

		@Override
		public int getSlotLimit(int slot) {
			return live().getSlotLimit(slot);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return live().isItemValid(slot, stack);
		}

		@Override
		public void setStackInSlot(int slot, ItemStack stack) {
			if (live() instanceof IItemHandlerModifiable modifiable) {
				modifiable.setStackInSlot(slot, stack);
			}
		}
	}

	/** The block entity is gone or unloaded: tell every holder of a handler. */
	void invalidateAll() {
		energy.invalidateAll();
		fluid.invalidateAll();
		item.invalidateAll();
	}

	/** A chest paired or unpaired ({@code ChestPairHooks.CAP_INVALIDATOR}). */
	public void invalidateItems() {
		item.invalidateAll();
	}

	/** One optional per face plus one for no face, each with the key it was made for. */
	private static final class FaceCache<H> {
		private static final int NO_FACE = 6;

		private final LazyOptional<?>[] optionals = new LazyOptional<?>[7];
		private final Object[] keys = new Object[7];

		@SuppressWarnings("unchecked")
		LazyOptional<H> get(@Nullable Direction side, @Nullable Object key, NonNullSupplier<H> factory) {
			int index = side == null ? NO_FACE : side.ordinal();
			if (key == null) {
				invalidate(index);
				return LazyOptional.empty();
			}
			if (optionals[index] != null && keys[index] == key) {
				return (LazyOptional<H>) optionals[index];
			}
			invalidate(index);
			LazyOptional<H> created = LazyOptional.of(factory);
			optionals[index] = created;
			keys[index] = key;
			return created;
		}

		void invalidateAll() {
			for (int index = 0; index < optionals.length; index++) {
				invalidate(index);
			}
		}

		private void invalidate(int index) {
			LazyOptional<?> old = optionals[index];
			if (old != null) {
				optionals[index] = null;
				keys[index] = null;
				old.invalidate();
			}
		}
	}
}
