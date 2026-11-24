package dev.alaindustrial.block.entity.elevator;

import dev.alaindustrial.block.entity.ElevatorWinchBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Puts back on the cabin what a chunk unload or a logout left hanging in the shaft (MOD-769).
 *
 * <p>A trip is computed from the clock, so a cabin whose chunk unloaded mid-trip is at its destination the moment the
 * chunk loads again; its riders are not: they were saved where they were, in the air of the shaft. The same goes for
 * a player who left the game in a moving cabin and comes back later. So when a winch loads, and when a player joins,
 * anything airborne in the cabin's column between the lowest floor and the winch is set down on the cabin's floor.
 * Something standing on solid ground (the pit under the lowest floor) is left where it is.
 */
public final class ElevatorRescue {

	private ElevatorRescue() {
	}

	/** Set down on the cabin everything left hanging in {@code winch}'s shaft. */
	public static void rescueAll(ServerLevel level, ElevatorWinchBlockEntity winch) {
		AABB column = column(winch);
		if (column == null) {
			return;
		}
		for (Entity entity : level.getEntitiesOfClass(Entity.class, column, entity -> !entity.isSpectator())) {
			rescue(winch, entity);
		}
	}

	/** A player joined: set them down on the cabin of any winch whose shaft they hang in. */
	public static void onPlayerJoin(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		int chunkX = player.blockPosition().getX() >> 4;
		int chunkZ = player.blockPosition().getZ() >> 4;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (blockEntity instanceof ElevatorWinchBlockEntity winch) {
						AABB column = column(winch);
						if (column != null && column.contains(player.position())) {
							rescue(winch, player);
						}
					}
				}
			}
		}
	}

	/** The online player with this id, or {@code null}. */
	public static @Nullable ServerPlayer online(ServerLevel level, @Nullable UUID id) {
		return id == null ? null : level.getServer().getPlayerList().getPlayer(id);
	}

	/** The cabin's column from its lowest floor up to the winch; {@code null} before the winch knows its floors. */
	static @Nullable AABB column(ElevatorWinchBlockEntity winch) {
		if (winch.floors().isEmpty()) {
			return null;
		}
		BlockPos cell = winch.cabinCell(winch.floors().getFirst());
		return new AABB(cell.getX(), winch.floors().getFirst(), cell.getZ(), cell.getX() + 1.0D,
				winch.getBlockPos().getY(), cell.getZ() + 1.0D);
	}

	private static void rescue(ElevatorWinchBlockEntity winch, Entity entity) {
		if (winch.trip() != null || entity.onGround() || !ElevatorCarry.inColumn(entity, winch.cabinCell(0))) {
			return;
		}
		double floor = winch.cabinHeight(0.0D);
		if (Double.isNaN(floor) || ElevatorCarry.onFloor(entity, winch.cabinCell(floor), floor)) {
			return;
		}
		double top = floor + ElevatorCarry.FLOOR_TOP;
		if (entity instanceof ServerPlayer player && player.connection != null) {
			player.teleportTo(player.getX(), top, player.getZ());
		} else {
			entity.setPos(entity.getX(), top, entity.getZ());
		}
		entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
		entity.resetFallDistance();
	}
}
