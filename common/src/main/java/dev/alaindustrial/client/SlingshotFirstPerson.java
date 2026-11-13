package dev.alaindustrial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.compat.client.Poses;
import dev.alaindustrial.item.tool.SlingshotItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * The slingshot's first-person draw (MOD-961): the second hand holding the pouch, and the bow's zoom.
 *
 * <p>Vanilla gives both to {@code Items.BOW} only, by identity. Every loader reaches these two entry points at the
 * same moment with the same arguments, so the player sees one picture: on Fabric two optional client mixins (the
 * head of the first-person {@code submitArmWithItem}, and the modifier handed to the last {@code Mth.lerp} of
 * {@code AbstractClientPlayer.getFieldOfViewModifier}), on NeoForge and Forge their {@code RenderHandEvent} and
 * {@code ComputeFovModifierEvent}, which those loaders fire from the same two places.
 *
 * <p>The decisions and the numbers live in {@link SlingshotDrawMath} and, for the bow's zoom, {@link BowDrawMath}.
 * Everything is read from the local player rather than from a render state, because Forge's hand event carries none.
 */
public final class SlingshotFirstPerson {

	/**
	 * Where the fist sits in the first pull frame, in view units: on the pouch, which the item model's first frame
	 * puts in the middle of the view, a little below the eye, half a block ahead — and just under it, on the
	 * player's side, so the fist pinches the pouch from below and the pouch stays in sight.
	 */
	private static final float POUCH_Y = -0.131f - 0.14f;
	private static final float POUCH_Z = -0.545f + 0.03f;

	/**
	 * The arm's turn about the fist: swung out to the side and tipped down, so the forearm reaches in from the lower
	 * corner across the view instead of pointing at the eye and filling the middle of it.
	 */
	private static final float ARM_YAW = 80.0f;
	private static final float ARM_PITCH = -15.0f;

	/** How far the arm drops while the hands are being swapped, as vanilla lowers an arm with its item. */
	private static final float EQUIP_DROP = 0.6f;

	private SlingshotFirstPerson() {
	}

	/**
	 * Called for each first-person hand just before vanilla draws it. While the local player draws a slingshot, the
	 * hand that does not hold it is drawn here as a bare arm gripping the pouch, and true is returned so vanilla
	 * skips its own drawing of that hand (and the item in it). Otherwise nothing is drawn and false is returned.
	 *
	 * @param equip how far the hand is lowered by an item swap, 0 when fully raised
	 */
	public static boolean submitInsteadOfHand(InteractionHand hand, PoseStack pose, SubmitNodeCollector collector,
			int light, float equip) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !SlingshotDrawMath.showsPouchArm(player.isUsingItem(), isDrawingSlingshot(player),
				player.getUseItemRemainingTicks(), hand == player.getUsedItemHand())) {
			return false;
		}
		if (!player.isInvisible()
				&& minecraft.getEntityRenderDispatcher().getRenderer(player) instanceof AvatarRenderer<?> renderer) {
			HumanoidArm mainArm = player.getMainArm();
			HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
			submitPouchArm(pose, collector, light, equip, arm, player, renderer);
		}
		return true;
	}

	/**
	 * The new field-of-view modifier for a loader event that offers the raw modifier, the effect scale and the value
	 * other listeners have set so far: unchanged unless a slingshot is being drawn, in which case the bow's zoom is
	 * composed into {@code current} ({@link BowDrawMath#composedFovModifier}) — on a game with no other FOV mod the
	 * same value Fabric gets from {@link #fovModifierBeforeLerp}, and another mod's change is kept, not overwritten.
	 */
	public static float newFovModifier(Player player, float modifier, float effectScale, float current) {
		if (!isDrawingSlingshot(player)) {
			return current;
		}
		return BowDrawMath.composedFovModifier(current, modifier, effectScale, zoomFactor(player));
	}

	/**
	 * The field-of-view modifier before vanilla interpolates it by the effect scale: narrowed as a bow narrows it
	 * while a slingshot is being drawn, otherwise unchanged.
	 */
	public static float fovModifierBeforeLerp(Player player, float modifier) {
		return isDrawingSlingshot(player) ? modifier * zoomFactor(player) : modifier;
	}

	/** The bow's zoom over the bow's 20-tick draw (decision 2 of MOD-961). */
	private static float zoomFactor(Player player) {
		return BowDrawMath.zoomFactor(player.getTicksUsingItem(), BowDrawMath.BOW_DRAW_TICKS);
	}

	private static boolean isDrawingSlingshot(Player player) {
		return player.isUsingItem() && player.getUseItem().getItem() instanceof SlingshotItem;
	}

	/**
	 * The bare arm, its fist on the pouch: vanilla's own empty-hand arm (the chain of {@code renderPlayerArm} with no
	 * swing), moved so its fist sits at the origin, turned about the fist, and set on the pouch of the current pull
	 * frame.
	 */
	private static void submitPouchArm(PoseStack pose, SubmitNodeCollector collector, int light, float equip,
			HumanoidArm arm, LocalPlayer player, AvatarRenderer<?> renderer) {
		float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		int ticks = player.getTicksUsingItem();
		pose.pushPose();
		pose.translate(0.0f, POUCH_Y - SlingshotDrawMath.pouchDrop(ticks) - equip * EQUIP_DROP,
				POUCH_Z + SlingshotDrawMath.pouchBack(ticks));
		Poses.rotate(pose, Axis.YP.rotationDegrees(side * ARM_YAW));
		Poses.rotate(pose, Axis.XP.rotationDegrees(ARM_PITCH));
		// Vanilla's first step (side * 0.64, -0.6, -0.72) less where its chain puts the fist (side * 0.694, -0.538,
		// -1.036), so the fist lands on the origin.
		pose.translate(side * -0.0538f, -0.0622f, 0.3162f);
		Poses.rotate(pose, Axis.YP.rotationDegrees(side * 45.0f));
		pose.translate(side * -1.0f, 3.6f, 3.5f);
		Poses.rotate(pose, Axis.ZP.rotationDegrees(side * 120.0f));
		Poses.rotate(pose, Axis.XP.rotationDegrees(200.0f));
		Poses.rotate(pose, Axis.YP.rotationDegrees(side * -135.0f));
		pose.translate(side * 5.6f, 0.0f, 0.0f);
		Identifier skin = player.getSkin().body().texturePath();
		if (arm == HumanoidArm.RIGHT) {
			boolean sleeve = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
			renderer.renderRightHand(pose, collector, light, skin, sleeve);
		} else {
			boolean sleeve = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
			renderer.renderLeftHand(pose, collector, light, skin, sleeve);
		}
		pose.popPose();
	}
}
