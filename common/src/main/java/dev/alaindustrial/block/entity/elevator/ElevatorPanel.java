package dev.alaindustrial.block.entity.elevator;

import dev.alaindustrial.block.entity.ElevatorWinchBlockEntity;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;

/**
 * What the elevator's floor panel shows (MOD-769, the owner's round 3): every floor of the shaft, whether the cabin
 * standing at the panel's floor can be sent there, and what the trip costs. The server reads it off the winch once a
 * tick; the panel's menu hands it to the client as vanilla container data, so the open packet carries nothing of the
 * mod's own (it differs per loader).
 *
 * <p>The layout is fixed: a header ({@link #COUNT} .. {@link #FIRST}), then {@link #MAX_ROWS} rows of two values,
 * a {@link FloorState} and its number: the fare for a floor the cabin can go to or cannot pay for, the height of the
 * gap for a floor cut off by one. A shaft of more floors shows the {@link #MAX_ROWS} round the cabin's floor. A vanilla
 * data slot travels as a 16-bit short, so every number is clamped to one.
 */
public final class ElevatorPanel implements ContainerData {

	/** How many floors the shaft has. */
	public static final int COUNT = 0;
	/** The index, from the bottom, of the floor the panel was opened at: where the cabin stands. */
	public static final int HERE = 1;
	/** The ordinal of the shaft's {@link ElevatorShaft.Problem}; not 0, every floor is shut. */
	public static final int PROBLEM = 2;
	/** The height of the gap a {@link ElevatorShaft.Problem#GAP} names. */
	public static final int PROBLEM_AT = 3;
	/** The EU the winch holds. */
	public static final int ENERGY = 4;
	/** The index of the floor in the first row. */
	public static final int FIRST = 5;
	/** Where the rows start. */
	public static final int ROWS = 6;
	/** The most floors one panel shows: a 64-block shaft of two-block stations has 32. */
	public static final int MAX_ROWS = 32;
	public static final int SIZE = ROWS + 2 * MAX_ROWS;

	/** What a floor's button does; the order is the wire format. */
	public enum FloorState {
		/** The cabin can go there: the number is the fare. */
		OPEN,
		/** The cabin stands there. */
		HERE,
		/** The winch holds less EU than the trip costs: the number is the fare. */
		NO_ENERGY,
		/** The tube is cut on the way: the number is the gap's height. */
		GAP;

		public static FloorState of(int ordinal) {
			FloorState[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : HERE;
		}
	}

	private final ElevatorWinchBlockEntity winch;
	private final int here;
	private final int[] values = new int[SIZE];
	private long readAt = Long.MIN_VALUE;

	/** The panel of the station at the floor {@code here} of {@code winch}'s shaft. */
	public ElevatorPanel(ElevatorWinchBlockEntity winch, int here) {
		this.winch = winch;
		this.here = here;
	}

	/** The data index of the state of the floor in row {@code row}. */
	public static int stateSlot(int row) {
		return ROWS + 2 * row;
	}

	/** The data index of the number of the floor in row {@code row}. */
	public static int valueSlot(int row) {
		return ROWS + 2 * row + 1;
	}

	/** The first floor a panel of {@code count} floors shows: the window of {@link #MAX_ROWS} round {@code here}. */
	public static int first(int count, int here) {
		if (count <= MAX_ROWS) {
			return 0;
		}
		return Math.max(0, Math.min(count - MAX_ROWS, here - MAX_ROWS / 2));
	}

	@Override
	public int get(int index) {
		refresh();
		return values[index];
	}

	@Override
	public void set(int index, int value) {
		values[index] = value;
	}

	@Override
	public int getCount() {
		return SIZE;
	}

	private void refresh() {
		Level level = winch.getLevel();
		long now = level == null ? 0L : level.getGameTime();
		if (now == readAt) {
			return;
		}
		readAt = now;
		Arrays.fill(values, 0);
		ElevatorShaft shaft = winch.shaft();
		List<Integer> floors = shaft.valid() ? shaft.floors() : winch.floors();
		int hereIndex = floors.indexOf(here);
		int first = first(floors.size(), hereIndex);
		values[COUNT] = floors.size();
		values[HERE] = hereIndex;
		values[PROBLEM] = shaft.problem().ordinal();
		values[PROBLEM_AT] = clamp(shaft.gap());
		values[ENERGY] = clamp(winch.getEnergyStorage().getAmount());
		values[FIRST] = first;
		// One count of the riders for every floor's fare and one gap search per floor (MOD-1030: the riders were
		// counted, and the gap searched twice, for each of up to 32 floors every tick the panel is open).
		int riders = winch.riders();
		for (int row = 0; row < MAX_ROWS && first + row < floors.size(); row++) {
			int floor = floors.get(first + row);
			FloorState state;
			long value = 0L;
			int gap = floor == here ? Integer.MIN_VALUE : winch.gapOnTheWayTo(floor);
			if (floor == here) {
				state = FloorState.HERE;
			} else if (gap != Integer.MIN_VALUE) {
				state = FloorState.GAP;
				value = gap;
			} else {
				value = winch.fareTo(floor, riders);
				state = winch.getEnergyStorage().getAmount() < value ? FloorState.NO_ENERGY : FloorState.OPEN;
			}
			values[stateSlot(row)] = state.ordinal();
			values[valueSlot(row)] = clamp(value);
		}
	}

	private static int clamp(long value) {
		return (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
	}
}
