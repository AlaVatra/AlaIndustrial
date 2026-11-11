package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge block registration: a replay of the shared {@link ContentManifest#BLOCKS} list, the twin of
 * {@code ModBlocksNeoForge} (MOD-962).
 *
 * <p><b>What lives here is the Forge registration MECHANISM, and only that:</b> the {@link DeferredRegister}
 * (which must live on this side) and a lazy supplier per entry, invoked when the block {@code RegisterEvent}
 * fires. That lateness is what lets manifest entries read earlier ones ({@code enriched_uranium_wall_torch}
 * reads the standing torch, the liquid blocks read their fluid).
 *
 * <p><b>The one Forge difference.</b> Forge has no {@code DeferredRegister.Blocks#registerBlock}: its plain
 * {@code DeferredRegister} does not stamp the registry key on the {@code Properties}, and 26.x refuses a block
 * built without one, so the supplier calls {@code setId} itself with the register's own
 * {@link DeferredRegister#key key}.
 *
 * <p>No typed handle is kept: everything reads {@link ModContent}, which the replay binds.
 */
public final class ModBlocksForge {
	public static final DeferredRegister<Block> BLOCKS =
			DeferredRegister.create(Registries.BLOCK, Industrialization.MOD_ID);

	/**
	 * Every manifest entry, queued on {@link #BLOCKS} the moment this class loads. Declared right after the
	 * register on purpose: static fields initialise in textual order, so the register exists here.
	 */
	private static final Map<String, RegistryObject<?>> REGISTERED = registerAll();

	private ModBlocksForge() {
	}

	/** Queues every {@link ContentManifest#BLOCKS} entry, in list order, and binds each into ModContent. */
	private static Map<String, RegistryObject<?>> registerAll() {
		Map<String, RegistryObject<?>> registered = new LinkedHashMap<>();
		for (ContentManifest.BlockDef<?> def : ContentManifest.BLOCKS) {
			if (registered.put(def.id(), register(def)) != null) {
				throw new IllegalStateException(
						"ContentManifest.BLOCKS declares block id '" + def.id() + "' twice");
			}
		}
		return Map.copyOf(registered);
	}

	/**
	 * One manifest entry: the entry's own properties chain ({@link ContentManifest.BlockDef#props()}) applied
	 * to a bare {@code Properties.of()}, stamped with the registry key and handed to the entry's factory, all
	 * inside the supplier, so nothing runs before the block {@code RegisterEvent}.
	 *
	 * <p>The {@link ModContent} slot is bound HERE, at class load, with {@code holder::get}: a lazy handle,
	 * so binding before the event is legal.
	 */
	private static <T extends Block> RegistryObject<T> register(ContentManifest.BlockDef<T> def) {
		ResourceKey<Block> key = BLOCKS.key(def.id());
		RegistryObject<T> holder = BLOCKS.register(def.id(),
				() -> def.factory().apply(def.props().apply(BlockBehaviour.Properties.of()).setId(key)));
		def.bind().accept(holder::get);
		return holder;
	}

	/**
	 * Class-load trigger for the {@code @Mod} constructor. Queueing and {@link ModContent} binding both happen
	 * in the static initializer above, so this only has to touch the class, and it checks, cheaply, that the
	 * replay covered the whole manifest rather than silently stopping short.
	 */
	public static void init() {
		if (REGISTERED.size() != ContentManifest.BLOCKS.size()) {
			throw new IllegalStateException("ModBlocksForge registered " + REGISTERED.size() + " of "
					+ ContentManifest.BLOCKS.size() + " manifest blocks");
		}
	}
}
