package dev.alaindustrial.core.forge;

import dev.alaindustrial.block.entity.ChestPairHooks;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.item.ItemLookup;
import dev.alaindustrial.core.monitor.ItemViewLookup;
import dev.alaindustrial.item.energy.ItemEnergyBridge;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import net.minecraftforge.event.AttachCapabilitiesEvent;

/**
 * The fluid and item transfer seams of MinecraftForge (MOD-962, slice 2.3), the part of
 * {@code IndustrializationNeoForge.installLoaderSeams} and {@code registerCapabilities} that is about moving
 * fluids and items between blocks and stacks. The energy seams stay in the entry point.
 */
public final class ForgeBridges {

	private ForgeBridges() {
	}

	/** The common lookups and bridges, and the chest-pair hook; call with the other loader seams. */
	public static void install() {
		FluidLookup.install(new ForgeFluidLookup());
		ItemLookup.install(new ForgeItemLookup());
		ItemViewLookup.install(new ForgeItemViewLookup());
		ItemEnergyBridge.install(new ForgeItemEnergyBridge());
		ItemFluidBridge.install(new ForgeItemFluidBridge());
		// A chest paired or unpaired: the handlers other mods hold for it are invalidated, not the whole block entity.
		ChestPairHooks.CAP_INVALIDATOR = be -> be.getCapability(BlockCapabilityProviderForge.SELF)
				.ifPresent(BlockCapabilityProviderForge::invalidateItems);
	}

	/** The capabilities of our block entities and item stacks, both attached on Forge's global bus. */
	public static void registerCapabilityEvents() {
		AttachCapabilitiesEvent.BlockEntities.BUS.addListener(BlockCapabilitiesForge::attach);
		AttachCapabilitiesEvent.ItemStacks.BUS.addListener(ItemStackCapabilitiesForge::attach);
	}
}
