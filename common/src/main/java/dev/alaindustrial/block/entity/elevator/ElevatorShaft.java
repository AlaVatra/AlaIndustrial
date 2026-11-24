package dev.alaindustrial.block.entity.elevator;

import dev.alaindustrial.block.ElevatorStationBlock;
import dev.alaindustrial.block.ElevatorWinchBlock;
import dev.alaindustrial.block.entity.ElevatorWinchBlockEntity;
import dev.alaindustrial.core.elevator.ElevatorShaftPart;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/**
 * What the winch finds when it reads its shaft (MOD-769): the glass tube runs straight down from the winch, the cabin
 * runs inside it, and every landing station in the tube is a floor.
 *
 * <p><b>Heights.</b> A floor is the height of the cabin's floor at that station: the station's lower half. The cabin
 * fills two cells from there, the two halves of the station.
 *
 * <p><b>The tube is the way.</b> The cabin runs only in the cells of the tube, so the way is clear exactly while the
 * tube is whole: a cell that is not part of the shaft any more (broken, or swapped for another block) is a gap, and so
 * is a cell that is not loaded. A gap under the winch ends the shaft there; a gap with more shaft below it is
 * reported ({@link Problem#GAP}), so a player who left one out is told where.
 *
 * @param winch   the winch's position
 * @param floors  the floors from the bottom up, each the height of the cabin's floor there
 * @param bottom  the height of the lowest cell of the tube
 * @param gap     the height of the gap that cuts the tube, {@link Integer#MIN_VALUE} when there is none
 * @param problem why the shaft cannot run, {@link Problem#NONE} when it can
 */
public record ElevatorShaft(BlockPos winch, List<Integer> floors, int bottom, int gap, Problem problem) {

	/** Why a shaft does not run; each has a message key {@code message.alaindustrial.elevator.problem.<id>}. */
	public enum Problem {
		NONE("none"),
		/** No shaft under the winch. */
		NO_COLUMN("no_column"),
		/** Fewer than two stations: nowhere to go. */
		ONE_FLOOR("one_floor"),
		/** The tube goes on below the longest the winch drives. */
		TOO_TALL("too_tall"),
		/** A cell of the tube is missing, and more of the shaft stands below it. */
		GAP("gap");

		public final String id;

		Problem(String id) {
			this.id = id;
		}
	}

	/** Read the tube under {@code winch}, at most {@code maxHeight} cells. */
	public static ElevatorShaft scan(Level level, BlockPos winch, int maxHeight) {
		List<Integer> floors = new ArrayList<>();
		int y = winch.getY() - 1;
		int lowest = level.getMinY();
		while (y >= lowest && winch.getY() - y <= maxHeight && part(level, winch.atY(y))) {
			BlockState state = level.getBlockState(winch.atY(y));
			if (state.getBlock() instanceof ElevatorStationBlock
					&& state.getValue(ElevatorStationBlock.HALF) == DoubleBlockHalf.LOWER
					&& y + 1 < winch.getY() && part(level, winch.atY(y + 1))) {
				floors.add(0, y);
			}
			y--;
		}
		int bottom = y + 1;
		int gap = Integer.MIN_VALUE;
		Problem problem = Problem.NONE;
		if (bottom == winch.getY()) {
			problem = Problem.NO_COLUMN;
		} else if (y >= lowest && part(level, winch.atY(y))) {
			problem = Problem.TOO_TALL;
		} else if (partBelow(level, winch, y, maxHeight)) {
			gap = y;
			problem = Problem.GAP;
		} else if (floors.size() < 2) {
			problem = Problem.ONE_FLOOR;
		}
		return new ElevatorShaft(winch, List.copyOf(floors), bottom, gap, problem);
	}

	/** Whether a piece of shaft stands under the gap at {@code gapY}, within the winch's reach. */
	private static boolean partBelow(Level level, BlockPos winch, int gapY, int maxHeight) {
		for (int y = gapY - 1; y >= level.getMinY() && winch.getY() - y <= maxHeight; y--) {
			if (part(level, winch.atY(y))) {
				return true;
			}
		}
		return false;
	}

	/** Whether the cell is a loaded part of a shaft. */
	public static boolean part(Level level, BlockPos cell) {
		return level.isLoaded(cell) && level.getBlockState(cell).getBlock() instanceof ElevatorShaftPart;
	}

	/** Whether the cabin can run in this shaft. */
	public boolean valid() {
		return problem == Problem.NONE;
	}

	/** The cabin's cell at height {@code y}: in the tube, under the winch. */
	public BlockPos cabinCell(int y) {
		return winch.atY(y);
	}

	/** The index of the floor at height {@code y}, or -1 when no floor is there. */
	public int floorIndex(double y) {
		for (int i = 0; i < floors.size(); i++) {
			if (Math.abs(floors.get(i) - y) < 1.0E-6D) {
				return i;
			}
		}
		return -1;
	}

	/** The lowest floor. Only for a {@link #valid()} shaft. */
	public int lowestFloor() {
		return floors.getFirst();
	}

	/** The highest floor. Only for a {@link #valid()} shaft. */
	public int highestFloor() {
		return floors.getLast();
	}

	/**
	 * The first cell from {@code low} to {@code high} (inclusive) under {@code winch} that is not a part of the shaft:
	 * a gap the cabin cannot pass. {@link Integer#MIN_VALUE} when the tube is whole there.
	 */
	public static int firstGap(Level level, BlockPos winch, int low, int high) {
		for (int y = low; y <= high; y++) {
			if (!part(level, winch.atY(y))) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	/** Whether the station of the floor at {@code floor} still stands in the tube, both halves. */
	public static boolean stationPresent(Level level, BlockPos winch, int floor) {
		BlockPos lower = winch.atY(floor);
		BlockPos upper = winch.atY(floor + 1);
		return level.isLoaded(upper) && level.getBlockState(lower).getBlock() instanceof ElevatorStationBlock
				&& level.getBlockState(upper).getBlock() instanceof ElevatorStationBlock;
	}

	/**
	 * The winch over the tube {@code from} stands in: up the tube cell by cell to the first block that is not a shaft
	 * cell, which must be a winch. {@code null} when there is none.
	 */
	public static @Nullable ElevatorWinchBlockEntity winchAbove(Level level, BlockPos from) {
		BlockPos cell = from.above();
		for (int i = 0; i <= 512 && cell.getY() < level.getMaxY(); i++) {
			BlockState state = level.getBlockState(cell);
			if (state.getBlock() instanceof ElevatorWinchBlock) {
				return level.getBlockEntity(cell) instanceof ElevatorWinchBlockEntity winch ? winch : null;
			}
			if (!(state.getBlock() instanceof ElevatorShaftPart)) {
				return null;
			}
			cell = cell.above();
		}
		return null;
	}
}
