package dev.alaindustrial.registry.forge;

import dev.alaindustrial.registry.ModDataRegistries;
import net.minecraftforge.registries.DataPackRegistryEvent;

/**
 * Forge's replay of {@link ModDataRegistries#REGISTRIES} (MOD-962), the counterpart of
 * {@code ModDataRegistriesNeoForge}: every datapack registry of the mod is added on
 * {@link DataPackRegistryEvent.NewRegistry}. A registry with no network codec is read by the server only;
 * Forge takes a {@code null} network codec so, and the registry is not reloadable (the builder default),
 * which is what NeoForge's {@code worldRegistry} means.
 *
 * <p>Wire it on the event's own bus: {@code DataPackRegistryEvent.NewRegistry.BUS.addListener(...)}.
 */
public final class ModDataRegistriesForge {

	private ModDataRegistriesForge() {
	}

	/** The listener: one call per entry of the shared list. */
	public static void addAll(DataPackRegistryEvent.NewRegistry event) {
		for (ModDataRegistries.DataRegistry<?> registry : ModDataRegistries.REGISTRIES) {
			add(event, registry);
		}
	}

	private static <T> void add(DataPackRegistryEvent.NewRegistry event, ModDataRegistries.DataRegistry<T> registry) {
		event.dataPackRegistry(registry.key(), registry.codec(), registry.networkCodec());
	}
}
