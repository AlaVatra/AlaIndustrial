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
 * Blueprints about utility blocks of a base (MOD-935): the mob beacons, the vacuum collector and the electric
 * mob trap with its tap, and the quiet base (sound absorbers, the silent piezo plate). Listed in
 * {@link BlueprintCatalog#ALL}.
 */
public final class UtilityBlueprints {

	private static final String MOD = "alaindustrial:";
	private static final String TABLE = "minecraft:crafting_table";
	private static final String BEACON = MOD + "mob_beacon";
	private static final String BEACON_MV = MOD + "mob_beacon_mv";
	private static final String BEACON_HV = MOD + "mob_beacon_hv";
	private static final String COLLECTOR = MOD + "vacuum_collector";
	private static final String TRAP = MOD + "electric_mob_trap";
	private static final String TAP = MOD + "fluid_tap";
	private static final String ABSORBER = MOD + "sound_absorber";
	private static final String SILENT_PLATE = MOD + "silent_piezo_plate";

	/**
	 * The small beacon alone, dark; a cable from a generator lighting it (good); the medium beacon made from
	 * the small one at the crafting table, then the large one from the medium. Indices: 0 beacon, 1 cable,
	 * 2 generator; 0 the table.
	 */
	public static final BlueprintDef MOB_BEACONS = new BlueprintDef("mob_beacons",
			List.of(BEACON, BEACON_MV, BEACON_HV),
			List.of(
					new Step(List.of(beacon(false)), 0, 1, -1, -1, Verdict.NONE),
					new Step(List.of(beacon(true), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)),
							0, 1, 2, 0, Verdict.GOOD),
					new Step(List.of(at(TABLE, 0, 0, 0)), 0, 2, List.of(), Verdict.NONE,
							motions(0, "+" + BEACON, "+" + MOD + "energy_crystal", "+minecraft:glowstone",
									"+" + MOD + "advanced_circuit", "-" + BEACON_MV)),
					new Step(List.of(at(TABLE, 0, 0, 0)), 0, 3, List.of(), Verdict.NONE,
							motions(0, "+" + BEACON_MV, "+" + MOD + "lapotron_crystal", "+" + MOD + "resonance_coil",
									"+" + MOD + "advanced_circuit", "-" + BEACON_HV))));

	/**
	 * The vacuum collector pulling drops in, a pipe under it carrying them to a chest (good); the electric mob
	 * trap in its place, its loot going the same way (good); the pipe moved to the trap's top, which connects
	 * to nothing (bad); a tap on the trap's side letting the liquid experience out. Indices: 0 collector or
	 * trap, 1 pipe, 2 chest, 3 cable, 4 generator, 5 stone, 6 tap.
	 */
	public static final BlueprintDef MOB_TRAP = new BlueprintDef("mob_trap",
			List.of(COLLECTOR, TRAP, TAP),
			List.of(
					new Step(trapRig(COLLECTOR, false), 0, 1, List.of(Flow.energy(4, 0),
							Flow.item(0, 2, "minecraft:wheat")), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:wheat", 0, true))),
					new Step(trapRig(TRAP, false), 0, 2, List.of(Flow.energy(4, 0),
							Flow.item(0, 2, "minecraft:rotten_flesh")), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:bone", 0, true))),
					new Step(trapRig(TRAP, true), 1, 2, List.of(Flow.energy(4, 0)), Verdict.BAD,
							List.of(new ItemMotion("minecraft:rotten_flesh", 0, true))),
					new Step(join(trapRig(TRAP, false), List.of(new Placement(TAP, 0, 1, 1,
							Map.of("facing", "south", "ceiling", "false", "open", "false"), Map.of("open", "true")))),
							6, 3, List.of(Flow.energy(4, 0)), Verdict.NONE,
							List.of(new ItemMotion("minecraft:experience_bottle", 6, false)))));

	/**
	 * A sound absorber beside a running macerator; four absorbers joined into one group, its lamps showing a
	 * higher stage (good); the silent piezo plate made from a piezo plate and string; two silent plates on a
	 * floor feeding a battery box. Indices: 0 generator, 1 cable, 2 macerator, 3.. the absorbers; of the
	 * floor: 0..2 stone, 3 and 4 plates, 5 cable, 6 box.
	 */
	public static final BlueprintDef QUIET_BASE = new BlueprintDef("quiet_base",
			List.of(ABSORBER, SILENT_PLATE),
			List.of(
					new Step(join(noisyLine(), List.of(absorber(4, 0, 1))), 3, 1, 0, 2, Verdict.NONE),
					new Step(join(noisyLine(), absorberGroup()), 3, 1, 0, 2, Verdict.GOOD),
					new Step(List.of(at(TABLE, 0, 0, 0)), 0, 2, List.of(), Verdict.NONE,
							motions(0, "+" + PIEZO_PLATE, "+minecraft:string", "+minecraft:string",
									"-" + SILENT_PLATE)),
					new Step(List.of(stone(0, 0, 0), stone(1, 0, 0), stone(2, 0, 0), silentPlate(0), silentPlate(1),
							cableOf(COPPER_CABLE, 2, 1, 0, "west", "west_low", "east"), boxAt(3, 1, 0)),
							4, 2, 4, 6, Verdict.GOOD)));

	private UtilityBlueprints() {
	}

	private static Placement beacon(boolean lightsUp) {
		return new Placement(BEACON, 0, 0, 0, Map.of("lit", "false"), lightsUp ? Map.of("lit", "true") : Map.of());
	}

	/**
	 * 0 the collector or trap one block up, lighting up; 1 a pipe under it ({@code pipeOnTop}: over it) to
	 * 2 a chest; 3 a cable from the west, 4 a generator and 5 the stone it stands on.
	 */
	private static List<Placement> trapRig(String id, boolean pipeOnTop) {
		int pipeY = pipeOnTop ? 2 : 0;
		String face = pipeOnTop ? "down_mode" : "up_mode";
		return List.of(new Placement(id, 0, 1, 0, Map.of("lit", "false"), Map.of("lit", "true")),
				new Placement(ITEM_PIPE, 0, pipeY, 0, Map.of(face, "extract", "east_mode", "insert")),
				new Placement("minecraft:chest", 1, pipeY, 0, Map.of("facing", "south", "type", "single")),
				cableOf(COPPER_CABLE, -1, 1, 0, "west", "east"),
				new Placement(GENERATOR, -2, 1, 0, Map.of("facing", "south", "lit", "true")), stone(-2, 0, 0));
	}

	/** 0 generator, 1 cable, 2 a running macerator. */
	private static List<Placement> noisyLine() {
		return List.of(generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
				machine(MACERATOR, 2, 0, true, false));
	}

	/** An absorber at ({@code x}, {@code y}, 0) at {@code tier}; each of {@code joined} is a face that merged. */
	private static Placement absorber(int x, int y, int tier, String... joined) {
		Map<String, String> props = new java.util.HashMap<>(Map.of("tier", Integer.toString(tier)));
		for (String face : List.of("north", "south", "east", "west", "up", "down")) {
			props.put(face, Boolean.toString(List.of(joined).contains(face)));
		}
		return new Placement(ABSORBER, x, y, 0, props);
	}

	/** Four absorbers as a two-by-two panel at x 4..5, y 0..1: one group, stage three. */
	private static List<Placement> absorberGroup() {
		List<Placement> all = new ArrayList<>();
		all.add(absorber(4, 0, 3, "east", "up"));
		all.add(absorber(5, 0, 3, "west", "up"));
		all.add(absorber(4, 1, 3, "east", "down"));
		all.add(absorber(5, 1, 3, "west", "down"));
		return all;
	}

	/** A silent piezo plate at ({@code x}, 1, 0), pressed later in the step. */
	private static Placement silentPlate(int x) {
		return new Placement(SILENT_PLATE, x, 1, 0, Map.of("powered", "false"), Map.of("powered", "true"));
	}

	private static Placement at(String id, int x, int y, int z) {
		return new Placement(id, x, y, z, Map.of());
	}

	/** Item motions on block {@code index}: each entry is "+id" for an input or "-id" for an output. */
	private static List<ItemMotion> motions(int index, String... entries) {
		List<ItemMotion> all = new ArrayList<>();
		for (String entry : entries) {
			all.add(new ItemMotion(entry.substring(1), index, entry.charAt(0) == '+'));
		}
		return all;
	}
}
