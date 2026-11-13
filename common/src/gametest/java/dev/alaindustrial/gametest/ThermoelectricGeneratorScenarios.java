package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.ThermoAxis;
import dev.alaindustrial.block.ThermoelectricGeneratorBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.ThermoelectricGeneratorBlockEntity;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Game tests of the thermoelectric generator (MOD-811), declared once and replayed by every loader: the rate
 * follows the temperature difference of each working axis and the tags behind it, an axis without a hot and a
 * cold end makes nothing, two axes add up, an unlit campfire is no heat, a datapack can add a material, the faces
 * show what works, the energy leaves through a cable, and the screen's rate carries the server's multiplier.
 *
 * <p>Every assertion is taken inside the callback that built the rig, with the generator driven by hand
 * ({@link AlaGameTestHelper#drive}): lava and water have no world tick in between to spread, and the counts are
 * exact. Every rig fits the 8×8×8 template (coordinates 0..7, walls included).
 */
public final class ThermoelectricGeneratorScenarios {

	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ThermoelectricGeneratorScenarios::lavaAndBlueIceBeatCampfireAndWater,
						"thermoelectric_lava_blue_ice_beats_campfire_water"),
				RosterEntry.of(ThermoelectricGeneratorScenarios::axisWithoutHotAndColdMakesNothing,
						"thermoelectric_axis_without_hot_and_cold_makes_nothing"),
				RosterEntry.of(ThermoelectricGeneratorScenarios::twoAxesAddUp, "thermoelectric_two_axes_add_up"),
				RosterEntry.of(ThermoelectricGeneratorScenarios::litStateGatesTheBlockNotItsWater,
						"thermoelectric_unlit_campfire_is_no_heat"),
				RosterEntry.of(ThermoelectricGeneratorScenarios::datapackAddsAMaterial,
						"thermoelectric_datapack_adds_a_material"),
				RosterEntry.of(ThermoelectricGeneratorScenarios::facesShowTheWorkingAxis,
						"thermoelectric_faces_show_the_working_axis"),
				RosterEntry.of(ThermoelectricGeneratorScenarios::energyLeavesThroughACable,
						"thermoelectric_energy_leaves_through_a_cable").ticks(40),
				RosterEntry.of(ThermoelectricGeneratorScenarios::rateChannelCarriesTheServerMultiplier,
						"thermoelectric_rate_channel_carries_server_multiplier"));

		private Roster() {
		}
	}

	/** Ticks a measurement runs: one neighbour read (on the first tick) and a whole number of EU at every rate. */
	private static final int RUN = 20;

	private ThermoelectricGeneratorScenarios() {
	}

	// --- rig helpers -------------------------------------------------------------------------------

	private static ThermoelectricGeneratorBlockEntity generator(GameTestHelper helper, BlockPos pos) {
		return AlaGameTestHelper.place(helper, pos, ModContent.THERMOELECTRIC_GENERATOR.get(),
				ThermoelectricGeneratorBlockEntity.class);
	}

	private static long stored(ThermoelectricGeneratorBlockEntity generator) {
		return generator.getEnergyStorage().getAmount();
	}

	/** A fluid source at {@code pos}, walled in with stone on every side but {@code open} so it cannot spread. */
	private static void containedFluid(GameTestHelper helper, BlockPos pos, Block fluid, Direction open) {
		for (Direction side : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST,
				Direction.EAST, Direction.UP}) {
			if (side != open) {
				helper.setBlock(pos.relative(side), Blocks.STONE);
			}
		}
		helper.setBlock(pos, fluid);
	}

	private static BlockState campfire(boolean lit, boolean waterlogged) {
		return Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, lit)
				.setValue(CampfireBlock.WATERLOGGED, waterlogged);
	}

	private static void expectStored(GameTestHelper helper, ThermoelectricGeneratorBlockEntity generator, long expected,
			String what) {
		long stored = stored(generator);
		if (stored != expected) {
			helper.fail(what + ": stored " + stored + " EU in " + RUN + " ticks, expected " + expected);
		}
	}

	// --- scenarios -----------------------------------------------------------------------------------

	/**
	 * Lava against blue ice is the best axis (8 degrees, 2 EU/t); a lit campfire against water the weakest (2
	 * degrees, 0.5 EU/t). Exact over 20 ticks: 40 EU and 10 EU — the levels and the block and fluid tags both
	 * count. Also checks the face temperatures the screen reads.
	 *
	 * @implements MOD-811 — output grows with the temperature difference; lava + blue ice beats campfire + water
	 */
	public static void lavaAndBlueIceBeatCampfireAndWater(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("globalEuRateMultiplier", 1.0f).set("thermoelectricMilliEuPerDegree", 250);
			BlockPos best = new BlockPos(2, 2, 3);
			containedFluid(helper, best.north(), Blocks.LAVA, Direction.SOUTH);
			helper.setBlock(best.south(), Blocks.BLUE_ICE);
			ThermoelectricGeneratorBlockEntity hot = generator(helper, best);

			BlockPos weak = new BlockPos(5, 2, 3);
			helper.setBlock(weak.north(), campfire(true, false));
			containedFluid(helper, weak.south(), Blocks.WATER, Direction.NORTH);
			ThermoelectricGeneratorBlockEntity mild = generator(helper, weak);

			AlaGameTestHelper.drive(hot, helper, RUN);
			AlaGameTestHelper.drive(mild, helper, RUN);
			expectStored(helper, hot, 2L * RUN, "lava + blue ice");
			expectStored(helper, mild, RUN / 2, "lit campfire + water");
			if (hot.faceTemperature(Direction.NORTH) != 4 || hot.faceTemperature(Direction.SOUTH) != -4) {
				helper.fail("lava / blue ice read as " + hot.faceTemperature(Direction.NORTH) + " / "
						+ hot.faceTemperature(Direction.SOUTH) + ", expected 4 / -4");
			}
			if (mild.faceTemperature(Direction.NORTH) != 1 || mild.faceTemperature(Direction.SOUTH) != -1) {
				helper.fail("campfire / water read as " + mild.faceTemperature(Direction.NORTH) + " / "
						+ mild.faceTemperature(Direction.SOUTH) + ", expected 1 / -1");
			}
		}
		helper.succeed();
	}

	/**
	 * Hot against nothing, hot against hot, cold against cold: no axis works, nothing is stored, the mode says why.
	 *
	 * @implements MOD-811 — the block is silent without a hot and a cold face across from each other
	 */
	public static void axisWithoutHotAndColdMakesNothing(GameTestHelper helper) {
		BlockPos lone = new BlockPos(1, 2, 2);
		helper.setBlock(lone.north(), Blocks.MAGMA_BLOCK);
		ThermoelectricGeneratorBlockEntity oneSide = generator(helper, lone);

		BlockPos twoHot = new BlockPos(4, 2, 2);
		helper.setBlock(twoHot.north(), Blocks.MAGMA_BLOCK);
		helper.setBlock(twoHot.south(), Blocks.MAGMA_BLOCK);
		ThermoelectricGeneratorBlockEntity hotHot = generator(helper, twoHot);

		BlockPos twoCold = new BlockPos(4, 2, 5);
		helper.setBlock(twoCold.west(), Blocks.BLUE_ICE);
		helper.setBlock(twoCold.east(), Blocks.PACKED_ICE);
		ThermoelectricGeneratorBlockEntity coldCold = generator(helper, twoCold);

		for (ThermoelectricGeneratorBlockEntity generator : List.of(oneSide, hotHot, coldCold)) {
			AlaGameTestHelper.drive(generator, helper, RUN);
		}
		expectStored(helper, oneSide, 0, "magma against nothing");
		expectStored(helper, hotHot, 0, "magma against magma");
		expectStored(helper, coldCold, 0, "blue ice against packed ice");
		for (ThermoelectricGeneratorBlockEntity generator : List.of(oneSide, hotHot, coldCold)) {
			int mode = generator.getDataAccess().get(ThermoelectricGeneratorBlockEntity.Channel.MAX_PROGRESS.ordinal());
			if (mode != ThermoelectricGeneratorBlockEntity.MODE_NO_PAIR) {
				helper.fail("an idle generator at " + generator.getBlockPos() + " reports mode " + mode);
			}
		}
		helper.succeed();
	}

	/**
	 * Magma over blue ice (7 degrees) and a lava cauldron against blue ice (8 degrees) on one block: 15 degrees,
	 * 3.75 EU/t — exactly 1500 EU over 400 ticks, so neither axis is dropped and no fraction is lost.
	 *
	 * @implements MOD-811 — two working axes add up; the fraction of an EU is banked, not lost
	 */
	public static void twoAxesAddUp(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("globalEuRateMultiplier", 1.0f).set("thermoelectricMilliEuPerDegree", 250);
			BlockPos pos = new BlockPos(3, 2, 3);
			helper.setBlock(pos.above(), Blocks.MAGMA_BLOCK);
			helper.setBlock(pos.below(), Blocks.BLUE_ICE);
			helper.setBlock(pos.north(), Blocks.LAVA_CAULDRON);
			helper.setBlock(pos.south(), Blocks.BLUE_ICE);
			ThermoelectricGeneratorBlockEntity generator = generator(helper, pos);
			AlaGameTestHelper.drive(generator, helper, 4);
			if (stored(generator) != 15) {
				helper.fail("two axes stored " + stored(generator) + " EU in 4 ticks, expected 15 (3.75 EU/t)");
			}
			AlaGameTestHelper.drive(generator, helper, 396);
			if (stored(generator) != 1500) {
				helper.fail("two axes stored " + stored(generator) + " EU in 400 ticks, expected 1500");
			}
			int axes = generator.getDataAccess().get(ThermoelectricGeneratorBlockEntity.Channel.PROGRESS.ordinal());
			if (axes != 2) {
				helper.fail("working axes channel says " + axes + ", expected 2");
			}
		}
		helper.succeed();
	}

	/**
	 * A campfire that is not lit is no heat; the water inside a waterlogged one still counts as cold — the block is
	 * read first, then the fluid of its cell.
	 *
	 * @implements MOD-811 — an unlit campfire gives nothing; a cell's fluid is read after its block
	 */
	public static void litStateGatesTheBlockNotItsWater(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("globalEuRateMultiplier", 1.0f).set("thermoelectricMilliEuPerDegree", 250);
			BlockPos dry = new BlockPos(2, 2, 3);
			helper.setBlock(dry.north(), campfire(false, false));
			helper.setBlock(dry.south(), Blocks.BLUE_ICE);
			ThermoelectricGeneratorBlockEntity unlit = generator(helper, dry);

			BlockPos wet = new BlockPos(5, 2, 3);
			helper.setBlock(wet.north(), campfire(false, true));
			helper.setBlock(wet.south(), Blocks.MAGMA_BLOCK);
			ThermoelectricGeneratorBlockEntity soaked = generator(helper, wet);

			AlaGameTestHelper.drive(unlit, helper, RUN);
			AlaGameTestHelper.drive(soaked, helper, RUN);
			expectStored(helper, unlit, 0, "unlit campfire + blue ice");
			expectStored(helper, soaked, RUN, "waterlogged unlit campfire + magma (1 + 3 degrees)");
		}
		helper.succeed();
	}

	/**
	 * Shroomlight is in no shipped tag; the gametest data adds it to {@code alaindustrial:thermoelectric/hot_1}
	 * (the file sits in each loader's gametest resources), and the generator takes it as heat level 1: 1 + 4
	 * degrees against blue ice, 1.25 EU/t, 25 EU in 20 ticks. Red if the table were hard-coded.
	 *
	 * @implements MOD-811 — the temperature table is data: a datapack adds a material without a rebuild
	 */
	public static void datapackAddsAMaterial(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("globalEuRateMultiplier", 1.0f).set("thermoelectricMilliEuPerDegree", 250);
			BlockPos pos = new BlockPos(3, 2, 3);
			helper.setBlock(pos.west(), Blocks.SHROOMLIGHT);
			helper.setBlock(pos.east(), Blocks.BLUE_ICE);
			ThermoelectricGeneratorBlockEntity generator = generator(helper, pos);
			AlaGameTestHelper.drive(generator, helper, RUN);
			expectStored(helper, generator, 25, "shroomlight (gametest datapack, hot 1) + blue ice");
		}
		helper.succeed();
	}

	/**
	 * The faces show the working axis: lava to the north and blue ice to the south paint the north-south axis hot
	 * at its negative (north) end, the other two stay off; take the ice away and the axis goes off within one
	 * neighbour read.
	 *
	 * @implements MOD-811 — the face of a working axis is coloured by the block state, an idle one is neutral
	 */
	public static void facesShowTheWorkingAxis(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		containedFluid(helper, pos.north(), Blocks.LAVA, Direction.SOUTH);
		helper.setBlock(pos.south(), Blocks.BLUE_ICE);
		ThermoelectricGeneratorBlockEntity generator = generator(helper, pos);
		AlaGameTestHelper.drive(generator, helper, 1);
		BlockState shown = helper.getBlockState(pos);
		if (shown.getValue(ThermoelectricGeneratorBlock.AXIS_Z) != ThermoAxis.HOT_NEGATIVE
				|| shown.getValue(ThermoelectricGeneratorBlock.AXIS_X) != ThermoAxis.OFF
				|| shown.getValue(ThermoelectricGeneratorBlock.AXIS_Y) != ThermoAxis.OFF) {
			helper.fail("lava north / blue ice south shows " + shown + ", expected axis_z=hot_negative only");
		}
		helper.setBlock(pos.south(), Blocks.AIR);
		AlaGameTestHelper.drive(generator, helper, ThermoelectricGeneratorBlockEntity.SCAN_INTERVAL);
		BlockState after = helper.getBlockState(pos);
		if (after.getValue(ThermoelectricGeneratorBlock.AXIS_Z) != ThermoAxis.OFF) {
			helper.fail("the ice is gone but the face still shows " + after);
		}
		helper.succeed();
	}

	/**
	 * Source, wire, sink: magma west and blue ice east (1.75 EU/t) feed two copper cables south to a battery box.
	 * The first cable must carry energy mid-run (ADR-001: a segment is a live buffer, not a teleport), the box must
	 * receive it, and nothing is created on the way: generator, cables and box together hold at most the 70 EU the
	 * block made in 40 ticks.
	 *
	 * @implements MOD-811 — the energy leaves through an ordinary cable on any free face
	 */
	public static void energyLeavesThroughACable(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("globalEuRateMultiplier", 1.0f).set("thermoelectricMilliEuPerDegree", 250);
			BlockPos pos = new BlockPos(2, 2, 2);
			helper.setBlock(pos.west(), Blocks.MAGMA_BLOCK);
			helper.setBlock(pos.east(), Blocks.BLUE_ICE);
			ThermoelectricGeneratorBlockEntity generator = generator(helper, pos);
			BlockPos cableA = pos.south();
			BlockPos cableB = cableA.south();
			BlockPos box = cableB.south();
			helper.setBlock(cableA, ModContent.COPPER_CABLE.get());
			helper.setBlock(cableB, ModContent.COPPER_CABLE.get());
			// FACING = NORTH puts the box's IN face toward the cable.
			helper.setBlock(box, ModContent.BATTERY_BOX.get().defaultBlockState()
					.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
			if (EnergyScenarioSupport.be(helper, box) instanceof BatteryBoxBlockEntity bb) {
				bb.getEnergyStorage().setAmountUntracked(0L);
			}
			long cablePeak = 0;
			for (int i = 0; i < 40; i++) {
				EnergyScenarioSupport.tick(helper, generator);
				EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, cableA));
				EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, cableB));
				NetworkManager.tickAll(helper.getLevel());
				cablePeak = Math.max(cablePeak, cableAmount(helper, cableA));
				EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, box));
			}
			long inBox = EnergyScenarioSupport.be(helper, box) instanceof BatteryBoxBlockEntity bb
					? bb.getEnergyStorage().getAmount() : -1L;
			long total = stored(generator) + cableAmount(helper, cableA) + cableAmount(helper, cableB) + inBox;
			if (cablePeak <= 0) {
				helper.fail("the cable beside the generator never held energy in 40 ticks");
			} else if (inBox <= 0) {
				helper.fail("the battery box at the end of the line got " + inBox + " EU");
			} else if (total > 70) {
				helper.fail("generator, cables and box hold " + total + " EU, more than the 70 EU made in 40 ticks");
			}
		}
		helper.succeed();
	}

	private static long cableAmount(GameTestHelper helper, BlockPos cable) {
		return EnergyScenarioSupport.be(helper, cable) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount()
				: 0L;
	}

	/**
	 * The screen's rate is computed on the server, after the global multiplier: 3.75 EU/t reads 375 hundredths, and
	 * with the multiplier at 2 it reads 750 — a client could not know the server's multiplier.
	 *
	 * @implements MOD-811 — the screen shows how much EU the block makes, as the server counts it
	 */
	public static void rateChannelCarriesTheServerMultiplier(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		helper.setBlock(pos.above(), Blocks.MAGMA_BLOCK);
		helper.setBlock(pos.below(), Blocks.BLUE_ICE);
		helper.setBlock(pos.north(), Blocks.LAVA_CAULDRON);
		helper.setBlock(pos.south(), Blocks.BLUE_ICE);
		ThermoelectricGeneratorBlockEntity generator = generator(helper, pos);
		int rateIndex = ThermoelectricGeneratorBlockEntity.Channel.RATE_CENTI.ordinal();
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("globalEuRateMultiplier", 1.0f).set("thermoelectricMilliEuPerDegree", 250);
			AlaGameTestHelper.drive(generator, helper, 1);
			int plain = generator.getDataAccess().get(rateIndex);
			if (plain != 375) {
				helper.fail("rate channel reads " + plain + " at multiplier 1, expected 375");
			}
			o.set("globalEuRateMultiplier", 2.0f);
			AlaGameTestHelper.drive(generator, helper, 1);
			int doubled = generator.getDataAccess().get(rateIndex);
			if (doubled != 750) {
				helper.fail("rate channel reads " + doubled + " at multiplier 2, expected 750");
			}
		}
		helper.succeed();
	}
}
