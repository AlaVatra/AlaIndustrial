package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;
import static dev.alaindustrial.client.blueprint.BlueprintRigs.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.Flow;
import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Blueprints about farm and wind field tools (MOD-797). Listed in {@link BlueprintCatalog#ALL}. */
public final class FieldToolsBlueprints {

	private static final String CRAFTING_TABLE = "minecraft:crafting_table";
	private static final String OAK_LEAVES = "minecraft:oak_leaves";
	private static final String HIVE_ATTACHMENT = "alaindustrial:hive_attachment";
	private static final String APIARY_STATION = "alaindustrial:apiary_drone_station";
	private static final String APIARY_STATION_HIVE = "alaindustrial:apiary_drone_hive";

	/**
	 * The wind gauge made at a crafting table, then held against a short tower where the air is calm and
	 * the mill feeds nothing, the same mill on a tall tower feeding the battery box, and the tall tower
	 * under leaves, where the closed sky kills the wind even at height.
	 */
	public static final BlueprintDef WIND_GAUGE = new BlueprintDef("wind_gauge",
			List.of("alaindustrial:wind_gauge"),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							motions(0, "+minecraft:stick", "+minecraft:stick", "+minecraft:glass_pane",
									"+alaindustrial:tin_ingot", "+alaindustrial:tin_ingot",
									"+alaindustrial:wooden_gear", "+alaindustrial:wooden_gear",
									"-alaindustrial:wind_gauge")),
					lowTowerStep(),
					highTowerStep(),
					leafedTowerStep()));

	/**
	 * The garden drone made at a crafting table, then a station whose drone slot is empty and whose wheat
	 * stays young, the same field with the drone at work ripening the wheat, and the iron hoe going into
	 * the station (0, position 1), which the drone borrows from it.
	 */
	public static final BlueprintDef GARDEN_DRONE = new BlueprintDef("garden_drone",
			List.of("alaindustrial:garden_drone"),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							motions(0, "+minecraft:copper_ingot", "+minecraft:copper_ingot",
									"+minecraft:copper_ingot", "+minecraft:copper_ingot", "+minecraft:iron_ingot",
									"+minecraft:iron_ingot", "+alaindustrial:electronic_circuit", "+minecraft:hopper",
									"-alaindustrial:garden_drone")),
					new Step(dronelessField(), 0, 1, List.of(), Verdict.BAD, List.of()),
					new Step(droneField(true, "3", "7"), 0, 1, List.of(Flow.energy(2, 0)), Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:garden_drone", 0, true))),
					new Step(droneField(true, "3", "7"), 0, 1, List.of(Flow.energy(2, 0)), Verdict.NONE,
							List.of(new ItemMotion("minecraft:iron_hoe", 0, true)))));

	/**
	 * The soul vessel made at a crafting table; the small mob repeller on its line, whose slot takes any
	 * vessel, even an empty one (0, position 2); a full vessel going in, which the repeller consumes; the
	 * repeller grown into the medium one on a gold line.
	 */
	public static final BlueprintDef SOUL_VESSEL = new BlueprintDef("soul_vessel",
			List.of("alaindustrial:soul_vessel"),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							motions(0, "+alaindustrial:silver_plate", "+alaindustrial:silver_plate",
									"+alaindustrial:silver_plate", "+alaindustrial:silver_plate",
									"+minecraft:glass", "+minecraft:glass", "+minecraft:glass",
									"+minecraft:soul_sand", "+alaindustrial:electronic_circuit",
									"-alaindustrial:soul_vessel")),
					new Step(repellerLine(repeller(MOB_REPELLER, true, false), COPPER_CABLE), 0, 2,
							List.of(Flow.energy(2, 0)), Verdict.NONE, List.of()),
					new Step(repellerLine(repeller(MOB_REPELLER, true, false), COPPER_CABLE), 0, 2,
							List.of(Flow.energy(2, 0)), Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:soul_vessel", 0, true))),
					new Step(repellerLine(repeller(MOB_REPELLER_MV, false, true), GOLD_CABLE), 0, 2,
							List.of(Flow.energy(2, 0)), Verdict.GOOD, List.of())));

	/**
	 * The apiary (MOD-923): a hive attachment on top of a beehive, its jar showing the hive's honey; the same jar
	 * in front of the hive's entrance, where it would lock the bees in; the two-block apiary station getting its
	 * drone and the map the attachment was written on; the station powered, the full hive emptied by a drone
	 * that flies the honey home; the honey piped out of the station's base into a tank while the combs come out.
	 */
	public static final BlueprintDef APIARY = new BlueprintDef("apiary",
			List.of(HIVE_ATTACHMENT, APIARY_STATION, APIARY_STATION_HIVE, "alaindustrial:apiary_drone",
					"alaindustrial:apiary_map"),
			List.of(
					new Step(List.of(beehive("2", null), jarOnTop("2", null)), 1, 1, -1, -1, Verdict.NONE),
					new Step(List.of(beehive("2", null),
							new Placement(HIVE_ATTACHMENT, 4, 0, 1, Map.of("facing", "south", "honey_level", "0"))),
							1, 1, List.of(), Verdict.BAD, List.of()),
					new Step(apiary(false, false), 2, 2, List.of(), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:apiary_map", 1, true),
									new ItemMotion("alaindustrial:apiary_drone", 3, true),
									new ItemMotion("alaindustrial:apiary_map", 2, true))),
					new Step(apiary(true, false), 3, 3,
							List.of(Flow.energy(5, 2), Flow.item(1, 3, "alaindustrial:honey_bucket")), Verdict.GOOD,
							List.of()),
					new Step(apiary(true, true), 6, 4,
							List.of(Flow.energy(5, 2), Flow.item(2, 7, "alaindustrial:honey_bucket")), Verdict.NONE,
							List.of(new ItemMotion("minecraft:honeycomb", 2, false)))));

	/** A vanilla beehive at (4, 0, 0), its entrance toward the viewer, at {@code level}, then {@code later}. */
	private static Placement beehive(String level, String later) {
		return new Placement("minecraft:beehive", 4, 0, 0, Map.of("facing", "south", "honey_level", level),
				later == null ? Map.of() : Map.of("honey_level", later));
	}

	/** A hive attachment standing on top of the beehive, its jar at {@code level}, then {@code later}. */
	private static Placement jarOnTop(String level, String later) {
		return new Placement(HIVE_ATTACHMENT, 4, 1, 0, Map.of("facing", "up", "honey_level", level),
				later == null ? Map.of() : Map.of("honey_level", later));
	}

	/**
	 * The apiary rig: 0 beehive, 1 attachment, 2 station base, 3 station hive, then, when {@code powered}, 4 cable
	 * and 5 generator (the full hive is emptied by the drone halfway through the step), and, when {@code piped},
	 * 6 fluid pipe and 7 tank of honey.
	 */
	private static List<Placement> apiary(boolean powered, boolean piped) {
		List<Placement> all = new ArrayList<>();
		if (powered && !piped) {
			all.add(beehive("5", "0"));
			all.add(jarOnTop("5", "0"));
		} else {
			all.add(beehive(piped ? "0" : "2", null));
			all.add(jarOnTop(piped ? "0" : "2", null));
		}
		all.add(new Placement(APIARY_STATION, 0, 0, 0, Map.of("facing", "south")));
		all.add(new Placement(APIARY_STATION_HIVE, 0, 1, 0, Map.of("lit", Boolean.toString(powered))));
		if (powered) {
			all.add(cableOf(COPPER_CABLE, -1, 0, 0, "west", "east"));
			all.add(generatorAt(-2));
		}
		if (piped) {
			all.add(fluidPipeOf(FLUID_PIPE, 1, 0, 0, "west", "extract", "east", "insert"));
			all.add(tankWith(2, 0, 0, "alaindustrial:honey"));
		}
		return all;
	}

	/** The crafting table every recipe of this group is made at. */
	private static Placement table() {
		return new Placement(CRAFTING_TABLE, 0, 0, 0, Map.of());
	}

	/** A wind mill with its rotor at ({@code y}), facing west so its back, where the cable goes, is in view. */
	private static Placement millOn(int y) {
		return new Placement(WIND_MILL, 0, y, 0, Map.of("facing", "west")).holding("alaindustrial:windmill_rotor");
	}

	/**
	 * A wind mill on a stone tower of {@code height} blocks: 0..height-1 stone, {@code height} the mill,
	 * then its cable down the side to the battery box on the ground.
	 */
	private static List<Placement> towerRig(int height) {
		List<Placement> all = new ArrayList<>();
		for (int y = 0; y < height; y++) {
			all.add(stone(0, y, 0));
		}
		all.add(millOn(height));
		all.add(cableOf(COPPER_CABLE, 1, height, 0, "west", "down"));
		for (int y = height - 1; y >= 1; y--) {
			all.add(cableOf(COPPER_CABLE, 1, y, 0, "up", "down"));
		}
		all.add(cableOf(COPPER_CABLE, 1, 0, 0, "up", "east"));
		all.add(batteryBox("west"));
		return all;
	}

	/** The mill on a two-block tower (its index is 2): the gauge reads calm air, no energy reaches the box. */
	private static Step lowTowerStep() {
		return new Step(towerRig(2), 2, 2, -1, -1, Verdict.BAD,
				List.of(new ItemMotion("alaindustrial:wind_gauge", 2, true)));
	}

	/** The same mill on an eight-block tower (its index is 8): the wind reaches it and feeds the box. */
	private static Step highTowerStep() {
		List<Placement> rig = towerRig(8);
		return new Step(rig, 8, 2, 8, rig.size() - 1, Verdict.GOOD);
	}

	/** The tall tower with oak leaves right over the mill: the closed sky kills the wind. */
	private static Step leafedTowerStep() {
		List<Placement> rig = towerRig(8);
		rig.add(new Placement(OAK_LEAVES, 0, 9, 0, Map.of()));
		return new Step(rig, rig.size() - 1, 3, -1, -1, Verdict.BAD);
	}

	/**
	 * The drone field with an empty drone slot: 0 station, 1 cable, 2 generator, 3..5 farmland, 6..8 wheat
	 * that stays young for the whole step.
	 */
	private static List<Placement> dronelessField() {
		Placement station = new Placement(GARDEN_DRONE_STATION, 0, 0, 0, Map.of()).holding(
				"minecraft:wheat_seeds", "minecraft:bone_meal", "minecraft:air", "minecraft:air",
				"minecraft:air", "minecraft:air", "minecraft:air", "minecraft:iron_hoe");
		List<Placement> all = new ArrayList<>(List.of(station,
				cableOf(COPPER_CABLE, -1, 0, 0, "west", "east"), generatorAt(-2)));
		for (int x = 1; x <= 3; x++) {
			all.add(new Placement("minecraft:farmland", x, 0, 0, Map.of("moisture", "7")));
		}
		for (int x = 1; x <= 3; x++) {
			all.add(new Placement("minecraft:wheat", x, 1, 0, Map.of("age", "3")));
		}
		return all;
	}

	/** Item motions on block {@code index}: each entry is "+id" for an input or "-id" for an output. */
	private static List<ItemMotion> motions(int index, String... entries) {
		List<ItemMotion> all = new ArrayList<>();
		for (String entry : entries) {
			all.add(new ItemMotion(entry.substring(1), index, entry.charAt(0) == '+'));
		}
		return all;
	}

	private FieldToolsBlueprints() {
	}
}
