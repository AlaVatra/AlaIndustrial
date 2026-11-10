package dev.alaindustrial.core.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ItemCapabilityRoster;
import dev.alaindustrial.registry.ModContent;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jspecify.annotations.Nullable;

/**
 * The item-stack capabilities, attached through {@code AttachCapabilitiesEvent.ItemStacks}: an item class in common
 * cannot override Forge's {@code getCapabilityProvider} (no Forge type in common). Every powered item of
 * {@link ItemCapabilityRoster} gets {@code ENERGY} ({@link StackAsEnergyStorage}), the two vacuum capsules get
 * {@code FLUID_HANDLER_ITEM} ({@link BucketFluidHandlerItem#capsule}).
 *
 * <p>Forge fires the event lazily, on a stack's first capability query, so the roster is read then, once.
 */
final class ItemStackCapabilitiesForge {

	private static volatile Set<Item> energyItems;

	private ItemStackCapabilitiesForge() {
	}

	static void attach(AttachCapabilitiesEvent.ItemStacks event) {
		ItemStack stack = event.getObject();
		Item item = stack.getItem();
		if (energyItems().contains(item)) {
			event.addCapability(Industrialization.id("item_energy"), new One<>(ForgeCapabilities.ENERGY,
					LazyOptional.of(() -> new StackAsEnergyStorage(stack))));
		}
		if (item == ModContent.VACUUM_CAPSULE.get() || item == ModContent.FILLED_VACUUM_CAPSULE.get()) {
			event.addCapability(Industrialization.id("capsule_fluid"), new One<>(ForgeCapabilities.FLUID_HANDLER_ITEM,
					LazyOptional.of(() -> BucketFluidHandlerItem.capsule(stack))));
		}
	}

	private static Set<Item> energyItems() {
		Set<Item> items = energyItems;
		if (items == null) {
			items = Collections.newSetFromMap(new IdentityHashMap<>());
			items.addAll(ItemCapabilityRoster.energyItems());
			energyItems = items;
		}
		return items;
	}

	/** A provider that answers one capability, from any side. */
	private record One<H>(Capability<H> capability, LazyOptional<H> handler) implements ICapabilityProvider {
		@Override
		public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
			return cap == capability ? handler.cast() : LazyOptional.empty();
		}
	}
}
