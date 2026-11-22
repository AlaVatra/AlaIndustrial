package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity;
import dev.alaindustrial.block.entity.IronFurnaceBlockEntity;
import dev.alaindustrial.block.entity.machine.ResultChooser;
import dev.alaindustrial.menu.ResultChoiceMenu;
import dev.alaindustrial.network.ResultChoicePayload;
import dev.alaindustrial.network.ResultChoiceSync;
import dev.alaindustrial.recipe.RecipeConflictScan;
import dev.alaindustrial.recipe.ResultOptions;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * The result button of the electric and iron furnaces and the recipe-conflict detector (MOD-1018), run by every
 * loader from the roster.
 *
 * <p>The fixtures are planted recipes of the gametest pack ({@code data/alaindustrial_gametest/recipe/
 * result_choice_*.json}, one copy per loader): poor slag, which our own furnace recipe turns into cement, also
 * smelts into gravel; a twin of our trellis pattern and of our apiary map's ingredients craft something else; and a
 * near-miss trellis whose middle column takes a different item, which must not count as a conflict. The poor slag
 * models Tech Reborn's refined iron: two furnace recipes, one input, and before MOD-1018 our furnaces only ever made
 * the first.
 */
public final class ResultChoiceScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ResultChoiceScenarios::electricFurnaceRunsTheChosenResult,
						"result_choice_electric_furnace_runs_the_chosen_result").ticks(20),
				RosterEntry.of(ResultChoiceScenarios::ironFurnaceRunsTheChosenResult,
						"result_choice_iron_furnace_runs_the_chosen_result").ticks(20),
				RosterEntry.of(ResultChoiceScenarios::choiceSurvivesSaveAndFallsBackPerInput,
						"result_choice_survives_save_and_falls_back").ticks(20),
				RosterEntry.of(ResultChoiceScenarios::conflictDetectorFindsThePlantedPairs,
						"result_choice_conflict_detector_finds_planted_pairs").ticks(20));

		private Roster() {}
	}

	private static final BlockPos FURNACE = new BlockPos(1, 2, 1);
	private static final BlockPos RELOADED = new BlockPos(3, 2, 1);
	private static final BlockPos OLD_SAVE = new BlockPos(1, 2, 3);

	private static final String OUR_SMELT = "alaindustrial:cement_from_slag_poor";
	private static final String PLANTED_SMELT = "alaindustrial_gametest:result_choice_slag_poor_to_gravel";

	private ResultChoiceScenarios() {
	}

	/**
	 * Poor slag in the electric furnace: by default our cement, as before; after one press of the result button the
	 * planted gravel. Before MOD-1018 the furnace made cement whatever the player did.
	 *
	 * @implements MOD-1018 — the electric furnace lists every result of its input, ours first, and runs the
	 *     chosen one.
	 */
	public static void electricFurnaceRunsTheChosenResult(GameTestHelper helper) {
		ElectricFurnaceBlockEntity furnace = AlaGameTestHelper.place(helper, FURNACE,
				ModContent.ELECTRIC_FURNACE.get(), ElectricFurnaceBlockEntity.class);
		furnace.setItem(ElectricFurnaceBlockEntity.INPUT_SLOT, new ItemStack(ModContent.SLAG_POOR.get(), 4));
		if (!assertOptions(helper, ResultChoiceSync.snapshot(0, furnace), 0)) {
			return;
		}
		if (!smeltOne(helper, furnace, ModContent.CEMENT.get(), "the default (no press)")) {
			return;
		}
		furnace.removeItem(ElectricFurnaceBlockEntity.OUTPUT_SLOT, 64);
		ServerPlayer player = player(helper);
		press(helper, player, furnace.createMenu(1, player.getInventory(), player));
		if (!assertOptions(helper, ResultChoiceSync.snapshot(0, furnace), 1)) {
			return;
		}
		if (!smeltOne(helper, furnace, Items.GRAVEL, "the result chosen with one press")) {
			return;
		}
		helper.succeed();
	}

	/**
	 * The same in the iron furnace, with the input brought by a hopper from above: automation only inserts, the
	 * furnace makes the chosen result.
	 *
	 * @implements MOD-1018 — the iron furnace runs the chosen result, also for an input a hopper inserted.
	 */
	public static void ironFurnaceRunsTheChosenResult(GameTestHelper helper) {
		helper.setBlock(FURNACE, ModContent.IRON_FURNACE.get());
		IronFurnaceBlockEntity furnace = helper.getBlockEntity(FURNACE, IronFurnaceBlockEntity.class);
		furnace.setItem(IronFurnaceBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 4));
		ItemStack left = HopperBlockEntity.addItem(new SimpleContainer(1), furnace,
				new ItemStack(ModContent.SLAG_POOR.get(), 4), Direction.UP);
		helper.assertTrue(left.isEmpty() && furnace.getItem(IronFurnaceBlockEntity.INPUT_SLOT).getCount() == 4,
				"fixture error: a hopper above must insert the poor slag into the input slot, left " + left);
		if (!assertOptions(helper, ResultChoiceSync.snapshot(0, furnace), 0)) {
			return;
		}
		ServerPlayer player = player(helper);
		press(helper, player, furnace.createMenu(1, player.getInventory(), player));
		if (!assertOptions(helper, ResultChoiceSync.snapshot(0, furnace), 1)) {
			return;
		}
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(FURNACE);
		int bound = 2 * Math.max(1, Config.ironFurnaceCookTime) + 10;
		for (int tick = 0; tick < bound && furnace.getItem(IronFurnaceBlockEntity.RESULT_SLOT).isEmpty(); tick++) {
			IronFurnaceBlockEntity.serverTick(level, abs, level.getBlockState(abs), furnace);
		}
		ItemStack out = furnace.getItem(IronFurnaceBlockEntity.RESULT_SLOT);
		helper.assertTrue(out.is(Items.GRAVEL), "the iron furnace must make the chosen gravel, made " + out);
		helper.succeed();
	}

	/**
	 * The choice is saved as the result item's id; a save without it (every furnace before MOD-1018) loads with the
	 * default. An input that has no such result runs its own default, and the stored choice comes back with the old
	 * input; a press that wraps round to the default clears it.
	 *
	 * @implements MOD-1018 — the furnace keeps the chosen result item across a save, per input, and old saves load.
	 */
	public static void choiceSurvivesSaveAndFallsBackPerInput(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ElectricFurnaceBlockEntity furnace = AlaGameTestHelper.place(helper, FURNACE,
				ModContent.ELECTRIC_FURNACE.get(), ElectricFurnaceBlockEntity.class);
		furnace.setItem(ElectricFurnaceBlockEntity.INPUT_SLOT, new ItemStack(ModContent.SLAG_POOR.get(), 1));
		furnace.cycleResult();
		CompoundTag tag = furnace.saveCustomOnly(level.registryAccess());
		helper.assertTrue("minecraft:gravel".equals(tag.getStringOr(ResultChooser.SAVE_KEY, "")),
				"the save must carry the chosen result item, got " + tag);

		ElectricFurnaceBlockEntity reloaded = AlaGameTestHelper.place(helper, RELOADED,
				ModContent.ELECTRIC_FURNACE.get(), ElectricFurnaceBlockEntity.class);
		reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		if (!assertOptions(helper, ResultChoiceSync.snapshot(0, reloaded), 1)) {
			return;
		}

		// Raw iron has one result: the choice does not apply, and is not forgotten.
		reloaded.setItem(ElectricFurnaceBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 1));
		ResultChoicePayload iron = ResultChoiceSync.snapshot(0, reloaded);
		helper.assertTrue(iron.options().size() == 1 && iron.selected() == 0 && !iron.hasChoice(),
				"raw iron must have one result, selected and with no button, got " + describe(iron));
		helper.assertTrue("minecraft:gravel".equals(reloaded.resultChooser().preferred()),
				"an input without the chosen result must keep the stored choice");
		reloaded.setItem(ElectricFurnaceBlockEntity.INPUT_SLOT, new ItemStack(ModContent.SLAG_POOR.get(), 1));
		if (!assertOptions(helper, ResultChoiceSync.snapshot(0, reloaded), 1)) {
			return;
		}
		reloaded.cycleResult();
		helper.assertTrue(reloaded.resultChooser().preferred() == null
						&& ResultChoiceSync.snapshot(0, reloaded).selected() == 0,
				"a press on the last option must wrap round to the default and clear the choice");

		CompoundTag oldSave = tag.copy();
		oldSave.remove(ResultChooser.SAVE_KEY);
		ElectricFurnaceBlockEntity old = AlaGameTestHelper.place(helper, OLD_SAVE,
				ModContent.ELECTRIC_FURNACE.get(), ElectricFurnaceBlockEntity.class);
		old.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), oldSave));
		helper.assertTrue(old.getItem(ElectricFurnaceBlockEntity.INPUT_SLOT).is(ModContent.SLAG_POOR.get()),
				"a save without the choice must keep its input");
		if (!assertOptions(helper, ResultChoiceSync.snapshot(0, old), 0)) {
			return;
		}
		helper.succeed();
	}

	/**
	 * The detector reports each planted pair under its kind, ours first, and leaves the near-miss pattern alone.
	 *
	 * @implements MOD-1018 — the recipe-conflict detector finds one input with different results where one recipe is
	 *     ours, for furnace recipes and for shaped and shapeless crafting.
	 */
	public static void conflictDetectorFindsThePlantedPairs(GameTestHelper helper) {
		List<RecipeConflictScan.Conflict> found =
				RecipeConflictScan.scan(helper.getLevel().getServer().getRecipeManager().getRecipes());
		if (!assertFound(helper, found, RecipeConflictScan.Kind.SMELTING, OUR_SMELT, PLANTED_SMELT,
						"alaindustrial:cement", "minecraft:gravel")
				|| !assertFound(helper, found, RecipeConflictScan.Kind.CRAFTING_SHAPED, "alaindustrial:trellis",
						"alaindustrial_gametest:result_choice_trellis_twin", "alaindustrial:trellis",
						"minecraft:ladder")
				|| !assertFound(helper, found, RecipeConflictScan.Kind.CRAFTING_SHAPELESS, "alaindustrial:apiary_map",
						"alaindustrial_gametest:result_choice_apiary_map_twin", "alaindustrial:apiary_map",
						"minecraft:book")) {
			return;
		}
		for (RecipeConflictScan.Conflict conflict : found) {
			if (conflict.other().equals("alaindustrial_gametest:result_choice_trellis_near_miss")) {
				helper.fail("the near-miss trellis shares no item in its middle column and must not be reported: "
						+ conflict.line());
				return;
			}
		}
		helper.succeed();
	}

	// --- helpers ---

	/** Checks the button's view of poor slag: cement (ours) then gravel (planted), with {@code selected} chosen. */
	private static boolean assertOptions(GameTestHelper helper, ResultChoicePayload choices, int selected) {
		List<ResultChoicePayload.Entry> options = choices.options();
		boolean ok = options.size() == 2
				&& options.get(0).result().is(ModContent.CEMENT.get())
				&& "alaindustrial".equals(options.get(0).source())
				&& options.get(1).result().is(Items.GRAVEL)
				&& "alaindustrial_gametest".equals(options.get(1).source())
				&& choices.selected() == selected && choices.hasChoice();
		if (!ok) {
			helper.fail("poor slag must offer cement (ours, default) then gravel (planted), option " + selected
					+ " selected; got " + describe(choices));
		}
		return ok;
	}

	private static String describe(ResultChoicePayload choices) {
		StringBuilder text = new StringBuilder("[");
		for (ResultChoicePayload.Entry entry : choices.options()) {
			text.append(ResultOptions.keyOf(entry.result().getItem())).append(" from ").append(entry.source())
					.append("; ");
		}
		return text.append("] selected ").append(choices.selected()).toString();
	}

	/** Runs the furnace until one item is in its output and checks it is {@code expected}. */
	private static boolean smeltOne(GameTestHelper helper, ElectricFurnaceBlockEntity furnace, Item expected,
			String what) {
		int bound = 4 * Math.max(1, Config.electricFurnaceDuration) + 40;
		for (int tick = 0; tick < bound && furnace.getItem(ElectricFurnaceBlockEntity.OUTPUT_SLOT).isEmpty();
				tick++) {
			furnace.getEnergyStorage().setAmountUntracked(Config.machineBuffer);
			AlaGameTestHelper.drive(furnace, helper, 1);
		}
		ItemStack out = furnace.getItem(ElectricFurnaceBlockEntity.OUTPUT_SLOT);
		if (!out.is(expected)) {
			helper.fail("the electric furnace must make " + ResultOptions.keyOf(expected) + " for " + what
					+ ", made " + out);
			return false;
		}
		return true;
	}

	/** Presses the result button through the menu, as the screen's click does. */
	private static void press(GameTestHelper helper, ServerPlayer player, AbstractContainerMenu menu) {
		helper.assertTrue(menu.clickMenuButton(player, ResultChoiceMenu.BUTTON_CYCLE_RESULT),
				"the menu must accept the result button press");
	}

	private static ServerPlayer player(GameTestHelper helper) {
		return AlaGameTestHelper.survivalPlayer(helper);
	}

	private static boolean assertFound(GameTestHelper helper, List<RecipeConflictScan.Conflict> found,
			RecipeConflictScan.Kind kind, String ours, String other, String ourResult, String otherResult) {
		for (RecipeConflictScan.Conflict conflict : found) {
			if (conflict.kind() == kind && conflict.ours().equals(ours) && conflict.other().equals(other)) {
				boolean ok = conflict.ourResult().equals(ourResult) && conflict.otherResult().equals(otherResult);
				if (!ok) {
					helper.fail("the detector named the wrong results: " + conflict.line());
				}
				return ok;
			}
		}
		helper.fail("the detector missed the planted " + kind.id() + " pair " + ours + " vs " + other + "; found "
				+ found.size() + " conflicts");
		return false;
	}
}
