package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.ResultChoiceMenu;
import dev.alaindustrial.network.ResultChoicePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * The result button of the electric and iron furnace screens (MOD-1018): a 12×12 square to the right of the
 * output slot that steps the furnace to its next result. Shown only while the input has two results or more
 * ({@link ResultChoicePayload#hasChoice}); the tooltip names the selected result, the mod its recipe comes from
 * and the option number.
 *
 * <p>The press rides the vanilla container-button channel with {@link ResultChoiceMenu#BUTTON_CYCLE_RESULT}; the
 * server answers with a new {@link ResultChoicePayload}, so the button never changes its own state.
 *
 * <p>The art is {@code textures/gui/result_choice_button.png}, 12×24: the idle face on top, the hover face below.
 *
 * @param x left edge, relative to the screen frame — measured against the screen's own texture, see the caller
 * @param y top edge, relative to the screen frame
 */
public record ResultChoiceButton(int x, int y) {

	public static final int SIZE = 12;

	private static final Identifier TEXTURE = Industrialization.id("textures/gui/result_choice_button.png");
	private static final int TEXTURE_WIDTH = SIZE;
	private static final int TEXTURE_HEIGHT = SIZE * 2;

	/** Whether the button is drawn and answers at all: only with a real choice. */
	public static boolean shown(ResultChoicePayload choices) {
		return choices.hasChoice();
	}

	public boolean isOver(int leftPos, int topPos, double mx, double my) {
		int bx = leftPos + x;
		int by = topPos + y;
		return mx >= bx && mx < bx + SIZE && my >= by && my < by + SIZE;
	}

	/** Draws the button when there is a choice; {@code hovered} picks the lit face. */
	public void draw(GuiGraphicsExtractor graphics, int leftPos, int topPos, ResultChoicePayload choices,
			boolean hovered) {
		if (!shown(choices)) {
			return;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + x, topPos + y, 0.0F, hovered ? SIZE : 0.0F,
				SIZE, SIZE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
	}

	/** The tooltip lines: the selected result, its recipe's mod, and "n of m" with what a click does. */
	public static List<Component> tooltip(ResultChoicePayload choices) {
		List<Component> lines = new ArrayList<>(3);
		ResultChoicePayload.Entry entry = choices.selectedEntry();
		if (entry == null) {
			return lines;
		}
		lines.add(Component.translatable("gui.alaindustrial.result_choice.result", entry.result().getHoverName()));
		lines.add(Component.translatable("gui.alaindustrial.result_choice.source", entry.source()));
		lines.add(Component.translatable("gui.alaindustrial.result_choice.hint", choices.selected() + 1,
				choices.options().size()));
		return lines;
	}

	/** Sends one press for {@code menu} and plays the vanilla button click. */
	public static void press(Minecraft minecraft, AbstractContainerMenu menu) {
		if (minecraft.gameMode == null) {
			return;
		}
		minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ResultChoiceMenu.BUTTON_CYCLE_RESULT);
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}
