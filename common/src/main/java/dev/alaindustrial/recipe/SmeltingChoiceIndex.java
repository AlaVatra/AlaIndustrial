package dev.alaindustrial.recipe;

import dev.alaindustrial.registry.ModRecipes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;

/**
 * Which electric-furnace cards of the recipe viewers show a result that the furnace makes only after a press of
 * its result button (MOD-1018): the recipes whose result is not the default for at least one item their input
 * accepts. Their card says so in the output slot's tooltip, so the category neither hides another mod's smelt nor
 * pretends the furnace makes it without a press.
 *
 * <p>The default is decided by the order {@link ResultOptions} uses at runtime — the mod's smelting family, then
 * the mod's {@code minecraft:smelting} recipes, then every other one in the collection's order — so a card and
 * the machine cannot disagree about which result comes first. JEI asks this on the client from the recipes it
 * lists, REI on the server from the recipes it fills its displays from.
 */
public final class SmeltingChoiceIndex {

	private SmeltingChoiceIndex() {
	}

	/** The ids of the recipes whose result needs the button for some input item. */
	public static Set<ResourceKey<Recipe<?>>> choiceOnly(Collection<RecipeHolder<?>> recipes) {
		List<RecipeHolder<?>> ordered = new ArrayList<>();
		List<RecipeHolder<?>> ownVanilla = new ArrayList<>();
		List<RecipeHolder<?>> otherVanilla = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes) {
			if (holder.value() instanceof AlaProcessingRecipe mod && mod.kind() == ModRecipes.SMELTING) {
				ordered.add(holder);
			} else if (holder.value() instanceof SmeltingRecipe) {
				(ResultOptions.isOwn(holder) ? ownVanilla : otherVanilla).add(holder);
			}
		}
		ordered.addAll(ownVanilla);
		ordered.addAll(otherVanilla);

		Map<Item, Item> defaultResult = new HashMap<>();
		Set<ResourceKey<Recipe<?>>> choiceOnly = new HashSet<>();
		for (RecipeHolder<?> holder : ordered) {
			AlaProcessingRecipe card = holder.value() instanceof SmeltingRecipe smelting
					? VanillaSmeltingMirror.mirror(smelting)
					: (AlaProcessingRecipe) holder.value();
			if (card == null || card.ingredients().size() != 1 || card.resultStack().isEmpty()) {
				continue;
			}
			Item result = card.resultStack().getItem();
			for (Item input : itemsOf(card.ingredient())) {
				Item first = defaultResult.putIfAbsent(input, result);
				if (first != null && first != result) {
					choiceOnly.add(holder.id());
				}
			}
		}
		return choiceOnly;
	}

	// MOD-498: Ingredient#items() is soft-deprecated, and no other accessor lists the items (see
	// VanillaSmeltingMirror#itemsOf, which this mirrors).
	@SuppressWarnings("deprecation")
	private static List<Item> itemsOf(Ingredient ingredient) {
		return ingredient.items().map(Holder::value).toList();
	}
}
