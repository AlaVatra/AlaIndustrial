package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.ThermoelectricGeneratorBlockEntity;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Game tests of the thermoelectric row (MOD-811): generators that touch hand their energy along to the one with a
 * cable, the piezo path's shared store ({@code SharedStoreGroup}) and nothing else. A row of four with one cable
 * hands on the sum, and the cable beside it carries it (ADR-001); a gap splits the row; two cables hand on only
 * what the row made, out of one shared budget; the member cap splits a long row; and a row with no consumer lets
 * its network fall asleep (ADR-007).
 *
 * <p>Every generator here has magma to the north and blue ice to the south: 3 + 4 degrees, 1.75 EU/t, so 40 driven
 * ticks make exactly 70 EU per generator. Everything is driven by hand inside the callback that built the rig, so
 * the whole run is ONE game tick: the row's per-tick budget is spent once for the run. The scenarios that are not
 * about the budget lift it out of the way; {@link #twoOutletsShareOneBudget} relies on exactly that. Every rig fits
 * the 8×8×8 template (coordinates 0..7).
 */
public final class ThermoelectricRowScenarios {

	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ThermoelectricRowScenarios::rowHandsTheSumToOneCable,
						"thermoelectric_row_hands_the_sum_to_one_cable"),
				RosterEntry.of(ThermoelectricRowScenarios::gapSplitsTheRow, "thermoelectric_row_gap_splits_the_row"),
				RosterEntry.of(ThermoelectricRowScenarios::twoOutletsHandOnOnlyWhatTheRowMade,
						"thermoelectric_row_two_cables_do_not_double"),
				RosterEntry.of(ThermoelectricRowScenarios::twoOutletsShareOneBudget,
						"thermoelectric_row_two_outlets_share_one_budget"),
				RosterEntry.of(ThermoelectricRowScenarios::memberCapSplitsALongRow,
						"thermoelectric_row_member_cap_splits_a_long_row"),
				RosterEntry.of(ThermoelectricRowScenarios::rowWithoutConsumerLetsTheNetworkSleep,
						"thermoelectric_row_without_consumer_lets_network_sleep"));

		private Roster() {
		}
	}

	/** Ticks a run drives: 40 × 1.75 EU/t is a whole 70 EU per generator. */
	private static final int RUN = 40;
	/** EU one generator makes in {@link #RUN} ticks. */
	private static final long PER_GENERATOR = 70;
	/** The layer and the row line every rig stands on: magma at z-1, blue ice at z+1. */
	private static final int Y = 2;
	private static final int Z = 2;
	/** A budget no run here can reach, for the scenarios that are not about the budget. */
	private static final int NO_BUDGET_LIMIT = 1_000_000;

	private ThermoelectricRowScenarios() {
	}

	// --- rig helpers -------------------------------------------------------------------------------

	/** A generator at {@code (x, Y, Z)} with magma to the north and blue ice to the south. */
	private static ThermoelectricGeneratorBlockEntity generator(GameTestHelper helper, int x) {
		BlockPos pos = new BlockPos(x, Y, Z);
		helper.setBlock(pos.north(), Blocks.MAGMA_BLOCK);
		helper.setBlock(pos.south(), Blocks.BLUE_ICE);
		return AlaGameTestHelper.place(helper, pos, ModContent.THERMOELECTRIC_GENERATOR.get(),
				ThermoelectricGeneratorBlockEntity.class);
	}

	private static List<ThermoelectricGeneratorBlockEntity> row(GameTestHelper helper, int... xs) {
		List<ThermoelectricGeneratorBlockEntity> row = new ArrayList<>();
		for (int x : xs) {
			row.add(generator(helper, x));
		}
		return row;
	}

	/**
	 * An outlet line at column {@code x}: a copper cable beside the row's end at {@code (x, Y, Z)}, a second one
	 * south of it and a battery box south of that, its IN face toward the cable. Returns the first cable's position.
	 */
	private static BlockPos cableToBox(GameTestHelper helper, int x) {
		BlockPos first = new BlockPos(x, Y, Z);
		helper.setBlock(first, ModContent.COPPER_CABLE.get());
		helper.setBlock(first.south(), ModContent.COPPER_CABLE.get());
		BlockPos box = first.south(2);
		helper.setBlock(box, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		if (EnergyScenarioSupport.be(helper, box) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().setAmountUntracked(0L);
		}
		return first;
	}

	/** One driven tick of the rig: the generators in order, the outlet lines' cables, the networks, the boxes. */
	private static void tickRig(GameTestHelper helper, List<ThermoelectricGeneratorBlockEntity> generators,
			List<BlockPos> lines) {
		for (ThermoelectricGeneratorBlockEntity generator : generators) {
			EnergyScenarioSupport.tick(helper, generator);
		}
		for (BlockPos line : lines) {
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, line));
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, line.south()));
		}
		NetworkManager.tickAll(helper.getLevel());
		for (BlockPos line : lines) {
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, line.south(2)));
		}
	}

	private static long cableAmount(GameTestHelper helper, BlockPos cable) {
		return EnergyScenarioSupport.be(helper, cable) instanceof CableBlockEntity c
				? c.getEnergyStorage().getAmount() : 0L;
	}

	private static long boxAmount(GameTestHelper helper, BlockPos firstCable) {
		return EnergyScenarioSupport.be(helper, firstCable.south(2)) instanceof BatteryBoxBlockEntity bb
				? bb.getEnergyStorage().getAmount() : -1L;
	}

	/** What left the row down one outlet line: both cables and the box. */
	private static long downLine(GameTestHelper helper, BlockPos firstCable) {
		return cableAmount(helper, firstCable) + cableAmount(helper, firstCable.south())
				+ boxAmount(helper, firstCable);
	}

	private static long stored(ThermoelectricGeneratorBlockEntity generator) {
		return generator.getEnergyStorage().getAmount();
	}

	private static long storedSum(List<ThermoelectricGeneratorBlockEntity> generators) {
		long sum = 0;
		for (ThermoelectricGeneratorBlockEntity generator : generators) {
			sum += stored(generator);
		}
		return sum;
	}

	private static void baseKnobs(ConfigOverrides o) {
		o.set("globalEuRateMultiplier", 1.0f).set("thermoelectricMilliEuPerDegree", 250)
				.set("thermoelectricRowMaxGenerators", 8);
	}

	// --- scenarios -----------------------------------------------------------------------------------

	/**
	 * Four generators in a row and ONE cable beside the last: the row hands on the sum of all four, not the last
	 * one's 70 EU. The cable beside the row carries energy mid-run (ADR-001), the box at the end of the line gets
	 * it, the three members without a cable are drained, and nothing is created or lost: generators, cables and
	 * box together hold exactly the 280 EU the four made.
	 *
	 * @implements MOD-811 — touching generators hand their energy along the row to the one with a cable
	 */
	public static void rowHandsTheSumToOneCable(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			baseKnobs(o);
			o.set("thermoelectricRowOutputPerTick", NO_BUDGET_LIMIT);
			List<ThermoelectricGeneratorBlockEntity> row = row(helper, 1, 2, 3, 4);
			BlockPos line = cableToBox(helper, 5);
			long cablePeak = 0;
			for (int i = 0; i < RUN; i++) {
				tickRig(helper, row, List.of(line));
				cablePeak = Math.max(cablePeak, cableAmount(helper, line));
			}
			long made = PER_GENERATOR * row.size();
			long total = storedSum(row) + downLine(helper, line);
			ThermoelectricGeneratorBlockEntity outlet = row.get(3);
			if (cablePeak <= 0) {
				helper.fail("the cable beside the row never held energy in " + RUN + " ticks");
			} else if (boxAmount(helper, line) <= 0) {
				helper.fail("the battery box at the end of the line got " + boxAmount(helper, line) + " EU");
			} else if (total != made) {
				helper.fail("row, cables and box hold " + total + " EU, expected exactly the " + made + " EU made");
			} else if (downLine(helper, line) <= PER_GENERATOR) {
				helper.fail("only " + downLine(helper, line) + " EU left the row: no more than the outlet's own "
						+ PER_GENERATOR + ", the members' energy never moved");
			} else if (storedSum(row.subList(0, 3)) >= PER_GENERATOR) {
				helper.fail("the three members without a cable still hold " + storedSum(row.subList(0, 3)) + " EU");
			} else if (outlet.rowSize() != 4 || outlet.rowOutlets() != 1 || !outlet.isRowOutlet()) {
				helper.fail("the outlet sees a row of " + outlet.rowSize() + " with " + outlet.rowOutlets()
						+ " outlets, expected 4 with 1");
			}
		}
		helper.succeed();
	}

	/**
	 * Two pairs with an empty cell between them and a cable beside the near pair only: the near pair hands its
	 * energy on, the far pair is a row of its own with no outlet and keeps every EU it made.
	 *
	 * @implements MOD-811 — a gap breaks the row: generators that do not touch share nothing
	 */
	public static void gapSplitsTheRow(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			baseKnobs(o);
			o.set("thermoelectricRowOutputPerTick", NO_BUDGET_LIMIT);
			List<ThermoelectricGeneratorBlockEntity> far = row(helper, 1, 2);
			List<ThermoelectricGeneratorBlockEntity> near = row(helper, 4, 5);
			BlockPos line = cableToBox(helper, 6);
			List<ThermoelectricGeneratorBlockEntity> all = new ArrayList<>(far);
			all.addAll(near);
			for (int i = 0; i < RUN; i++) {
				tickRig(helper, all, List.of(line));
			}
			for (ThermoelectricGeneratorBlockEntity generator : far) {
				if (stored(generator) != PER_GENERATOR) {
					helper.fail("the far generator at " + generator.getBlockPos() + " holds " + stored(generator)
							+ " EU, expected all its " + PER_GENERATOR + ": energy crossed the gap");
				}
				if (generator.rowSize() != 2 || generator.rowOutlets() != 0) {
					helper.fail("the far pair sees a row of " + generator.rowSize() + " with " + generator.rowOutlets()
							+ " outlets, expected 2 with none");
				}
			}
			if (near.get(1).rowSize() != 2 || downLine(helper, line) <= PER_GENERATOR) {
				helper.fail("the near pair (row of " + near.get(1).rowSize() + ") handed on only "
						+ downLine(helper, line) + " EU");
			}
		}
		helper.succeed();
	}

	/**
	 * Four generators with a cable at each end, each line to its own box: both ends are outlets, and the two lines
	 * together got what the row made and not a single EU more — two cables do not double the row (ADR-027: the
	 * energy is counted once, at the member that made it).
	 *
	 * @implements MOD-811 — two cables on one row hand on only what the row made
	 */
	public static void twoOutletsHandOnOnlyWhatTheRowMade(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			baseKnobs(o);
			o.set("thermoelectricRowOutputPerTick", NO_BUDGET_LIMIT);
			List<ThermoelectricGeneratorBlockEntity> row = row(helper, 2, 3, 4, 5);
			BlockPos west = cableToBox(helper, 1);
			BlockPos east = cableToBox(helper, 6);
			for (int i = 0; i < RUN; i++) {
				tickRig(helper, row, List.of(west, east));
			}
			long made = PER_GENERATOR * row.size();
			long total = storedSum(row) + downLine(helper, west) + downLine(helper, east);
			if (row.get(0).rowOutlets() != 2) {
				helper.fail("the row sees " + row.get(0).rowOutlets() + " outlets, expected 2");
			} else if (boxAmount(helper, west) <= 0 || boxAmount(helper, east) <= 0) {
				helper.fail("a box got nothing: west " + boxAmount(helper, west) + ", east " + boxAmount(helper, east));
			} else if (total != made) {
				helper.fail("row, both lines and boxes hold " + total + " EU, expected exactly the " + made + " made");
			}
		}
		helper.succeed();
	}

	/**
	 * The same row of four with a cable at each end and a row budget of 2 EU per tick. The run is one game tick, so
	 * the row may gather 2 EU into its outlets for the whole run, both outlets together: the two middle generators,
	 * which no cable touches, keep their 140 EU minus exactly those 2. An outlet with a budget of its own would take
	 * 4.
	 *
	 * @implements MOD-811 — the row's throughput is one budget, shared by all its outlets
	 */
	public static void twoOutletsShareOneBudget(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			baseKnobs(o);
			o.set("thermoelectricRowOutputPerTick", 2);
			List<ThermoelectricGeneratorBlockEntity> row = row(helper, 2, 3, 4, 5);
			BlockPos west = cableToBox(helper, 1);
			BlockPos east = cableToBox(helper, 6);
			for (int i = 0; i < RUN; i++) {
				tickRig(helper, row, List.of(west, east));
			}
			long middle = storedSum(row.subList(1, 3));
			if (middle != 2 * PER_GENERATOR - 2) {
				helper.fail("the two middle generators hold " + middle + " EU, expected " + (2 * PER_GENERATOR - 2)
						+ ": the row's outlets gathered " + (2 * PER_GENERATOR - middle) + " EU on a 2 EU budget");
			}
		}
		helper.succeed();
	}

	/**
	 * Four touching generators with a row cap of 2 and a cable beside the last: the walk from the first one takes
	 * the first two, the last two are a row of their own, and only that one has the outlet — the first pair keeps
	 * every EU it made.
	 *
	 * @implements MOD-811 — the row length is capped by a config knob; a longer row splits
	 */
	public static void memberCapSplitsALongRow(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			baseKnobs(o);
			o.set("thermoelectricRowOutputPerTick", NO_BUDGET_LIMIT).set("thermoelectricRowMaxGenerators", 2);
			List<ThermoelectricGeneratorBlockEntity> row = row(helper, 1, 2, 3, 4);
			BlockPos line = cableToBox(helper, 5);
			for (int i = 0; i < RUN; i++) {
				tickRig(helper, row, List.of(line));
			}
			for (ThermoelectricGeneratorBlockEntity generator : row) {
				if (generator.rowSize() != 2) {
					helper.fail("the generator at " + generator.getBlockPos() + " sees a row of " + generator.rowSize()
							+ ", expected 2 under a cap of 2");
				}
			}
			if (storedSum(row.subList(0, 2)) != 2 * PER_GENERATOR) {
				helper.fail("the first pair holds " + storedSum(row.subList(0, 2)) + " EU, expected all its "
						+ 2 * PER_GENERATOR + ": the cap did not split the row");
			}
		}
		helper.succeed();
	}

	/**
	 * A row of two with one cable and nothing to take the energy: the generators fill the cable, and then the
	 * network goes to sleep (ADR-007) instead of ticking forever. The row's energy that did not fit stays in the
	 * generators.
	 *
	 * @implements MOD-811 — a row with no consumer does not keep its network awake
	 */
	public static void rowWithoutConsumerLetsTheNetworkSleep(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			baseKnobs(o);
			o.set("thermoelectricRowOutputPerTick", NO_BUDGET_LIMIT);
			List<ThermoelectricGeneratorBlockEntity> row = row(helper, 2, 3);
			BlockPos cable = new BlockPos(4, Y, Z);
			helper.setBlock(cable, ModContent.COPPER_CABLE.get());
			for (int i = 0; i < RUN; i++) {
				for (ThermoelectricGeneratorBlockEntity generator : row) {
					EnergyScenarioSupport.tick(helper, generator);
				}
				EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, cable));
				NetworkManager.tickAll(helper.getLevel());
			}
			EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(cable));
			if (net == null) {
				helper.fail("no energy network formed on the row's cable");
			} else if (cableAmount(helper, cable) <= 0) {
				helper.fail("the row never filled its cable");
			} else if (net.isAwake()) {
				helper.fail("the row's network is awake with a full cable and no consumer: it must sleep");
			} else if (storedSum(row) <= 0) {
				helper.fail("the row holds nothing: the full line kept drawing");
			}
		}
		helper.succeed();
	}
}
