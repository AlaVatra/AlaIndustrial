package dev.alaindustrial.gametest;

import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.gametest.visual.VisualWorld;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * The battery box's two ports side by side (MOD-940): a box turned to show its input face (the face it is turned
 * to, a socket with a blue core) beside one turned to show its output face (the opposite one, a red core), from the
 * front and at an angle that also shows a side and the top. A third box faces east, so a socket seen on a turned
 * block is checked too.
 */
final class BatteryBoxPortsStand {

	private static final int MIN_X = 66;
	private static final int MIN_Z = 20;
	private static final int BOX_Z = 26;

	private BatteryBoxPortsStand() {
	}

	static void shoot(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		StandPad.lay(singleplayer, MIN_X, MIN_Z, MIN_X + 10, MIN_Z + 10);
		server.runCommand("clear @p");
		int x = MIN_X + 4;
		box(server, x, "south");
		box(server, x + 2, "north");
		box(server, x + 4, "east");
		VisualWorld.awaitBlockOnClient(context, new BlockPos(x + 4, StandPad.BLOCK_Y, BOX_Z),
				"alaindustrial:battery_box");

		server.runCommand("tp @p " + (x + 1) + ".5 " + StandPad.BLOCK_Y + " " + (BOX_Z + 3) + ".5 180 20");
		context.waitTicks(10);
		ShotRecorder.capture("world_battery_box_ports_front");
		server.runCommand("tp @p " + (x + 6) + ".5 " + StandPad.BLOCK_Y + " " + (BOX_Z + 3) + ".5 135 30");
		context.waitTicks(10);
		ShotRecorder.capture("world_battery_box_ports_angle");
	}

	private static void box(TestServerContext server, int x, String facing) {
		server.runCommand("setblock " + x + " " + StandPad.BLOCK_Y + " " + BOX_Z
				+ " alaindustrial:battery_box[facing=" + facing + "]");
	}
}
