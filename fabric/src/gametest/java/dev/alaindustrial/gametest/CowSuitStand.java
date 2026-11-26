package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.differingPixels;
import static dev.alaindustrial.gametest.VisualStandSupport.explainWithDiff;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.compat.L3Chunks;
import dev.alaindustrial.registry.ModContent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.cow.CowVariants;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cows in the shielding suit (MOD-569, the owner's "light kit"), one of each model a player meets in 26.3, left to
 * right: temperate, warm, cold, a calf - all in the full suit - and a temperate cow wearing the helmet alone. Shot
 * from the side and turned toward the camera. The frames are for a human to review (shot family
 * {@code cow_suit_}): the gas mask, blanket, straps, gaiters and boots sit on every model, the cold cow's blanket
 * lies on its fur, and the last cow shows the mask and nothing else.
 *
 * <p>What the stand asserts on its own, from three frames of the same side view: the cows are drawn at all (the row
 * with the suits taken off differs from the empty view in more than a hundredth of the picture), and the suit is
 * drawn on them (the dressed row differs from the undressed one in more than a four-hundredth). Measured on the
 * build server: five cows eight blocks away cover about 25 000 px of the 1280x720 frame, the yellow of the suit
 * alone about 2 600. The second gate is the one that goes red when the suit layer is not applied, whatever happens
 * to the cows themselves - provided the two frames show the same pose, which {@link #settle} waits for.
 */
@SuppressWarnings("UnstableApiUsage")
public final class CowSuitStand {

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

	/** Where the row stands: east of the wall stand (x 286), clear of every rig on the z 150 line. */
	private static final int ROW_X = 314;
	private static final int ROW_Y = 101;
	private static final int ROW_Z = 150;
	private static final int SPAN = 9;
	/** The five cows, left to right as the camera south of the row sees them; the fourth is a calf. */
	private static final List<ResourceKey<CowVariant>> VARIANTS = List.of(CowVariants.TEMPERATE, CowVariants.WARM,
			CowVariants.COLD, CowVariants.TEMPERATE, CowVariants.TEMPERATE);
	private static final int CALF = 3;
	private static final int HELMET_ONLY = 4;
	/** Facing east: the camera south of the row sees each flank. */
	private static final float SIDE_YAW = -90.0F;
	/** How long the client may take to show the pose and the dress the server set. */
	private static final int SETTLE_TICKS = 200;

	private CowSuitStand() {
	}

	public static void check(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		server.runCommand("weather clear");
		server.runCommand("time set day");
		server.runCommand("gamemode spectator @p");
		// Loaded and kept loaded before the first command: a /fill into an unloaded chunk refuses the whole box.
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			for (int cx = (ROW_X - SPAN) >> 4; cx <= (ROW_X + SPAN) >> 4; cx++) {
				for (int cz = (ROW_Z - SPAN) >> 4; cz <= (ROW_Z + SPAN) >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz);
				}
			}
		});
		server.runCommand("fill " + (ROW_X - SPAN) + " " + ROW_Y + " " + (ROW_Z - SPAN) + " " + (ROW_X + SPAN) + " "
				+ (ROW_Y + 4) + " " + (ROW_Z + SPAN) + " minecraft:air");
		server.runCommand("fill " + (ROW_X - SPAN) + " " + (ROW_Y - 1) + " " + (ROW_Z - SPAN) + " " + (ROW_X + SPAN)
				+ " " + (ROW_Y - 1) + " " + (ROW_Z + SPAN) + " minecraft:smooth_stone");
		List<Cow> row = new ArrayList<>();
		server.runOnServer(mc -> row.addAll(spawnRow(mc.overworld())));
		if (row.size() != VARIANTS.size()) {
			throw new AssertionError("[GUITEST][COW_SUIT] only " + row.size() + " of " + VARIANTS.size()
					+ " cows were made");
		}

		// Facing east, the camera south of the row sees each flank. The two side frames are compared, so each waits
		// until the client has the cows in that pose and in that dress: a client body turns toward the head over many
		// ticks, and a frame shot mid-turn differs from the next one by the turn, suit or no suit.
		settle(context, singleplayer, row, SIDE_YAW, true);
		Path side = shoot(context, singleplayer, "cow_suit_side");
		// The same view with the suits off: what the suit layer adds is the difference between the two.
		server.runOnServer(mc -> row.forEach(cow -> dress(cow, -1)));
		settle(context, singleplayer, row, SIDE_YAW, false);
		Path bare = shoot(context, singleplayer, "cow_suit_side_bare");
		server.runOnServer(mc -> {
			for (int i = 0; i < row.size(); i++) {
				dress(row.get(i), i);
			}
		});
		turn(server, row, -30.0F);                   // turned toward the camera: the gas masks, three quarters
		shoot(context, singleplayer, "cow_suit_front");
		server.runOnServer(mc -> row.forEach(Cow::discard));
		context.waitTicks(5);
		Path empty = shoot(context, singleplayer, "cow_suit_side_empty");

		int pixels = pixelCount(side);
		int cows = differingPixels(bare, empty);
		int suit = differingPixels(side, bare);
		LOG.info("[GUITEST][COW_SUIT] undressed cows vs empty: {} px differ, required > {}; "
				+ "dressed vs undressed: {} px, required > {}", cows, pixels / 100, suit, pixels / 400);
		if (cows <= pixels / 100) {
			throw new AssertionError("[GUITEST][COW_SUIT] the side frame with five undressed cows differs from the "
					+ "empty one in only " + cows + " px - the cows are not being drawn. "
					+ explainWithDiff(bare, empty));
		}
		if (suit <= pixels / 400) {
			throw new AssertionError("[GUITEST][COW_SUIT] the dressed row differs from the undressed one in only "
					+ suit + " px - the shielding suit is not being drawn on the cows. " + explainWithDiff(side, bare));
		}
	}

	/** The row, goals off so nobody wanders or turns to look; the last cow wears the helmet alone. */
	private static List<Cow> spawnRow(ServerLevel level) {
		List<Cow> row = new ArrayList<>();
		var variants = level.registryAccess().lookupOrThrow(Registries.COW_VARIANT);
		for (int i = 0; i < VARIANTS.size(); i++) {
			Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			if (cow == null) {
				continue;
			}
			cow.setVariant(variants.getOrThrow(VARIANTS.get(i)));
			cow.setBaby(i == CALF);
			cow.setNoAi(true);
			dress(cow, i);
			cow.snapTo(ROW_X - 6 + 3 * i + 0.5, ROW_Y, ROW_Z + 0.5, SIDE_YAW, 0.0F);
			// The client starts the body at the head yaw of the spawn packet; a head left at 0 would make every cow
			// face the camera first and turn east only over the next seconds.
			cow.setYHeadRot(SIDE_YAW);
			cow.setYBodyRot(SIDE_YAW);
			level.addFreshEntity(cow);
			row.add(cow);
		}
		return row;
	}

	/** Dresses cow {@code i} of the row (the last one gets the helmet alone); {@code -1} takes the suit off. */
	private static void dress(Cow cow, int i) {
		boolean full = i >= 0 && i != HELMET_ONLY;
		cow.setItemSlot(EquipmentSlot.HEAD, piece(i >= 0, ModContent.SHIELDING_HELMET.get()));
		cow.setItemSlot(EquipmentSlot.CHEST, piece(full, ModContent.SHIELDING_CHESTPLATE.get()));
		cow.setItemSlot(EquipmentSlot.LEGS, piece(full, ModContent.SHIELDING_LEGGINGS.get()));
		cow.setItemSlot(EquipmentSlot.FEET, piece(full, ModContent.SHIELDING_BOOTS.get()));
	}

	private static ItemStack piece(boolean worn, Item item) {
		return worn ? new ItemStack(item) : ItemStack.EMPTY;
	}

	/** Waits until the client draws every cow of the row with its body at {@code yaw} and in (or out of) the suit. */
	private static void settle(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<Cow> row,
			float yaw, boolean dressed) {
		int[] ids = row.stream().mapToInt(Cow::getId).toArray();
		placeCamera(singleplayer);                   // the client only knows the cows it is close enough to track
		context.waitFor(mc -> {
			for (int id : ids) {
				if (!(mc.level.getEntity(id) instanceof Cow cow)
						|| Math.abs(Mth.wrapDegrees(cow.yBodyRot - yaw)) > 0.5F
						|| Math.abs(Mth.wrapDegrees(cow.getYHeadRot() - yaw)) > 0.5F
						|| cow.getItemBySlot(EquipmentSlot.HEAD).isEmpty() == dressed) {
					return false;
				}
			}
			return true;
		}, SETTLE_TICKS);
	}

	private static void turn(TestServerContext server, List<Cow> row, float yaw) {
		server.runOnServer(mc -> row.forEach(cow -> {
			cow.snapTo(cow.getX(), cow.getY(), cow.getZ(), yaw, 0.0F);
			cow.setYBodyRot(yaw);
			cow.setYHeadRot(yaw);
		}));
	}

	private static int pixelCount(Path frame) {
		try {
			BufferedImage image = ImageIO.read(frame.toFile());
			if (image == null) {
				throw new AssertionError("[GUITEST][COW_SUIT] could not decode " + frame);
			}
			return image.getWidth() * image.getHeight();
		} catch (IOException e) {
			throw new AssertionError("[GUITEST][COW_SUIT] could not read " + frame, e);
		}
	}

	private static void placeCamera(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("tp @p " + (ROW_X + 0.5) + " " + (ROW_Y + 2.5) + " " + (ROW_Z + 8.5)
				+ " 180 20");
	}

	/** From the south, two and a half blocks up, looking north and a little down at the row. */
	private static Path shoot(ClientGameTestContext context, TestSingleplayerContext singleplayer, String name) {
		placeCamera(singleplayer);
		L3Chunks.waitRender(singleplayer);
		context.waitTicks(10);
		return takeCleanScreenshot(context, name);
	}
}
