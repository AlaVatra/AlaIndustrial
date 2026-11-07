package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.world.PlaceEvents;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Fabric's side of {@link PlaceEvents} (MOD-871): Fabric API has no block-place event, so the placement just
 * runs; the common owner checks already guarded it.
 */
public final class FabricPlaceEvents implements PlaceEvents {

	@Override
	public boolean place(ServerLevel level, BlockPos pos, Direction face, Entity placer, BooleanSupplier placement) {
		return placement.getAsBoolean();
	}
}
