package dev.alaindustrial.client.forge;

import dev.alaindustrial.client.render.RenderBoxProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The culling box of a block entity whose renderer draws beyond its block (MOD-633, MOD-962).
 *
 * <p>Forge culls every block entity by {@code IForgeBlockEntity.getRenderBoundingBox()} before it reads
 * {@code shouldRenderOffScreen}, and that box is the block's collision shape, so a wind-mill rotor vanishes as soon
 * as its block leaves the view. The box lives on the renderer in this mod ({@link RenderBoxProvider}, the same
 * method NeoForge calls), so the client mixin {@code LevelExtractorMixin} wraps Forge's call and asks the renderer
 * first.
 */
public final class ForgeRenderBoxes {

	private ForgeRenderBoxes() {
	}

	/** The renderer's own box for {@code blockEntity}, or {@code null} when its renderer declares none. */
	@SuppressWarnings("unchecked")
	public static @Nullable AABB of(BlockEntity blockEntity) {
		BlockEntityRenderer<BlockEntity, ?> renderer =
				Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity);
		if (renderer instanceof RenderBoxProvider<?> provider) {
			return ((RenderBoxProvider<BlockEntity>) provider).getRenderBoundingBox(blockEntity);
		}
		return null;
	}
}
