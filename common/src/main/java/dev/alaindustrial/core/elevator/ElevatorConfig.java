package dev.alaindustrial.core.elevator;

import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.Section;

/**
 * Knobs of the elevator (MOD-769), in the holder of their own subsystem (ADR-034). The json key of each knob is its
 * field name; {@code Config.REGISTRY} scans this class next to {@code Config}. The numbers are the research's of
 * 2026-11-22 accepted by the owner, mirrored in {@code docs/PERFORMANCE.md} and the spec
 * {@code docs/blocks/utility/elevator.md}.
 */
public final class ElevatorConfig {

	private ElevatorConfig() {
	}

	/** Internal EU buffer of the winch: an LV store's worth, a dozen 64-block rides with a passenger. */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 1,
			doc = "Elevator winch internal EU buffer. Applies to newly placed winches.")
	public static int elevatorBuffer = 10_000;

	/** EU per tick the winch takes from a cable: LV. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Highest EU per tick an elevator winch takes in from a cable.")
	public static int elevatorMaxInput = 32;

	/** EU every trip pays to set off. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Flat EU every elevator trip pays when the cabin sets off.")
	public static int elevatorLaunchCost = 50;

	/** EU per block of height for the empty cabin. */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 0,
			doc = "EU per block of climb for the empty elevator cabin.")
	public static int elevatorCostPerBlock = 8;

	/** EU per block of height for every passenger: the cost grows with the load. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Extra EU per block of climb for every mob or player riding the elevator cabin.")
	public static int elevatorCostPerRider = 4;

	/** Share of the climb a descent pays: the brake. */
	@Knob(section = Section.LOGISTICS, min = 0.0, floorTo = 0.0,
			doc = "Share of the climb price an elevator descent pays (0..1).")
	public static double elevatorDescentShare = 0.25D;

	/** Top speed of the cabin, blocks per tick: 4 blocks a second, well under the 0.49 a contraption may move. */
	@Knob(section = Section.LOGISTICS, min = 0.01, floorTo = 0.01,
			doc = "Elevator cabin top speed in blocks per tick (0.2 = 4 blocks a second).")
	public static double elevatorSpeed = 0.2D;

	/** Speed of the safety catches lowering the cabin to an intact floor, blocks per tick. */
	@Knob(section = Section.LOGISTICS, min = 0.01, floorTo = 0.01,
			doc = "Speed the elevator safety catches lower the cabin at, in blocks per tick.")
	public static double elevatorCatchSpeed = 0.05D;

	/** Longest glass shaft under a winch, blocks. */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 3,
			doc = "Longest glass shaft an elevator winch drives, in blocks from the winch down.")
	public static int elevatorMaxHeight = 64;
}
