package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;

/**
 * Every stairs, slab and wall of the mod can be cut from its block in the stonecutter, with the vanilla
 * yield (MOD-957).
 *
 * <p>The static gate {@code stonecutter_pair_check.py} reads the recipe files; this reads what the running
 * server's {@link RecipeManager} actually loaded, so a recipe that parses to something else (a wrong count, an
 * ingredient the codec resolves differently, a recipe a datapack replaced) fails here. The forms come from the
 * block registry by class ({@code StairBlock}, {@code SlabBlock}, {@code WallBlock}) - not from a list of ids
 * and not from the tags, so a new family is checked without touching this file. The source block of a form is
 * the one mod block its stonecutting recipe accepts; the crafting table must turn that block, laid out in the
 * form's pattern, into the same form, so the pair is "its block" in both directions.
 */
public final class StonecutterDecorScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(StonecutterDecorScenarios::everyFormIsCutFromItsBlock,
						"stonecutter_every_form_is_cut_from_its_block"));

		private Roster() {}
	}

	/** Fewer forms than this means the registry walk found almost nothing, not that the mod shrank (40 today). */
	private static final int MIN_FORMS = 30;

	private StonecutterDecorScenarios() {}

	/** A stonecutting recipe of the mod. */
	private record Cut(Identifier id, StonecutterRecipe recipe) {
		ItemStack result() {
			// A single-item recipe ignores the input when it assembles.
			return recipe.assemble(new SingleRecipeInput(ItemStack.EMPTY));
		}
	}

	/** Which kind of form a block is, with the vanilla stonecutter yield. */
	private enum Kind {
		STAIRS(1), SLAB(2), WALL(1);

		final int yield;

		Kind(int yield) {
			this.yield = yield;
		}

		/** The kind of {@code block}, or {@code null} for any other block. */
		static Kind of(Block block) {
			if (block instanceof StairBlock) {
				return STAIRS;
			}
			if (block instanceof SlabBlock) {
				return SLAB;
			}
			return block instanceof WallBlock ? WALL : null;
		}
	}

	/**
	 * For each stairs, slab and wall of the namespace: exactly one stonecutting recipe makes it, from exactly one
	 * mod block that is not itself a form, with the vanilla count, and that block crafts it in the form's pattern.
	 * A stonecutting recipe of the mod whose result is none of the forms fails too.
	 *
	 * @implements MOD-957 - every stairs, slab and wall of the mod is stonecut from its block with the vanilla yield.
	 */
	public static void everyFormIsCutFromItsBlock(GameTestHelper helper) {
		Level level = helper.getLevel();
		RecipeManager manager = level.getServer().getRecipeManager();
		List<Item> forms = new ArrayList<>();
		List<Item> fullBlocks = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			Item item = block.asItem();
			if (!Industrialization.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())
					|| item == Items.AIR) {
				continue;
			}
			(Kind.of(block) != null ? forms : fullBlocks).add(item);
		}
		List<Cut> cutting = new ArrayList<>();
		for (RecipeHolder<?> holder : manager.getRecipes()) {
			if (Industrialization.MOD_ID.equals(holder.id().identifier().getNamespace())
					&& holder.value() instanceof StonecutterRecipe recipe) {
				cutting.add(new Cut(holder.id().identifier(), recipe));
			}
		}
		List<String> problems = new ArrayList<>();
		if (forms.size() < MIN_FORMS) {
			problems.add("found " + forms.size() + " stairs/slab/wall blocks, fewer than " + MIN_FORMS
					+ " - the registry walk is broken or the forms vanished");
		}
		for (Item form : forms) {
			checkForm(form, cutting, fullBlocks, manager, level, problems);
		}
		for (Cut cut : cutting) {
			if (forms.stream().noneMatch(form -> cut.result().is(form))) {
				problems.add(cut.id() + ": stonecutting recipe whose result is not a stairs, slab or wall of the mod");
			}
		}
		if (!problems.isEmpty()) {
			helper.fail("MOD-957: " + problems.size() + " stonecutter problem(s): " + String.join("; ", problems));
			return;
		}
		helper.succeed();
	}

	private static void checkForm(Item form, List<Cut> cutting, List<Item> fullBlocks, RecipeManager manager,
			Level level, List<String> problems) {
		String name = BuiltInRegistries.ITEM.getKey(form).getPath();
		Kind kind = Kind.of(((BlockItem) form).getBlock());
		List<Cut> making = cutting.stream().filter(cut -> cut.result().is(form)).toList();
		if (making.size() != 1) {
			problems.add(name + ": " + making.size() + " stonecutting recipes make it, expected 1");
			return;
		}
		Cut cut = making.get(0);
		int count = cut.result().getCount();
		if (count != kind.yield) {
			problems.add(name + ": the stonecutter gives " + count + ", vanilla " + kind + " gives " + kind.yield);
		}
		List<Item> sources = fullBlocks.stream()
				.filter(block -> cut.recipe().matches(new SingleRecipeInput(new ItemStack(block)), level)).toList();
		if (sources.size() != 1) {
			problems.add(name + ": the stonecutting recipe accepts " + sources.size() + " mod blocks " + sources
					+ ", expected exactly 1");
			return;
		}
		String crafted = craftedFrom(manager, level, sources.get(0), kind);
		if (!BuiltInRegistries.ITEM.getKey(form).toString().equals(crafted)) {
			problems.add(name + ": " + BuiltInRegistries.ITEM.getKey(sources.get(0)).getPath() + " in the " + kind
					+ " pattern crafts " + crafted + ", not the form");
		}
	}

	/** The id the crafting table makes from {@code block} laid out as the pattern of {@code kind}, or "nothing". */
	private static String craftedFrom(RecipeManager manager, Level level, Item block, Kind kind) {
		ItemStack b = new ItemStack(block);
		ItemStack e = ItemStack.EMPTY;
		CraftingInput input = switch (kind) {
			case SLAB -> CraftingInput.of(3, 1, List.of(b, b, b));
			case WALL -> CraftingInput.of(3, 2, List.of(b, b, b, b, b, b));
			case STAIRS -> CraftingInput.of(3, 3, List.of(b, e, e, b, b, e, b, b, b));
		};
		return manager.getRecipeFor(RecipeType.CRAFTING, input, level)
				.map(holder -> BuiltInRegistries.ITEM.getKey(holder.value().assemble(input).getItem()).toString())
				.orElse("nothing");
	}
}
