package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge {@code BlockEntityType} registration: a replay of the shared {@link ContentManifest#BLOCK_ENTITIES}
 * list over {@link Registries#BLOCK_ENTITY_TYPE}, lazily through a {@link DeferredRegister}; the twin of
 * {@code ModBlockEntitiesNeoForge} (MOD-962). The energy capability is hung on the block entities from
 * outside, by {@code AttachCapabilitiesEvent.BlockEntities} in the entry point.
 *
 * <p>There are deliberately NO typed handles: code that needs a concrete type asks the manifest.
 */
public final class ModBlockEntitiesForge {
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
			DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Industrialization.MOD_ID);

	/**
	 * Every manifest entry, queued on {@link #BLOCK_ENTITIES} the moment this class loads. Declared right
	 * after the register on purpose: static fields initialise in textual order.
	 */
	private static final int REGISTERED = registerAll();

	private ModBlockEntitiesForge() {
	}

	/** Queues every {@link ContentManifest#BLOCK_ENTITIES} entry, in list order; returns how many. */
	private static int registerAll() {
		int registered = 0;
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			register(def);
			registered++;
		}
		return registered;
	}

	/**
	 * Class-load trigger for the {@code @Mod} constructor. Queueing and {@link ModContent} binding both happen
	 * in the static initializer above, so this only has to touch the class, and it checks, cheaply, that the
	 * replay covered the whole manifest rather than silently stopping short.
	 */
	public static void init() {
		if (REGISTERED != ContentManifest.BLOCK_ENTITIES.size()) {
			throw new IllegalStateException("ModBlockEntitiesForge registered " + REGISTERED + " of "
					+ ContentManifest.BLOCK_ENTITIES.size() + " manifest block entities");
		}
	}

	/**
	 * One manifest entry. The valid-block set is resolved <b>inside</b> the type supplier, which the register
	 * invokes only when the block-entity {@code RegisterEvent} fires, by which point every block is registered.
	 * The {@link ModContent} slot is bound via {@code holder::get}, which bridges the invariant wildcard while
	 * staying lazy.
	 */
	private static <T extends BlockEntity> void register(ContentManifest.BlockEntityDef<T> def) {
		RegistryObject<BlockEntityType<T>> holder = BLOCK_ENTITIES.register(def.id(),
				() -> new BlockEntityType<>(def.factory(), def.blockSet()));
		def.bind().accept(holder::get);
	}
}
