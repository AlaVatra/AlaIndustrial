package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.loading.FMLLoader;
import org.jspecify.annotations.Nullable;

/**
 * Stands in for a third-party energy mod on the Forge lane (MOD-084), the twin of the NeoForge
 * {@code ForeignEnergyItemStandIn}: an apple gets {@link ForgeCapabilities#ENERGY}, attached through
 * {@link AttachCapabilitiesEvent.ItemStacks} the way another mod would, with its charge kept in the vanilla
 * {@link DataComponents#DAMAGE} component, a component the mod under test does not know about.
 *
 * <p>Dev-only: wired by {@link ForgeGameTestBootstrap} (the gametest source set is absent from the shipped jar) and
 * guarded by {@link FMLLoader#isProduction()}.
 */
public final class ForeignEnergyItemStandIn {

	/** Buffer of the stand-in — far above the pack's per-step budget, so the pack is what limits transfer. */
	public static final int CAPACITY = 100_000;

	/** Per-operation limit of the stand-in — likewise above the pack's budget. */
	public static final int MAX_TRANSFER = 100_000;

	private ForeignEnergyItemStandIn() {
	}

	/** Read the stand-in's charge the way this fake foreign mod stores it. */
	public static long storedEnergy(ItemStack stack) {
		return stack.getOrDefault(DataComponents.DAMAGE, 0);
	}

	static void install() {
		if (FMLLoader.isProduction()) {
			return;
		}
		AttachCapabilitiesEvent.ItemStacks.BUS.addListener(event -> {
			ItemStack stack = event.getObject();
			if (stack.is(Items.APPLE)) {
				LazyOptional<IEnergyStorage> storage = LazyOptional.of(() -> new Storage(stack));
				event.addCapability(Industrialization.id("test_foreign_energy"), new ICapabilityProvider() {
					@Override
					public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
						return cap == ForgeCapabilities.ENERGY ? storage.cast() : LazyOptional.empty();
					}
				});
			}
		});
	}

	/** Insert-and-extract like a foreign item, charge in {@code DAMAGE}. */
	private record Storage(ItemStack stack) implements IEnergyStorage {
		@Override
		public int receiveEnergy(int maxReceive, boolean simulate) {
			int stored = getEnergyStored();
			int taken = Math.max(0, Math.min(Math.min(maxReceive, MAX_TRANSFER), CAPACITY - stored));
			if (!simulate && taken > 0) {
				stack.set(DataComponents.DAMAGE, stored + taken);
			}
			return taken;
		}

		@Override
		public int extractEnergy(int maxExtract, boolean simulate) {
			int stored = getEnergyStored();
			int given = Math.max(0, Math.min(Math.min(maxExtract, MAX_TRANSFER), stored));
			if (!simulate && given > 0) {
				stack.set(DataComponents.DAMAGE, stored - given);
			}
			return given;
		}

		@Override
		public int getEnergyStored() {
			return stack.getOrDefault(DataComponents.DAMAGE, 0);
		}

		@Override
		public int getMaxEnergyStored() {
			return CAPACITY;
		}

		@Override
		public boolean canExtract() {
			return true;
		}

		@Override
		public boolean canReceive() {
			return true;
		}
	}
}
