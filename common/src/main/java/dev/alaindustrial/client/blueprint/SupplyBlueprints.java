package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blueprints about what the player makes to carry and use (MOD-935): the radiation care line from kelp to
 * iodine tablets and the antirad bar, honey from the centrifuge, and reinforced bone meal. Listed in
 * {@link BlueprintCatalog#ALL}.
 */
public final class SupplyBlueprints {

	private static final String MOD = "alaindustrial:";
	private static final String TABLE = "minecraft:crafting_table";
	private static final String KELP_ASH = MOD + "kelp_ash";
	private static final String IODINE = MOD + "iodine";
	private static final String TABLETS = MOD + "iodine_tablets";
	private static final String ANTIRAD_BAR = MOD + "antirad_bar";
	private static final String GUM = MOD + "chewing_gum";
	private static final String INULIN = MOD + "inulin";
	private static final String CENTRIFUGE = MOD + "honey_centrifuge";
	private static final String HONEY_BUCKET = MOD + "honey_bucket";
	private static final String HONEYCOMB = "minecraft:honeycomb";
	private static final String BONE_MEAL = MOD + "reinforced_bone_meal";

	/**
	 * Dried kelp ground into kelp ash; the ash and sulfur dust in the galvanic bath into iodine; iodine pressed
	 * into tablets; the antirad bar and then the gum at the crafting table. Indices of the machine lines: 0
	 * generator, 1 cable, 2 machine; of the bath line: 0 tank, 1 pipe, 2 bath, 3 cable, 4 generator.
	 */
	public static final BlueprintDef RADIATION_CARE = new BlueprintDef("radiation_care",
			List.of(KELP_ASH, IODINE, TABLETS, ANTIRAD_BAR, GUM),
			List.of(
					new Step(machineLine(MACERATOR), 2, 1, 0, 2, Verdict.NONE,
							motions(2, "+minecraft:dried_kelp", "-" + KELP_ASH)),
					new Step(bathLine(FLUID_TANK, true), 2, 2, 4, 2, Verdict.NONE,
							motions(2, "+" + KELP_ASH, "+" + MOD + "sulfur_dust", "-" + IODINE)),
					new Step(machineLine(COMPRESSOR), 2, 3, 0, 2, Verdict.GOOD,
							motions(2, "+" + IODINE, "-" + TABLETS)),
					new Step(List.of(table()), 0, 4, List.of(), Verdict.GOOD,
							motions(0, "+minecraft:milk_bucket", "+minecraft:cocoa_beans", "+" + INULIN, "+" + INULIN,
									"-" + ANTIRAD_BAR)),
					new Step(List.of(table()), 0, 5, List.of(), Verdict.NONE,
							motions(0, "+" + MOD + "raw_rubber", "+" + INULIN, "-" + GUM))));

	/**
	 * Honeycomb into a centrifuge with no power, dark (bad); a cable from a generator, the centrifuge spinning
	 * (good); an empty bucket in, a honey bucket out; a fluid pipe carrying the honey to a tank. Indices: 0
	 * centrifuge, 1 cable, 2 generator, 3 pipe, 4 tank.
	 */
	public static final BlueprintDef HONEY = new BlueprintDef("honey",
			List.of(CENTRIFUGE, HONEY_BUCKET),
			List.of(
					new Step(List.of(centrifuge(false)), 0, 1, List.of(), Verdict.BAD,
							motions(0, "+" + HONEYCOMB, "+" + HONEYCOMB)),
					new Step(centrifugeLine(false), 0, 1, 2, 0, Verdict.GOOD,
							motions(0, "+" + HONEYCOMB, "+" + HONEYCOMB)),
					new Step(centrifugeLine(false), 0, 1, 2, 0, Verdict.NONE,
							motions(0, "+minecraft:bucket", "-" + HONEY_BUCKET)),
					new Step(centrifugeLine(true), 4, 2, 2, 0, Verdict.GOOD,
							motions(0, "+" + HONEYCOMB))));

	/**
	 * Bone meal and biomass pressed into reinforced bone meal at the crafting table; a sapling on grass taking
	 * it; the tree grown at once (good). Indices: 0 grass, 1 the sapling, then the tree's trunk and crown.
	 */
	public static final BlueprintDef REINFORCED_BONE_MEAL = new BlueprintDef("reinforced_bone_meal",
			List.of(BONE_MEAL),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							motions(0, "+minecraft:bone_meal", "+minecraft:bone_meal", "+" + MOD + "biomass",
									"-" + BONE_MEAL)),
					new Step(List.of(at("minecraft:grass_block", 0, 0, 0),
							new Placement("minecraft:oak_sapling", 0, 1, 0, Map.of("stage", "0"))), 1, 2, List.of(),
							Verdict.NONE, motions(1, "+" + BONE_MEAL)),
					new Step(tree(), 1, 2, -1, -1, Verdict.GOOD)));

	private SupplyBlueprints() {
	}

	private static Placement table() {
		return at(TABLE, 0, 0, 0);
	}

	/** 0 generator, 1 cable, 2 the machine {@code id} facing the viewer, lighting up once its input went in. */
	private static List<Placement> machineLine(String id) {
		return List.of(generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), machine(id, 2, 0, false, true));
	}

	private static Placement centrifuge(boolean lit) {
		return new Placement(CENTRIFUGE, 0, 0, 0, Map.of("facing", "south", "lit", Boolean.toString(lit)),
				lit ? Map.of() : Map.of("lit", "true"));
	}

	/** 0 centrifuge, 1 cable, 2 generator east of it, and, when {@code piped}, 3 a pipe and 4 a honey tank west. */
	private static List<Placement> centrifugeLine(boolean piped) {
		List<Placement> all = new ArrayList<>(List.of(centrifuge(piped), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
				new Placement(GENERATOR, 2, 0, 0, Map.of("facing", "south", "lit", "true"))));
		if (piped) {
			all.add(fluidPipeOf(FLUID_PIPE, -1, 0, 0, "east", "extract", "west", "insert"));
			all.add(tankWith(-2, 0, 0, MOD + "honey"));
		}
		return all;
	}

	/** 0 grass, 1 the trunk's bottom, then the trunk and a crown of leaves. */
	private static List<Placement> tree() {
		List<Placement> all = new ArrayList<>(List.of(at("minecraft:grass_block", 0, 0, 0)));
		for (int y = 1; y <= 3; y++) {
			all.add(new Placement("minecraft:oak_log", 0, y, 0, Map.of("axis", "y")));
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				for (int y = 3; y <= 4; y++) {
					if ((x != 0 || z != 0 || y == 4) && (y == 3 || Math.abs(x) + Math.abs(z) < 2)) {
						all.add(new Placement("minecraft:oak_leaves", x, y, z, Map.of("persistent", "true")));
					}
				}
			}
		}
		return all;
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
