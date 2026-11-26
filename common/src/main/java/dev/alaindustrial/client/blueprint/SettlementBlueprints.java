package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.Flow;
import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blueprints about the work town (MOD-935): waking its core and wiring a shift worker's house, and rebuilding
 * the town hall with the dispatcher's plan and the builder drone. Listed in {@link BlueprintCatalog#ALL}.
 */
public final class SettlementBlueprints {

	private static final String MOD = "alaindustrial:";
	private static final String CORE = MOD + "settlement_core";
	private static final String ROAD = MOD + "town_road";
	private static final String INLET = MOD + "house_inlet";
	private static final String BED = MOD + "charging_bed";
	private static final String CAPSULE = MOD + "reception_capsule";
	private static final String DISPATCHER = MOD + "town_dispatcher";
	private static final String NEST = MOD + "drone_nest";
	private static final String DRONE = MOD + "construction_drone";
	private static final String SHEET = MOD + "town_hall_plan_sheet";
	private static final String RESTORATION_PLAN = MOD + "restoration_plan";
	private static final String HOUSE_PLAN = MOD + "house_plan";
	private static final String BRICKS = "minecraft:stone_bricks";

	/**
	 * The core found dark; a cable from a generator lighting it (good); a town road from the core to a house
	 * inlet; the charging bed behind the inlet; a road block missing, the inlet cut off (bad); the reception
	 * capsule taking its parts. Indices: 0 core, 1 cable, 2 generator, 3..5 road, 6 inlet, 7 and 8 bed,
	 * 9..11 capsule bottom up, 12 its cable.
	 */
	public static final BlueprintDef SETTLEMENT = new BlueprintDef("settlement",
			List.of(CORE, ROAD, INLET, BED, CAPSULE),
			List.of(
					new Step(List.of(core(false, false)), 0, 1, -1, -1, Verdict.NONE),
					new Step(coreFeed(false), 0, 1, 2, 0, Verdict.GOOD),
					new Step(join(coreFeed(true), house(-1, true, false)), 4, 2, List.of(Flow.energy(2, 0),
							Flow.energy(0, 6)), Verdict.NONE, List.of()),
					new Step(join(coreFeed(true), house(-1, true, true)), 7, 3, List.of(Flow.energy(2, 0),
							Flow.energy(0, 6), Flow.energy(6, 7)), Verdict.NONE, List.of()),
					new Step(join(coreFeed(true), house(2, false, true)), 4, 2, List.of(Flow.energy(2, 0)),
							Verdict.BAD, List.of()),
					new Step(join(join(coreFeed(true), house(-1, true, true)), capsule()), 9, 4,
							List.of(Flow.energy(2, 0), Flow.energy(2, 9)), Verdict.GOOD,
							List.of(new ItemMotion(MOD + "electronic_circuit", 9, true),
									new ItemMotion(MOD + "rubber", 9, true),
									new ItemMotion(MOD + "copper_plate", 9, true)))));

	/**
	 * The dispatcher taking the three town hall plan sheets; the restoration plan taken back out, which stops
	 * the drones (bad); the drone going into its nest on the hall's roof, the core alight beside it; blocks in
	 * the dispatcher's buffer and the drone carrying them to the hall (good); a house plan clicked on the core.
	 * Indices: 0 dispatcher, 1 core, 2 cable, 3 generator, 4 roof, 5 nest, 6 and 7 the wall being rebuilt.
	 */
	public static final BlueprintDef TOWN_REBUILD = new BlueprintDef("town_rebuild",
			List.of(DISPATCHER, SHEET, RESTORATION_PLAN, HOUSE_PLAN, NEST, DRONE),
			List.of(
					new Step(List.of(dispatcher(false)), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion(SHEET, 0, true), new ItemMotion(SHEET, 0, true),
									new ItemMotion(SHEET, 0, true))),
					new Step(List.of(dispatcher(false)), 0, 1, List.of(), Verdict.BAD,
							List.of(new ItemMotion(RESTORATION_PLAN, 0, false))),
					new Step(hall(false, false), 5, 2, List.of(Flow.energy(3, 1)), Verdict.NONE,
							List.of(new ItemMotion(DRONE, 5, true))),
					new Step(hall(true, true), 7, 3, List.of(Flow.energy(3, 1), Flow.item(0, 7, BRICKS)),
							Verdict.GOOD, List.of(new ItemMotion(BRICKS, 0, true),
									new ItemMotion("minecraft:oak_planks", 0, true))),
					new Step(hall(true, true), 1, 4, List.of(Flow.energy(3, 1)), Verdict.NONE,
							List.of(new ItemMotion(HOUSE_PLAN, 1, true)))));

	private SettlementBlueprints() {
	}

	/** The settlement core at {@code x}, dark or alight; {@code litLater} lights it during the step. */
	private static Placement core(int x, boolean lit, boolean litLater) {
		return new Placement(CORE, x, 0, 0, Map.of("lit", Boolean.toString(lit)),
				litLater ? Map.of("lit", "true") : Map.of());
	}

	private static Placement core(boolean lit, boolean litLater) {
		return core(0, lit, litLater);
	}

	/** 0 the core, 1 a cable toward the viewer, 2 the generator feeding it; {@code lit} or lighting up. */
	private static List<Placement> coreFeed(boolean lit) {
		return List.of(core(lit, true), cableOf(COPPER_CABLE, 0, 0, 1, "north", "south"),
				new Placement(GENERATOR, 0, 0, 2, Map.of("facing", "south", "lit", "true")));
	}

	/**
	 * The road east of the core (3..5, the block at {@code gapX} left out), the inlet at x 4 taking the road on
	 * its outer west face, and, when {@code bed}, the charging bed behind it (foot, then head).
	 */
	private static List<Placement> house(int gapX, boolean powered, boolean bed) {
		List<Placement> all = new ArrayList<>();
		for (int x = 1; x <= 3; x++) {
			if (x != gapX) {
				all.add(new Placement(ROAD, x, 0, 0, Map.of("east", "true", "west", "true",
						"powered", Boolean.toString(powered))));
			}
		}
		all.add(new Placement(INLET, 4, 0, 0, Map.of("facing", "west")));
		if (bed) {
			all.add(new Placement(BED, 5, 0, 0, Map.of("facing", "east", "part", "foot")));
			all.add(new Placement(BED, 6, 0, 0, Map.of("facing", "east", "part", "head")));
		}
		return all;
	}

	/** The reception capsule west of the generator, three blocks high, and the cable feeding its bottom. */
	private static List<Placement> capsule() {
		List<Placement> all = new ArrayList<>();
		for (String part : List.of("lower", "middle", "upper")) {
			int y = part.equals("lower") ? 0 : part.equals("middle") ? 1 : 2;
			all.add(new Placement(CAPSULE, -2, y, 2, Map.of("facing", "south", "part", part)));
		}
		all.add(cableOf(COPPER_CABLE, -1, 0, 2, "west", "east"));
		return all;
	}

	private static Placement dispatcher(boolean lit) {
		return new Placement(DISPATCHER, 0, 0, 0, Map.of("facing", "south", "lit", Boolean.toString(lit)));
	}

	/**
	 * The dispatcher (0), the core west of it with its cable and generator (1..3), a roof block (4) with the
	 * nest on it (5), and, when {@code building}, the hall's wall going up east of it (6, 7).
	 */
	private static List<Placement> hall(boolean working, boolean building) {
		List<Placement> all = new ArrayList<>(List.of(dispatcher(working), core(-2, true, false),
				cableOf(COPPER_CABLE, -2, 0, 1, "north", "south"),
				new Placement(GENERATOR, -2, 0, 2, Map.of("facing", "south", "lit", "true")),
				new Placement(BRICKS, 2, 0, 0, Map.of()), new Placement(NEST, 2, 1, 0, Map.of())));
		if (building) {
			all.add(new Placement(BRICKS, 4, 0, 0, Map.of()));
			all.add(new Placement(BRICKS, 4, 1, 0, Map.of()));
		}
		return all;
	}
}
