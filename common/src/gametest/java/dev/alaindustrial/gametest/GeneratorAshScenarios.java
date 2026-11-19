package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BlockEntityDataMigrations;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.menu.GeneratorMenu;
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
import net.minecraft.world.level.storage.TagValueInput;

/**
 * L2 suite for the ash the fuel generator leaves (MOD-987): the rule (one ash per
 * {@link GeneratorConfig#generatorFuelPerAsh} pieces of coal or charcoal, a block of coal as nine, nothing
 * from other fuel), a full ash slot that does not stop the burn, the slot roles for automation and the menu,
 * and the migration rung that moves the upgrade chips of a generator saved before the ash slot existed.
 *
 * <p>The burn is driven tick by tick ({@code serverTick} directly, as {@link GeneratorScenarios} does) with the
 * full output drawn after each tick, so the buffer never fills and the generator takes the next piece the
 * moment the last one burns out. Numbers come from the config and the vanilla burn times, never from the
 * class under test.
 */
public final class GeneratorAshScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(GeneratorAshScenarios::ashFromCoalOnly, "mod987_ash_from_coal_only").ticks(20, 60),
				RosterEntry.of(GeneratorAshScenarios::fullAshSlotKeepsBurning, "mod987_full_ash_slot_keeps_burning")
						.ticks(20, 60),
				RosterEntry.of(GeneratorAshScenarios::hopperTakesAshNotFuel, "mod987_hopper_takes_ash_not_fuel")
						.ticks(100, 200),
				RosterEntry.of(GeneratorAshScenarios::oldSavesKeepFuelChargeAndChips,
								"mod987_old_saves_keep_fuel_charge_and_chips")
						.ticks(20, 60));

		private Roster() {}
	}

	private GeneratorAshScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	private static final BlockPos POS_B = new BlockPos(3, 2, 1);
	private static final BlockPos POS_C = new BlockPos(5, 2, 1);

	/** Vanilla burn time of coal in ticks; the rigs' generators have no owner, so no Steady Hands. */
	private static final int COAL_TICKS = 1600;

	private static GeneratorBlockEntity place(GameTestHelper helper, BlockPos pos) {
		return AlaGameTestHelper.place(helper, pos, ModContent.GENERATOR.get(), GeneratorBlockEntity.class);
	}

	private static int ash(GeneratorBlockEntity gen) {
		ItemStack stack = gen.getItem(GeneratorBlockEntity.ASH_SLOT);
		return stack.is(ModContent.ASH.get()) ? stack.getCount() : (stack.isEmpty() ? 0 : -1);
	}

	/**
	 * Burn until {@code pieces} more pieces have left the fuel slot (or {@code maxTicks} pass), drawing the full
	 * output after each tick as a line would. A piece leaves the slot on the tick its burn starts.
	 */
	private static void burnPieces(GameTestHelper helper, GeneratorBlockEntity gen, int pieces, int maxTicks) {
		int euPerTick = Math.max(1, Math.round(GeneratorConfig.fuelEuPerTick * Config.globalEuRateMultiplier));
		int target = gen.getItem(GeneratorBlockEntity.FUEL_SLOT).getCount() - pieces;
		for (int i = 0; i < maxTicks && gen.getItem(GeneratorBlockEntity.FUEL_SLOT).getCount() > target; i++) {
			drive(gen, helper, 1);
			EnergyTransactions.get().runCommitting(txn -> gen.getEnergyStorage().extract(euPerTick, txn));
		}
	}

	/**
	 * MOD-987: one ash per eight pieces of coal; the count carries over from piece to piece; a block of coal
	 * counts as nine (one ash and one piece towards the next); planks and sticks leave nothing; charcoal is a
	 * piece of coal, a log and a lava bucket are not.
	 *
	 * @implements MOD-987-ASH — ash from coal and charcoal only, one per eight pieces, a block as nine
	 * @covers MOD-987
	 */
	public static void ashFromCoalOnly(GameTestHelper helper) {
		int perAsh = GeneratorConfig.generatorFuelPerAsh;
		helper.assertTrue(perAsh == 8, "the owner's rule is one ash per 8 coal, the knob reads " + perAsh);

		// The tags behind the rule, without burning anything.
		helper.assertTrue(GeneratorBlockEntity.ashUnits(new ItemStack(Items.COAL)) == 1, "coal is not one unit");
		helper.assertTrue(GeneratorBlockEntity.ashUnits(new ItemStack(Items.CHARCOAL)) == 1,
				"charcoal is not one unit");
		helper.assertTrue(GeneratorBlockEntity.ashUnits(new ItemStack(Items.COAL_BLOCK)) == 9,
				"a block of coal is not nine units");
		for (Item none : List.of(Items.OAK_PLANKS, Items.OAK_LOG, Items.STICK, Items.LAVA_BUCKET, Items.BLAZE_ROD)) {
			helper.assertTrue(GeneratorBlockEntity.ashUnits(new ItemStack(none)) == 0, none + " leaves ash");
		}

		// Eight coal: nothing for seven, the eighth makes one ash and clears the count.
		GeneratorBlockEntity coal = place(helper, POS);
		coal.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, perAsh));
		burnPieces(helper, coal, perAsh - 1, (perAsh - 1) * COAL_TICKS + 10);
		helper.assertTrue(coal.getItem(GeneratorBlockEntity.FUEL_SLOT).getCount() == 1, "seven coal were not taken");
		helper.assertTrue(ash(coal) == 0 && coal.ashUnits() == perAsh - 1,
				"after seven coal: ash " + ash(coal) + ", count " + coal.ashUnits());
		burnPieces(helper, coal, 1, COAL_TICKS + 10);
		helper.assertTrue(coal.getItem(GeneratorBlockEntity.FUEL_SLOT).isEmpty(), "the eighth coal was not taken");
		helper.assertTrue(ash(coal) == 1 && coal.ashUnits() == 0,
				"after eight coal: ash " + ash(coal) + ", count " + coal.ashUnits());

		// Planks and a stick: they burn, and leave no ash and no count.
		GeneratorBlockEntity wood = place(helper, POS_B);
		wood.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.OAK_PLANKS, 2));
		burnPieces(helper, wood, 2, 400);
		wood.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.STICK));
		burnPieces(helper, wood, 1, 400);
		helper.assertTrue(wood.getItem(GeneratorBlockEntity.FUEL_SLOT).isEmpty(), "the wood was not taken");
		helper.assertTrue(ash(wood) == 0 && wood.ashUnits() == 0,
				"wood left ash " + ash(wood) + ", count " + wood.ashUnits());

		// A block of coal: nine units the tick it is taken, so one ash now and one unit carried.
		GeneratorBlockEntity block = place(helper, POS_C);
		block.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL_BLOCK));
		burnPieces(helper, block, 1, 10);
		helper.assertTrue(ash(block) == 1 && block.ashUnits() == 9 - perAsh,
				"a block of coal: ash " + ash(block) + ", count " + block.ashUnits());

		// The count is part of the save: the seventh coal today and the eighth tomorrow still make ash.
		RegistryAccess registries = helper.getLevel().registryAccess();
		CompoundTag tag = block.saveCustomOnly(registries);
		GeneratorBlockEntity restored = new GeneratorBlockEntity(block.getBlockPos(),
				helper.getLevel().getBlockState(block.getBlockPos()));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		helper.assertTrue(restored.ashUnits() == 9 - perAsh && ash(restored) == 1,
				"after a reload: count " + restored.ashUnits() + ", ash " + ash(restored));
		helper.succeed();
	}

	/**
	 * MOD-987: a full ash slot stops nothing. The generator takes the next block of coal, burns it at the full
	 * output, the ash it would have left is lost and the count still moves on; the comparator keeps reading the
	 * fuel slot alone (MOD-991), so a full ash slot with no fuel reads 0.
	 *
	 * @implements MOD-987-FULL — a full ash slot: the burn goes on, the ash stops piling up
	 * @covers MOD-987
	 */
	public static void fullAshSlotKeepsBurning(GameTestHelper helper) {
		GeneratorBlockEntity gen = place(helper, POS);
		int max = new ItemStack(ModContent.ASH.get()).getMaxStackSize();
		gen.setItem(GeneratorBlockEntity.ASH_SLOT, new ItemStack(ModContent.ASH.get(), max));
		helper.assertTrue(gen.comparatorSignal() == 0, "a full ash slot and no fuel reads " + gen.comparatorSignal());

		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL_BLOCK, 2));
		burnPieces(helper, gen, 1, 10);
		helper.assertTrue(gen.getItem(GeneratorBlockEntity.FUEL_SLOT).getCount() == 1, "the block was not taken");
		helper.assertTrue(ash(gen) == max, "the full ash slot now holds " + ash(gen));
		helper.assertTrue(gen.ashUnits() == 9 - GeneratorConfig.generatorFuelPerAsh,
				"the count did not move on past the lost ash: " + gen.ashUnits());

		// 100 more ticks into an empty buffer (far below its capacity, so no pause): every tick at the full output.
		int euPerTick = Math.max(1, Math.round(GeneratorConfig.fuelEuPerTick * Config.globalEuRateMultiplier));
		gen.getEnergyStorage().setAmountUntracked(0);
		drive(gen, helper, 100);
		long made = gen.getEnergyStorage().getAmount();
		helper.assertTrue(made == 100L * euPerTick, "with a full ash slot the generator made " + made + " EU in"
				+ " 100 ticks, a burning one makes " + euPerTick + " EU/t");
		helper.assertTrue(helper.getLevel().getBlockState(gen.getBlockPos()).getValue(BlockStateProperties.LIT),
				"a generator with a full ash slot must still be lit");
		helper.assertTrue(gen.comparatorSignal() == 1, "one block of fuel next to a full ash slot reads "
				+ gen.comparatorSignal() + ", the fuel slot alone reads 1");
		helper.succeed();
	}

	/**
	 * MOD-987: automation takes the ash and only the ash. A hopper under the generator empties the ash slot and
	 * leaves the coal; a hopper above holding ash puts none of it into the generator; the menu's ash slot takes
	 * nothing from the player; the front face shows automation no slot at all.
	 *
	 * @implements MOD-987-SLOTS — ash out by hand and by automation on a working face, never in
	 * @covers MOD-987
	 */
	public static void hopperTakesAshNotFuel(GameTestHelper helper) {
		GeneratorBlockEntity gen = place(helper, POS);
		// A full buffer pauses the burn (TC-GEN-001-NEG03), so the coal holds still for the whole test.
		gen.getEnergyStorage().setAmountUntracked(gen.getEnergyStorage().getCapacity());
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 10));
		gen.setItem(GeneratorBlockEntity.ASH_SLOT, new ItemStack(ModContent.ASH.get(), 3));

		// The slot rules, face by face.
		ItemStack ashStack = new ItemStack(ModContent.ASH.get());
		ItemStack coalStack = new ItemStack(Items.COAL);
		helper.assertTrue(gen.canTakeItemThroughFace(GeneratorBlockEntity.ASH_SLOT, ashStack, Direction.DOWN),
				"automation cannot take the ash");
		helper.assertTrue(!gen.canTakeItemThroughFace(GeneratorBlockEntity.FUEL_SLOT, coalStack, Direction.DOWN),
				"automation can pull the fuel out");
		helper.assertTrue(!gen.canPlaceItemThroughFace(GeneratorBlockEntity.ASH_SLOT, ashStack, Direction.UP),
				"automation can put ash into the ash slot");
		helper.assertTrue(!gen.canPlaceItemThroughFace(GeneratorBlockEntity.ASH_SLOT, coalStack, Direction.UP),
				"automation can put coal into the ash slot");
		Direction front = helper.getLevel().getBlockState(gen.getBlockPos())
				.getValue(BlockStateProperties.HORIZONTAL_FACING);
		helper.assertTrue(gen.getSlotsForFace(front).length == 0, "the front face shows automation a slot");

		// The menu: the ash slot takes nothing from the player.
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		GeneratorMenu menu = new GeneratorMenu(1, player.getInventory(), gen,
				ContainerLevelAccess.create(helper.getLevel(), gen.getBlockPos()));
		var ashSlot = menu.getSlot(GeneratorBlockEntity.ASH_SLOT);
		helper.assertTrue(ashSlot.getContainerSlot() == GeneratorBlockEntity.ASH_SLOT
				&& !ashSlot.mayPlace(ashStack) && !ashSlot.mayPlace(coalStack),
				"the menu's ash slot takes items from the player");

		// Real hoppers: one under the generator pulls, one above holds ash that must stay where it is.
		helper.setBlock(POS.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
		helper.setBlock(POS.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		HopperBlockEntity above = helper.getBlockEntity(POS.above(), HopperBlockEntity.class);
		above.setItem(0, new ItemStack(ModContent.ASH.get(), 2));
		helper.succeedWhen(() -> {
			HopperBlockEntity below = helper.getBlockEntity(POS.below(), HopperBlockEntity.class);
			int pulled = 0;
			int coalPulled = 0;
			for (int i = 0; i < below.getContainerSize(); i++) {
				ItemStack s = below.getItem(i);
				pulled += s.is(ModContent.ASH.get()) ? s.getCount() : 0;
				coalPulled += s.is(Items.COAL) ? s.getCount() : 0;
			}
			helper.assertTrue(coalPulled == 0, "the hopper below pulled " + coalPulled + " coal");
			helper.assertTrue(gen.getItem(GeneratorBlockEntity.FUEL_SLOT).getCount() == 10,
					"the coal moved: " + gen.getItem(GeneratorBlockEntity.FUEL_SLOT));
			helper.assertTrue(pulled == 3 && gen.getItem(GeneratorBlockEntity.ASH_SLOT).isEmpty(),
					"the hopper below holds " + pulled + " ash, the slot "
							+ gen.getItem(GeneratorBlockEntity.ASH_SLOT));
			helper.assertTrue(above.getItem(0).getCount() == 2, "the hopper above put ash into the generator");
		});
	}

	/**
	 * MOD-987: a generator saved before the ash slot existed opens with nothing lost. The tags are written by
	 * hand in the old shape (fuel at 0, the four upgrade arms at 1..4), never by the current save path: a
	 * burning generator with charge, coal and a chip in every arm a save of that time could hold. It loads with
	 * the fuel, the charge and the burn where they were, the ash slot empty and each chip one slot up, on its
	 * own arm; the same from a save with no version at all. A save of the new layout is never moved: one the
	 * current jar wrote, one an older jar opened and wrote back (ash in the ash slot), and a full last arm.
	 *
	 * @implements MOD-987-MIGRATION — the v1 to v2 rung moves the upgrade chips, nothing else, nothing lost
	 * @covers R-PER-01
	 */
	public static void oldSavesKeepFuelChargeAndChips(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		helper.assertTrue(BlockEntityDataMigrations.DATA_VERSION == 2, "the ash slot is format version 2, the ladder"
				+ " reads " + BlockEntityDataMigrations.DATA_VERSION);
		String items = "Items:[{Slot:0b,id:\"minecraft:coal\",count:20},"
				+ "{Slot:1b,id:\"alaindustrial:mute_chip\",count:1},"
				+ "{Slot:2b,id:\"minecraft:redstone\",count:1},{Slot:3b,id:\"alaindustrial:stats_chip\",count:1},"
				+ "{Slot:4b,id:\"minecraft:glowstone_dust\",count:1}]";
		String rest = "Energy:2500L,BurnTime:900,BurnDuration:1600";
		for (String version : List.of("AlaDataVersion:1,", "")) {
			GeneratorBlockEntity old = load(helper, registries, "{" + version + rest + "," + items + "}");
			String what = version.isEmpty() ? "a save with no version" : "a version 1 save";
			expect(helper, what, old, Items.COAL, 20, 0);
			expect(helper, what, old, null, 0, GeneratorBlockEntity.ASH_SLOT);
			int start = old.upgradeSlotStart();
			helper.assertTrue(start == GeneratorBlockEntity.SLOT_COUNT, "the upgrade block starts at " + start);
			expect(helper, what, old, ModContent.MUTE_CHIP.get(), 1, start);
			expect(helper, what, old, Items.REDSTONE, 1, start + 1);
			expect(helper, what, old, ModContent.STATS_CHIP.get(), 1, start + 2);
			expect(helper, what, old, Items.GLOWSTONE_DUST, 1, start + 3);
			helper.assertTrue(old.isMuted() && old.hasStatsChip(), what + ": the chips stopped working");
			helper.assertTrue(old.getEnergyStorage().getAmount() == 2500L, what + ": charge "
					+ old.getEnergyStorage().getAmount());
			CompoundTag resaved = old.saveCustomOnly(registries);
			helper.assertTrue(resaved.getIntOr("BurnTime", -1) == 900 && resaved.getIntOr("BurnDuration", -1) == 1600,
					what + ": the burn came back as " + resaved);
			helper.assertTrue(resaved.getIntOr(BlockEntityDataMigrations.DATA_VERSION_KEY, -1) == 2,
					what + ": re-saved without the current version");

			// The re-save is the new layout: loading it again moves nothing.
			GeneratorBlockEntity again = load(helper, registries, resaved);
			expect(helper, what + " re-saved", again, ModContent.MUTE_CHIP.get(), 1, start);
			expect(helper, what + " re-saved", again, Items.GLOWSTONE_DUST, 1, start + 3);
		}

		// A new-layout save an older jar wrote back (no version, ash in the ash slot): the chips are in place.
		GeneratorBlockEntity roundTrip = load(helper, registries, "{" + rest + ",Items:[{Slot:0b,id:\"minecraft:coal\","
				+ "count:5},{Slot:1b,id:\"alaindustrial:ash\",count:4},"
				+ "{Slot:2b,id:\"alaindustrial:mute_chip\",count:1}]}");
		expect(helper, "an older jar's write-back", roundTrip, ModContent.ASH.get(), 4, GeneratorBlockEntity.ASH_SLOT);
		expect(helper, "an older jar's write-back", roundTrip, ModContent.MUTE_CHIP.get(), 1, 2);

		// A full last arm cannot be the old layout; shifting it would overwrite that slot, so nothing moves.
		GeneratorBlockEntity full = load(helper, registries, "{AlaDataVersion:1," + rest + ",Items:[{Slot:1b,"
				+ "id:\"minecraft:redstone\",count:1},{Slot:5b,id:\"minecraft:glowstone_dust\",count:1}]}");
		expect(helper, "a full last arm", full, Items.REDSTONE, 1, GeneratorBlockEntity.ASH_SLOT);
		expect(helper, "a full last arm", full, Items.GLOWSTONE_DUST, 1, 5);
		helper.succeed();
	}

	private static GeneratorBlockEntity load(GameTestHelper helper, RegistryAccess registries, String snbt) {
		return load(helper, registries, SaveFormatTestSupport.parse(snbt));
	}

	private static GeneratorBlockEntity load(GameTestHelper helper, RegistryAccess registries, CompoundTag tag) {
		GeneratorBlockEntity gen = SaveFormatTestSupport.fresh(GeneratorBlockEntity::new, ModContent.GENERATOR.get());
		return SaveFormatTestSupport.load(gen, registries, tag);
	}

	/** Slot {@code slot} holds {@code count} of {@code item}; a {@code null} item means the slot is empty. */
	private static void expect(GameTestHelper helper, String what, GeneratorBlockEntity gen, Item item, int count,
			int slot) {
		ItemStack stack = gen.getItem(slot);
		boolean ok = item == null ? stack.isEmpty() : stack.is(item) && stack.getCount() == count;
		helper.assertTrue(ok, what + ": slot " + slot + " should hold " + (item == null ? "nothing" : count + " x "
				+ item) + ", holds " + stack);
	}
}
