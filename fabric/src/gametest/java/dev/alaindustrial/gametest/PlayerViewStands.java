package dev.alaindustrial.gametest;

import dev.alaindustrial.gametest.visual.ShotRecorder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * What a player sees around the items and blocks of one batch (MOD-961, MOD-905, MOD-811, MOD-977, MOD-940): the
 * hands in first person, a jetpack on the back, clipboards on a wall, a thermoelectric row, the battery box's ports.
 * Shot in {@link ScreensClientGameTest}'s world after its item frames; each stand lays its own pad south of the
 * screen rig.
 */
final class PlayerViewStands {

	/**
	 * {@code -Pplayerviewonly}: the screens lane shoots only these frames and the world lane is skipped, for iterating
	 * on them without the whole catalogue.
	 */
	static final boolean ONLY = System.getProperty("alaindustrial.playerviewonly") != null;

	private PlayerViewStands() {
	}

	/** Under {@code -Pplayerviewonly}, shoots these frames, closes the manifest and returns true; else does nothing. */
	static boolean shotAlone(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		if (!ONLY) {
			return false;
		}
		shoot(context, singleplayer);
		// The clipboard's window is in the full catalogue's screen row; this lane shoots it too, alone, so its look
		// can be iterated on without the whole catalogue (MOD-905).
		TodoClipboardStand.shootWindow(context, singleplayer);
		ShotRecorder.finish();
		return true;
	}

	static void shoot(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		FirstPersonHandStands.shoot(context, singleplayer);
		TodoClipboardStand.shootWall(context, singleplayer);
		JetpackMk2Stand.shoot(context, singleplayer);
		ThermoelectricRowStand.shoot(context, singleplayer);
		BatteryBoxPortsStand.shoot(context, singleplayer);
	}
}
