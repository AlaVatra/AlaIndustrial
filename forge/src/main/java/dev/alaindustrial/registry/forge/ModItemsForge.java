package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.tool.HammerItem;
import dev.alaindustrial.item.tool.forge.HammerItemForge;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge item registration: a replay of the shared {@link ContentManifest#ITEMS} list, lazily through a
 * {@link DeferredRegister}; the twin of {@code ModItemsNeoForge} (MOD-962).
 *
 * <p><b>What lives here is the Forge registration MECHANISM, and only that:</b> the {@link DeferredRegister},
 * a lazy supplier per entry, and {@link #LOADER_ITEMS}, the forge hammer whose CLASS is Forge's rather than
 * shared. Unlike NeoForge's {@code DeferredRegister.Items#registerItem}, Forge's plain register does not stamp
 * the registry key on the {@code Properties}, so the supplier calls {@code setId} itself.
 *
 * <p><b>Timing.</b> Every factory runs when the item {@code RegisterEvent} fires, by which point the block,
 * fluid and entity-type registries are already filled (vanilla registry order), so the manifest's by-id
 * resolution is safe here for the same reason it is on the other two loaders.
 *
 * <p><b>The typed field below is a handle, not a registration:</b> the creative tab's icon reads it. Adding an
 * item does NOT require adding one.
 */
public final class ModItemsForge {
	public static final DeferredRegister<Item> ITEMS =
			DeferredRegister.create(Registries.ITEM, Industrialization.MOD_ID);

	/** The forge hammer alone needs a loader-specific crafting-remainder hook. */
	private static final Map<String, Function<Item.Properties, ? extends Item>> LOADER_ITEMS = Map.of(
			"forge_hammer", p -> new HammerItemForge(HammerItem.hammerProperties(p)));

	/**
	 * Every manifest entry, queued on {@link #ITEMS} the moment this class loads. Declared right after the
	 * register and the override map on purpose: static fields initialise in textual order.
	 */
	private static final Map<String, RegistryObject<Item>> REGISTERED = registerAll();

	/** The creative tab's icon ({@code ModCreativeTabForge}). */
	public static final RegistryObject<Item> MACERATOR_ITEM = handle("macerator");

	private ModItemsForge() {
	}

	/** Queues every {@link ContentManifest#ITEMS} entry, in list order, and binds each into ModContent. */
	private static Map<String, RegistryObject<Item>> registerAll() {
		Map<String, RegistryObject<Item>> registered = new LinkedHashMap<>();
		for (ContentManifest.ItemDef def : ContentManifest.ITEMS) {
			if (registered.put(def.id(), register(def)) != null) {
				throw new IllegalStateException(
						"ContentManifest.ITEMS declares item id '" + def.id() + "' twice");
			}
		}
		return Map.copyOf(registered);
	}

	/**
	 * One manifest entry: the factory (shared, or this loader's override) is called inside the supplier with
	 * id-stamped {@code Properties}, when the item {@code RegisterEvent} fires. That lateness is what lets a
	 * factory resolve a block, a fluid, an entity type, or an EARLIER item of this very list.
	 *
	 * <p>The {@link ModContent} slot is bound HERE, at class load: a {@link RegistryObject} is a
	 * {@code Supplier} and resolves lazily, so binding before the event is legal.
	 */
	private static RegistryObject<Item> register(ContentManifest.ItemDef def) {
		Function<Item.Properties, ? extends Item> factory =
				ContentManifest.itemFactory(def, LOADER_ITEMS.get(def.id()));
		ResourceKey<Item> key = ITEMS.key(def.id());
		RegistryObject<Item> holder =
				ITEMS.register(def.id(), () -> factory.apply(new Item.Properties().setId(key)));
		def.bind().accept(holder);
		return holder;
	}

	/** The queued holder for a manifest id; throws rather than returning {@code null}. */
	private static RegistryObject<Item> handle(String id) {
		RegistryObject<Item> holder = REGISTERED.get(id);
		if (holder == null) {
			throw new IllegalStateException("ModItemsForge handle for '" + id
					+ "' has no queued item: is the entry missing from ContentManifest.ITEMS?");
		}
		return holder;
	}

	/**
	 * Class-load trigger for the {@code @Mod} constructor. Queueing and {@link ModContent} binding both happen
	 * in the static initializer above, so this only has to touch the class, and it checks, cheaply, that the
	 * replay covered the whole manifest rather than silently stopping short.
	 */
	public static void init() {
		if (REGISTERED.size() != ContentManifest.ITEMS.size()) {
			throw new IllegalStateException("ModItemsForge registered " + REGISTERED.size() + " of "
					+ ContentManifest.ITEMS.size() + " manifest items");
		}
	}
}
