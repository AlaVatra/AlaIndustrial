package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.core.world.PlaceEvents;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * NeoForge's side of {@link PlaceEvents} (MOD-871). The same order as {@code CommonHooks.onPlaceItemIntoWorld}
 * uses for a player's click: snapshot the cell, place, post {@code BlockEvent.EntityPlaceEvent} through
 * {@code EventHooks.onBlockPlace}, and restore the snapshot when a listener cancels it.
 */
public final class NeoForgePlaceEvents implements PlaceEvents {

	@Override
	public boolean place(ServerLevel level, BlockPos pos, Direction face, Entity placer, BooleanSupplier placement) {
		BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos);
		if (!placement.getAsBoolean()) {
			return false;
		}
		if (EventHooks.onBlockPlace(placer, before, face)) {
			level.restoringBlockSnapshots = true;
			try {
				before.restore(before.getFlags() | Block.UPDATE_CLIENTS);
			} finally {
				level.restoringBlockSnapshots = false;
			}
			return false;
		}
		return true;
	}
}
