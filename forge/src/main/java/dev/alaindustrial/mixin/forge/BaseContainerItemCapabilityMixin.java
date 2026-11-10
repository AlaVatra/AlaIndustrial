package dev.alaindustrial.mixin.forge;

import dev.alaindustrial.core.forge.BlockCapabilitiesForge;
import dev.alaindustrial.core.forge.BlockCapabilityProviderForge;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets our provider answer the item capability of our block entities that extend {@link BaseContainerBlockEntity}
 * (MOD-962): the chests, the iron furnace, the advanced workbench, the storage module and the warehouse shelf.
 *
 * <p>Forge's patch of {@code BaseContainerBlockEntity.getCapability} answers {@code ITEM_HANDLER} with an unsided
 * {@code InvWrapper(this)} before it ever asks the attached providers, so other mods' pipes and Forge's own hopper
 * (which goes through the capability) would see one chest half and ignore the faces of a furnace. For a block
 * entity of ours this returns {@link BlockCapabilityProviderForge#item} instead: sided, and the joined pair for a
 * double chest. Vanilla and other mods' containers are left alone, and a removed block entity answers nothing,
 * as the patch does. {@code require = 1}: if the patched method disappears, the game stops loading.
 */
@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerItemCapabilityMixin {

	@Inject(method = "getCapability", at = @At("HEAD"), cancellable = true, require = 1)
	private void alaindustrial$ourItemHandler(Capability<?> cap, @Nullable Direction side,
			CallbackInfoReturnable<LazyOptional<?>> cir) {
		if (cap != ForgeCapabilities.ITEM_HANDLER) {
			return;
		}
		BlockEntity self = (BlockEntity) (Object) this;
		if (!BlockCapabilitiesForge.isOurItemContainer(self.getType())) {
			return;
		}
		cir.setReturnValue(self.isRemoved() ? LazyOptional.empty()
				: self.getCapability(BlockCapabilityProviderForge.SELF).resolve()
						.<LazyOptional<?>>map(provider -> provider.item(side)).orElse(LazyOptional.empty()));
	}
}
