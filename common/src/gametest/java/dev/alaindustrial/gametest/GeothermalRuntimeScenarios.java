package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.machine.MachineChannels;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.environment.DrawMeter;
import dev.alaindustrial.core.environment.FuelRuntime;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * L2 suite for the geothermal generator's plain-words readout (MOD-1000): how long its lava lasts at the line's
 * real draw and the load against the full output, worked out by the fuel generator's rule (MOD-990) and only with
 * the statistics chip; and the burn rule that span assumes, a light load burning only the lava it draws.
 *
 * <p>Ticks are driven by hand with the draw taken after each one, as a line would. Expected numbers come from the
 * config, never from the class under test.
 */
public final class GeothermalRuntimeScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(GeothermalRuntimeScenarios::lavaRuntimeFollowsTheChipAndTheDraw,
						"mod1000_lava_runtime_follows_the_chip_and_the_draw").ticks(20, 60),
				RosterEntry.of(GeothermalRuntimeScenarios::lightLoadBurnsOnlyTheLavaItDraws,
						"mod1000_light_load_burns_only_the_lava_it_draws").ticks(20, 60));

		private Roster() {}
	}

	private GeothermalRuntimeScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	private static final BlockPos POS_EMPTY = new BlockPos(4, 2, 1);

	/** The progress channel: on the geothermal generator it carries the burn reserve ({@code lavaTicks}). */
	private static final int DATA_PROGRESS = MachineChannels.PROGRESS.ordinal();

	private static GeothermalGeneratorBlockEntity place(GameTestHelper helper, BlockPos pos) {
		return AlaGameTestHelper.place(helper, pos, ModContent.GEOTHERMAL_GENERATOR.get(),
				GeothermalGeneratorBlockEntity.class);
	}

	/** What one burn tick puts in the buffer, from the config: the knob after the global multiplier. */
	private static int euPerTick() {
		return Math.max(1, Math.round(GeneratorConfig.geothermalEuPerTick * Config.globalEuRateMultiplier));
	}

	private static void drawEachTick(GeothermalGeneratorBlockEntity geo, GameTestHelper helper, long eu, int ticks) {
		for (int i = 0; i < ticks; i++) {
			drive(geo, helper, 1);
			if (eu > 0L) {
				EnergyTransactions.get().runCommitting(txn -> geo.getEnergyStorage().extract(eu, txn));
			}
		}
	}

	/**
	 * MOD-1000: the lava span and the load are worked out only with the statistics chip, and then they follow the
	 * line's real draw. Without the chip nothing is measured and the menu's snapshot has no lava line. With it, a
	 * line drawing 4 EU/t reads "4 of 16 EU/t" and the span is every EU still to come over 4 EU/t: the burn
	 * reserve, the WHOLE buckets in the tank (a part bucket never burns) and the bucket waiting in the intake slot,
	 * plus the buffer. A line that stops drawing reads "waiting"; a generator with nothing at all reads "no lava".
	 *
	 * @implements MOD-1000-RUNTIME — lava span and load from the real draw, only with the statistics chip
	 * @covers MOD-1000
	 */
	public static void lavaRuntimeFollowsTheChipAndTheDraw(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper, POS);
		geo.fluidTank.fluid = FluidHolder.of(Fluids.LAVA);
		geo.fluidTank.amount = 10 * FluidAmounts.BUCKET;
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		GeothermalGeneratorMenu menu = new GeothermalGeneratorMenu(1, player.getInventory(), geo,
				ContainerLevelAccess.create(helper.getLevel(), geo.getBlockPos()));
		long now = helper.getLevel().getGameTime();

		// No chip: a line draws, and nothing is measured or worked out.
		drawEachTick(geo, helper, 4L, 100);
		helper.assertTrue(!geo.hasStatsChip(), "the rig needs a generator without the statistics chip");
		helper.assertTrue(geo.fuelReadout() == null, "no chip, yet the generator worked out " + geo.fuelReadout());
		helper.assertTrue(menu.statsSnapshot(geo, now).fuel().isEmpty(), "no chip, yet the snapshot has a lava line");

		// The chip: 4 EU/t drawn, from a window that starts when the chip went in. The reserve took the whole tank
		// and is nearly full by now, so a bucket in the slot and five and a half buckets in the tank stay put.
		helper.assertTrue(geo.fluidTank.amount == 0L, "the rig expects the reserve to have taken the whole tank");
		geo.setItem(geo.upgradeSlotStart() + 2, new ItemStack(ModContent.STATS_CHIP.get()));
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		geo.fluidTank.amount = 5 * FluidAmounts.BUCKET + FluidAmounts.BUCKET / 2;
		drawEachTick(geo, helper, 4L, 200);
		FuelRuntime.Readout lava = menu.statsSnapshot(geo, now + 1).fuel().orElse(null);
		helper.assertTrue(lava != null, "a chipped generator's snapshot carries no lava line");
		helper.assertTrue(lava.kind() == FuelRuntime.Kind.LAVA, "the geothermal readout must speak of lava");
		helper.assertTrue(lava.euPerTick() == euPerTick(), "full output " + lava.euPerTick() + ", expected "
				+ euPerTick());
		helper.assertTrue(lava.drawTicks() > 0 && lava.drawEu() == 4L * lava.drawTicks(),
				"a 4 EU/t line reads " + lava.drawEu() + " EU over " + lava.drawTicks() + " ticks");
		helper.assertTrue(geo.getItem(GeothermalGeneratorBlockEntity.INPUT_SLOT).is(Items.LAVA_BUCKET)
						&& geo.fluidTank.amount == 5 * FluidAmounts.BUCKET + FluidAmounts.BUCKET / 2,
				"the rig needs the bucket still in the slot and the tank untouched, tank " + geo.fluidTank.amount);
		long reserve = geo.getDataAccess().get(DATA_PROGRESS);
		long wholeBuckets = geo.fluidTank.amount / FluidAmounts.BUCKET;
		long lavaTicks = reserve + (wholeBuckets + 1L) * GeneratorConfig.geothermalBurnTicks;
		long expected = (lavaTicks * euPerTick() + geo.getEnergyStorage().getAmount()) / 4L;
		helper.assertTrue(Math.abs(lava.ticksLeft() - expected) <= 1L, "at 4 EU/t the lava lasts " + expected
				+ " ticks (reserve " + reserve + ", " + wholeBuckets + " whole buckets in the tank, 1 in the slot),"
				+ " the readout says " + lava.ticksLeft());

		// The line stops drawing: once the window has rolled past the old load, the lava waits.
		drawEachTick(geo, helper, 0L, DrawMeter.SAMPLE_EVERY * DrawMeter.SAMPLES + DrawMeter.SAMPLE_EVERY);
		FuelRuntime.Readout idle = geo.fuelReadout();
		helper.assertTrue(idle != null && idle.ticksLeft() == FuelRuntime.WAITING,
				"a generator nothing draws from reads " + idle + ", not waiting");

		// Nothing in it at all: no lava, not "waiting".
		GeothermalGeneratorBlockEntity empty = place(helper, POS_EMPTY);
		empty.setItem(empty.upgradeSlotStart() + 2, new ItemStack(ModContent.STATS_CHIP.get()));
		drawEachTick(empty, helper, 0L, 2);
		FuelRuntime.Readout none = empty.fuelReadout();
		helper.assertTrue(none != null && none.ticksLeft() == FuelRuntime.EMPTY && none.kind() == FuelRuntime.Kind.LAVA,
				"an empty geothermal generator reads " + none + ", not \"no lava\"");
		helper.succeed();
	}

	/**
	 * A light load burns only the lava it draws (MOD-1000, the fuel generator's MOD-986 rule): a buffer with less
	 * room than one burn tick makes holds the burn, and a line drawing a few EU a tick from a nearly full buffer
	 * costs burn ticks worth what it drew, not one burn tick per game tick with the rest thrown away. The span the
	 * readout shows assumes exactly this.
	 *
	 * @covers R-NRG-11
	 * @covers MOD-1000
	 */
	public static void lightLoadBurnsOnlyTheLavaItDraws(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper, POS);
		int euPerTick = euPerTick();
		long cap = geo.getEnergyStorage().getCapacity();
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		drive(geo, helper, 1); // the bucket goes into the burn reserve

		// Room for less than one burn tick: the burn holds.
		geo.getEnergyStorage().setAmountUntracked(cap - (euPerTick - 1));
		int held1 = geo.getDataAccess().get(DATA_PROGRESS);
		drive(geo, helper, 3);
		int held2 = geo.getDataAccess().get(DATA_PROGRESS);
		helper.assertTrue(held1 > 0 && held1 == held2, "a gap smaller than one burn tick must hold the burn: "
				+ held1 + " -> " + held2);

		// A light draw from a nearly full buffer: what burns matches what the line took, within one burn tick.
		long lightDraw = Math.max(1, euPerTick / 4);
		int ticks = 60;
		int before = geo.getDataAccess().get(DATA_PROGRESS);
		drawEachTick(geo, helper, lightDraw, ticks);
		int after = geo.getDataAccess().get(DATA_PROGRESS);
		long burnt = (long) (before - after) * euPerTick;
		long drawn = lightDraw * ticks;
		helper.assertTrue(after > 0, "the lava ran out mid-test, the rig is wrong: reserve " + after);
		helper.assertTrue(burnt <= drawn + euPerTick, "a light load wasted lava: burnt " + burnt + " EU of lava for "
				+ drawn + " EU drawn");
		helper.succeed();
	}
}
