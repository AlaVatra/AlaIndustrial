package dev.alaindustrial.recipe;

import dev.alaindustrial.Industrialization;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/**
 * The recipe-conflict detector (MOD-1018): pairs of recipes of one vanilla type where one input yields different
 * results and at least one of the two recipes is ours (namespace {@code alaindustrial}). The game answers such an
 * input with the first recipe it finds, so the other mod's result is unreachable there — exactly what hid Tech
 * Reborn's refined iron behind our tempered iron. Run on a live server by {@code /ala recipes conflicts}.
 *
 * <p><b>What it finds.</b>
 * <ul>
 *   <li>Single-input types — furnace, blast furnace, smoker, campfire, stonecutter: two recipes whose inputs share
 *       at least one item and whose results are different items. The stonecutter lists every result and lets the
 *       player pick, so its pairs are reported as a kind of their own, informational.</li>
 *   <li>Crafting, shaped: two patterns of the same width and height whose every cell is empty in both or shares
 *       an item, as laid out or mirrored left to right (the game matches both).</li>
 *   <li>Crafting, shapeless: the same number of ingredients, and a one-to-one pairing of them where each pair
 *       shares an item.</li>
 * </ul>
 * <b>Limits.</b> A shaped recipe against a shapeless one is not compared; nor are recipes whose result depends on
 * the input (special recipes such as dyeing, repair, map cloning) or other mods' own recipe types. Ingredients are
 * compared by the items their tags hold on this server, so a pair can depend on the pack. A pair with the same
 * result item is a duplicate, not a conflict, and is not reported.
 */
public final class RecipeConflictScan {

	/** The kinds a conflict is reported under, in report order. */
	public enum Kind {
		SMELTING("smelting"), BLASTING("blasting"), SMOKING("smoking"), CAMPFIRE("campfire_cooking"),
		CRAFTING_SHAPED("crafting_shaped"), CRAFTING_SHAPELESS("crafting_shapeless"),
		/** Informational: the stonecutter shows every result and the player picks. */
		STONECUTTING("stonecutting");

		private final String id;

		Kind(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}
	}

	/**
	 * One conflicting pair.
	 *
	 * @param kind        the recipe type both belong to
	 * @param ours        our recipe's id (when both are ours, the first in id order)
	 * @param other       the other recipe's id
	 * @param ourResult   our recipe's result item id
	 * @param otherResult the other recipe's result item id
	 * @param sharedInput items both accept (for crafting, one item of the first shared cell), at most three
	 */
	public record Conflict(Kind kind, String ours, String other, String ourResult, String otherResult,
			List<String> sharedInput) {

		/** One line of the report. */
		public String line() {
			return kind.id() + ": " + ours + " -> " + ourResult + "  vs  " + other + " -> " + otherResult
					+ "  (input " + String.join(", ", sharedInput) + ")";
		}
	}

	private static final int SHOWN_INPUTS = 3;

	private RecipeConflictScan() {
	}

	/** Every conflict among {@code recipes}, grouped by kind and sorted by our recipe's id. */
	public static List<Conflict> scan(Collection<RecipeHolder<?>> recipes) {
		List<Conflict> found = new ArrayList<>();
		Map<Kind, List<Single>> single = new LinkedHashMap<>();
		List<Grid> crafting = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes) {
			Kind kind = singleKind(holder);
			if (kind != null && holder.value() instanceof SingleItemRecipe recipe) {
				Item result = resultOf(holder, () -> recipe.assemble(new SingleRecipeInput(ItemStack.EMPTY)));
				if (result != null) {
					single.computeIfAbsent(kind, k -> new ArrayList<>())
							.add(new Single(holder, items(recipe.input()), result));
				}
			} else if (holder.value() instanceof CraftingRecipe recipe) {
				Grid grid = Grid.of(holder, recipe);
				if (grid != null) {
					crafting.add(grid);
				}
			}
		}
		single.forEach((kind, list) -> singleConflicts(kind, list, found));
		craftingConflicts(crafting, found);
		found.sort(Comparator.comparing((Conflict c) -> c.kind().ordinal()).thenComparing(Conflict::ours)
				.thenComparing(Conflict::other));
		return found;
	}

	private static Kind singleKind(RecipeHolder<?> holder) {
		Object recipe = holder.value();
		if (recipe instanceof SmeltingRecipe) {
			return Kind.SMELTING;
		}
		if (recipe instanceof BlastingRecipe) {
			return Kind.BLASTING;
		}
		if (recipe instanceof SmokingRecipe) {
			return Kind.SMOKING;
		}
		if (recipe instanceof CampfireCookingRecipe) {
			return Kind.CAMPFIRE;
		}
		if (recipe instanceof StonecutterRecipe) {
			return Kind.STONECUTTING;
		}
		return null;
	}

	private record Single(RecipeHolder<?> holder, Set<Item> input, Item result) {
	}

	private static void singleConflicts(Kind kind, List<Single> recipes, List<Conflict> found) {
		Map<Item, List<Single>> byInput = new LinkedHashMap<>();
		for (Single recipe : recipes) {
			for (Item item : recipe.input()) {
				byInput.computeIfAbsent(item, k -> new ArrayList<>()).add(recipe);
			}
		}
		Map<String, Conflict> pairs = new LinkedHashMap<>();
		byInput.forEach((item, list) -> {
			for (int i = 0; i < list.size(); i++) {
				for (int j = i + 1; j < list.size(); j++) {
					Single a = list.get(i);
					Single b = list.get(j);
					if (a.result() != b.result() && (isOurs(a.holder()) || isOurs(b.holder()))) {
						record(pairs, kind, a.holder(), a.result(), b.holder(), b.result(), item);
					}
				}
			}
		});
		found.addAll(pairs.values());
	}

	private static void record(Map<String, Conflict> pairs, Kind kind, RecipeHolder<?> a, Item aResult,
			RecipeHolder<?> b, Item bResult, Item input) {
		boolean aFirst = isOurs(a) && (!isOurs(b) || idOf(a).compareTo(idOf(b)) <= 0);
		RecipeHolder<?> ours = aFirst ? a : b;
		RecipeHolder<?> other = aFirst ? b : a;
		String key = kind + "|" + idOf(ours) + "|" + idOf(other);
		Conflict seen = pairs.get(key);
		String name = itemId(input);
		if (seen == null) {
			pairs.put(key, new Conflict(kind, idOf(ours), idOf(other), itemId(aFirst ? aResult : bResult),
					itemId(aFirst ? bResult : aResult), new ArrayList<>(List.of(name))));
		} else if (seen.sharedInput().size() < SHOWN_INPUTS && !seen.sharedInput().contains(name)) {
			seen.sharedInput().add(name);
		}
	}

	/** A crafting recipe reduced to what the comparison needs; {@code null} for a recipe it cannot read. */
	private record Grid(RecipeHolder<?> holder, boolean shaped, int width, int height, List<Set<Item>> cells,
			Item result) {

		static Grid of(RecipeHolder<?> holder, CraftingRecipe recipe) {
			if (recipe.isSpecial()) {
				return null;
			}
			Item result = resultOf(holder, () -> recipe.assemble(CraftingInput.EMPTY));
			if (result == null) {
				return null;
			}
			if (recipe instanceof ShapedRecipe shaped) {
				List<Set<Item>> cells = new ArrayList<>();
				for (Optional<Ingredient> cell : shaped.getIngredients()) {
					cells.add(cell.map(RecipeConflictScan::items).orElse(Set.of()));
				}
				return new Grid(holder, true, shaped.getWidth(), shaped.getHeight(), cells, result);
			}
			if (recipe instanceof ShapelessRecipe shapeless) {
				List<Set<Item>> cells = new ArrayList<>();
				for (Ingredient ingredient : shapeless.placementInfo().ingredients()) {
					cells.add(items(ingredient));
				}
				return new Grid(holder, false, 0, 0, cells, result);
			}
			return null;
		}
	}

	private static void craftingConflicts(List<Grid> recipes, List<Conflict> found) {
		Map<String, Conflict> pairs = new LinkedHashMap<>();
		for (Grid ours : recipes) {
			if (!isOurs(ours.holder())) {
				continue;
			}
			for (Grid other : recipes) {
				if (other == ours || other.result() == ours.result() || other.shaped() != ours.shaped()
						|| other.cells().size() != ours.cells().size()
						// both ours: one report per pair
						|| (isOurs(other.holder()) && idOf(other.holder()).compareTo(idOf(ours.holder())) < 0)) {
					continue;
				}
				Item shared = ours.shaped() ? shapedOverlap(ours, other) : shapelessOverlap(ours, other);
				if (shared != null) {
					record(pairs, ours.shaped() ? Kind.CRAFTING_SHAPED : Kind.CRAFTING_SHAPELESS,
							ours.holder(), ours.result(), other.holder(), other.result(), shared);
				}
			}
		}
		found.addAll(pairs.values());
	}

	/** An item of the first shared filled cell when the two patterns can take the same grid, else {@code null}. */
	private static Item shapedOverlap(Grid a, Grid b) {
		if (a.width() != b.width() || a.height() != b.height()) {
			return null;
		}
		Item straight = cellsOverlap(a, b, false);
		return straight != null ? straight : cellsOverlap(a, b, true);
	}

	private static Item cellsOverlap(Grid a, Grid b, boolean mirrored) {
		Item first = null;
		for (int y = 0; y < a.height(); y++) {
			for (int x = 0; x < a.width(); x++) {
				Set<Item> left = a.cells().get(y * a.width() + x);
				int bx = mirrored ? a.width() - 1 - x : x;
				Set<Item> right = b.cells().get(y * b.width() + bx);
				if (left.isEmpty() != right.isEmpty()) {
					return null;
				}
				if (!left.isEmpty()) {
					Item common = anyCommon(left, right);
					if (common == null) {
						return null;
					}
					if (first == null) {
						first = common;
					}
				}
			}
		}
		return first;
	}

	/** An item of the pairing when the two ingredient lists can be matched one to one, else {@code null}. */
	private static Item shapelessOverlap(Grid a, Grid b) {
		int n = a.cells().size();
		int[] matchOfB = new int[n];
		Arrays.fill(matchOfB, -1);
		for (int i = 0; i < n; i++) {
			if (!augment(a, b, i, matchOfB, new boolean[n])) {
				return null;
			}
		}
		// Every ingredient of b is paired now; name an item of the pair that b's first ingredient is in.
		return n == 0 ? null : anyCommon(a.cells().get(matchOfB[0]), b.cells().get(0));
	}

	/** Kuhn's augmenting path: give ingredient {@code i} of {@code a} a partner in {@code b}. */
	private static boolean augment(Grid a, Grid b, int i, int[] matchOfB, boolean[] visited) {
		for (int j = 0; j < matchOfB.length; j++) {
			if (visited[j] || anyCommon(a.cells().get(i), b.cells().get(j)) == null) {
				continue;
			}
			visited[j] = true;
			if (matchOfB[j] < 0 || augment(a, b, matchOfB[j], matchOfB, visited)) {
				matchOfB[j] = i;
				return true;
			}
		}
		return false;
	}

	private static Item anyCommon(Set<Item> a, Set<Item> b) {
		Set<Item> small = a.size() <= b.size() ? a : b;
		Set<Item> large = small == a ? b : a;
		for (Item item : small) {
			if (large.contains(item)) {
				return item;
			}
		}
		return null;
	}

	private static boolean isOurs(RecipeHolder<?> holder) {
		return ResultOptions.isOwn(holder);
	}

	private static String idOf(RecipeHolder<?> holder) {
		return holder.id().identifier().toString();
	}

	private static String itemId(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/**
	 * The result item, or {@code null} when it is empty or reading it throws — another mod's recipe may read the
	 * empty input we hand it; one such recipe must not stop the scan.
	 */
	private static Item resultOf(RecipeHolder<?> holder, Supplier<ItemStack> assemble) {
		try {
			ItemStack stack = assemble.get();
			return stack.isEmpty() ? null : stack.getItem();
		} catch (RuntimeException | LinkageError error) {
			Industrialization.LOGGER.debug("Recipe conflict scan skips {}: its result could not be read",
					holder.id().identifier(), error);
			return null;
		}
	}

	// MOD-498: Ingredient#items() is soft-deprecated and no other accessor lists the items (see VanillaSmeltingMirror).
	@SuppressWarnings("deprecation")
	private static Set<Item> items(Ingredient ingredient) {
		Set<Item> items = new HashSet<>();
		ingredient.items().map(Holder::value).forEach(items::add);
		return items;
	}
}
