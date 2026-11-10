package dev.alaindustrial.gametest.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Forge twin of the foreign-material stand-in (MOD-290): the same two item ids under the same foreign
 * namespace as Fabric's {@code ForeignMaterialMod} and NeoForge's {@code ForeignMaterialStandIn}, so the shared
 * scenario bodies look them up by id and run unchanged. The recipes under test reach them only through
 * {@code #c:ingots/tin} and {@code #c:ores/tin}, added by the gametest data this lane shares with NeoForge.
 *
 * <p>Forge's {@link DeferredRegister} has no item helper that sets the id, so each item names its own key.
 * Dev-only, behind {@link FMLLoader#isProduction()}.
 */
public final class ForeignMaterialStandIn {

	/** Namespace of the imaginary third-party mod. Anything but {@code alaindustrial}. */
	public static final String NAMESPACE = "foreignmod";

	private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, NAMESPACE);

	static {
		item("tin_ingot");
		item("tin_ore");
	}

	private ForeignMaterialStandIn() {
	}

	private static void item(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NAMESPACE, name));
		ITEMS.register(name, () -> new Item(new Item.Properties().setId(key)));
	}

	static void register(BusGroup modBus) {
		if (FMLLoader.isProduction()) {
			return;
		}
		ITEMS.register(modBus);
	}
}
