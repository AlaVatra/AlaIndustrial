package dev.alaindustrial.client.blueprint;

import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blueprints about building materials (MOD-935): industrial concrete from slag to the set block, and the
 * quarry construction set built into two towers with a rail between them. Listed in
 * {@link BlueprintCatalog#ALL}.
 */
public final class ConstructionBlueprints {

	private static final String MOD = "alaindustrial:";
	private static final String TABLE = "minecraft:crafting_table";
	private static final String CEMENT = MOD + "cement";
	private static final String FRESH = MOD + "fresh_concrete";
	private static final String CONCRETE = MOD + "industrial_concrete";
	private static final String CONCRETE_SLAB = MOD + "industrial_concrete_slab";
	private static final String CONCRETE_STAIRS = MOD + "industrial_concrete_stairs";
	private static final String FOUNDATION = MOD + "quarry_foundation";
	private static final String INVAR_FOUNDATION = MOD + "invar_quarry_foundation";
	private static final String NETHERITE_FOUNDATION = MOD + "netherite_alloy_quarry_foundation";
	private static final String SCAFFOLD = MOD + "tempered_iron_scaffold";
	private static final String CONCRETE_BEAM = MOD + "concrete_rail_beam";
	private static final String PLATE_BEAM = MOD + "plate_rail_beam";
	private static final String PLATE_BLOCK = MOD + "reinforced_plate_block";
	private static final String PLATE_SLAB = MOD + "reinforced_plate_slab";
	private static final String PLATE_STAIRS = MOD + "reinforced_plate_stairs";
	private static final String HAZARD = MOD + "hazard_stripe_block";
	private static final String TI_PLATE = MOD + "tempered_iron_plate";

	/** Height of the scaffold towers over their foundations. */
	private static final int TOWER = 3;

	/**
	 * Slag fired in a furnace into cement; cement, sand, gravel and a water bucket at the crafting table into
	 * fresh concrete; three blocks of it laid and setting; the same three set into industrial concrete (good);
	 * its slab and stairs beside them. Indices: 0 the furnace or table, then the laid row from x 0.
	 */
	public static final BlueprintDef CONCRETE_SET = new BlueprintDef("concrete",
			List.of(CEMENT, FRESH, CONCRETE, CONCRETE_SLAB, CONCRETE_STAIRS),
			List.of(
					new Step(List.of(new Placement("minecraft:furnace", 0, 0, 0,
							Map.of("facing", "south", "lit", "false"), Map.of("lit", "true"))), 0, 1, List.of(),
							Verdict.NONE, motions(0, "+" + MOD + "slag", "-" + CEMENT)),
					new Step(List.of(at(TABLE, 0, 0, 0)), 0, 2, List.of(), Verdict.NONE,
							motions(0, "+" + CEMENT, "+" + CEMENT, "+minecraft:sand", "+minecraft:sand",
									"+minecraft:gravel", "+minecraft:gravel", "+minecraft:water_bucket",
									"-" + FRESH, "-minecraft:bucket")),
					new Step(row(FRESH), 1, 3, -1, -1, Verdict.NONE),
					new Step(row(CONCRETE), 1, 4, -1, -1, Verdict.GOOD),
					new Step(concreteForms(), 3, 5, -1, -1, Verdict.NONE)));

	/**
	 * The quarry foundation crafted; its three grades in a row; two scaffold towers on foundations; a concrete
	 * rail and a plate rail laid flat between the towers' tops; the reinforced plate block, its slab and stairs
	 * and the hazard stripe block as the floor in front. Indices of the build: 0 and 1 the foundations, 2..4
	 * the west tower bottom up, 5..7 the east tower, 8..10 the rails, 11.. the floor.
	 */
	public static final BlueprintDef QUARRY_BLOCKS = new BlueprintDef("quarry_blocks",
			List.of(FOUNDATION, INVAR_FOUNDATION, NETHERITE_FOUNDATION, SCAFFOLD, CONCRETE_BEAM, PLATE_BEAM,
					PLATE_BLOCK, PLATE_SLAB, PLATE_STAIRS, HAZARD),
			List.of(
					new Step(List.of(at(TABLE, 0, 0, 0)), 0, 1, List.of(), Verdict.NONE,
							motions(0, "+" + TI_PLATE, "+" + TI_PLATE, "+" + CONCRETE, "+" + CONCRETE,
									"+" + MOD + "tempered_iron_block", "-" + FOUNDATION)),
					new Step(List.of(at(FOUNDATION, 0, 0, 0), at(INVAR_FOUNDATION, 1, 0, 0),
							at(NETHERITE_FOUNDATION, 2, 0, 0)), 1, 2, -1, -1, Verdict.NONE),
					new Step(towers(false, false), 3, 3, -1, -1, Verdict.NONE),
					new Step(towers(true, false), 9, 4, -1, -1, Verdict.NONE),
					new Step(towers(true, true), 12, 5, -1, -1, Verdict.GOOD)));

	private ConstructionBlueprints() {
	}

	/** Three blocks of {@code id} in a row at x 0..2; fresh concrete sets a little during the step. */
	private static List<Placement> row(String id) {
		List<Placement> all = new ArrayList<>();
		for (int x = 0; x <= 2; x++) {
			all.add(id.equals(FRESH) ? new Placement(FRESH, x, 0, 0, Map.of("setting", "0"), Map.of("setting", "2"))
					: at(id, x, 0, 0));
		}
		return all;
	}

	/** The set row (0..2), stairs climbing onto it from the east (3) and a slab beyond them (4). */
	private static List<Placement> concreteForms() {
		List<Placement> all = new ArrayList<>(row(CONCRETE));
		all.add(new Placement(CONCRETE_STAIRS, 3, 0, 0, Map.of("facing", "west", "half", "bottom",
				"shape", "straight")));
		all.add(new Placement(CONCRETE_SLAB, 4, 0, 0, Map.of("type", "bottom")));
		return all;
	}

	/**
	 * Two foundations four blocks apart (0, 1), a scaffold tower of {@link #TOWER} on each (2..4, 5..7), the
	 * rails between their tops when {@code railed} (8 and 9 concrete, 10 plate), the floor when {@code floored}.
	 */
	private static List<Placement> towers(boolean railed, boolean floored) {
		List<Placement> all = new ArrayList<>(List.of(at(FOUNDATION, 0, 0, 0), at(INVAR_FOUNDATION, 4, 0, 0)));
		for (int x : new int[] {0, 4}) {
			for (int y = 1; y <= TOWER; y++) {
				all.add(at(SCAFFOLD, x, y, 0));
			}
		}
		if (railed) {
			all.add(new Placement(CONCRETE_BEAM, 1, TOWER, 0, Map.of("axis", "x")));
			all.add(new Placement(CONCRETE_BEAM, 2, TOWER, 0, Map.of("axis", "x")));
			all.add(new Placement(PLATE_BEAM, 3, TOWER, 0, Map.of("axis", "x")));
		}
		if (floored) {
			all.add(at(HAZARD, 0, 0, 2));
			all.add(at(PLATE_BLOCK, 1, 0, 2));
			all.add(new Placement(PLATE_SLAB, 2, 0, 2, Map.of("type", "bottom")));
			all.add(new Placement(PLATE_STAIRS, 3, 0, 2, Map.of("facing", "west", "half", "bottom",
					"shape", "straight")));
			all.add(at(HAZARD, 4, 0, 2));
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
