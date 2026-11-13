package dev.alaindustrial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.compat.client.Poses;
import dev.alaindustrial.item.misc.TodoClipboardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The to-do clipboard held in first person (MOD-905), drawn the way vanilla draws a filled map: the owner's playtest
 * found the clipboard "hanging in the air, no hand, unnatural". Now it lies in the player's hands.
 *
 * <ul>
 * <li>In the main hand with the other hand empty, both hands hold it in front of the player; it tilts up toward the
 * eye as the player looks down, so the list is read by looking down, like a map.</li>
 * <li>With something in the other hand, or in the off hand, one arm holds it out at its side.</li>
 * <li>The walk's bob, the turn's sway, the swing and the raise and lower of a slot change come with it, because the
 * numbers are vanilla's map numbers and the pose handed in already carries the bob and the sway.</li>
 * </ul>
 *
 * <p>Vanilla gives that picture only to stacks with a map id. Every loader reaches {@link #submitInsteadOfHand} just
 * before vanilla draws a first-person hand, with the same arguments (ADR-054): on Fabric an optional client mixin at
 * the head of {@code submitArmWithItem}, on NeoForge and Forge their {@code RenderHandEvent}. The clipboard itself is
 * the item's model in the {@code none} display context — the board and the sheet's page of
 * {@code TodoSheetItemModel}, the same painter as on the wall — set where vanilla puts the map.
 *
 * <p>There is no field-of-view hook: holding a clipboard does not zoom.
 */
public final class TodoClipboardFirstPerson {

	/**
	 * The clipboard's size where a map is 0.38 wide: the board, 15 pixels tall, a little taller than a map with its
	 * border, so the page's eleven rows stay readable.
	 */
	private static final float BOARD_SCALE = 0.5f;
	/**
	 * Held in both hands the map space is doubled; the board then shrinks so its clip stays in view while the player
	 * looks down to read it.
	 */
	private static final float TWO_HANDED_SCALE = 0.4f;
	/** Moves the model so its sheet's face, centred, lies on the map's plane: the board's middle and its face. */
	private static final float SHEET_FACE_Y = 0.5f - 1.25f / 16.0f;
	private static final float SHEET_MIDDLE_Z = 0.5f - 7.5f / 16.0f;

	/** One render state per hand, refilled every frame, as vanilla keeps one per hand in its render state. */
	private static final ItemStackRenderState[] STATES = {new ItemStackRenderState(), new ItemStackRenderState()};

	private TodoClipboardFirstPerson() {
	}

	/**
	 * Called for each first-person hand just before vanilla draws it. When that hand holds a to-do clipboard, the hand
	 * (or both hands) and the clipboard are drawn here, as vanilla draws a map, and true is returned so vanilla skips
	 * its own drawing of that hand. Otherwise nothing is drawn and false is returned.
	 *
	 * @param pitch the camera's interpolated pitch, which tilts a two-handed map
	 * @param equip how far the hand is lowered by an item swap, 0 when fully raised
	 * @param swing the attack swing's progress, 0 to 1
	 */
	public static boolean submitInsteadOfHand(InteractionHand hand, ItemStack stack, PoseStack pose,
			SubmitNodeCollector collector, int light, float pitch, float equip, float swing) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !(stack.getItem() instanceof TodoClipboardItem) || player.isScoping()) {
			return false;
		}
		HumanoidArm mainArm = player.getMainArm();
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
		AvatarRenderer<?> renderer = !player.isInvisible()
				&& minecraft.getEntityRenderDispatcher().getRenderer(player) instanceof AvatarRenderer<?> avatar
				? avatar : null;
		pose.pushPose();
		if (hand == InteractionHand.MAIN_HAND && player.getOffhandItem().isEmpty()) {
			twoHanded(pose, collector, light, pitch, equip, swing, player, renderer);
			submitBoard(hand, stack, pose, collector, light, player, TWO_HANDED_SCALE);
		} else {
			oneHanded(pose, collector, light, equip, arm, swing, player, renderer);
			submitBoard(hand, stack, pose, collector, light, player, BOARD_SCALE);
		}
		pose.popPose();
		return true;
	}

	/** Vanilla's two-handed map: both hands under it, tilted toward the eye as the player looks down. */
	private static void twoHanded(PoseStack pose, SubmitNodeCollector collector, int light, float pitch,
			float equip, float swing, LocalPlayer player, @Nullable AvatarRenderer<?> renderer) {
		float root = Mth.sqrt(swing);
		float lift = -0.2f * Mth.sin(swing * Mth.PI);
		float push = -0.4f * Mth.sin(root * Mth.PI);
		pose.translate(0.0f, -lift / 2.0f, push);
		float tilt = mapTilt(pitch);
		pose.translate(0.0f, 0.04f + equip * -1.2f + tilt * -0.5f, -0.72f);
		rotate(pose, Axis.XP, tilt * -85.0f);
		if (renderer != null) {
			pose.pushPose();
			rotate(pose, Axis.YP, 90.0f);
			mapHand(pose, collector, light, HumanoidArm.RIGHT, player, renderer);
			mapHand(pose, collector, light, HumanoidArm.LEFT, player, renderer);
			pose.popPose();
		}
		rotate(pose, Axis.XP, Mth.sin(root * Mth.PI) * 20.0f);
		pose.scale(2.0f, 2.0f, 2.0f);
	}

	/** Vanilla's one-handed map: the arm at its side holding it out. */
	private static void oneHanded(PoseStack pose, SubmitNodeCollector collector, int light, float equip,
			HumanoidArm arm, float swing, LocalPlayer player, @Nullable AvatarRenderer<?> renderer) {
		float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		pose.translate(side * 0.125f, -0.125f, 0.0f);
		if (renderer != null) {
			pose.pushPose();
			rotate(pose, Axis.ZP, side * 10.0f);
			playerArm(pose, collector, light, equip, swing, arm, player, renderer);
			pose.popPose();
		}
		pose.translate(side * 0.51f, -0.08f + equip * -1.2f, -0.75f);
		float root = Mth.sqrt(swing);
		float wave = Mth.sin(root * Mth.PI);
		pose.translate(side * -0.5f * wave, 0.4f * Mth.sin(root * Mth.TWO_PI) - 0.3f * wave,
				-0.3f * Mth.sin(swing * Mth.PI));
		rotate(pose, Axis.XP, wave * -45.0f);
		rotate(pose, Axis.YP, side * wave * -30.0f);
	}

	/**
	 * The clipboard where vanilla would draw the map's picture (the pose {@link #twoHanded} or {@link #oneHanded}
	 * left): the model turned so its sheet faces the eye with the clip at the top and the page reading left to
	 * right, its face on the map's plane.
	 */
	private static void submitBoard(InteractionHand hand, ItemStack stack, PoseStack pose,
			SubmitNodeCollector collector, int light, LocalPlayer player, float scale) {
		ItemStackRenderState state = STATES[hand.ordinal()];
		Minecraft.getInstance().getItemModelResolver().updateForLiving(state, stack, ItemDisplayContext.NONE, player);
		pose.pushPose();
		pose.scale(scale, scale, scale);
		rotate(pose, Axis.XP, 90.0f);
		rotate(pose, Axis.YP, 180.0f);
		pose.translate(0.0f, SHEET_FACE_Y, SHEET_MIDDLE_Z);
		state.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	/** Vanilla's tilt of a two-handed map: 0 looking down at it, 1 looking ahead or up. */
	private static float mapTilt(float pitch) {
		float t = Mth.clamp(1.0f - pitch / 45.0f + 0.1f, 0.0f, 1.0f);
		return -Mth.cos(t * Mth.PI) * 0.5f + 0.5f;
	}

	/** One of the two hands under a two-handed map, as vanilla places it. */
	private static void mapHand(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidArm arm,
			LocalPlayer player, AvatarRenderer<?> renderer) {
		float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		pose.pushPose();
		rotate(pose, Axis.YP, 92.0f);
		rotate(pose, Axis.XP, 45.0f);
		rotate(pose, Axis.ZP, side * -41.0f);
		pose.translate(side * 0.3f, -1.1f, 0.45f);
		hand(pose, collector, light, arm, player, renderer);
		pose.popPose();
	}

	/** The arm of a one-handed map: vanilla's empty-hand arm, swing included. */
	private static void playerArm(PoseStack pose, SubmitNodeCollector collector, int light, float equip,
			float swing, HumanoidArm arm, LocalPlayer player, AvatarRenderer<?> renderer) {
		float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		float root = Mth.sqrt(swing);
		float x = -0.3f * Mth.sin(root * Mth.PI);
		float y = 0.4f * Mth.sin(root * Mth.TWO_PI);
		float z = -0.4f * Mth.sin(swing * Mth.PI);
		pose.translate(side * (x + 0.64f), y - 0.6f + equip * -0.6f, z - 0.72f);
		rotate(pose, Axis.YP, side * 45.0f);
		rotate(pose, Axis.YP, side * Mth.sin(root * Mth.PI) * 70.0f);
		rotate(pose, Axis.ZP, side * Mth.sin(swing * swing * Mth.PI) * -20.0f);
		pose.translate(side * -1.0f, 3.6f, 3.5f);
		rotate(pose, Axis.ZP, side * 120.0f);
		rotate(pose, Axis.XP, 200.0f);
		rotate(pose, Axis.YP, side * -135.0f);
		pose.translate(side * 5.6f, 0.0f, 0.0f);
		hand(pose, collector, light, arm, player, renderer);
	}

	private static void hand(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidArm arm,
			LocalPlayer player, AvatarRenderer<?> renderer) {
		Identifier skin = player.getSkin().body().texturePath();
		if (arm == HumanoidArm.RIGHT) {
			boolean sleeve = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
			renderer.renderRightHand(pose, collector, light, skin, sleeve);
		} else {
			boolean sleeve = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
			renderer.renderLeftHand(pose, collector, light, skin, sleeve);
		}
	}

	private static void rotate(PoseStack pose, Axis axis, float degrees) {
		Poses.rotate(pose, axis.rotationDegrees(degrees));
	}
}
