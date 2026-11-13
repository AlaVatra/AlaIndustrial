package dev.alaindustrial.client;

import dev.alaindustrial.item.tool.ElectricBowItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * The Electric Bow's first-person draw (MOD-363, made to work by MOD-961): the bow's zoom over the Electric Bow's
 * own draw time, and the other hand hidden while it is drawn, as for a vanilla bow.
 *
 * <p>Vanilla gives both to {@code Items.BOW} only, by an {@code ItemStack.is(Object)} identity test, so a subclass of
 * {@code BowItem} gets neither. The entry points and their wiring are the slingshot's ({@link SlingshotFirstPerson}):
 * on Fabric two optional client mixins (the head of the first-person {@code submitArmWithItem}, and the modifier
 * handed to the last {@code Mth.lerp} of {@code AbstractClientPlayer.getFieldOfViewModifier}), on NeoForge and Forge
 * their {@code RenderHandEvent} and {@code ComputeFovModifierEvent}, fired from the same two places.
 *
 * <p>The pull of the drawing hand itself is vanilla's {@code BOW} arm pose, timed to this bow's draw by the shared
 * optional mixin {@code FirstPersonHandsAndItemsRendererElectricBowMixin}.
 */
public final class ElectricBowFirstPerson {

	private ElectricBowFirstPerson() {
	}

	/**
	 * Called for each first-person hand just before vanilla draws it: true while the local player draws an Electric
	 * Bow and this is the other hand, so vanilla skips drawing it (and the item in it), as it does for a drawn bow.
	 */
	public static boolean skipsHand(InteractionHand hand) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && BowDrawMath.hidesOtherHand(player.isUsingItem(), isDrawingElectricBow(player),
				hand == player.getUsedItemHand());
	}

	/**
	 * The new field-of-view modifier for a loader event that offers the raw modifier, the effect scale and the value
	 * other listeners have set so far: unchanged unless an Electric Bow is being drawn, in which case its zoom is
	 * composed into {@code current} ({@link BowDrawMath#composedFovModifier}).
	 */
	public static float newFovModifier(Player player, float modifier, float effectScale, float current) {
		if (!isDrawingElectricBow(player)) {
			return current;
		}
		return BowDrawMath.composedFovModifier(current, modifier, effectScale, zoomFactor(player));
	}

	/**
	 * The field-of-view modifier before vanilla interpolates it by the effect scale: narrowed as a bow narrows it
	 * while an Electric Bow is being drawn, otherwise unchanged.
	 */
	public static float fovModifierBeforeLerp(Player player, float modifier) {
		return isDrawingElectricBow(player) ? modifier * zoomFactor(player) : modifier;
	}

	/** The bow's zoom, complete when this bow's draw is: 16 ticks while charged, 20 when flat. */
	private static float zoomFactor(Player player) {
		return BowDrawMath.zoomFactor(player.getTicksUsingItem(), ElectricBowItem.drawTicks(player.getUseItem()));
	}

	private static boolean isDrawingElectricBow(Player player) {
		return player.isUsingItem() && player.getUseItem().getItem() instanceof ElectricBowItem;
	}
}
