package dev.alaindustrial.mixin.client.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.client.ElectricBowFirstPerson;
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
 * The other hand hidden while an Electric Bow is drawn (MOD-363, MOD-961) on Fabric, as vanilla hides it for a drawn
 * {@code Items.BOW}. It sits at the head of {@code submitArmWithItem}, where NeoForge and Forge fire their
 * {@code RenderHandEvent}, and skips vanilla's drawing of the hand when
 * {@link ElectricBowFirstPerson#skipsHand} says so, as a cancelled hand event skips it there.
 *
 * <p><b>26.3 names the class {@link FirstPersonHandsAndItemsRenderer}</b>; 26.2's twin targets
 * {@code ItemInHandRenderer}.
 *
 * <p>Cosmetic by nature, so it lives in the Fabric optional config: a conflict costs the hidden hand, never the game.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererElectricBowHandMixin {

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void alaindustrial$electricBowHidesOtherHand(PlayerRenderState playerState,
			FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot, InteractionHand hand,
			float attack, ItemStack stack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector,
			int light, CallbackInfo ci) {
		if (ElectricBowFirstPerson.skipsHand(hand)) {
			ci.cancel();
		}
	}
}
