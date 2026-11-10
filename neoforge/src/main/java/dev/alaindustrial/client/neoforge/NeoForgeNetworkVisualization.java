package dev.alaindustrial.client.neoforge;

import dev.alaindustrial.client.render.NetworkOverlayRenderer;
import dev.alaindustrial.client.ClientContentManifest;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * NeoForge adapter for the loader-neutral Network Analyzer highlight (MOD-016, MOD-033): forwards the
 * render-time collector from {@link SubmitCustomGeometryEvent} to the common
 * {@link NetworkOverlayRenderer}, giving NeoForge the exact same per-frame tube/joint/flow-spark
 * geometry as Fabric.
 *
 * <p>History: the first NeoForge port (MOD-022) approximated the overlay with vanilla per-tick
 * gizmos ({@code Minecraft#collectPerTickGizmos()} + {@code Gizmos.line/cuboid/point}) because
 * {@code RenderLevelStageEvent} exposes no {@code SubmitNodeCollector}. NeoForge 26.2 does expose
 * one through {@link SubmitCustomGeometryEvent}, which fires in {@code LevelRenderer.submitFeatures}
 * right before vanilla submits its own gizmo primitives — the same frame point as the Fabric
 * {@code LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES} hook — so the per-tick approximation (20 Hz
 * motion, pixel-sized flow points) is gone entirely (MOD-033, MOD-060).
 *
 * <p>The payload reaches the renderer the same way as on Fabric: its entry in the shared
 * {@link dev.alaindustrial.client.ClientPayloadManifest} pushes it into
 * {@link NetworkOverlayRenderer#updatePayload}. (It used to be polled from a field here every frame —
 * a field nothing cleared, MOD-665 D1.)
 */
public final class NeoForgeNetworkVisualization {

	private NeoForgeNetworkVisualization() {
	}

	/** Game-bus listener: submit this frame's overlay geometry. Registered in
	 * {@code IndustrializationNeoForgeClient}. */
	public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
		for (ClientContentManifest.WorldOverlay overlay : ClientContentManifest.WORLD_OVERLAYS) {
			overlay.submitFrame(event.getSubmitNodeCollector(),
					event.getLevelRenderState().cameraRenderState);
		}
	}
}
