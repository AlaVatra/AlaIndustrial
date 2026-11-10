package dev.alaindustrial.mixin.forge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.alaindustrial.registry.forge.ForgeFluidTypes;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives the mod's fluids their Forge {@link FluidType} without a subclass per fluid (MOD-962, the Forge twin of
 * the NeoForge {@code FluidTypeLookupMixin}).
 *
 * <p>Forge's patched {@code Fluid.getFluidType()} caches its answer in a field and, while the field is empty,
 * asks {@code ForgeHooks.getVanillaFluidType(this)}, which throws for a fluid it does not know. That one call is
 * wrapped: a fluid the mod registered gets the type {@code ForgeFluidTypes} recorded for it, any other fluid goes
 * on to the original call — Forge's own answer, or the next mod's wrapper. The wrap runs only until the field is
 * filled, so the lookup is paid once per fluid. Read from the Forge 66.1.0 sources (the {@code Fluid} patch and
 * {@code ForgeHooks.getVanillaFluidType(Fluid)}).
 *
 * <p>A MixinExtras {@code @WrapOperation}, not a {@code @Redirect}: wrappers from two mods chain. MixinExtras
 * ships with Forge itself ({@code mixinextras-forge} 0.5.4 in the userdev libraries). {@code require = 1} keeps
 * the failure loud: a target that disappears stops the game from loading instead of leaving the fluids typeless.
 */
@Mixin(Fluid.class)
public abstract class FluidTypeLookupMixin {

	@WrapOperation(method = "getFluidType", require = 1, at = @At(value = "INVOKE",
			target = "Lnet/minecraftforge/common/ForgeHooks;getVanillaFluidType"
					+ "(Lnet/minecraft/world/level/material/Fluid;)Lnet/minecraftforge/fluids/FluidType;"))
	private FluidType alaindustrial$modFluidType(Fluid fluid, Operation<FluidType> original) {
		FluidType own = ForgeFluidTypes.typeOf(fluid);
		return own != null ? own : original.call(fluid);
	}
}
