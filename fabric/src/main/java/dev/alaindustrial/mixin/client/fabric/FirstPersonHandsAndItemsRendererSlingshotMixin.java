package dev.alaindustrial.mixin.client.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.client.SlingshotFirstPerson;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The pouch hand while a slingshot is drawn (MOD-961) on Fabric. It sits at the head of {@code submitArmWithItem},
 * the place where NeoForge and Forge fire their {@code RenderHandEvent} with the same arguments, and hands the call
 * to {@link SlingshotFirstPerson#submitInsteadOfHand}; when that draws the pouch arm, vanilla's drawing of the hand
 * is skipped, as a cancelled hand event skips it there.
 *
 * <p><b>26.3 names the class {@link FirstPersonHandsAndItemsRenderer}</b>; 26.2's twin targets
 * {@code ItemInHandRenderer}.
 *
 * <p>Cosmetic by nature, so it lives in the Fabric optional config: a conflict costs the pouch hand, never the game.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererSlingshotMixin {

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void alaindustrial$slingshotPouchHand(PlayerRenderState playerState,
			FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot, InteractionHand hand,
			float attack, ItemStack stack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector,
			int light, CallbackInfo ci) {
		if (SlingshotFirstPerson.submitInsteadOfHand(hand, poseStack, collector, light, inverseArmHeight)) {
			ci.cancel();
		}
	}
}
