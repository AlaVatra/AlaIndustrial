package dev.alaindustrial.client.forge;

import dev.alaindustrial.client.ClientContentManifest;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Replays the shared {@link ClientContentManifest#WORLD_OVERLAYS} once a frame (MOD-962). Forge has no event with
 * the render-time collector, so the client mixin {@code LevelRendererMixin} calls this from
 * {@code LevelRenderer.submitFeatures}, right before vanilla finalises its gizmos: the frame point of Fabric's
 * {@code BEFORE_GIZMOS} and NeoForge's {@code SubmitCustomGeometryEvent}.
 */
public final class ForgeWorldOverlays {

	private ForgeWorldOverlays() {
	}

	/** Submits this frame's overlay geometry, in the list's drawing order. */
	public static void submit(SubmitNodeCollector collector, CameraRenderState camera) {
		for (ClientContentManifest.WorldOverlay overlay : ClientContentManifest.WORLD_OVERLAYS) {
			overlay.submitFrame(collector, camera);
		}
	}
}
