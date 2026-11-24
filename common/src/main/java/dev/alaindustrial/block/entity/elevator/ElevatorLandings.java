package dev.alaindustrial.block.entity.elevator;

import dev.alaindustrial.block.ElevatorStationBlock;
import dev.alaindustrial.block.entity.ElevatorWinchBlockEntity;
import dev.alaindustrial.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The stations of a shaft follow the cabin (MOD-769): the doors of the floor the cabin stands at open and its lamp
 * turns green, every other station is shut with a red lamp. While the cabin runs, every station is shut.
 *
 * <p>Written as a reconciliation, not as events: the winch calls it whenever the cabin starts, stops or arrives and on
 * every shaft reading, and it only writes the blocks that disagree. So a station placed by hand next to a standing
 * cabin catches up within one reading.
 */
public final class ElevatorLandings {

	private ElevatorLandings() {
	}

	/** Bring every station of {@code winch}'s shaft in line with where its cabin is. */
	public static void sync(ServerLevel level, ElevatorWinchBlockEntity winch) {
		ElevatorShaft shaft = winch.shaft();
		for (int floor : shaft.floors()) {
			boolean here = winch.standsAt(floor);
			boolean moved = false;
			for (int up = 0; up < 2; up++) {
				moved |= setOpen(level, shaft.cabinCell(floor + up), here);
			}
			if (moved) {
				level.playSound(null, shaft.cabinCell(floor), here ? ModSounds.REACTOR_DOOR_OPEN.get()
						: ModSounds.REACTOR_DOOR_CLOSE.get(), SoundSource.BLOCKS, 0.5F, 1.2F);
			}
		}
	}

	private static boolean setOpen(ServerLevel level, BlockPos cell, boolean open) {
		BlockState state = level.getBlockState(cell);
		if (!(state.getBlock() instanceof ElevatorStationBlock)
				|| state.getValue(ElevatorStationBlock.OPEN) == open) {
			return false;
		}
		level.setBlock(cell, state.setValue(ElevatorStationBlock.OPEN, open), Block.UPDATE_ALL);
		return true;
	}
}
