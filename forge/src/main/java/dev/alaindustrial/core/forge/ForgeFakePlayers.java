package dev.alaindustrial.core.forge;

import com.mojang.authlib.GameProfile;
import dev.alaindustrial.core.net.LevelStateHolder;
import dev.alaindustrial.core.net.LevelStateRegistry;
import dev.alaindustrial.core.world.FakePlayers;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Forge implementation of the {@link FakePlayers} seam (MOD-962, slice 2.5): one {@link ForgeFakePlayer} per level
 * and profile, as NeoForge's {@code FakePlayerFactory} caches them. Server thread only.
 *
 * <p>The cache is dropped per level and at server stop through {@link LevelStateRegistry} (the roster's
 * {@code LEVEL_UNLOAD} and {@code SERVER_STOPPED} sweeps), the role NeoForge's own level-unload hook plays there.
 */
public final class ForgeFakePlayers implements FakePlayers, LevelStateHolder {

	private record Key(ServerLevel level, GameProfile profile) {
	}

	private final Map<Key, ForgeFakePlayer> players = new HashMap<>();

	public ForgeFakePlayers() {
		LevelStateRegistry.register(this);
	}

	@Override
	public ServerPlayer get(ServerLevel level, GameProfile profile) {
		return players.computeIfAbsent(new Key(level, profile), key -> new ForgeFakePlayer(key.level(), key.profile()));
	}

	@Override
	public String stateId() {
		return "forge-fake-players";
	}

	@Override
	public void clearLevel(Object level) {
		players.keySet().removeIf(key -> key.level() == level);
	}

	@Override
	public void clearAll() {
		players.clear();
	}

	@Override
	public int retained() {
		return players.size();
	}
}
