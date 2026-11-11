package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.world.PlaceEvents;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Forge implementation of the {@link PlaceEvents} seam (MOD-962, slice 2.5), in NeoForge's order: snapshot, place,
 * post {@code BlockEvent.EntityPlaceEvent}, restore the snapshot when a listener cancelled it.
 *
 * <p>{@code ForgeEventFactory.onBlockPlace} names the block against the opposite of {@code face} as the one placed
 * against, the clicked block. The restore notifies neighbours ({@code UPDATE_ALL}), as NeoForge's
 * {@code getFlags() | UPDATE_CLIENTS} does; Forge's own item placement restores without neighbour updates, which
 * is deliberately not copied.
 */
public final class ForgePlaceEvents implements PlaceEvents {

	@Override
	public boolean place(ServerLevel level, BlockPos pos, Direction face, Entity placer, BooleanSupplier placement) {
		BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos);
		if (!placement.getAsBoolean()) {
			return false;
		}
		if (ForgeEventFactory.onBlockPlace(placer, before, face)) {
			level.restoringBlockSnapshots = true;
			try {
				before.restore(true, true);
			} finally {
				level.restoringBlockSnapshots = false;
			}
			return false;
		}
		return true;
	}
}
