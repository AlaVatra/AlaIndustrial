package dev.alaindustrial.mixin.client.forge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alaindustrial.client.forge.ForgePiezoPlateModels;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Break and mining particles of a camouflaged piezo plate show the floor it copies, as on the other loaders
 * (MOD-764, MOD-962).
 *
 * <p>Forge's {@code TerrainParticle.updateSprite} asks the model for {@code particleMaterial(ModelData)} with the
 * data of the block at that position, and its {@code ModelDataManager} keeps data of block entities only. The
 * plate has none, so the model never learns which floor it lies on and answers with its own texture. Fabric and
 * NeoForge hand the model the level and the position instead. Here the one call is wrapped: for a plate on a
 * floor it camouflages, the floor's own particle material is returned ({@link ForgePiezoPlateModels}); every
 * other block goes on to the original call.
 */
@Mixin(TerrainParticle.class)
public abstract class TerrainParticleMixin {

	@WrapOperation(method = "updateSprite", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;particleMaterial"
					+ "(Lnet/minecraftforge/client/model/data/ModelData;)"
					+ "Lnet/minecraft/client/resources/model/sprite/Material$Baked;"))
	private Material.Baked alaindustrial$plateShowsItsFloor(BlockStateModel model, ModelData data,
			Operation<Material.Baked> original, @Local(argsOnly = true) BlockState state,
			@Local(argsOnly = true) BlockPos pos) {
		Material.Baked floor = ForgePiezoPlateModels.floorParticle(state, pos);
		return floor != null ? floor : original.call(model, data);
	}
}
