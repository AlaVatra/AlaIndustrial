package dev.alaindustrial.gametest;

import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * First-person frames of items in use or held up (MOD-961, MOD-905): the slingshot at its three pull frames, the
 * Electric Bow beside the vanilla bow at the same draw times, and the to-do clipboard held like a map.
 *
 * <p>The draw is the player's own: the use key is held through the test input, so the client starts using the item
 * the way a held right button does and keeps counting draw ticks until the key is let go. Each frame waits for the
 * draw tick it is named after rather than for a number of ticks, so a slow tick cannot shift it into the next pull
 * frame. A frame is a picture for review: it asserts nothing about where the hands are.
 */
final class FirstPersonHandStands {

	private static final int MIN_X = 4;
	private static final int MIN_Z = 20;
	private static final int MAX_X = 16;
	private static final int MAX_Z = 32;
	private static final int STAND_X = 10;
	private static final int STAND_Z = 22;
	/** A stone backdrop across the view, so the hands are judged against the same thing in every run. */
	private static final int WALL_Z = 29;

	private FirstPersonHandStands() {
	}

	static void shoot(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		StandPad.lay(singleplayer, MIN_X, MIN_Z, MAX_X, MAX_Z);
		server.runCommand("fill " + MIN_X + " " + StandPad.BLOCK_Y + " " + WALL_Z + " " + MAX_X + " "
				+ (StandPad.BLOCK_Y + 5) + " " + WALL_Z + " minecraft:stone_bricks");
		server.runCommand("gamemode creative @p");
		server.runCommand("tp @p " + STAND_X + ".5 " + StandPad.BLOCK_Y + " " + STAND_Z + ".5 0 0");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(10);

		shootSlingshot(context, server);
		shootBow(context, server, "alaindustrial:electric_bow", "electric");
		shootBow(context, server, "minecraft:bow", "vanilla");
		shootClipboard(context, server);

		server.runCommand("clear @p");
		context.waitTicks(3);
	}

	private static void shootSlingshot(ClientGameTestContext context, TestServerContext server) {
		hold(context, server, "alaindustrial:slingshot", "minecraft:torch 16");
		context.getInput().holdKey(options -> options.keyUse);
		awaitDraw(context, 2);
		ShotRecorder.capture("hand_slingshot_draw_start");
		awaitDraw(context, 14);
		ShotRecorder.capture("hand_slingshot_draw_13");
		awaitDraw(context, 20);
		ShotRecorder.capture("hand_slingshot_draw_18");
		letGo(context, server);
	}

	private static void shootBow(ClientGameTestContext context, TestServerContext server, String id, String name) {
		hold(context, server, id, "minecraft:arrow 16");
		context.getInput().holdKey(options -> options.keyUse);
		awaitDraw(context, 6);
		ShotRecorder.capture("hand_bow_draw_" + name + "_6");
		awaitDraw(context, 22);
		ShotRecorder.capture("hand_bow_draw_" + name + "_22");
		letGo(context, server);
	}

	/**
	 * The clipboard of the screen rig's list held like a map (MOD-905), with 12 oak logs and 128 cobblestone in the
	 * inventory so one counter falls short and one is green: in both hands looking down at it and looking ahead, in
	 * the main hand beside a torch in the other, and in the off hand alone.
	 */
	private static void shootClipboard(ClientGameTestContext context, TestServerContext server) {
		holdClipboard(context, server, EquipmentSlot.MAINHAND, false);
		look(context, server, 50);
		ShotRecorder.capture("hand_todo_clipboard_two_hands_reading");
		look(context, server, 0);
		ShotRecorder.capture("hand_todo_clipboard_two_hands_ahead");
		holdClipboard(context, server, EquipmentSlot.MAINHAND, true);
		look(context, server, 30);
		ShotRecorder.capture("hand_todo_clipboard_right_beside_torch");
		holdClipboard(context, server, EquipmentSlot.OFFHAND, false);
		ShotRecorder.capture("hand_todo_clipboard_left");
		look(context, server, 0);
	}

	private static void holdClipboard(ClientGameTestContext context, TestServerContext server, EquipmentSlot slot,
			boolean torchInOtherHand) {
		server.runCommand("clear @p");
		server.runOnServer(mc -> {
			ServerPlayer player = mc.getPlayerList().getPlayers().get(0);
			player.getInventory().setSelectedSlot(0);
			ItemStack board = new ItemStack(ModContent.TODO_CLIPBOARD.get());
			board.set(ModDataComponents.TODO_LIST.get(), TodoClipboardStand.list());
			player.setItemSlot(slot, board);
			if (torchInOtherHand) {
				player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
			}
			player.getInventory().setItem(4, new ItemStack(Items.OAK_LOG, 12));
			player.getInventory().setItem(5, new ItemStack(Items.COBBLESTONE, 64));
			player.getInventory().setItem(6, new ItemStack(Items.COBBLESTONE, 64));
		});
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		context.waitTicks(20);
	}

	/** Turns the camera to {@code pitch} degrees below the horizon, facing the backdrop. */
	private static void look(ClientGameTestContext context, TestServerContext server, int pitch) {
		server.runCommand("tp @p " + STAND_X + ".5 " + StandPad.BLOCK_Y + " " + STAND_Z + ".5 0 " + pitch);
		context.waitTicks(10);
	}

	private static void hold(ClientGameTestContext context, TestServerContext server, String item, String ammo) {
		server.runCommand("clear @p");
		server.runCommand("give @p " + item);
		server.runCommand("item replace entity @p hotbar.8 with " + ammo);
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		context.waitTicks(10);
	}

	private static void awaitDraw(ClientGameTestContext context, int ticks) {
		context.waitFor(mc -> mc.player.isUsingItem() && mc.player.getTicksUsingItem() >= ticks);
	}

	/** Lets the shot go, then clears whatever it threw and puts the backdrop back where a torch may have hit it. */
	private static void letGo(ClientGameTestContext context, TestServerContext server) {
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitTicks(20);
		server.runCommand("kill @e[type=!minecraft:player]");
		server.runCommand("fill " + MIN_X + " " + StandPad.BLOCK_Y + " " + (STAND_Z + 1) + " " + MAX_X + " "
				+ (StandPad.BLOCK_Y + 5) + " " + (WALL_Z - 1) + " minecraft:air");
		server.runCommand("fill " + MIN_X + " " + StandPad.BLOCK_Y + " " + WALL_Z + " " + MAX_X + " "
				+ (StandPad.BLOCK_Y + 5) + " " + WALL_Z + " minecraft:stone_bricks");
		context.waitTicks(3);
	}
}
