package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.machine.StatusLine;
import dev.alaindustrial.menu.ElectricFurnaceMenu;
import dev.alaindustrial.network.ResultChoicePayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Electric Furnace. The static frame (panel, recessed slots, empty
 * energy bar, empty progress arrow) comes from a single 256×256 GUI atlas PNG; the dynamic layer
 * (energy fill, progress fill) is drawn by {@link ProgressMachineScreen}. Item icons are drawn by
 * vanilla at the {@link ElectricFurnaceMenu} slot positions, which are aligned to this texture.
 *
 * <p>MOD-1018 adds the result button ({@link ResultChoiceButton}) to the right of the output slot, shown only
 * while the input has two results or more.
 */
public class ElectricFurnaceScreen extends ProgressMachineScreen<ElectricFurnaceMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/electric_furnace.png");

	// Golden progress-arrow fill sprite in the atlas service area (left-to-right, grows with progress).
	private static final ProgressSpec PROGRESS = new ProgressSpec(
			176, 44, 25, 9,  // sprite u/v/w/h
			82, 38,           // dest x/y in the 176×166 frame
			false);           // no min-1px

	/** Click area of the recipe viewers (MOD-716): exactly the progress sprite. */
	public static final GuiRect PROGRESS_AREA = PROGRESS.area();

	/** Atlas, energy bar, progress sprite and status row: the whole screen (MOD-716, CLI-3). */
	private static final MachineLayout LAYOUT = MachineLayout.of(TEXTURE, EnergyBarSpec.LEFT)
			.withProgress(PROGRESS)
			.withStatus(MachineLayout.StatusBand.STANDARD);

	/**
	 * The result button, right of the output slot. Measured on {@code electric_furnace.png}: the output slot's
	 * frame is x 116..133, y 34..51 and the frame is plain panel grey from x 134 to the border at x 173, so the
	 * 12×12 button sits at x 138 (a 4-px gap) and y 37, centred on the slot (34 + (18 - 12) / 2 = 37).
	 */
	public static final ResultChoiceButton RESULT_BUTTON = new ResultChoiceButton(138, 37);

	public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, LAYOUT);
	}

	/**
	 * Status row (MOD-458) — the family's shared "why am I idle" caption: no recipe, a partial batch, a
	 * blocked output, or a buffer that has stayed empty long enough to mean something.
	 */
	@Override
	protected StatusLine status() {
		return this.menu.getStatus();
	}

	/** The result button, drawn under the panels like every control of a machine screen (MOD-693). */
	@Override
	protected void drawUnderPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.drawUnderPanels(graphics, mouseX, mouseY);
		RESULT_BUTTON.draw(graphics, this.leftPos, this.topPos, this.menu.resultChoices(),
				resultButtonLit(mouseX, mouseY));
	}

	/** Whether the result button is shown and under the point, by the rule a click obeys (MOD-738). */
	public boolean resultButtonLit(double mouseX, double mouseY) {
		ResultChoicePayload choices = this.menu.resultChoices();
		return ResultChoiceButton.shown(choices) && frameAcceptsInput(mouseX, mouseY)
				&& RESULT_BUTTON.isOver(this.leftPos, this.topPos, mouseX, mouseY);
	}

	@Override
	protected void controlTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (resultButtonLit(mouseX, mouseY)) {
			List<Component> lines = ResultChoiceButton.tooltip(this.menu.resultChoices());
			if (!lines.isEmpty()) {
				graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			}
		}
	}

	@Override
	protected boolean controlClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && resultButtonLit(event.x(), event.y())
				&& this.minecraft != null) {
			ResultChoiceButton.press(this.minecraft, this.menu);
			return true;
		}
		return false;
	}
}
