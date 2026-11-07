package dev.alaindustrial.core.world;

import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * A loader's block-place event around a placement that no player click made (MOD-871).
 *
 * <p>A torch fired from the slingshot is placed by the projectile through {@code BlockItem.place}, not through
 * {@code ItemStack.useOn}, so NeoForge's own place event (fired from {@code CommonHooks.onPlaceItemIntoWorld}) does
 * not run, and a claim mod would never be asked. NeoForge's implementation runs the placement, then posts
 * {@code BlockEvent.EntityPlaceEvent} for the cell with the shooter as the placer and restores the cell when a
 * listener cancels. Fabric API has no block-place event, so its implementation only runs the placement; the
 * common checks ({@code Player.mayUseItemAt}, {@code Level.mayInteract}, {@code Projectile.mayInteract}) cover it.
 * Same seam shape as {@link FakePlayers}.
 */
public interface PlaceEvents {

	/**
	 * Runs {@code placement} at {@code pos} on behalf of {@code placer} and asks the loader's listeners about it.
	 *
	 * @param face the face of the block the new one was placed against (the hit face)
	 * @param placement places the block; returns false when nothing was placed
	 * @return true when the block was placed and no listener refused it; false leaves the cell as it was
	 */
	boolean place(ServerLevel level, BlockPos pos, Direction face, Entity placer, BooleanSupplier placement);

	PlaceEvents[] INSTANCE = new PlaceEvents[1];

	static void install(PlaceEvents impl) {
		INSTANCE[0] = impl;
	}

	/** The installed seam; throws before a loader installed one, rather than place past a claim mod. */
	static PlaceEvents get() {
		PlaceEvents impl = INSTANCE[0];
		if (impl == null) {
			throw new IllegalStateException("PlaceEvents seam not installed by the loader");
		}
		return impl;
	}
}
