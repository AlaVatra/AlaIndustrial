package dev.alaindustrial.mixin.client.forge;

import dev.alaindustrial.client.forge.ForgeWorldOverlays;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame hook of the shared world overlays on Forge (MOD-962): Forge fires no event with the render-time
 * {@link SubmitNodeCollector}, so the overlays are submitted from {@code LevelRenderer.submitFeatures} right before
 * vanilla finalises its gizmos. That is the point Fabric's {@code BEFORE_GIZMOS} and NeoForge's
 * {@code SubmitCustomGeometryEvent} use, so an overlay that emits gizmos lands in this frame's batch and one that
 * submits its own primitives draws next to vanilla's. Read from the Forge 66.1.0 patched sources.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

	@Inject(method = "submitFeatures", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizeGizmoCollection()V"))
	private void alaindustrial$worldOverlays(LevelRenderState levelRenderState, SubmitNodeCollector collector,
			boolean renderOutline, CallbackInfo ci) {
		ForgeWorldOverlays.submit(collector, levelRenderState.cameraRenderState);
	}
}
