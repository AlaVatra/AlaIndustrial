package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.gametest.CableWashSourceScenarios;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.loading.FMLLoader;
import org.jspecify.annotations.Nullable;

/**
 * Stands in for another mod's water canister on the Forge lane (MOD-912), the twin of the NeoForge
 * {@code ForeignWaterItemStandIn}: {@link CableWashSourceScenarios#FOREIGN_CANISTER} gets
 * {@link ForgeCapabilities#FLUID_HANDLER_ITEM}. It holds water only, up to a bucket, in millibuckets in the vanilla
 * {@code repair_cost} component, refuses every fill and gives any amount it holds. Forge's universal jar has no
 * component-backed item handler, so the handler is written here.
 *
 * <p>Dev-only, behind {@link FMLLoader#isProduction()}.
 */
public final class ForeignWaterItemStandIn {

	private ForeignWaterItemStandIn() {
	}

	static void install() {
		if (FMLLoader.isProduction()) {
			return;
		}
		AttachCapabilitiesEvent.ItemStacks.BUS.addListener(event -> {
			ItemStack stack = event.getObject();
			if (stack.is(CableWashSourceScenarios.FOREIGN_CANISTER)) {
				LazyOptional<IFluidHandlerItem> handler = LazyOptional.of(() -> new Canister(stack));
				event.addCapability(Industrialization.id("test_foreign_canister"), new ICapabilityProvider() {
					@Override
					public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
						return cap == ForgeCapabilities.FLUID_HANDLER_ITEM ? handler.cast() : LazyOptional.empty();
					}
				});
			}
		});
	}

	private record Canister(ItemStack stack) implements IFluidHandlerItem {

		private int mb() {
			Integer stored = stack.get(DataComponents.REPAIR_COST);
			return stored == null ? 0 : stored;
		}

		@Override
		public ItemStack getContainer() {
			return stack;
		}

		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			int mb = mb();
			return mb > 0 ? new FluidStack(Fluids.WATER, mb) : FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return (int) FluidAmounts.BUCKET;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack fluid) {
			return fluid.getFluid() == Fluids.WATER;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return 0;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (resource.isEmpty() || resource.getFluid() != Fluids.WATER) {
				return FluidStack.EMPTY;
			}
			return drain(resource.getAmount(), action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			int held = mb();
			int given = Math.min(maxDrain, held);
			if (given <= 0) {
				return FluidStack.EMPTY;
			}
			if (action.execute()) {
				stack.set(DataComponents.REPAIR_COST, held - given);
			}
			return new FluidStack(Fluids.WATER, given);
		}
	}
}
