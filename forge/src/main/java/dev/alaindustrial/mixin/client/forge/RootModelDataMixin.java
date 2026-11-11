package dev.alaindustrial.mixin.client.forge;

import dev.alaindustrial.block.entity.KokSagyzRootBlockEntity;
import dev.alaindustrial.client.forge.ForgeRootSoilModels;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.extensions.IForgeBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands a kok-sagyz root's soil to the chunk mesher as model data (MOD-962; the Forge twin of the NeoForge
 * {@code RootModelDataMixin}): {@link ForgeRootSoilModels} draws the root as that soil. A new soil, set by the
 * block or read from a sync packet, asks Forge to refresh the data so the section is meshed again.
 */
@Mixin(KokSagyzRootBlockEntity.class)
public abstract class RootModelDataMixin implements IForgeBlockEntity {

	@Override
	public ModelData getModelData() {
		return ModelData.builder()
				.with(ForgeRootSoilModels.SOIL, ((KokSagyzRootBlockEntity) (Object) this).soil())
				.build();
	}

	@Inject(method = {"setSoil", "loadAdditional"}, at = @At("RETURN"))
	private void alaindustrial$refreshSoil(CallbackInfo ci) {
		KokSagyzRootBlockEntity root = (KokSagyzRootBlockEntity) (Object) this;
		if (root.getLevel() != null && root.getLevel().isClientSide()) {
			requestModelDataUpdate();
		}
	}
}
