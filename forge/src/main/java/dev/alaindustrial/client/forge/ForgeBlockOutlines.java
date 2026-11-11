package dev.alaindustrial.client.forge;

import dev.alaindustrial.client.ClientContentManifest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Replays the shared {@link ClientContentManifest#BLOCK_OUTLINES} (MOD-778, MOD-962): the first entry that answers
 * for the hovered block becomes the drawn outline. Called by the client mixin {@code LevelExtractorMixin} after
 * vanilla has extracted the outline, so only the shape changes; translucency, contrast and styling stay
 * vanilla's, as with Fabric's {@code AFTER_BLOCK_OUTLINE_EXTRACTION}. An outline another mod took over through
 * {@code RenderHighlightEvent} (a custom renderer) is left alone.
 */
public final class ForgeBlockOutlines {

	private ForgeBlockOutlines() {
	}

	/** Swaps the extracted outline's shape for the first shared outline that answers, if any. */
	public static void replace(LevelRenderState levelState) {
		BlockOutlineRenderState outline = levelState.blockOutlineRenderState;
		ClientLevel level = Minecraft.getInstance().level;
		if (outline == null || outline.customRenderer() != null || level == null) {
			return;
		}
		BlockState state = level.getBlockState(outline.pos());
		for (ClientContentManifest.BlockOutline def : ClientContentManifest.BLOCK_OUTLINES) {
			VoxelShape shape = def.shape(level, outline.pos(), state);
			if (shape != null) {
				levelState.blockOutlineRenderState = new BlockOutlineRenderState(outline.pos(),
						outline.isTranslucent(), outline.highContrast(), shape, outline.collisionShape(),
						outline.occlusionShape(), outline.interactionShape());
				return;
			}
		}
	}
}
