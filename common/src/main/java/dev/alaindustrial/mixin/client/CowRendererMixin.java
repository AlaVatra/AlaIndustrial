package dev.alaindustrial.mixin.client;

import dev.alaindustrial.client.render.entity.layers.CowShieldingSuitLayer;
import dev.alaindustrial.client.render.entity.layers.ShieldingSuitRenderState;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.CowRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Teaches {@code CowRenderer} the shielding suit (MOD-569), as {@code VillagerRendererMixin} does for the
 * villager: registers {@link CowShieldingSuitLayer} and copies the worn suit pieces into the render state on
 * extraction. Shared client mixin config, so every loader applies it with no per-loader registration. The
 * mooshroom has its own renderer and is not touched: it is past the end of {@code RadiationMobs.isConvertible}.
 * Only pieces tagged {@code #alaindustrial:radiation_shielding} are copied — the layer has sheets for those alone.
 */
@Mixin(CowRenderer.class)
public abstract class CowRendererMixin {

	@Inject(method = "<init>", at = @At("TAIL"))
	private void alaindustrial$addSuitLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
		((LivingEntityRendererInvoker) this).alaindustrial$invokeAddLayer(
				new CowShieldingSuitLayer((CowRenderer) (Object) this));
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/cow/Cow;"
			+ "Lnet/minecraft/client/renderer/entity/state/CowRenderState;F)V", at = @At("TAIL"))
	private void alaindustrial$copySuit(Cow cow, CowRenderState state, float tick, CallbackInfo ci) {
		if (!(state instanceof ShieldingSuitRenderState suit)) {
			return;
		}
		suit.alaindustrial$setSuit(shielding(cow, EquipmentSlot.HEAD), shielding(cow, EquipmentSlot.CHEST),
				shielding(cow, EquipmentSlot.LEGS), shielding(cow, EquipmentSlot.FEET));
	}

	private static ItemStack shielding(Cow cow, EquipmentSlot slot) {
		ItemStack stack = cow.getItemBySlot(slot);
		return stack.is(ModTags.Items.RADIATION_SHIELDING) ? stack : ItemStack.EMPTY;
	}
}
