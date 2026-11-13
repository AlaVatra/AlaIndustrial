package dev.alaindustrial.gametest;

import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.gametest.visual.VisualWorld;
import dev.alaindustrial.visual.ShotGroup;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Thermoelectric generators in the world (MOD-811): the demo stand's bay rebuilt as it is there (three generators
 * from the aisle back, magma west and blue ice east of each, the battery box behind the back one), its first
 * generator's screen opened by a right click, and a lone generator between a soul campfire and ice (a soul
 * campfire: its light is too weak to melt the ice), whose hot and cold
 * faces are the only ones a player can see: magma and blue ice are opaque and hide the faces they touch. A bare
 * generator with nothing around it shows the idle casing and the screen's "no pair" status; both screens are shot
 * again in Russian, the language whose status line once ran out of the frame.
 */
final class ThermoelectricRowStand {

	private static final int MIN_X = 48;
	private static final int MIN_Z = 20;
	private static final int ROW_X = 53;
	/** The row's first generator, on the aisle; the demo stand's z=6..8 shifted onto this pad. */
	private static final int ROW_Z = 26;
	private static final int LONE_X = 59;
	/** A generator with nothing at any face: the idle casing and the "no pair" status. */
	private static final int BARE_X = 56;

	private ThermoelectricRowStand() {
	}

	static void shoot(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		StandPad.lay(singleplayer, MIN_X, MIN_Z, MIN_X + 14, MIN_Z + 14);
		server.runCommand("clear @p");
		for (int z = ROW_Z; z <= ROW_Z + 2; z++) {
			setblock(server, ROW_X - 1, z, "minecraft:magma_block");
			setblock(server, ROW_X, z, "alaindustrial:thermoelectric_generator");
			setblock(server, ROW_X + 1, z, "minecraft:blue_ice");
		}
		setblock(server, ROW_X, ROW_Z + 3, "alaindustrial:battery_box");
		setblock(server, LONE_X - 1, ROW_Z, "minecraft:soul_campfire");
		setblock(server, LONE_X, ROW_Z, "alaindustrial:thermoelectric_generator");
		setblock(server, LONE_X + 1, ROW_Z, "minecraft:ice");
		setblock(server, BARE_X, ROW_Z + 3, "alaindustrial:thermoelectric_generator");
		VisualWorld.awaitBlockOnClient(context, new BlockPos(LONE_X + 1, StandPad.BLOCK_Y, ROW_Z), "minecraft:ice");
		context.waitTicks(40);

		tp(server, ROW_X + 0.5, ROW_Z - 3.5, 0, 30);
		context.waitTicks(10);
		ShotRecorder.capture("world_thermoelectric_row");
		tp(server, LONE_X - 1.5, ROW_Z - 2.0, -39, 20);
		context.waitTicks(10);
		ShotRecorder.capture("world_thermoelectric_lone_hot_side");
		tp(server, LONE_X + 2.5, ROW_Z - 2.0, 39, 20);
		context.waitTicks(10);
		ShotRecorder.capture("world_thermoelectric_lone_cold_side");
		tp(server, BARE_X - 1.0, ROW_Z + 1.7, -40, 35);
		context.waitTicks(10);
		ShotRecorder.capture("world_thermoelectric_bare_casing");

		shootScreens(context, server, "");
		switchLanguage(context, "ru_ru");
		ShotRecorder.locale("ru_ru");
		try {
			shootScreens(context, server, "_ru");
		} finally {
			switchLanguage(context, "en_us");
			ShotRecorder.locale("en_us");
		}
	}

	/** The row generator's screen and the bare one's, each opened by a right click as a player opens it. */
	private static void shootScreens(ClientGameTestContext context, TestServerContext server, String suffix) {
		BlockPos first = new BlockPos(ROW_X, StandPad.BLOCK_Y, ROW_Z);
		tp(server, ROW_X + 0.5, ROW_Z - 1.5, 0, 30);
		openScreen(context, first, "thermoelectric generator of the row");
		ShotRecorder.capture("gui_thermoelectric_generator_row" + suffix, ShotGroup.GUI, "thermoelectric_generator",
				ShotRecorder.rules("R-GUI-01", "R-GUI-03"),
				"Thermoelectric generator of the demo row (MOD-811), magma west and blue ice east: the west-east "
						+ "row hot at the west cell and cold at the east one, its bar lit and its rate shown, the "
						+ "other two rows at 0, the output line agrees, every label inside the frame");
		VisualWorld.awaitNoScreen(context);

		BlockPos bare = new BlockPos(BARE_X, StandPad.BLOCK_Y, ROW_Z + 3);
		tp(server, BARE_X + 0.5, ROW_Z + 1.5, 0, 30);
		openScreen(context, bare, "bare thermoelectric generator");
		ShotRecorder.capture("gui_thermoelectric_generator_bare" + suffix, ShotGroup.GUI, "thermoelectric_generator",
				ShotRecorder.rules("R-GUI-01", "R-GUI-03"),
				"Thermoelectric generator with nothing at any face (MOD-811): six neutral cells, three dark "
						+ "bars, every rate 0 and the no-pair status inside the frame");
		VisualWorld.awaitNoScreen(context);
	}

	private static void openScreen(ClientGameTestContext context, BlockPos pos, String what) {
		context.waitTicks(5);
		context.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)));
		VisualWorld.awaitContainerScreen(context, what);
		context.waitTicks(10);
	}

	/**
	 * Applies a language the way the language screen does and waits, bounded, until a key known to differ between
	 * English and Russian reads in the wanted script (the reload must not be joined on the render thread).
	 */
	private static void switchLanguage(ClientGameTestContext context, String code) {
		AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
		context.runOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			reload.set(mc.reloadResourcePacks());
		});
		boolean wantCyrillic = code.startsWith("ru");
		for (int i = 0; i < 100; i++) {
			CompletableFuture<Void> future = reload.get();
			String probe = context.computeOnClient(
					mc -> Component.translatable("block.alaindustrial.thermoelectric_generator").getString());
			boolean cyrillic = probe.codePoints().anyMatch(cp -> cp >= 0x0400 && cp <= 0x04FF);
			if (future != null && future.isDone() && cyrillic == wantCyrillic) {
				// The reload overlay fades out over about a second after the reload is done: wait it out.
				context.waitTicks(40);
				return;
			}
			context.waitTicks(5);
		}
		throw new AssertionError("[THERMO] language switch to '" + code + "' did not land within 25 s");
	}

	private static void setblock(TestServerContext server, int x, int z, String block) {
		server.runCommand("setblock " + x + " " + StandPad.BLOCK_Y + " " + z + " " + block);
	}

	private static void tp(TestServerContext server, double x, double z, float yaw, float pitch) {
		server.runCommand("tp @p " + x + " " + StandPad.BLOCK_Y + " " + z + " " + yaw + " " + pitch);
	}
}
