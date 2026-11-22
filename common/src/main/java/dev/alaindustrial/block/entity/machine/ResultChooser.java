package dev.alaindustrial.block.entity.machine;

import dev.alaindustrial.core.machine.ResultPreference;
import dev.alaindustrial.recipe.ResultOptions;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The result button of a furnace (MOD-1018): the distinct results of the current input, the player's stored
 * choice, and which option the furnace therefore runs. One field of the electric furnace and of the iron furnace;
 * neither re-implements the rule ({@link ResultPreference}) or the order of the options ({@link ResultOptions}).
 *
 * <p><b>Saved as the chosen result item's id</b> under {@link #SAVE_KEY}; no key means the default. Adding the
 * key changes no slot, so a furnace saved before MOD-1018 loads as it was, with no migration rung.
 *
 * <p><b>Cached per input.</b> The furnace asks every tick it works; listing the options walks every recipe of the
 * server, so the list is kept until the input changes (same item and components) or the server's
 * {@link RecipeManager} is replaced, which a {@code /reload} does.
 */
public final class ResultChooser {

	/** The NBT key of the stored choice. */
	public static final String SAVE_KEY = "result_choice";

	private final RecipeType<SmeltingRecipe> vanillaType;
	private final boolean includeModSmelting;

	/** The chosen result item's id, or {@code null} while the furnace runs its default. */
	private @Nullable String preferred;

	private ItemStack cachedInput = ItemStack.EMPTY;
	private @Nullable RecipeManager cachedRecipes;
	private List<ResultOptions.Option> cachedOptions = List.of();

	/**
	 * @param vanillaType        the vanilla furnace type the machine runs; the machine names it itself so the
	 *                           guide's station check can read it from its source (MOD-757)
	 * @param includeModSmelting whether the mod's own {@code alaindustrial:smelting} family is one of the sources
	 *                           (the electric furnace) or not (the iron furnace)
	 */
	public ResultChooser(RecipeType<SmeltingRecipe> vanillaType, boolean includeModSmelting) {
		this.vanillaType = vanillaType;
		this.includeModSmelting = includeModSmelting;
	}

	/** Every distinct result of {@code input}, default first; empty for an empty input or no recipe. */
	public List<ResultOptions.Option> options(ServerLevel level, ItemStack input) {
		if (input.isEmpty()) {
			return List.of();
		}
		RecipeManager recipes = level.recipeAccess();
		if (recipes != cachedRecipes || !ItemStack.isSameItemSameComponents(input, cachedInput)) {
			cachedOptions = ResultOptions.collect(recipes.getRecipes(), input, level, vanillaType,
					includeModSmelting);
			cachedInput = input.copyWithCount(1);
			cachedRecipes = recipes;
		}
		return cachedOptions;
	}

	/** The position of the option the furnace runs for {@code input}, or -1 when it has none. */
	public int selectedIndex(ServerLevel level, ItemStack input) {
		return ResultPreference.selectedIndex(ResultOptions.keys(options(level, input)), preferred);
	}

	/** The option the furnace runs for {@code input}, or {@code null} when nothing smelts it. */
	public ResultOptions.@Nullable Option selected(ServerLevel level, ItemStack input) {
		List<ResultOptions.Option> options = options(level, input);
		int at = ResultPreference.selectedIndex(ResultOptions.keys(options), preferred);
		return at < 0 ? null : options.get(at);
	}

	/**
	 * One press of the button: step to the next result of {@code input}.
	 *
	 * @return whether the option the furnace runs changed — the caller then restarts the operation in progress
	 */
	public boolean cycle(ServerLevel level, ItemStack input) {
		List<String> keys = ResultOptions.keys(options(level, input));
		int before = ResultPreference.selectedIndex(keys, preferred);
		preferred = ResultPreference.afterCycle(keys, preferred);
		return ResultPreference.selectedIndex(keys, preferred) != before;
	}

	/** The stored choice (a result item's id), or {@code null} for the default. */
	public @Nullable String preferred() {
		return preferred;
	}

	public void save(ValueOutput output) {
		if (preferred != null) {
			output.putString(SAVE_KEY, preferred);
		}
	}

	public void load(ValueInput input) {
		preferred = input.getString(SAVE_KEY).filter(id -> !id.isEmpty()).orElse(null);
	}
}
