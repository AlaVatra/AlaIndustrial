package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.core.environment.FuelRuntime;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.environment.GeothermalLava;
import dev.alaindustrial.core.environment.LavaBurnTally;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.heat.HeatSource;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * L2 suite for the geothermal generator's features working together (MOD-993 epic): the blaze catalyst (MOD-997)
 * under the whole-tick burn rule (MOD-1000) and the heat for the machine on top (MOD-994); a catalysed bucket in the
 * burnt-lava tally (MOD-999) and the shavings count (MOD-995); the runtime readout's rate while a catalysed bucket
 * burns (MOD-1000); the lava a generator broken mid-bucket carries (MOD-998); and a cistern chip (MOD-996) in a save
 * written before the shavings and catalyst slots moved the upgrade block (MOD-995).
 *
 * <p>Each lane's suite pins its own feature on a generator that has none of the others; these scenarios pin the
 * seams between them. Ticks are driven by hand ({@code serverTick} directly). Numbers come from the config, never
 * from the class under test.
 */
public final class GeothermalIntegrationScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(GeothermalIntegrationScenarios::catalysedBucketCountsAsOneBucket,
						"mod993_catalysed_bucket_counts_as_one_bucket").ticks(20, 60),
				RosterEntry.of(GeothermalIntegrationScenarios::catalystWaitsForAWholeCatalysedTick,
						"mod993_catalyst_waits_for_a_whole_catalysed_tick").ticks(20, 60),
				RosterEntry.of(GeothermalIntegrationScenarios::readoutReadsTheCatalysedRate,
						"mod993_readout_reads_the_catalysed_rate").ticks(20, 60),
				RosterEntry.of(GeothermalIntegrationScenarios::brokenWhileCatalysedCarriesThePlainRest,
						"mod993_broken_while_catalysed_carries_the_plain_rest").ticks(20, 60),
				RosterEntry.of(GeothermalIntegrationScenarios::oldSaveCisternKeepsItsArmAndOverfill,
						"mod993_old_save_cistern_keeps_its_arm_and_overfill").ticks(20, 60));

		private Roster() {}
	}

	private GeothermalIntegrationScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	private static final BlockPos MACHINE = new BlockPos(3, 2, 1);
	private static final BlockPos UNDER_MACHINE = MACHINE.below();

	private static GeothermalGeneratorBlockEntity place(GameTestHelper helper, BlockPos pos) {
		return AlaGameTestHelper.place(helper, pos, ModContent.GEOTHERMAL_GENERATOR.get(),
				GeothermalGeneratorBlockEntity.class);
	}

	private static void fill(GeothermalGeneratorBlockEntity geo, int buckets) {
		geo.fluidTank.fluid = FluidHolder.of(Fluids.LAVA);
		geo.fluidTank.amount = (long) buckets * FluidAmounts.BUCKET;
	}

	private static void powder(GeothermalGeneratorBlockEntity geo, int count) {
		geo.setItem(GeothermalGeneratorBlockEntity.CATALYST_SLOT, new ItemStack(Items.BLAZE_POWDER, count));
	}

	private static void statsChip(GeothermalGeneratorBlockEntity geo) {
		geo.setItem(geo.upgradeSlotStart() + 2, new ItemStack(ModContent.STATS_CHIP.get()));
	}

	/** A mechanical rate after the global multiplier, as the base tick credits it. */
	private static long credited(int mechanical) {
		return mechanical > 0 ? Math.max(1, Math.round(mechanical * Config.globalEuRateMultiplier)) : 0;
	}

	/** The catalysed rate from the knobs: {@code geothermalEuPerTick × geothermalCatalystRatePercent / 100}. */
	private static int catalysedRate() {
		return GeneratorConfig.geothermalEuPerTick * GeneratorConfig.geothermalCatalystRatePercent / 100;
	}

	/** EU of a whole catalysed bucket from the knobs. */
	private static long catalysedBucket() {
		return (long) GeneratorConfig.geothermalEuPerTick * GeneratorConfig.geothermalBurnTicks
				* GeneratorConfig.geothermalCatalystEuPercent / 100L;
	}

	/** Run one tick from an empty buffer and return the EU it made. */
	private static long tick(GameTestHelper helper, GeothermalGeneratorBlockEntity geo) {
		geo.getEnergyStorage().setAmountUntracked(0);
		drive(geo, helper, 1);
		return geo.getEnergyStorage().getAmount();
	}

	private static int count(GeothermalGeneratorBlockEntity geo, int slot) {
		return geo.getItem(slot).getCount();
	}

	/**
	 * @implements MOD-993 - a catalysed bucket under the statistics chip is one bucket for the burnt-lava tally
	 *     (MOD-999) and the shavings count (MOD-995): the tally grows while its EU is paid out, ends at exactly
	 *     1000 mB, and its EU is everything the bucket put in the buffer; one shaving drops.
	 * @covers MOD-999
	 * @covers MOD-995
	 */
	public static void catalysedBucketCountsAsOneBucket(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper, POS);
		statsChip(geo);
		fill(geo, 1);
		powder(geo, 1);
		long total = tick(helper, geo);
		helper.assertTrue(geo.isCatalysed(), "the powder did not start a catalysed bucket");
		for (int i = 0; i < 99; i++) {
			total += tick(helper, geo);
		}
		long midway = geo.lavaBurntTally().mb();
		helper.assertTrue(midway > 0 && midway < LavaBurnTally.MB_PER_BUCKET,
				"a catalysed bucket 100 ticks in counted " + midway + " mB");
		int ticks = 100;
		while (geo.isCatalysed() && ticks < 4 * GeneratorConfig.geothermalBurnTicks) {
			total += tick(helper, geo);
			ticks++;
		}
		helper.assertTrue(!geo.isCatalysed(), "the catalysed bucket never burnt out");
		helper.assertTrue(geo.lavaBurntTally().mb() == LavaBurnTally.MB_PER_BUCKET,
				"one catalysed bucket counted " + geo.lavaBurntTally().mb() + " mB");
		helper.assertTrue(geo.lavaBurntTally().eu() == total,
				"the tally gave the bucket " + geo.lavaBurntTally().eu() + " EU, the buffer got " + total);
		if (Config.globalEuRateMultiplier == 1.0) {
			helper.assertTrue(total == catalysedBucket(), "the catalysed bucket paid " + total + " EU");
		}
		helper.assertTrue(count(geo, GeothermalGeneratorBlockEntity.SHAVINGS_SLOT) == 1 && geo.shavingLavaTicks() == 0,
				"one catalysed bucket left " + count(geo, GeothermalGeneratorBlockEntity.SHAVINGS_SLOT)
						+ " shavings, count " + geo.shavingLavaTicks());
		helper.succeed();
	}

	/**
	 * @implements MOD-993 - the powder is taken only when the buffer takes a whole catalysed tick (MOD-997 under the
	 *     MOD-1000 rule): with room for a plain tick but not a catalysed one the generator burns plain lava and keeps
	 *     the powder; with a full buffer it burns plain lava for the heat of the machine on top (MOD-994) and still
	 *     keeps the powder; with an empty buffer it takes it.
	 * @covers MOD-997
	 * @covers MOD-994
	 */
	public static void catalystWaitsForAWholeCatalysedTick(GameTestHelper helper) {
		helper.assertTrue(credited(catalysedRate()) > credited(GeneratorConfig.geothermalEuPerTick),
				"the rig needs a catalysed rate above the plain one");
		GeothermalGeneratorBlockEntity geo = place(helper, UNDER_MACHINE);
		fill(geo, 3);
		powder(geo, 1);
		long capacity = geo.getEnergyStorage().getCapacity();
		long between = capacity - credited(GeneratorConfig.geothermalEuPerTick);
		for (int i = 0; i < 5; i++) {
			geo.getEnergyStorage().setAmountUntracked(between);
			drive(geo, helper, 1);
		}
		helper.assertTrue(count(geo, GeothermalGeneratorBlockEntity.CATALYST_SLOT) == 1 && !geo.isCatalysed(),
				"room for a plain tick only, yet the powder went: " + count(geo,
						GeothermalGeneratorBlockEntity.CATALYST_SLOT) + " left, catalysed " + geo.isCatalysed());
		helper.assertTrue(geo.isBurning(), "with room for a plain tick the plain lava must burn");

		VulcanizerBlockEntity be = AlaGameTestHelper.place(helper, MACHINE, ModContent.VULCANIZER.get(),
				VulcanizerBlockEntity.class);
		be.getEnergyStorage().setAmountUntracked(800L);
		be.setItem(VulcanizerBlockEntity.RAW_RUBBER_SLOT, new ItemStack(ModContent.RAW_RUBBER.get(), 4));
		be.setItem(VulcanizerBlockEntity.SULFUR_SLOT, new ItemStack(ModContent.SULFUR_DUST.get(), 16));
		for (int i = 0; i < 10; i++) {
			geo.getEnergyStorage().setAmountUntracked(capacity);
			drive(geo, helper, 1);
			drive(be, helper, 1);
		}
		helper.assertTrue(geo.isBurning() && be.heatSource() == HeatSource.GEOTHERMAL_GENERATOR,
				"a full generator under a vulcanizer that wants heat must burn for it");
		helper.assertTrue(count(geo, GeothermalGeneratorBlockEntity.CATALYST_SLOT) == 1 && !geo.isCatalysed(),
				"a burn for heat alone took the powder");

		geo.getEnergyStorage().setAmountUntracked(0);
		drive(geo, helper, 1);
		helper.assertTrue(geo.isCatalysed() && geo.getItem(GeothermalGeneratorBlockEntity.CATALYST_SLOT).isEmpty(),
				"an empty buffer did not take the powder");
		helper.succeed();
	}

	/**
	 * @implements MOD-993 - the runtime readout (MOD-1000) states the full output of the bucket burning now: the
	 *     catalysed rate while a catalysed bucket burns (MOD-997), the plain one again after it.
	 * @covers MOD-1000
	 */
	public static void readoutReadsTheCatalysedRate(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper, POS);
		statsChip(geo);
		fill(geo, 2);
		powder(geo, 1);
		tick(helper, geo);
		FuelRuntime.Readout hot = geo.fuelReadout();
		helper.assertTrue(geo.isCatalysed() && hot != null && hot.euPerTick() == credited(catalysedRate()),
				"a catalysed bucket reads " + (hot == null ? "nothing" : hot.euPerTick() + " EU/t") + ", not "
						+ credited(catalysedRate()));
		int guard = 0;
		while (geo.isCatalysed() && guard++ < 4 * GeneratorConfig.geothermalBurnTicks) {
			tick(helper, geo);
		}
		tick(helper, geo);
		FuelRuntime.Readout plain = geo.fuelReadout();
		helper.assertTrue(!geo.isCatalysed() && plain != null
						&& plain.euPerTick() == credited(GeneratorConfig.geothermalEuPerTick),
				"after the catalysed bucket the readout says " + (plain == null ? "nothing" : plain.euPerTick()));
		helper.succeed();
	}

	/**
	 * @implements MOD-993 - a generator broken while a catalysed bucket burns carries the rest of that bucket as the
	 *     plain lava it was worth, rounded down (MOD-998 with MOD-997): nothing of the bucket is lost and none is made.
	 * @covers MOD-998
	 */
	public static void brokenWhileCatalysedCarriesThePlainRest(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper, POS);
		fill(geo, 3);
		powder(geo, 1);
		for (int i = 0; i < 100; i++) {
			tick(helper, geo);
		}
		helper.assertTrue(geo.isCatalysed(), "the rig needs a catalysed bucket burning");
		int burnTicks = GeneratorConfig.geothermalBurnTicks;
		int restTicks = (int) (geo.catalystEu() * burnTicks / catalysedBucket());
		long expected = geo.fluidTank.amount + GeothermalLava.reserveMb(geo.lavaTicks() + restTicks, burnTicks);
		Long carried = geo.collectComponents().get(ModDataComponents.STORED_LAVA.get());
		helper.assertTrue(restTicks > 0 && carried != null && carried == expected,
				"the generator would carry " + carried + " mB, the tank, the pool and the catalysed rest make "
						+ expected);
		helper.succeed();
	}

	/**
	 * @implements MOD-993 - a save from before the shavings and catalyst slots (version 2: upgrades at 2..5) with a
	 *     cistern chip on arm 1 (container slot 3) and lava over the plain tank loads with the chip on arm 1 of the
	 *     new block (slot 5), the cistern working and every drop of the overfill kept (MOD-995 rung with MOD-996).
	 * @covers MOD-996
	 * @covers R-PER-01
	 */
	public static void oldSaveCisternKeepsItsArmAndOverfill(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		long overfill = GeothermalGeneratorBlockEntity.TANK_CAPACITY + 5 * FluidAmounts.BUCKET;
		int reserve = 15 * GeneratorConfig.geothermalBurnTicks;
		String snbt = "{AlaDataVersion:2,Energy:100L,LavaTicks:" + reserve + ",FluidTankMb:" + overfill + "L,"
				+ "Items:[{Slot:3b,id:\"alaindustrial:cistern_chip\",count:1}]}";
		GeothermalGeneratorBlockEntity old = SaveFormatTestSupport.load(
				SaveFormatTestSupport.fresh(GeothermalGeneratorBlockEntity::new, ModContent.GEOTHERMAL_GENERATOR.get()),
				registries, SaveFormatTestSupport.parse(snbt));
		int arm1 = old.upgradeSlotStart() + 1;
		helper.assertTrue(old.getItem(arm1).is(ModContent.CISTERN_CHIP.get()),
				"the cistern chip landed in slot " + arm1 + " as " + old.getItem(arm1));
		helper.assertTrue(old.getItem(GeothermalGeneratorBlockEntity.CATALYST_SLOT).isEmpty(),
				"the catalyst slot kept the old chip: " + old.getItem(GeothermalGeneratorBlockEntity.CATALYST_SLOT));
		helper.assertTrue(old.hasCistern(), "the migrated cistern chip does not work");
		helper.assertTrue(old.fluidTank.amount == overfill && old.lavaTicks() == reserve,
				"the lava came back as " + old.fluidTank.amount + " mB and " + old.lavaTicks() + " burn ticks");
		helper.assertTrue(!old.mayRemoveUpgrade(old.getItem(arm1)),
				"the cistern can come out while the lava is over the plain tank");
		helper.succeed();
	}
}
