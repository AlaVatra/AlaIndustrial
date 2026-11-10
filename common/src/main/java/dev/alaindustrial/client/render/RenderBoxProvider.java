package dev.alaindustrial.client.render;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * A block entity renderer that draws beyond its own block and says where (MOD-633, MOD-962).
 *
 * <p>Vanilla, and so Fabric, never culls a block entity against the view frustum. Both Forge loaders do, and
 * both do it BEFORE they read {@code shouldRenderOffScreen}, so whatever a renderer draws outside its block
 * vanishes the moment that block leaves the view unless the culling box covers it. NeoForge asks the renderer
 * ({@code IBlockEntityRendererExtension.getRenderBoundingBox(T)}, a default method a renderer's own
 * {@link #getRenderBoundingBox} overrides by name); MinecraftForge asks the block entity
 * ({@code IForgeBlockEntity.getRenderBoundingBox()}), so its client wraps that call and asks the renderer through
 * this interface instead. One method on the renderer serves both: its erasure is the same
 * {@code getRenderBoundingBox(BlockEntity)} bridge NeoForge calls.
 *
 * @param <T> the renderer's block entity type
 */
public interface RenderBoxProvider<T extends BlockEntity> {

	/** The world-space box everything the renderer draws for {@code blockEntity} stays inside. */
	AABB getRenderBoundingBox(T blockEntity);
}
