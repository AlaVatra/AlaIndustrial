package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.block.TodoClipboardBlock;
import dev.alaindustrial.block.entity.TodoClipboardBlockEntity;
import dev.alaindustrial.client.TodoInventoryTally;
import dev.alaindustrial.compat.client.Poses;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Writes a to-do clipboard's list on its sheet on a wall, floor or ceiling (MOD-905). The page itself — header,
 * rows, icons, the "have/need" counters — is {@link TodoSheet}'s, shared with the clipboard in the hand; this
 * renderer only turns the block model's frame onto the clipboard's face.
 *
 * <p><b>Frame.</b> The block model lies on the floor with its clip at +Z and the blockstate turns it onto its
 * face; {@link TodoClipboardBlock#modelRotation} is that rotation, applied here the way a blockstate applies it
 * (X first, then Y, both clockwise, so negated for the right-handed {@link Axis}).
 *
 * <p>Only rows inside the clipboard's own cell are drawn, so no loader's culling box needs widening. The text is
 * left out past {@link #MAX_TEXT_DISTANCE}: at that range it is a grey smudge anyway. The counters read the
 * client's one inventory tally ({@link TodoInventoryTally}), which is rebuilt when the inventory changes, not per
 * sheet per frame.
 */
public class TodoClipboardRenderer
		implements BlockEntityRenderer<TodoClipboardBlockEntity, TodoClipboardRenderer.State> {

	private static final double MAX_TEXT_DISTANCE = 16.0;

	private final ItemModelResolver itemModelResolver;
	private final Font font;

	public TodoClipboardRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
		this.font = context.font();
	}

	/** Per-frame snapshot of what the sheet shows. */
	public static final class State extends BlockEntityRenderState {
		int rotX;
		int rotY;
		final TodoSheet.Page page = new TodoSheet.Page();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TodoClipboardBlockEntity clipboard, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(clipboard, state, partialTicks, cameraPosition, breakProgress);
		int[] rotation = TodoClipboardBlock.modelRotation(clipboard.getBlockState());
		state.rotX = rotation[0];
		state.rotY = rotation[1];
		if (cameraPosition.distanceToSqr(Vec3.atCenterOf(clipboard.getBlockPos()))
				>= MAX_TEXT_DISTANCE * MAX_TEXT_DISTANCE) {
			state.page.clear();
			return;
		}
		TodoSheet.extract(state.page, font, clipboard.getBlockState().getBlock().getName(), clipboard.list(),
				TodoInventoryTally.current(), itemModelResolver, clipboard.getLevel(),
				(int) clipboard.getBlockPos().asLong());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		// The blockstate's turn, about the cell's centre: X first, then Y (so Y is pushed first).
		poseStack.translate(0.5F, 0.5F, 0.5F);
		Poses.rotate(poseStack, Axis.YP.rotationDegrees(-state.rotY));
		Poses.rotate(poseStack, Axis.XP.rotationDegrees(-state.rotX));
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		TodoSheet.submit(state.page, poseStack, collector, state.lightCoords);
		poseStack.popPose();
	}
}
