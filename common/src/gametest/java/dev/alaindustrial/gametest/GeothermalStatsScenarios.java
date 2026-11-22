package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.machine.MachineChannels;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.environment.LavaBurnTally;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

/**
 * The geothermal generator's statistics chip counts the lava it burnt and the EU it gave (MOD-999), on every
 * loader: nothing without the chip, the lava and the generated EU together with it, nothing on a pause with a full
 * buffer, and the count kept across a reload.
 */
public final class GeothermalStatsScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(GeothermalStatsScenarios::lavaBurntCountsOnlyWithTheChip,
								"mod999_lava_burnt_counts_only_with_the_chip")
						.ticks(20, 60));

		private Roster() {}
	}

	private GeothermalStatsScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/**
	 * @implements MOD-999 — with the chip the generator counts the lava it burnt, in whole mB never rounded up,
	 *     and the EU that lava really put in the buffer, equal to the "Generated" counter; a full buffer's pause
	 *     counts nothing; without the chip nothing is counted, saved or sent; an older save starts from zero.
	 */
	public static void lavaBurntCountsOnlyWithTheChip(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = AlaGameTestHelper.place(helper, POS,
				ModContent.GEOTHERMAL_GENERATOR.get(), GeothermalGeneratorBlockEntity.class);
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		GeothermalGeneratorMenu menu = new GeothermalGeneratorMenu(1, player.getInventory(), geo,
				ContainerLevelAccess.create(helper.getLevel(), geo.getBlockPos()));
		var registries = helper.getLevel().registryAccess();
		long now = helper.getLevel().getGameTime();
		int burnTicks = GeneratorConfig.geothermalBurnTicks;
		long draw = GeneratorConfig.geothermalEuPerTick;
		fillTank(geo, 3);

		// No chip: lava burns, nothing is counted, saved or put in the snapshot.
		drawEachTick(geo, helper, draw, 100);
		helper.assertTrue(!geo.hasStatsChip(), "the rig needs a generator without the statistics chip");
		helper.assertTrue(geo.fluidTank.amount == 0L && reserve(geo) == 3L * burnTicks - 100L,
				"the no-chip phase must load the three buckets and burn 100 ticks, the reserve reads " + reserve(geo));
		helper.assertTrue(geo.lavaBurntTally().isEmpty(), "no chip, yet the generator counted "
				+ geo.lavaBurntTally().shown());
		CompoundTag bare = geo.saveCustomOnly(registries);
		helper.assertTrue(!bare.contains("StatsLavaBurntMb") && !bare.contains("StatsLavaEu"),
				"no chip, yet the save carries a burnt-lava key");
		helper.assertTrue(menu.statsSnapshot(geo, now).lavaBurnt().isEmpty(),
				"no chip, yet the snapshot carries burnt lava");

		// The chip: the line draws the full output, stops until the buffer is full and the burn pauses, then draws
		// again. Reserve ticks burnt = reserve now + buckets loaded since - reserve at the end.
		geo.setItem(geo.upgradeSlotStart() + 2, new ItemStack(ModContent.STATS_CHIP.get()));
		long reserveStart = reserve(geo);
		long tankStart = geo.fluidTank.amount;
		drawEachTick(geo, helper, draw, 700);
		drawEachTick(geo, helper, 0L, 400);
		helper.assertTrue(geo.getEnergyStorage().getAmount() == geo.getEnergyStorage().getCapacity(),
				"the pause needs a full buffer, the rig has " + geo.getEnergyStorage().getAmount());
		LavaBurnTally.Shown beforePause = geo.lavaBurntTally().shown();
		long generatedBeforePause = geo.getEnergyStorage().getTotalEnergyGenerated();
		drawEachTick(geo, helper, 0L, 100);
		helper.assertTrue(beforePause.equals(geo.lavaBurntTally().shown()),
				"the pause counted lava: " + beforePause + " then " + geo.lavaBurntTally().shown());
		helper.assertTrue(generatedBeforePause == geo.getEnergyStorage().getTotalEnergyGenerated(),
				"the pause generated EU");
		drawEachTick(geo, helper, draw, 900);

		long loaded = (tankStart - geo.fluidTank.amount) / FluidAmounts.BUCKET * burnTicks;
		long burnt = reserveStart + loaded - reserve(geo);
		long expectedMb = burnt * LavaBurnTally.MB_PER_BUCKET / burnTicks;
		helper.assertTrue(burnt > burnTicks, "the rig must burn more than a bucket, it burnt " + burnt + " ticks");
		helper.assertTrue(geo.lavaBurntTally().mb() == expectedMb,
				"counted " + geo.lavaBurntTally().mb() + " mB, the reserve lost " + burnt + " ticks = " + expectedMb
						+ " mB");
		long generated = geo.getEnergyStorage().getTotalEnergyGenerated();
		helper.assertTrue(geo.lavaBurntTally().eu() == generated,
				"the lava gave " + geo.lavaBurntTally().eu() + " EU, the Generated counter reads " + generated);
		LavaBurnTally.Shown shown = menu.statsSnapshot(geo, now + 1).lavaBurnt().orElse(null);
		helper.assertTrue(geo.lavaBurntTally().shown().equals(shown), "the snapshot's burnt lava reads " + shown);
		helper.assertTrue(shown != null && shown.euPerBucket() == generated * 1000L / expectedMb,
				"EU per bucket reads " + (shown == null ? "nothing" : shown.euPerBucket()));

		// Save and load: the tally comes back; a save without the keys (before MOD-999) starts from zero.
		CompoundTag tag = geo.saveCustomOnly(registries);
		helper.assertTrue(tag.contains("StatsLavaBurntMb") && tag.contains("StatsLavaEu"),
				"a chipped generator that burnt lava saves no tally");
		GeothermalGeneratorBlockEntity restored = new GeothermalGeneratorBlockEntity(geo.getBlockPos(),
				helper.getLevel().getBlockState(geo.getBlockPos()));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		helper.assertTrue(restored.lavaBurntTally().shown().equals(geo.lavaBurntTally().shown()),
				"after a reload the tally reads " + restored.lavaBurntTally().shown());
		CompoundTag old = tag.copy();
		old.remove("StatsLavaBurntMb");
		old.remove("StatsLavaEu");
		GeothermalGeneratorBlockEntity fromOld = new GeothermalGeneratorBlockEntity(geo.getBlockPos(),
				helper.getLevel().getBlockState(geo.getBlockPos()));
		fromOld.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, old));
		helper.assertTrue(fromOld.lavaBurntTally().isEmpty(), "an old save loads with a tally "
				+ fromOld.lavaBurntTally().shown());
		helper.succeed();
	}

	/** Put {@code buckets} of lava in the tank. */
	private static void fillTank(GeothermalGeneratorBlockEntity geo, int buckets) {
		geo.fluidTank.fluid = FluidHolder.of(Fluids.LAVA);
		geo.fluidTank.amount = buckets * FluidAmounts.BUCKET;
	}

	/** The burn reserve in ticks: the generator's progress channel. */
	private static long reserve(GeothermalGeneratorBlockEntity geo) {
		return geo.getDataAccess().get(MachineChannels.PROGRESS.ordinal());
	}

	/** Tick the generator {@code ticks} times, drawing {@code eu} out of it after each tick as a line would. */
	private static void drawEachTick(GeothermalGeneratorBlockEntity geo, GameTestHelper helper, long eu, int ticks) {
		for (int i = 0; i < ticks; i++) {
			drive(geo, helper, 1);
			if (eu > 0L) {
				EnergyTransactions.get().runCommitting(txn -> geo.getEnergyStorage().extract(eu, txn));
			}
		}
	}
}
