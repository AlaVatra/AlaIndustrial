package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.machine.ResultChoiceHost;
import dev.alaindustrial.block.entity.machine.ResultChooser;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.ElectricFurnaceMenu;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.ProcessingRecipeInput;
import dev.alaindustrial.recipe.ResultOptions;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * LV electric furnace — works exactly like a vanilla furnace (processes all vanilla smelting
 * recipes) but consumes EU instead of fuel, and is faster. Additionally accepts mod-specific
 * {@code alaindustrial:smelting} recipes (e.g. dusts → ingots) which take priority over vanilla
 * by default when both would match the same input.
 *
 * <p><b>Result button (MOD-1018).</b> When the input has several distinct results — our recipes and every
 * furnace recipe of other mods — the player picks one with the button by the output slot; the choice lives in a
 * {@link ResultChooser} field and is saved with the furnace. Pipes and hoppers only insert, so automation runs the
 * chosen result too.
 *
 * <p>Behaviour lives in {@link AbstractProcessingMachineBlockEntity}; this class only declares the
 * recipe source (mod-smelting first, then vanilla furnace fallback), the default duration, buffer
 * and menu.
 */
public final class ElectricFurnaceBlockEntity extends AbstractProcessingMachineBlockEntity
		implements MenuProvider, ResultChoiceHost {
	/** Input slot index — re-export of the shared processing-machine slot 0. */
	public static final int INPUT_SLOT = 0;
	/** Output slot index — re-export of the shared processing-machine slot 1. */
	public static final int OUTPUT_SLOT = 1;

	/** The result button's choice and the options of the input (MOD-1018). */
	private final ResultChooser chooser = new ResultChooser(RecipeType.SMELTING, true);

	/** Mod-specific smelting recipes — only the slot check asks them now; the tick asks {@link #chooser}. */
	private final RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe> modRecipeCheck =
			checkFor(ModRecipes.SMELTING);
	/** Vanilla furnace recipes — used as fallback when no mod recipe matches. */
	private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> vanillaRecipeCheck =
			RecipeManager.createCheck(RecipeType.SMELTING);

	public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ELECTRIC_FURNACE_BE.get(), pos, state, EnergyTier.LV, Config.machineBuffer,
				() -> Config.electricFurnaceDuration);
	}

	/**
	 * The option the result button selects (MOD-1018): by default the mod's recipe, then the mod's own vanilla
	 * duplicate, then the first other furnace recipe ({@link ResultOptions}).
	 */
	@Override
	protected RecipeSolution resolveInput(ServerLevel level, ItemStack input) {
		ResultOptions.Option option = chooser.selected(level, input);
		if (option == null) {
			return RecipeSolution.empty();
		}
		if (option.modRecipe() != null) {
			return RecipeSolution.of(option.modRecipe());
		}
		// A vanilla furnace recipe carries no EU cost: energy 0 makes the base fall back to the default duration.
		return new RecipeSolution(0, option.result().copy());
	}

	@Override
	protected boolean canPlaceInput(ItemStack stack) {
		// Accept if a mod recipe OR a vanilla smelting recipe matches — re-checked against the live
		// level (resolveInput already does this, but the GUI calls canPlaceItem on the client side too,
		// so we keep the explicit server-side check here for the same reason the original did).
		if (stack.isEmpty() || !(level instanceof ServerLevel sl)) {
			return false;
		}
		if (ModRecipes.lookup(modRecipeCheck, sl, stack) != null) {
			return true;
		}
		return vanillaRecipeCheck.getRecipeFor(new SingleRecipeInput(stack), sl).isPresent();
	}

	@Override
	public ResultChooser resultChooser() {
		return chooser;
	}

	@Override
	public ItemStack resultChoiceInput() {
		return items.get(INPUT_SLOT);
	}

	@Override
	public @Nullable ServerLevel resultChoiceLevel() {
		return level instanceof ServerLevel sl ? sl : null;
	}

	/**
	 * One press of the result button. A different result restarts the operation in progress — it would finish
	 * with a product it did not start — and wakes the furnace, as the sawmill's mode switch does.
	 */
	@Override
	public void cycleResult() {
		if (level instanceof ServerLevel sl && chooser.cycle(sl, items.get(INPUT_SLOT))) {
			progress = 0;
			wake();
		}
		setChanged();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		chooser.save(output);
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		chooser.load(input);
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ElectricFurnaceMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
