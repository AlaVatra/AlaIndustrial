package dev.alaindustrial.gametest.forge;

import java.lang.reflect.Method;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.gametest.ForgeGameTestHooks;

/**
 * Gives Forge's game-test server the mods' {@code en_us} translations a dedicated server has (MOD-962 stage 3).
 *
 * <p>Forge loads the mods' language files on a server in {@code ServerLifecycleHooks.handleServerStarting}
 * ({@code LanguageHook.loadLanguagesOnServer}, dedicated server only), which {@code DedicatedServer.initServer}
 * calls and the game-test server's {@code initServer} does not (it calls only {@code handleServerAboutToStart}).
 * Without this a server-side {@code Component.translatable(...).getString()} is the bare key on the Forge lane alone,
 * so a scenario that reads a status report (quarry, crystal farm) would test the harness, not the mod. The hook is
 * package-private in Forge, hence the reflection (dev-only test code).
 */
final class GameTestServerLanguages {

	private GameTestServerLanguages() {
	}

	static void install() {
		if (!ForgeGameTestHooks.isGametestServer()) {
			return;
		}
		ServerAboutToStartEvent.BUS.addListener((ServerAboutToStartEvent event) -> load(event.getServer()));
	}

	private static void load(MinecraftServer server) {
		try {
			Method load = Class.forName("net.minecraftforge.server.LanguageHook")
					.getDeclaredMethod("loadLanguagesOnServer", MinecraftServer.class);
			load.setAccessible(true);
			load.invoke(null, server);
		} catch (ReflectiveOperationException | RuntimeException e) {
			throw new IllegalStateException("cannot load the server languages on Forge's game-test server: " + e, e);
		}
	}
}
