package dev.alaindustrial.mixin.client.fabric;

import dev.alaindustrial.client.ElectricBowFirstPerson;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The Electric Bow's zoom while drawing (MOD-363, MOD-961) on Fabric. Vanilla narrows the view only for
 * {@code Items.BOW}, by an {@code ItemStack.is(Object)} identity test; here the modifier handed to the method's one
 * {@code Mth.lerp(effectScale, 1, modifier)} is narrowed the same way, over the Electric Bow's own draw time.
 * NeoForge and Forge reach the same entry point from their {@code ComputeFovModifierEvent}, so this mixin is
 * Fabric-only.
 *
 * <p>Cosmetic by nature, so it lives in the Fabric optional config: a conflict costs the zoom, never the game.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerElectricBowZoomMixin {

	@ModifyArg(method = "getFieldOfViewModifier",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"), index = 2)
	private float alaindustrial$electricBowZoom(float modifier) {
		return ElectricBowFirstPerson.fovModifierBeforeLerp((Player) (Object) this, modifier);
	}
}
