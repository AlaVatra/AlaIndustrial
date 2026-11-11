package dev.alaindustrial;

import dev.alaindustrial.network.ConfigSync;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;

/**
 * Forge glue for the loader-neutral {@link Config} (MOD-962, slice 2.4), line for line the twin of
 * {@code NeoForgeConfigLoader}: the file is {@code <config dir>/alaindustrial.json}, a dev run takes the knobs'
 * development defaults (MOD-847) before the first load, and {@code /reload} re-reads it and re-sends it to every
 * connected client (MOD-695). The file format, the startup write of defaults and {@code /ala config reload} are
 * all common.
 */
final class ForgeConfigLoader {

	private ForgeConfigLoader() {
	}

	private static Path configPath() {
		return FMLPaths.CONFIGDIR.get().resolve("alaindustrial.json");
	}

	static void register() {
		Config.configPath = ForgeConfigLoader::configPath;
		if (!FMLEnvironment.production) {
			Config.REGISTRY.useDevelopmentDefaults();
		}
		Config.reload();
	}

	static void reload(@Nullable MinecraftServer server) {
		ConfigSync.reloadAndBroadcast(server);
	}
}
