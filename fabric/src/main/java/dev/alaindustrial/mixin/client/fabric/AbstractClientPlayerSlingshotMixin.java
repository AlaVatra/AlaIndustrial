package dev.alaindustrial.mixin.client.fabric;

import dev.alaindustrial.client.SlingshotFirstPerson;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The slingshot's zoom while drawing (MOD-961) on Fabric. Vanilla narrows the view only for {@code Items.BOW}; here
 * the modifier handed to the method's one {@code Mth.lerp(effectScale, 1, modifier)} is narrowed the same way while a
 * slingshot is drawn, which is where vanilla narrows it for a bow. NeoForge and Forge reach the same entry point from
 * their {@code ComputeFovModifierEvent}, fired with that same pre-lerp modifier, so this mixin is Fabric-only.
 *
 * <p>Cosmetic by nature, so it lives in the Fabric optional config: a conflict costs the zoom, never the game.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerSlingshotMixin {

	@ModifyArg(method = "getFieldOfViewModifier",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"), index = 2)
	private float alaindustrial$slingshotZoom(float modifier) {
		return SlingshotFirstPerson.fovModifierBeforeLerp((Player) (Object) this, modifier);
	}
}
