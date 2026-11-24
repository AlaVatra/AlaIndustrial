package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.block.entity.elevator.ElevatorPanel;
import dev.alaindustrial.block.entity.elevator.ElevatorShaft;
import dev.alaindustrial.menu.ElevatorPanelMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * The elevator's floor panel (MOD-769, the owner's round 3): one button per floor, laid out like the panel in a real
 * cabin, floor 1 at the bottom and the top floor at the top, in up to four columns. Each button names its floor and
 * the trip's price; the cabin's own floor is lit and marked, a floor the cabin cannot reach is shut, and the cursor on
 * a button says why. A shaft that does not run shuts every button and says what is wrong under them.
 *
 * <p>Drawn in the mod's plain panel style ({@link GuiStyle}), no texture of its own. Nothing here decides anything: a
 * press sends the floor's index over vanilla's container-button packet and the server sends the cabin
 * ({@link ElevatorPanelMenu#clickMenuButton}). The floors arrive as container data after the screen opens, so the panel
 * sizes itself every frame to the floors it shows.
 */
public class ElevatorPanelScreen extends AbstractContainerScreen<ElevatorPanelMenu> {

	public static final int BUTTON_W = 84;
	public static final int BUTTON_H = 16;
	private static final int GAP = 3;
	private static final int PAD = 7;
	private static final int HEADER_H = 18;
	private static final int LINE_H = 10;
	/** Rows of buttons in one column before the panel opens another. */
	private static final int COLUMN_ROWS = 8;
	private static final int MAX_COLUMNS = (ElevatorPanel.MAX_ROWS + COLUMN_ROWS - 1) / COLUMN_ROWS;
	private static final int TOOLTIP_WIDTH = 180;

	public ElevatorPanelScreen(ElevatorPanelMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PAD * 2 + MAX_COLUMNS * BUTTON_W + (MAX_COLUMNS - 1) * GAP,
				HEADER_H + COLUMN_ROWS * (BUTTON_H + GAP) + PAD);
	}

	private int columns() {
		return Math.max(1, (this.menu.shownCount() + COLUMN_ROWS - 1) / COLUMN_ROWS);
	}

	private int rows() {
		int shown = this.menu.shownCount();
		return Math.max(1, (shown + columns() - 1) / columns());
	}

	private int panelWidth() {
		int buttons = columns() * BUTTON_W + (columns() - 1) * GAP;
		return PAD * 2 + Math.max(buttons, this.font.width(this.title));
	}

	private List<FormattedCharSequence> problemLines() {
		ElevatorShaft.Problem problem = this.menu.problem();
		if (problem == ElevatorShaft.Problem.NONE) {
			return List.of();
		}
		String key = "message.alaindustrial.elevator.problem." + problem.id;
		Component text = problem == ElevatorShaft.Problem.GAP ? Component.translatable(key, this.menu.problemAt())
				: Component.translatable(key);
		return this.font.split(text, panelWidth() - PAD * 2);
	}

	private int panelHeight() {
		int lines = problemLines().size();
		return HEADER_H + rows() * (BUTTON_H + GAP) - GAP + PAD + (lines == 0 ? 0 : lines * LINE_H + 4);
	}

	/** The panel follows the floors it shows: centred on the screen at their size. */
	private void place() {
		this.leftPos = (this.width - panelWidth()) / 2;
		this.topPos = (this.height - panelHeight()) / 2;
	}

	/** Screen x of the left edge of the button of row {@code row}. */
	private int buttonX(int row) {
		return this.leftPos + PAD + (row % columns()) * (BUTTON_W + GAP);
	}

	/** Screen y of the top edge of the button of row {@code row}: the first row at the bottom. */
	private int buttonY(int row) {
		int line = row / columns();
		return this.topPos + HEADER_H + (rows() - 1 - line) * (BUTTON_H + GAP);
	}

	/** The row whose button is under the cursor, or -1. */
	public int buttonAt(double mouseX, double mouseY) {
		place();
		for (int row = 0; row < this.menu.shownCount(); row++) {
			int x = buttonX(row);
			int y = buttonY(row);
			if (mouseX >= x && mouseX < x + BUTTON_W && mouseY >= y && mouseY < y + BUTTON_H) {
				return row;
			}
		}
		return -1;
	}

	/** The middle of the button of row {@code row}, in GUI pixels: where a frame puts the cursor. */
	public int[] buttonMiddle(int row) {
		place();
		return new int[] {buttonX(row) + BUTTON_W / 2, buttonY(row) + BUTTON_H / 2};
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// The panel moves with its size, so its text is drawn with it in extractBackground.
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		place();
		int width = panelWidth();
		GuiStyle.panel(graphics, this.leftPos, this.topPos, width, panelHeight());
		graphics.text(this.font, this.title, this.leftPos + PAD, this.topPos + 6, GuiStyle.LABEL, false);
		int hovered = buttonAt(mouseX, mouseY);
		for (int row = 0; row < this.menu.shownCount(); row++) {
			drawButton(graphics, row, row == hovered);
		}
		int y = this.topPos + HEADER_H + rows() * (BUTTON_H + GAP) - GAP + 4;
		for (FormattedCharSequence line : problemLines()) {
			graphics.text(this.font, line, this.leftPos + PAD, y, GuiStyle.STATUS_BLOCKING, false);
			y += LINE_H;
		}
	}

	private void drawButton(GuiGraphicsExtractor graphics, int row, boolean hovered) {
		int x = buttonX(row);
		int y = buttonY(row);
		ElevatorPanel.FloorState state = this.menu.state(row);
		boolean pressable = this.menu.pressable(row);
		boolean here = state == ElevatorPanel.FloorState.HERE;
		if (here) {
			graphics.fill(x, y, x + BUTTON_W, y + BUTTON_H, GuiStyle.BUTTON_ACTIVE_EDGE);
			graphics.fill(x + 1, y + 1, x + BUTTON_W - 1, y + BUTTON_H - 1, GuiStyle.BUTTON_ACTIVE);
		} else {
			graphics.fill(x, y, x + BUTTON_W, y + BUTTON_H, pressable ? GuiStyle.BUTTON : GuiStyle.SLOT);
		}
		if (hovered && pressable) {
			graphics.fill(x, y, x + BUTTON_W, y + BUTTON_H, GuiStyle.HOVER_WASH);
		}
		int textY = y + (BUTTON_H - 8) / 2;
		int labelColour = pressable || here ? GuiStyle.HEADER_TEXT : GuiStyle.TEXT_DIM;
		graphics.text(this.font, floorName(row), x + 4, textY, labelColour, false);
		@Nullable Component side = sideText(row, state);
		if (side != null) {
			int colour = state == ElevatorPanel.FloorState.NO_ENERGY ? GuiStyle.STATUS_BLOCKING
					: here ? GuiStyle.BUTTON_ACTIVE_EDGE : GuiStyle.PANEL;
			graphics.text(this.font, side, x + BUTTON_W - 4 - this.font.width(side), textY, colour, false);
		}
	}

	private Component floorName(int row) {
		return Component.translatable("message.alaindustrial.elevator.floor", this.menu.firstShown() + row + 1);
	}

	/** What the right of a button says: the price, or that the cabin is here; nothing for a cut-off floor. */
	private @Nullable Component sideText(int row, ElevatorPanel.FloorState state) {
		return switch (state) {
			case HERE -> Component.translatable("gui.alaindustrial.elevator_panel.here");
			case OPEN, NO_ENERGY -> Component.translatable("gui.alaindustrial.elevator_panel.fare",
					this.menu.value(row));
			case GAP -> null;
		};
	}

	/** Why a button does what it does: where it sends the cabin and for how much, or why it is shut. */
	private Component reason(int row) {
		if (this.menu.problem() != ElevatorShaft.Problem.NONE) {
			return Component.translatable("message.alaindustrial.elevator.problem." + this.menu.problem().id,
					this.menu.problemAt());
		}
		return switch (this.menu.state(row)) {
			case OPEN -> Component.translatable("gui.alaindustrial.elevator_panel.go", floorName(row),
					this.menu.value(row));
			case HERE -> Component.translatable("message.alaindustrial.elevator.here");
			case NO_ENERGY -> Component.translatable("message.alaindustrial.elevator.no_energy", this.menu.value(row),
					this.menu.energy());
			case GAP -> Component.translatable("message.alaindustrial.elevator.problem.gap", this.menu.value(row));
		};
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int row = buttonAt(mouseX, mouseY);
		if (row < 0) {
			super.extractTooltip(graphics, mouseX, mouseY);
			return;
		}
		List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(reason(row), TOOLTIP_WIDTH));
		graphics.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int row = buttonAt(event.x(), event.y());
			if (row >= 0) {
				if (this.menu.pressable(row)) {
					this.minecraft.getSoundManager()
							.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
					this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
							this.menu.firstShown() + row);
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
}
