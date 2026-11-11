package dev.alaindustrial.mixin.client.forge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.alaindustrial.client.forge.ForgeBlockOutlines;
import dev.alaindustrial.client.forge.ForgeRenderBoxes;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two Forge client hooks in the level extraction (MOD-962), read from the Forge 66.1.0 patched sources.
 *
 * <ul>
 *   <li><b>Block outlines.</b> After vanilla (or Forge's {@code RenderHighlightEvent}) has extracted the hovered
 *       block's outline, {@link ForgeBlockOutlines} swaps its shape for a shared one: the semantics of Fabric's
 *       {@code AFTER_BLOCK_OUTLINE_EXTRACTION}, with vanilla's own styling. {@code RETURN} covers Forge's early
 *       return for a custom renderer too, which the replacement then leaves alone.</li>
 *   <li><b>Culling boxes.</b> Forge's patch culls each block entity by {@code getRenderBoundingBox()} before it
 *       reads {@code shouldRenderOffScreen}, in two places (section block entities and the global list). Both
 *       calls are wrapped so a renderer that draws beyond its block answers with its own box
 *       ({@link ForgeRenderBoxes}); any other block entity keeps Forge's answer. A {@code @WrapOperation}, so
 *       another mod's wrapper chains; {@code require = 2} fails loudly if Forge moves either call.</li>
 * </ul>
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {

	@Inject(method = "extractBlockOutline", at = @At("RETURN"))
	private void alaindustrial$blockOutline(Camera camera, LevelRenderState levelState, CallbackInfo ci) {
		ForgeBlockOutlines.replace(levelState);
	}

	@WrapOperation(method = "extractVisibleBlockEntities", require = 2, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/entity/BlockEntity;getRenderBoundingBox()"
					+ "Lnet/minecraft/world/phys/AABB;"))
	private AABB alaindustrial$renderBox(BlockEntity blockEntity, Operation<AABB> original) {
		AABB own = ForgeRenderBoxes.of(blockEntity);
		return own != null ? own : original.call(blockEntity);
	}
}
