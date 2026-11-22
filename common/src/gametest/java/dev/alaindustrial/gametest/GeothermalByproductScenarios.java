package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BlockEntityDataMigrations;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * L2 suite for what the geothermal generator gained in MOD-995 and MOD-997: the obsidian shaving it leaves per
 * bucket of lava burnt, the blaze-powder catalyst that burns a bucket hotter, the slot roles of both for the
 * player and automation, and the migration rung that moves the upgrade chips of a generator saved before either
 * slot existed while keeping every drop of its lava.
 *
 * <p>The burn is driven tick by tick ({@code serverTick} directly) with the buffer emptied before each tick, so the
 * generator never pauses and the charge after a tick is exactly what that tick made. Numbers come from the config,
 * never from the class under test.
 */
public final class GeothermalByproductScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(GeothermalByproductScenarios::shavingPerBucketBurnt, "mod995_shaving_per_bucket_burnt")
						.ticks(20, 60),
				RosterEntry.of(GeothermalByproductScenarios::fullShavingsSlotKeepsBurning,
								"mod995_full_shavings_slot_keeps_burning")
						.ticks(20, 60),
				RosterEntry.of(GeothermalByproductScenarios::hoppersMoveShavingsAndPowderOnly,
								"mod995_hoppers_move_shavings_and_powder_only")
						.ticks(100, 200),
				RosterEntry.of(GeothermalByproductScenarios::catalystBurnsABucketHotter,
								"mod997_catalyst_burns_a_bucket_hotter")
						.ticks(20, 60),
				RosterEntry.of(GeothermalByproductScenarios::oldSavesKeepLavaAndChips,
								"mod995_old_saves_keep_lava_and_chips")
						.ticks(20, 60));

		private Roster() {}
	}

	private GeothermalByproductScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	private static final BlockPos POS_B = new BlockPos(3, 2, 1);

	private static GeothermalGeneratorBlockEntity place(GameTestHelper helper, BlockPos pos) {
		return AlaGameTestHelper.place(helper, pos, ModContent.GEOTHERMAL_GENERATOR.get(),
				GeothermalGeneratorBlockEntity.class);
	}

	/** Put {@code buckets} of lava into the generator's tank; the next ticks move them into the burn reserve. */
	private static void fill(GeothermalGeneratorBlockEntity geo, int buckets) {
		geo.fluidTank.fluid = FluidHolder.of(Fluids.LAVA);
		geo.fluidTank.amount = (long) buckets * FluidAmounts.BUCKET;
	}

	/** The mechanical EU of one tick after the global multiplier, as the base tick credits it. */
	private static long credited(int mechanical) {
		return mechanical > 0 ? Math.max(1, Math.round(mechanical * Config.globalEuRateMultiplier)) : 0;
	}

	/** Run one tick from an empty buffer and return the EU it made. */
	private static long tick(GameTestHelper helper, GeothermalGeneratorBlockEntity geo) {
		geo.getEnergyStorage().setAmountUntracked(0);
		drive(geo, helper, 1);
		return geo.getEnergyStorage().getAmount();
	}

	private static int count(GeothermalGeneratorBlockEntity geo, int slot, Item item) {
		ItemStack stack = geo.getItem(slot);
		return stack.is(item) ? stack.getCount() : (stack.isEmpty() ? 0 : -1);
	}

	private static int shavings(GeothermalGeneratorBlockEntity geo) {
		return count(geo, GeothermalGeneratorBlockEntity.SHAVINGS_SLOT, ModContent.OBSIDIAN_SHAVINGS.get());
	}

	/**
	 * MOD-995: one obsidian shaving per bucket of lava burnt. A generator with no lava leaves nothing; half a bucket
	 * leaves nothing and carries the count; the rest of that bucket completes the shaving; the count survives a
	 * reload.
	 *
	 * @implements MOD-995-SHAVINGS — one shaving per bucket of lava burnt, counted across a reload
	 * @covers MOD-995
	 */
	public static void shavingPerBucketBurnt(GameTestHelper helper) {
		int bucket = GeneratorConfig.geothermalBurnTicks;
		helper.assertTrue(GeneratorConfig.geothermalBucketsPerShaving == 1, "the owner's rule is one shaving per"
				+ " bucket, the knob reads " + GeneratorConfig.geothermalBucketsPerShaving);

		// No lava: no burn, no shaving, no count.
		GeothermalGeneratorBlockEntity dry = place(helper, POS_B);
		for (int i = 0; i < 50; i++) {
			tick(helper, dry);
		}
		helper.assertTrue(shavings(dry) == 0 && dry.shavingLavaTicks() == 0,
				"a generator with no lava left " + shavings(dry) + " shavings, count " + dry.shavingLavaTicks());

		GeothermalGeneratorBlockEntity geo = place(helper, POS);
		fill(geo, 2);
		for (int i = 0; i < bucket / 2; i++) {
			tick(helper, geo);
		}
		helper.assertTrue(shavings(geo) == 0 && geo.shavingLavaTicks() == bucket / 2,
				"after half a bucket: shavings " + shavings(geo) + ", count " + geo.shavingLavaTicks());

		// The count is part of the save: half a bucket today and half tomorrow still make a shaving.
		RegistryAccess registries = helper.getLevel().registryAccess();
		CompoundTag tag = geo.saveCustomOnly(registries);
		GeothermalGeneratorBlockEntity restored = new GeothermalGeneratorBlockEntity(geo.getBlockPos(),
				helper.getLevel().getBlockState(geo.getBlockPos()));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		helper.assertTrue(restored.shavingLavaTicks() == bucket / 2,
				"after a reload the count reads " + restored.shavingLavaTicks());

		for (int i = 0; i < bucket - bucket / 2; i++) {
			tick(helper, geo);
		}
		helper.assertTrue(shavings(geo) == 1 && geo.shavingLavaTicks() == 0,
				"after one bucket: shavings " + shavings(geo) + ", count " + geo.shavingLavaTicks());
		for (int i = 0; i < bucket; i++) {
			tick(helper, geo);
		}
		helper.assertTrue(shavings(geo) == 2, "after two buckets: shavings " + shavings(geo));
		helper.succeed();
	}

	/**
	 * MOD-995: a full shavings slot stops nothing. The generator burns the next bucket at the full output, the
	 * shaving it would have left is lost and the count moves on.
	 *
	 * @implements MOD-995-FULL — a full shavings slot: the burn goes on, the shavings stop piling up
	 * @covers MOD-995
	 */
	public static void fullShavingsSlotKeepsBurning(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper, POS);
		int max = new ItemStack(ModContent.OBSIDIAN_SHAVINGS.get()).getMaxStackSize();
		geo.setItem(GeothermalGeneratorBlockEntity.SHAVINGS_SLOT,
				new ItemStack(ModContent.OBSIDIAN_SHAVINGS.get(), max));
		fill(geo, 1);
		long plain = credited(GeneratorConfig.geothermalEuPerTick);
		for (int i = 0; i < GeneratorConfig.geothermalBurnTicks; i++) {
			long made = tick(helper, geo);
			if (made != plain) {
				helper.fail("tick " + i + " with a full shavings slot made " + made + " EU, a burning generator"
						+ " makes " + plain);
				return;
			}
		}
		helper.assertTrue(shavings(geo) == max, "the full shavings slot now holds " + shavings(geo));
		helper.assertTrue(geo.shavingLavaTicks() == 0, "the count did not move on past the lost shaving: "
				+ geo.shavingLavaTicks());
		helper.succeed();
	}

	/**
	 * MOD-995, MOD-997: automation takes the shavings and only the shavings, and puts blaze powder into the catalyst
	 * slot and nowhere else, on a working face. A hopper under the generator empties the shavings slot and leaves
	 * the powder; a hopper above puts its blaze powder into the catalyst slot and keeps its shavings;
	 * the menu's shavings slot takes nothing, its catalyst slot only blaze powder; the front face shows no slot.
	 *
	 * @implements MOD-995-SLOTS — shavings out by hand and by automation on a working face, never in
	 * @implements MOD-997-SLOT — the catalyst slot takes blaze powder only, by hand or by automation
	 * @covers MOD-995
	 * @covers MOD-997
	 */
	public static void hoppersMoveShavingsAndPowderOnly(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper, POS);
		// No lava and a full buffer: nothing burns, so no powder is taken while the test runs.
		geo.getEnergyStorage().setAmountUntracked(geo.getEnergyStorage().getCapacity());
		geo.setItem(GeothermalGeneratorBlockEntity.SHAVINGS_SLOT, new ItemStack(ModContent.OBSIDIAN_SHAVINGS.get(), 3));
		geo.setItem(GeothermalGeneratorBlockEntity.CATALYST_SLOT, new ItemStack(Items.BLAZE_POWDER, 2));

		ItemStack shaving = new ItemStack(ModContent.OBSIDIAN_SHAVINGS.get());
		ItemStack powder = new ItemStack(Items.BLAZE_POWDER);
		int catalyst = GeothermalGeneratorBlockEntity.CATALYST_SLOT;
		int out = GeothermalGeneratorBlockEntity.SHAVINGS_SLOT;
		helper.assertTrue(geo.canTakeItemThroughFace(out, shaving, Direction.DOWN), "automation cannot take shavings");
		helper.assertTrue(!geo.canTakeItemThroughFace(catalyst, powder, Direction.DOWN), "automation pulls the powder");
		helper.assertTrue(!geo.canPlaceItemThroughFace(out, shaving, Direction.UP), "automation fills the shavings");
		helper.assertTrue(geo.canPlaceItemThroughFace(catalyst, powder, Direction.UP), "automation cannot add powder");
		for (Item wrong : List.of(Items.BLAZE_ROD, Items.LAVA_BUCKET, Items.MAGMA_CREAM, Items.COAL)) {
			helper.assertTrue(!geo.canPlaceItem(catalyst, new ItemStack(wrong)), "the catalyst slot takes " + wrong);
		}
		helper.assertTrue(!geo.canPlaceItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, powder),
				"the lava slot takes blaze powder");
		Direction front = helper.getLevel().getBlockState(geo.getBlockPos())
				.getValue(BlockStateProperties.HORIZONTAL_FACING);
		helper.assertTrue(geo.getSlotsForFace(front).length == 0, "the front face shows automation a slot");

		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		GeothermalGeneratorMenu menu = new GeothermalGeneratorMenu(1, player.getInventory(), geo,
				ContainerLevelAccess.create(helper.getLevel(), geo.getBlockPos()));
		var shavingsSlot = menu.getSlot(out);
		var catalystSlot = menu.getSlot(catalyst);
		helper.assertTrue(shavingsSlot.getContainerSlot() == out && !shavingsSlot.mayPlace(shaving)
				&& !shavingsSlot.mayPlace(powder), "the menu's shavings slot takes items from the player");
		helper.assertTrue(catalystSlot.getContainerSlot() == catalyst && catalystSlot.mayPlace(powder)
				&& !catalystSlot.mayPlace(new ItemStack(Items.BLAZE_ROD)),
				"the menu's catalyst slot does not take blaze powder alone");

		helper.setBlock(POS.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
		helper.setBlock(POS.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		HopperBlockEntity above = helper.getBlockEntity(POS.above(), HopperBlockEntity.class);
		above.setItem(0, new ItemStack(Items.BLAZE_POWDER, 2));
		above.setItem(1, new ItemStack(ModContent.OBSIDIAN_SHAVINGS.get(), 2));
		helper.succeedWhen(() -> {
			HopperBlockEntity below = helper.getBlockEntity(POS.below(), HopperBlockEntity.class);
			int pulled = 0;
			int other = 0;
			for (int i = 0; i < below.getContainerSize(); i++) {
				ItemStack s = below.getItem(i);
				pulled += s.is(ModContent.OBSIDIAN_SHAVINGS.get()) ? s.getCount() : 0;
				other += s.isEmpty() || s.is(ModContent.OBSIDIAN_SHAVINGS.get()) ? 0 : s.getCount();
			}
			helper.assertTrue(other == 0, "the hopper below pulled something else than shavings");
			helper.assertTrue(pulled == 3 && geo.getItem(out).isEmpty(),
					"the hopper below holds " + pulled + " shavings, the slot " + geo.getItem(out));
			helper.assertTrue(count(geo, catalyst, Items.BLAZE_POWDER) == 4,
					"the catalyst slot holds " + geo.getItem(catalyst) + ", the hopper above should have added 2");
			helper.assertTrue(above.getItem(1).getCount() == 2, "the hopper above put shavings into the generator");
		});
	}

	/**
	 * MOD-997: blaze powder catalyses one bucket. It burns at the catalysed rate and pays out exactly the catalysed
	 * bucket's EU, takes one powder, leaves one shaving like any bucket (MOD-995), and the next bucket with no powder
	 * left burns at the plain rate. No powder is taken while the buffer is full. The rates stay under the LV cap.
	 *
	 * @implements MOD-997-CATALYST — one powder per bucket: 24 EU/t and 20 000 EU, then back to 16 EU/t
	 * @covers MOD-997
	 */
	public static void catalystBurnsABucketHotter(GameTestHelper helper) {
		int plainRate = GeneratorConfig.geothermalEuPerTick;
		int hotRate = GeothermalGeneratorBlockEntity.catalysedEuPerTick();
		long hotBucket = GeothermalGeneratorBlockEntity.catalysedBucketEu();
		helper.assertTrue(GeneratorConfig.geothermalCatalystRatePercent == 150
				&& GeneratorConfig.geothermalCatalystEuPercent == 125, "the owner's catalyst is x1.5 output and"
				+ " +25 % per bucket");
		helper.assertTrue(hotRate == plainRate * 3 / 2 && hotBucket == (long) plainRate
				* GeneratorConfig.geothermalBurnTicks * 5 / 4, "catalysed rate " + hotRate + ", bucket " + hotBucket);
		helper.assertTrue(hotRate <= EnergyTier.LV.maxVoltage(), "the catalysed rate " + hotRate
				+ " is above the LV cap");

		// A full buffer: the powder waits.
		GeothermalGeneratorBlockEntity paused = place(helper, POS_B);
		fill(paused, 2);
		paused.setItem(GeothermalGeneratorBlockEntity.CATALYST_SLOT, new ItemStack(Items.BLAZE_POWDER, 1));
		paused.getEnergyStorage().setAmountUntracked(paused.getEnergyStorage().getCapacity());
		drive(paused, helper, 5);
		helper.assertTrue(count(paused, GeothermalGeneratorBlockEntity.CATALYST_SLOT, Items.BLAZE_POWDER) == 1
				&& !paused.isCatalysed(), "a paused generator took its powder");

		GeothermalGeneratorBlockEntity geo = place(helper, POS);
		fill(geo, 2);
		geo.setItem(GeothermalGeneratorBlockEntity.CATALYST_SLOT, new ItemStack(Items.BLAZE_POWDER, 1));
		long total = 0;
		int ticks = 0;
		long first = tick(helper, geo);
		total += first;
		ticks++;
		helper.assertTrue(geo.isCatalysed() && first == credited(hotRate), "the first tick made " + first
				+ " EU, catalysed " + geo.isCatalysed());
		helper.assertTrue(geo.getItem(GeothermalGeneratorBlockEntity.CATALYST_SLOT).isEmpty(),
				"the powder was not taken");
		while (geo.isCatalysed() && ticks < 2 * GeneratorConfig.geothermalBurnTicks) {
			total += tick(helper, geo);
			ticks++;
		}
		if (Config.globalEuRateMultiplier == 1.0) {
			helper.assertTrue(total == hotBucket, "the catalysed bucket paid " + total + " EU, not " + hotBucket);
		}
		long expectedTicks = (hotBucket + hotRate - 1) / hotRate;
		helper.assertTrue(ticks == expectedTicks, "the catalysed bucket burnt " + ticks + " ticks, not "
				+ expectedTicks);
		helper.assertTrue(shavings(geo) == 1 && geo.shavingLavaTicks() == 0,
				"a catalysed bucket left " + shavings(geo) + " shavings, count " + geo.shavingLavaTicks());

		// The second bucket has no powder: the plain rate, and the generator still burns.
		long next = tick(helper, geo);
		helper.assertTrue(next == credited(plainRate) && !geo.isCatalysed(), "with no powder left a tick made "
				+ next + " EU, catalysed " + geo.isCatalysed());
		helper.assertTrue(geo.currentEuPerTick() == plainRate, "the readout rate is " + geo.currentEuPerTick());
		helper.succeed();
	}

	/**
	 * MOD-995, MOD-997: a geothermal generator saved before the shavings and catalyst slots existed opens with
	 * nothing lost. The tags are written by hand in the old shape (lava bucket at 0, buckets at 1, the four upgrade
	 * arms at 2..5) with lava in the tank and in the burn reserve. It loads with the lava where it was, the two new
	 * slots empty and each chip two slots up on its own arm; the same from a version 1 save and one with no version.
	 * A save of the new layout is never moved: a re-save, one an older jar wrote back (shavings or powder in the new
	 * slots) and one with a filled last arm.
	 *
	 * @implements MOD-995-MIGRATION — the v2 to v3 rung moves the upgrade chips two slots up, keeps the lava
	 * @covers R-PER-01
	 */
	public static void oldSavesKeepLavaAndChips(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		helper.assertTrue(BlockEntityDataMigrations.DATA_VERSION >= 3, "the shavings and catalyst slots are format"
				+ " version 3, the ladder reads " + BlockEntityDataMigrations.DATA_VERSION);
		String items = "Items:[{Slot:0b,id:\"minecraft:lava_bucket\",count:1},"
				+ "{Slot:1b,id:\"minecraft:bucket\",count:5},"
				+ "{Slot:2b,id:\"alaindustrial:mute_chip\",count:1},{Slot:3b,id:\"minecraft:redstone\",count:1},"
				+ "{Slot:4b,id:\"alaindustrial:stats_chip\",count:1},"
				+ "{Slot:5b,id:\"minecraft:glowstone_dust\",count:1}]";
		String rest = "Energy:2500L,LavaTicks:4500,FluidTankMb:3000L";
		for (String version : List.of("AlaDataVersion:2,", "AlaDataVersion:1,", "")) {
			GeothermalGeneratorBlockEntity old = load(helper, registries, "{" + version + rest + "," + items + "}");
			String what = version.isEmpty() ? "a save with no version" : "a save with " + version;
			expect(helper, what, old, Items.LAVA_BUCKET, 1, GeothermalGeneratorBlockEntity.INPUT_SLOT);
			expect(helper, what, old, Items.BUCKET, 5, GeothermalGeneratorBlockEntity.OUTPUT_SLOT);
			expect(helper, what, old, null, 0, GeothermalGeneratorBlockEntity.SHAVINGS_SLOT);
			expect(helper, what, old, null, 0, GeothermalGeneratorBlockEntity.CATALYST_SLOT);
			int start = old.upgradeSlotStart();
			helper.assertTrue(start == GeothermalGeneratorBlockEntity.SLOT_COUNT, "the upgrade block starts at "
					+ start);
			expect(helper, what, old, ModContent.MUTE_CHIP.get(), 1, start);
			expect(helper, what, old, Items.REDSTONE, 1, start + 1);
			expect(helper, what, old, ModContent.STATS_CHIP.get(), 1, start + 2);
			expect(helper, what, old, Items.GLOWSTONE_DUST, 1, start + 3);
			helper.assertTrue(old.isMuted() && old.hasStatsChip(), what + ": the chips stopped working");
			helper.assertTrue(old.lavaTicks() == 4500 && old.fluidTank.amount == 3000L
					&& old.fluidTank.fluid.is(Fluids.LAVA), what + ": the lava came back as " + old.lavaTicks()
					+ " burn ticks and " + old.fluidTank.amount + " mB");
			helper.assertTrue(old.getEnergyStorage().getAmount() == 2500L, what + ": charge "
					+ old.getEnergyStorage().getAmount());
			CompoundTag resaved = old.saveCustomOnly(registries);
			helper.assertTrue(resaved.getIntOr(BlockEntityDataMigrations.DATA_VERSION_KEY, -1)
					== BlockEntityDataMigrations.DATA_VERSION, what + ": re-saved without the current version");

			// The re-save is the new layout: loading it again moves nothing.
			GeothermalGeneratorBlockEntity again = load(helper, registries, resaved);
			expect(helper, what + " re-saved", again, ModContent.MUTE_CHIP.get(), 1, start);
			expect(helper, what + " re-saved", again, Items.GLOWSTONE_DUST, 1, start + 3);
			helper.assertTrue(again.lavaTicks() == 4500 && again.fluidTank.amount == 3000L,
					what + " re-saved: the lava moved");
		}

		// New-layout saves an older jar wrote back (no version): shavings or powder in the new slots.
		GeothermalGeneratorBlockEntity shav = load(helper, registries, "{" + rest + ",Items:[{Slot:2b,"
				+ "id:\"alaindustrial:obsidian_shavings\",count:4},{Slot:4b,id:\"alaindustrial:mute_chip\",count:1}]}");
		expect(helper, "an older jar's write-back", shav, ModContent.OBSIDIAN_SHAVINGS.get(), 4,
				GeothermalGeneratorBlockEntity.SHAVINGS_SLOT);
		expect(helper, "an older jar's write-back", shav, ModContent.MUTE_CHIP.get(), 1, 4);
		GeothermalGeneratorBlockEntity powder = load(helper, registries, "{" + rest + ",Items:[{Slot:3b,"
				+ "id:\"minecraft:blaze_powder\",count:6},{Slot:4b,id:\"alaindustrial:mute_chip\",count:1}]}");
		expect(helper, "an older jar's write-back", powder, Items.BLAZE_POWDER, 6,
				GeothermalGeneratorBlockEntity.CATALYST_SLOT);
		expect(helper, "an older jar's write-back", powder, ModContent.MUTE_CHIP.get(), 1, 4);

		// A filled last arm cannot be the old layout; shifting would overwrite it, so nothing moves.
		GeothermalGeneratorBlockEntity full = load(helper, registries, "{AlaDataVersion:2," + rest + ",Items:[{Slot:2b,"
				+ "id:\"minecraft:redstone\",count:1},{Slot:7b,id:\"minecraft:glowstone_dust\",count:1}]}");
		expect(helper, "a full last arm", full, Items.REDSTONE, 1, GeothermalGeneratorBlockEntity.SHAVINGS_SLOT);
		expect(helper, "a full last arm", full, Items.GLOWSTONE_DUST, 1, 7);
		helper.succeed();
	}

	private static GeothermalGeneratorBlockEntity load(GameTestHelper helper, RegistryAccess registries, String snbt) {
		return load(helper, registries, SaveFormatTestSupport.parse(snbt));
	}

	private static GeothermalGeneratorBlockEntity load(GameTestHelper helper, RegistryAccess registries,
			CompoundTag tag) {
		GeothermalGeneratorBlockEntity geo = SaveFormatTestSupport.fresh(GeothermalGeneratorBlockEntity::new,
				ModContent.GEOTHERMAL_GENERATOR.get());
		return SaveFormatTestSupport.load(geo, registries, tag);
	}

	/** Slot {@code slot} holds {@code count} of {@code item}; a {@code null} item means the slot is empty. */
	private static void expect(GameTestHelper helper, String what, GeothermalGeneratorBlockEntity geo, Item item,
			int count, int slot) {
		ItemStack stack = geo.getItem(slot);
		boolean ok = item == null ? stack.isEmpty() : stack.is(item) && stack.getCount() == count;
		helper.assertTrue(ok, what + ": slot " + slot + " should hold " + (item == null ? "nothing" : count + " x "
				+ item) + ", holds " + stack);
	}
}
