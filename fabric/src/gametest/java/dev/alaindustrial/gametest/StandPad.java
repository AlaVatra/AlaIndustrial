package dev.alaindustrial.gametest;

import dev.alaindustrial.compat.L3Chunks;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;

/**
 * A flat, empty pad for one L3 stand of the screens lane, away from its row of screens: the chunks force-loaded
 * first (a {@code /fill} that touches an unloaded chunk is refused whole, MOD-610), then a smooth stone floor at
 * y 99 and air above it.
 */
final class StandPad {

	static final int FLOOR_Y = 99;
	static final int BLOCK_Y = FLOOR_Y + 1;

	private StandPad() {
	}

	static void lay(TestSingleplayerContext singleplayer, int minX, int minZ, int maxX, int maxZ) {
		TestServerContext server = singleplayer.getServer();
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
				for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz);
				}
			}
		});
		server.runCommand("fill " + minX + " " + FLOOR_Y + " " + minZ + " " + maxX + " " + FLOOR_Y + " " + maxZ
				+ " minecraft:smooth_stone");
		server.runCommand("fill " + minX + " " + BLOCK_Y + " " + minZ + " " + maxX + " " + (BLOCK_Y + 5) + " " + maxZ
				+ " minecraft:air");
		server.runCommand("kill @e[type=!minecraft:player]");
		L3Chunks.waitRender(singleplayer);
	}
}
