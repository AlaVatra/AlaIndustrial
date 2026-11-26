package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.Flow;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blueprints about moving people between floors (MOD-935): the glass elevator shaft over three floors and the
 * escalator run from its drive to its top landing. Listed in {@link BlueprintCatalog#ALL}.
 */
public final class TransportBlueprints {

	private static final String WINCH = "alaindustrial:elevator_winch";
	private static final String SHAFT = "alaindustrial:elevator_shaft";
	private static final String STATION = "alaindustrial:elevator_station";
	private static final String DRIVE = "alaindustrial:escalator_drive";
	private static final String DRIVE_MV = "alaindustrial:escalator_drive_mv";
	private static final String SECTION = "alaindustrial:escalator_section";
	private static final String LANDING = "alaindustrial:escalator_landing";

	/** Floors of the elevator scene: station bottoms at y 0, 3 and 6, one shaft frame between, the winch at 8. */
	private static final int[] FLOORS = {0, 3, 6};
	private static final int WINCH_Y = 8;
	/** Sections of the escalator scene, rising east one block per section from the drive at the origin. */
	private static final int SECTIONS = 3;

	/**
	 * The first floor's station; the shaft with frames up to two more stations; the winch on a shaft with a
	 * frame missing, its lamp red (bad); the frame back and a cable at the winch's side, the lamp green and the
	 * bottom doors open (good); a ride to the top, the doors opening where the cabin stops. Indices: 0..5 the
	 * stations' halves bottom up, then the frames bottom up, then the winch, cable, stone and generator.
	 */
	public static final BlueprintDef ELEVATOR = new BlueprintDef("elevator",
			List.of(WINCH, SHAFT, STATION),
			List.of(
					new Step(station(0, false), 0, 1, -1, -1, Verdict.NONE),
					new Step(shaft(-1, false, false), 6, 2, -1, -1, Verdict.NONE),
					new Step(join(shaft(FLOORS[1] + 2, false, false), List.of(winch(false, false))), 7, 3, -1, -1,
							Verdict.BAD),
					new Step(join(shaft(-1, true, false), powered(false)), 8, 4, 10, 8, Verdict.GOOD),
					new Step(join(shaft(-1, true, true), powered(true)), 4, 5, List.of(Flow.energy(10, 8)),
							Verdict.NONE, List.of())));

	/**
	 * The drive in the floor, its arrow up the run; the sections one block forward and one up, with no top
	 * landing, their arrows red (bad); the landing on top and a cable at the drive's side, ready and blue
	 * (good); someone steps on and the band runs; the MV drive in its place, the fast band. Indices: 0 drive,
	 * 1..6 the sections' halves bottom up, 7 landing, 8 cable, 9 generator.
	 */
	public static final BlueprintDef ESCALATOR = new BlueprintDef("escalator",
			List.of(DRIVE, DRIVE_MV, SECTION, LANDING),
			List.of(
					new Step(List.of(drive(DRIVE, "off")), 0, 1, -1, -1, Verdict.NONE),
					new Step(run(DRIVE, "off", false, false), 3, 2, -1, -1, Verdict.BAD),
					new Step(run(DRIVE, "ready", false, true), 7, 3, 9, 0, Verdict.GOOD),
					new Step(run(DRIVE, "running", false, true), 3, 2, 9, 0, Verdict.GOOD),
					new Step(run(DRIVE_MV, "running", true, true), 0, 1, 9, 0, Verdict.NONE)));

	private TransportBlueprints() {
	}

	/** The two halves of a station whose bottom is at {@code y}, doors toward the viewer. */
	private static List<Placement> station(int y, boolean open) {
		return station(y, open, open);
	}

	/** A station whose doors are {@code open} at the start of the step and {@code openLater} from its middle. */
	private static List<Placement> station(int y, boolean open, boolean openLater) {
		Map<String, String> late = open == openLater ? Map.of() : Map.of("open", Boolean.toString(openLater));
		List<Placement> halves = new ArrayList<>();
		for (String half : List.of("lower", "upper")) {
			halves.add(new Placement(STATION, 0, y + (half.equals("lower") ? 0 : 1), 0,
					Map.of("facing", "south", "half", half, "open", Boolean.toString(open)), late));
		}
		return halves;
	}

	/**
	 * Three stations (indices 0..5) and a frame between each pair (6, 7), the frame at {@code gapY} left out
	 * (-1: none); {@code bottomOpen} opens the ground floor's doors, {@code ride} sends the cabin to the top:
	 * the bottom doors close and the top ones open during the step.
	 */
	private static List<Placement> shaft(int gapY, boolean bottomOpen, boolean ride) {
		List<Placement> all = new ArrayList<>(station(FLOORS[0], bottomOpen, bottomOpen && !ride));
		all.addAll(station(FLOORS[1], false));
		all.addAll(station(FLOORS[2], false, ride));
		for (int floor = 0; floor < FLOORS.length - 1; floor++) {
			int y = FLOORS[floor] + 2;
			if (y != gapY) {
				all.add(new Placement(SHAFT, 0, y, 0, Map.of("up", "true", "down", "true")));
			}
		}
		return all;
	}

	/** The winch closing the shaft, screen toward the viewer: green when {@code assembled}, moving when lit. */
	private static Placement winch(boolean assembled, boolean moving) {
		return new Placement(WINCH, 0, WINCH_Y, 0, Map.of("facing", "south", "assembled",
				Boolean.toString(assembled), "lit", Boolean.toString(moving)));
	}

	/** The winch (8), a cable at its east side (9), the generator (10) and a stone under it (11). */
	private static List<Placement> powered(boolean moving) {
		return List.of(winch(true, moving), cableOf(COPPER_CABLE, 1, WINCH_Y, 0, "west", "east"),
				new Placement(GENERATOR, 2, WINCH_Y, 0, Map.of("facing", "south", "lit", "true")),
				stone(2, WINCH_Y - 1, 0));
	}

	/** The drive at the origin in the floor, its arrow east up the run. */
	private static Placement drive(String id, String mode) {
		return new Placement(id, 0, 0, 0, Map.of("facing", "east", "mode", mode));
	}

	/**
	 * The drive (0), {@link #SECTIONS} sections diagonally up east (1..6, lower then upper half), then, when
	 * {@code landed}, the top landing (7), a cable (8) and a generator (9) west of the drive.
	 */
	private static List<Placement> run(String driveId, String mode, boolean fast, boolean landed) {
		List<Placement> all = new ArrayList<>(List.of(drive(driveId, mode)));
		for (int i = 1; i <= SECTIONS; i++) {
			for (String half : List.of("lower", "upper")) {
				all.add(new Placement(SECTION, i, i + (half.equals("lower") ? 0 : 1), 0, Map.of("facing", "east",
						"half", half, "mode", mode, "fast", Boolean.toString(fast), "bottom",
						Boolean.toString(i == 1), "top", Boolean.toString(i == SECTIONS))));
			}
		}
		if (landed) {
			all.add(new Placement(LANDING, SECTIONS + 1, SECTIONS, 0, Map.of("facing", "east")));
			all.add(cableOf(COPPER_CABLE, -1, 0, 0, "west", "east"));
			all.add(new Placement(GENERATOR, -2, 0, 0, Map.of("facing", "south", "lit", "true")));
		}
		return all;
	}
}
