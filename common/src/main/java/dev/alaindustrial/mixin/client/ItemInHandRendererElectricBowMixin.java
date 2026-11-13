package dev.alaindustrial.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alaindustrial.item.tool.ElectricBowItem;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * How far the drawing hand has pulled back (MOD-363): vanilla assumes a fixed 20-tick draw inside
 * {@code submitArmWithItem}'s {@code BOW} branch; the Electric Bow draws over its own time, so the hand
 * reaches full pull on the same tick the shot does.
 *
 * <p>All three loaders keep that single {@code 20.0F} verbatim (their patches of the method do not touch
 * the {@code BOW} branch), so one shared mixin serves them all. The edit touches the constant only for an
 * Electric Bow stack, which renders through the {@code BOW} branch alone.
 *
 * <p>The other half of MOD-363 — the zoom and the other hand hidden — is {@code client.ElectricBowFirstPerson}
 * since MOD-961: the mixins that tried it by wrapping {@code ItemStack.is(Item)} never matched, because vanilla
 * calls the inherited {@code is(Object)}.
 *
 * <p><b>26.2 names the class {@link ItemInHandRenderer}</b>; 26.3's twin targets
 * {@code FirstPersonHandsAndItemsRenderer}.
 *
 * <p>Cosmetic by nature, so it lives in the optional config: a conflict costs the pose, never the game.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererElectricBowMixin {

	@ModifyExpressionValue(method = "submitArmWithItem", at = @At(value = "CONSTANT", args = "floatValue=20.0"))
	private float alaindustrial$electricBowDrawTicks(float vanillaDrawTicks, @Local(argsOnly = true) ItemStack stack) {
		return stack.getItem() instanceof ElectricBowItem ? ElectricBowItem.drawTicks(stack) : vanillaDrawTicks;
	}
}
