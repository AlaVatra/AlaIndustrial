package dev.alaindustrial.recipe;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModRecipes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The distinct results one input can be smelted into by our furnaces, in the order the result button lists them
 * (MOD-1018). Option 0 is the default, i.e. what the furnace makes while the player has chosen nothing.
 *
 * <p>The order is one rule for the machines and the recipe viewers ({@link SmeltingChoiceIndex}):
 * <ol>
 *   <li>the mod's own {@code alaindustrial:smelting} recipes (the electric furnace only);</li>
 *   <li>{@code minecraft:smelting} recipes of the mod's namespace;</li>
 *   <li>every other {@code minecraft:smelting} recipe, in the order {@code RecipeManager.getRecipes()} lists it.</li>
 * </ol>
 * The mod's recipes come first on purpose: "our result by default" is the owner's rule, and it no longer depends
 * on the order the game happened to load recipes in. Two recipes yielding the same item are one option; the first
 * one in that order is the recipe that runs. The key of an option is its result item's id.
 */
public final class ResultOptions {

	/**
	 * One result the input can be turned into.
	 *
	 * @param key       the result item's id, which the machine stores as the player's choice
	 * @param result    the assembled result of one operation
	 * @param recipe    the recipe that yields it
	 * @param modRecipe the recipe as an {@link AlaProcessingRecipe} when it is the mod's own smelting family,
	 *                  else {@code null} (a vanilla furnace recipe)
	 */
	public record Option(String key, ItemStack result, RecipeHolder<?> recipe,
			@Nullable AlaProcessingRecipe modRecipe) {

		/** The namespace of the recipe's id: the mod the recipe comes from, shown on the button's tooltip. */
		public String source() {
			return recipe.id().identifier().getNamespace();
		}

		/** The recipe's id. */
		public ResourceKey<Recipe<?>> id() {
			return recipe.id();
		}
	}

	private ResultOptions() {
	}

	/**
	 * Every distinct result of {@code input}, default first.
	 *
	 * @param recipes            the server's recipes ({@code RecipeManager.getRecipes()})
	 * @param vanillaType        the vanilla furnace type the machine runs (the station names it, MOD-757)
	 * @param includeModSmelting whether the mod's {@code alaindustrial:smelting} family counts (the electric
	 *                           furnace runs it, the iron furnace does not)
	 */
	public static List<Option> collect(Collection<RecipeHolder<?>> recipes, ItemStack input, Level level,
			RecipeType<SmeltingRecipe> vanillaType, boolean includeModSmelting) {
		if (input.isEmpty()) {
			return List.of();
		}
		List<Option> first = new ArrayList<>();
		List<Option> ownVanilla = new ArrayList<>();
		List<Option> otherVanilla = new ArrayList<>();
		ProcessingRecipeInput modInput = new ProcessingRecipeInput(input);
		SingleRecipeInput vanillaInput = new SingleRecipeInput(input);
		for (RecipeHolder<?> holder : recipes) {
			if (includeModSmelting && holder.value() instanceof AlaProcessingRecipe mod
					&& mod.kind() == ModRecipes.SMELTING && mod.matches(modInput, level)) {
				add(first, holder, mod.assemble(modInput), mod);
			} else if (holder.value() instanceof SmeltingRecipe smelting && smelting.getType() == vanillaType
					&& smelting.matches(vanillaInput, level)) {
				ItemStack result = assembleSafely(smelting, vanillaInput);
				add(isOwn(holder) ? ownVanilla : otherVanilla, holder, result, null);
			}
		}
		Map<String, Option> distinct = new LinkedHashMap<>();
		for (List<Option> group : List.of(first, ownVanilla, otherVanilla)) {
			for (Option option : group) {
				distinct.putIfAbsent(option.key(), option);
			}
		}
		return List.copyOf(distinct.values());
	}

	/** The keys of {@code options}, in order — what {@code ResultPreference} works on. */
	public static List<String> keys(List<Option> options) {
		List<String> keys = new ArrayList<>(options.size());
		for (Option option : options) {
			keys.add(option.key());
		}
		return keys;
	}

	/** The key an item is stored under: its registry id. */
	public static String keyOf(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** Whether a recipe belongs to the mod: its id is in the mod's namespace. */
	public static boolean isOwn(RecipeHolder<?> holder) {
		return Industrialization.MOD_ID.equals(holder.id().identifier().getNamespace());
	}

	private static void add(List<Option> into, RecipeHolder<?> holder, ItemStack result,
			@Nullable AlaProcessingRecipe mod) {
		if (!result.isEmpty()) {
			into.add(new Option(keyOf(result.getItem()), result, holder, mod));
		}
	}

	/**
	 * A furnace recipe's result, or empty when reading it throws: another mod may subclass {@link SmeltingRecipe}
	 * badly, and one broken recipe must not stop the furnace smelting everything else.
	 */
	private static ItemStack assembleSafely(SmeltingRecipe recipe, SingleRecipeInput input) {
		try {
			return recipe.assemble(input);
		} catch (RuntimeException | LinkageError error) {
			Industrialization.LOGGER.warn("A smelting recipe's result could not be read (recipe class {}); "
					+ "the furnace skips it", recipe.getClass().getName(), error);
			return ItemStack.EMPTY;
		}
	}
}
