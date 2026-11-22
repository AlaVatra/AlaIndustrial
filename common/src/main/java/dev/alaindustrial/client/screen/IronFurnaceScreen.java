package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.menu.IronFurnaceMenu;
import dev.alaindustrial.network.ResultChoicePayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeBookCategories;

/**
 * Screen of the iron furnace (MOD-1018): the vanilla furnace screen — its texture, flame and arrow sprites and
 * recipe-book tabs, the same identifiers the vanilla {@code FurnaceScreen} passes (read from the 26.3 bytecode) —
 * plus the result button to the right of the output slot. Before MOD-1018 the iron furnace opened the stock
 * {@code FurnaceScreen}, which has no place for it.
 */
public class IronFurnaceScreen extends AbstractFurnaceScreen<IronFurnaceMenu> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
	private static final Identifier LIT_PROGRESS_SPRITE =
			Identifier.withDefaultNamespace("container/furnace/lit_progress");
	private static final Identifier BURN_PROGRESS_SPRITE =
			Identifier.withDefaultNamespace("container/furnace/burn_progress");
	private static final Component FILTER_NAME = Component.translatable("gui.recipebook.toggleRecipes.smeltable");
	private static final List<RecipeBookComponent.TabInfo> TABS = List.of(
			new RecipeBookComponent.TabInfo(SearchRecipeBookCategory.FURNACE),
			new RecipeBookComponent.TabInfo(Items.PORKCHOP, RecipeBookCategories.FURNACE_FOOD),
			new RecipeBookComponent.TabInfo(Items.STONE, RecipeBookCategories.FURNACE_BLOCKS),
			new RecipeBookComponent.TabInfo(Items.LAVA_BUCKET, Items.EMERALD, RecipeBookCategories.FURNACE_MISC));

	/**
	 * The result button, right of the output slot. Measured on the vanilla 26.3 {@code furnace.png}: the output
	 * slot's large frame is x 111..136, y 30..55 and the panel is plain grey from x 137 to the border at x 173, so
	 * the 12×12 button sits at x 142 (a 5-px gap) and y 37, centred on the frame (30 + (26 - 12) / 2 = 37).
	 */
	public static final ResultChoiceButton RESULT_BUTTON = new ResultChoiceButton(142, 37);

	public IronFurnaceScreen(IronFurnaceMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, FILTER_NAME, TEXTURE, LIT_PROGRESS_SPRITE, BURN_PROGRESS_SPRITE, TABS);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		RESULT_BUTTON.draw(graphics, this.leftPos, this.topPos, this.menu.resultChoices(),
				resultButtonLit(mouseX, mouseY));
	}

	/**
	 * Whether the result button is shown and under the point. {@code isHovering} is the recipe-book screen's own
	 * rule: it answers false while an open recipe book covers the frame on a narrow window.
	 */
	public boolean resultButtonLit(double mouseX, double mouseY) {
		ResultChoicePayload choices = this.menu.resultChoices();
		return ResultChoiceButton.shown(choices) && isHovering(RESULT_BUTTON.x(), RESULT_BUTTON.y(),
				ResultChoiceButton.SIZE, ResultChoiceButton.SIZE, mouseX, mouseY);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (resultButtonLit(mouseX, mouseY)) {
			List<Component> lines = ResultChoiceButton.tooltip(this.menu.resultChoices());
			if (!lines.isEmpty()) {
				graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && resultButtonLit(event.x(), event.y())
				&& this.minecraft != null) {
			ResultChoiceButton.press(this.minecraft, this.menu);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}
}
