package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerStatus;
import dev.alaindustrial.block.entity.machine.MachineChannels;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.heat.HeatSource;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * L2 suite for the geothermal generator as a heat source for the machine standing on it (MOD-994): level 2, a
 * lava block's worth; heat only while lava really burns; with a full buffer it keeps burning for a machine on top
 * that waits for its heat and pauses otherwise; a change of the fire reaches the machine at once; the Thermal
 * Centrifuge takes none of it. The same rule as the fuel generator's (MOD-988), held by one component
 * ({@code FireHeat}); these scenarios pin it on the lava burner.
 *
 * <p>Ticks are driven by hand, generator first, as the MOD-988 scenarios do. Numbers come from the config, never
 * from the class under test.
 */
public final class GeothermalHeatScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(GeothermalHeatScenarios::burningGeothermalHeatsAtLevelTwo,
						"mod994_burning_geothermal_heats_at_level_two").ticks(20, 40),
				RosterEntry.of(GeothermalHeatScenarios::emptyGeothermalGivesNoHeat,
						"mod994_empty_geothermal_gives_no_heat").ticks(20, 40),
				RosterEntry.of(GeothermalHeatScenarios::fullBufferBurnsOnOnlyForAMachineThatWantsHeat,
						"mod994_full_buffer_burns_on_only_for_a_machine_that_wants_heat").ticks(20, 40),
				RosterEntry.of(GeothermalHeatScenarios::lavaStartAndStopReachTheMachineAtOnce,
						"mod994_lava_start_and_stop_reach_the_machine_at_once").ticks(20, 40),
				RosterEntry.of(GeothermalHeatScenarios::fullGeothermalDoesNotBurnForTheCentrifuge,
						"mod994_full_geothermal_does_not_burn_for_the_centrifuge").ticks(20, 40));

		private Roster() {}
	}

	private GeothermalHeatScenarios() {}

	private static final BlockPos MACHINE = new BlockPos(1, 2, 1);
	private static final BlockPos HEAT = MACHINE.below();
	/** Beside the centrifuge and only diagonal to the generator, so the signal reaches the machine alone. */
	private static final BlockPos SIGNAL = MACHINE.east();
	private static final long AMPLE_EU = 800L;

	/** Per-operation input price from {@code recipe/vulcanizing/rubber.json}, as the vulcanizer scenarios use. */
	private static final int RAW_RUBBER_PER_OPERATION = 1;
	private static final int SULFUR_PER_OPERATION = 4;

	/** The progress channel: on the geothermal generator it carries the burn reserve ({@code lavaTicks}). */
	private static final int DATA_PROGRESS = MachineChannels.PROGRESS.ordinal();

	private static GeothermalGeneratorBlockEntity geothermal(GameTestHelper helper, ItemStack filler) {
		GeothermalGeneratorBlockEntity geo = AlaGameTestHelper.place(helper, HEAT,
				ModContent.GEOTHERMAL_GENERATOR.get(), GeothermalGeneratorBlockEntity.class);
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, filler);
		return geo;
	}

	private static VulcanizerBlockEntity vulcanizer(GameTestHelper helper) {
		VulcanizerBlockEntity be = AlaGameTestHelper.place(helper, MACHINE, ModContent.VULCANIZER.get(),
				VulcanizerBlockEntity.class);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		return be;
	}

	private static void stock(VulcanizerBlockEntity be, int operations) {
		be.setItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT,
				new ItemStack(ModContent.RAW_RUBBER.get(), operations * RAW_RUBBER_PER_OPERATION));
		be.setItem(VulcanizerBlockEntity.SULFUR_SLOT,
				new ItemStack(ModContent.SULFUR_DUST.get(), operations * SULFUR_PER_OPERATION));
	}

	private static boolean holdsOperations(VulcanizerBlockEntity be, int operations) {
		return be.getItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT).getCount() == operations * RAW_RUBBER_PER_OPERATION
				&& be.getItem(VulcanizerBlockEntity.SULFUR_SLOT).getCount() == operations * SULFUR_PER_OPERATION;
	}

	private static int rubber(VulcanizerBlockEntity be) {
		ItemStack out = be.getItem(VulcanizerBlockEntity.OUTPUT_SLOT);
		return out.is(ModContent.RUBBER.get()) ? out.getCount() : (out.isEmpty() ? 0 : -1);
	}

	private static int operationTicks() {
		return MachineRates.duration(Config.vulcanizerDuration, Config.globalMachineSpeedMultiplier) + 2;
	}

	private static int lavaTicks(GeothermalGeneratorBlockEntity geo) {
		return geo.getDataAccess().get(DATA_PROGRESS);
	}

	/**
	 * One world tick of the pair, generator first. {@code fullBuffer} tops the generator's buffer up before its tick
	 * (the state a saturated line leaves it in, which pauses it); otherwise the buffer is emptied, so it has room.
	 */
	private static void tickPair(GeothermalGeneratorBlockEntity geo, VulcanizerBlockEntity be, GameTestHelper helper,
			boolean fullBuffer) {
		geo.getEnergyStorage().setAmountUntracked(fullBuffer ? geo.getEnergyStorage().getCapacity() : 0L);
		drive(geo, helper, 1);
		drive(be, helper, 1);
	}

	/**
	 * @implements MOD-994 - a vulcanizer on a burning geothermal generator makes a batch at x2 (level 2, a lava
	 *     block's worth) and names the generator as its heat source; the generator's output per burn tick stays the
	 *     configured one, and lava burns only on the ticks that make EU (measured by the statistics chip).
	 */
	public static void burningGeothermalHeatsAtLevelTwo(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = geothermal(helper, new ItemStack(Items.LAVA_BUCKET));
		geo.setItem(geo.upgradeSlotStart() + 2, new ItemStack(ModContent.STATS_CHIP.get()));
		VulcanizerBlockEntity be = vulcanizer(helper);
		stock(be, 2);

		tickPair(geo, be, helper, false); // the bucket goes into the burn reserve and the first tick burns
		int reserveAfterFirst = lavaTicks(geo);
		long generatedAfterFirst = geo.getEnergyStorage().getTotalEnergyGenerated();
		int ticks = operationTicks() + 3;
		for (int i = 0; i < ticks; i++) {
			tickPair(geo, be, helper, false);
		}

		helper.assertTrue(be.heatSource() == HeatSource.GEOTHERMAL_GENERATOR,
				"a burning geothermal generator below must be the heat source, got " + be.heatSource());
		helper.assertTrue(rubber(be) == HeatSource.GEOTHERMAL_GENERATOR.outputMultiplier()
						&& HeatSource.GEOTHERMAL_GENERATOR.outputMultiplier() == 2,
				"one batch on the geothermal generator must give 2 rubber, got " + rubber(be));
		helper.assertTrue(holdsOperations(be, 1), "one batch must consume exactly one operation's inputs");
		int burnt = reserveAfterFirst - lavaTicks(geo);
		helper.assertTrue(burnt == ticks, "with room every tick the lava burns once a tick: " + burnt + " of " + ticks);
		long perTick = Math.max(1, Math.round(GeneratorConfig.geothermalEuPerTick * Config.globalEuRateMultiplier));
		long generated = geo.getEnergyStorage().getTotalEnergyGenerated() - generatedAfterFirst;
		helper.assertTrue(generated == ticks * perTick, "the machine on top changed the output: generated "
				+ generated + " EU over " + ticks + " burn ticks, expected " + perTick + " EU each");
		helper.succeed();
	}

	/**
	 * @implements MOD-994 - a geothermal generator with no lava is no heat source: the stocked machine above waits on
	 *     heat and keeps its inputs. A lava bucket then lights it (the positive control: the same rig does heat).
	 */
	public static void emptyGeothermalGivesNoHeat(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = geothermal(helper, ItemStack.EMPTY);
		VulcanizerBlockEntity be = vulcanizer(helper);
		stock(be, 1);

		for (int i = 0; i < 20; i++) {
			tickPair(geo, be, helper, false);
		}
		helper.assertTrue(be.heatSource() == HeatSource.NONE && be.status() == VulcanizerStatus.NO_HEAT
						&& be.getDataAccess().get(DATA_PROGRESS) == 0 && holdsOperations(be, 1) && rubber(be) == 0,
				"an empty geothermal generator heated the machine: heat=" + be.heatSource() + " status="
						+ be.status() + " progress=" + be.getDataAccess().get(DATA_PROGRESS));

		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		for (int i = 0; i < 3; i++) {
			tickPair(geo, be, helper, false);
		}
		helper.assertTrue(be.heatSource() == HeatSource.GEOTHERMAL_GENERATOR && be.status() == VulcanizerStatus.READY,
				"lava must light the generator and heat the machine: heat=" + be.heatSource() + " status="
						+ be.status());
		helper.succeed();
	}

	/**
	 * @implements MOD-994 - the owner's rule, as on the fuel generator: with a FULL buffer the geothermal generator
	 *     still burns while the machine above wants its heat, every tick, so the heat never blinks and the batch
	 *     completes at x2; with the same full buffer and a machine that wants nothing (no inputs) it keeps the old
	 *     pause and burns no lava.
	 */
	public static void fullBufferBurnsOnOnlyForAMachineThatWantsHeat(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = geothermal(helper, new ItemStack(Items.LAVA_BUCKET));
		VulcanizerBlockEntity be = vulcanizer(helper);

		// One tick to settle: the bucket moves into the burn reserve, and a freshly placed machine reports READY
		// until its first tick, so one tick of lava may burn here. The pause is what follows.
		tickPair(geo, be, helper, true);
		int reserveBefore = lavaTicks(geo);
		for (int i = 0; i < 20; i++) {
			tickPair(geo, be, helper, true);
		}
		helper.assertTrue(reserveBefore > 0 && lavaTicks(geo) == reserveBefore && !geo.isBurning(),
				"a full geothermal generator under an idle machine must pause: reserve " + reserveBefore + " -> "
						+ lavaTicks(geo) + " burning=" + geo.isBurning());

		stock(be, 2);
		int unheatedTicks = 0;
		int ticks = operationTicks() + 3;
		for (int i = 0; i < ticks; i++) {
			tickPair(geo, be, helper, true);
			// Two ticks to start (the machine asks, the generator burns); from then on the heat must hold every tick.
			if (i >= 2 && be.heatSource() != HeatSource.GEOTHERMAL_GENERATOR) {
				unheatedTicks++;
			}
		}
		helper.assertTrue(unheatedTicks == 0, "heat blinked under a full geothermal generator: " + unheatedTicks
				+ " ticks without heat");
		helper.assertTrue(geo.isBurning() && lavaTicks(geo) < reserveBefore - operationTicks() + 3,
				"the lava must burn for the heat: reserve " + reserveBefore + " -> " + lavaTicks(geo));
		helper.assertTrue(rubber(be) == HeatSource.GEOTHERMAL_GENERATOR.outputMultiplier(),
				"the batch on a full geothermal generator must give 2 rubber, got " + rubber(be));
		helper.succeed();
	}

	/**
	 * @implements MOD-994 - the generator tells the machine above the moment the lava lights and the moment it runs
	 *     out, so neither waits out its idle sleep and no heat sticks after the lava is gone. Only the generator ticks
	 *     between the checks: what the machine knows there it was told.
	 */
	public static void lavaStartAndStopReachTheMachineAtOnce(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = geothermal(helper, ItemStack.EMPTY);
		VulcanizerBlockEntity be = vulcanizer(helper);
		stock(be, 1);
		drive(be, helper, 1); // NO_HEAT: the machine goes to sleep on it

		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		drive(geo, helper, 1); // the bucket goes into the reserve and burns on the same tick
		helper.assertTrue(be.heatSource() == HeatSource.GEOTHERMAL_GENERATOR,
				"lighting did not reach the machine above: heat=" + be.heatSource());
		for (int i = 0; i < 20; i++) {
			tickPair(geo, be, helper, false);
		}
		int guard = 0;
		while (lavaTicks(geo) > 0 && guard++ < 2 * GeneratorConfig.geothermalBurnTicks) {
			geo.getEnergyStorage().setAmountUntracked(0L);
			drive(geo, helper, 1);
		}
		drive(geo, helper, 1); // the first tick with nothing to burn
		helper.assertTrue(be.heatSource() == HeatSource.NONE,
				"the lava ran out but the machine above still sees heat: " + be.heatSource());
		drive(be, helper, 1);
		helper.assertTrue(be.status() == VulcanizerStatus.NO_HEAT && be.getDataAccess().get(DATA_PROGRESS) == 0
						&& holdsOperations(be, 1) && rubber(be) == 0,
				"a cold geothermal generator must leave the batch waiting with its inputs: status=" + be.status()
						+ " progress=" + be.getDataAccess().get(DATA_PROGRESS));
		helper.succeed();
	}

	/**
	 * @implements MOD-994 - the geothermal generator is no heat source for the Thermal Centrifuge (only the Electric
	 *     Heater is), so with a full buffer under a centrifuge that is spinning up, and so "waiting on heat", it keeps
	 *     its pause and burns no lava. The precondition is asserted first, or the scenario would pass on an idle rig.
	 */
	public static void fullGeothermalDoesNotBurnForTheCentrifuge(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = geothermal(helper, new ItemStack(Items.LAVA_BUCKET));
		geo.getEnergyStorage().setAmountUntracked(geo.getEnergyStorage().getCapacity());
		drive(geo, helper, 1); // the bucket moves into the burn reserve; a full buffer burns none of it
		int reserve = lavaTicks(geo);
		ThermalCentrifugeBlockEntity be = AlaGameTestHelper.place(helper, MACHINE,
				ModContent.THERMAL_CENTRIFUGE.get(), ThermalCentrifugeBlockEntity.class);
		be.setItem(ThermalCentrifugeBlockEntity.INPUT_SLOT, new ItemStack(ModContent.URANIUM_DUST.get(), 1));
		helper.setBlock(SIGNAL, Blocks.REDSTONE_BLOCK);
		be.getEnergyStorage().setAmountUntracked(Config.machineBuffer);
		drive(be, helper, 1);
		helper.assertTrue(be.isWaitingOnHeat(),
				"precondition: a signalled, stocked centrifuge must be waiting on heat, status=" + be.status());
		for (int i = 0; i < 20; i++) {
			geo.getEnergyStorage().setAmountUntracked(geo.getEnergyStorage().getCapacity());
			drive(geo, helper, 1);
			be.getEnergyStorage().setAmountUntracked(Config.machineBuffer);
			drive(be, helper, 1);
		}
		helper.assertTrue(reserve == GeneratorConfig.geothermalBurnTicks && lavaTicks(geo) == reserve
						&& !geo.isBurning(),
				"a full geothermal generator burned lava for a centrifuge it cannot heat: reserve " + reserve + " -> "
						+ lavaTicks(geo) + " burning=" + geo.isBurning());
		helper.succeed();
	}
}
