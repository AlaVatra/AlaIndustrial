package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.item.apiary.ApiaryMapPoints;
import dev.alaindustrial.menu.ApiaryMapMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/**
 * The apiary map's list (MOD-923): one row per written hive attachment — its number, its coordinates, its dimension
 * when that is not the player's — and a "Cross out" button on each row. An empty map says how to write one.
 *
 * <p>Drawn in the mod's plain panel style ({@link GuiStyle}), no texture of its own. Nothing here decides anything: a
 * press sends the row index over vanilla's container-button packet and the server edits the live map
 * ({@link ApiaryMapMenu#clickMenuButton}); the list redraws from the synced stack.
 */
public class ApiaryMapScreen extends AbstractContainerScreen<ApiaryMapMenu> {

	public static final int PANEL_W = 220;
	private static final int HEADER_H = 20;
	private static final int ROW_H = 16;
	private static final int FOOTER_H = 8;
	public static final int PANEL_H = HEADER_H + ApiaryMapPoints.MAX * ROW_H + FOOTER_H;

	private static final int PAD = 7;
	private static final int BUTTON_H = 12;
	private static final int BUTTON_PAD = 4;

	public ApiaryMapScreen(ApiaryMapMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PANEL_W, PANEL_H);
	}

	private static Component crossOutLabel() {
		return Component.translatable("gui.alaindustrial.apiary_map.cross_out");
	}

	private int buttonWidth() {
		return this.font.width(crossOutLabel()) + BUTTON_PAD * 2;
	}

	/** Screen x of a row's button's left edge. */
	private int buttonLeft() {
		return this.leftPos + PANEL_W - PAD - buttonWidth();
	}

	/** Screen y of the top of row {@code row}. */
	private int rowTop(int row) {
		return this.topPos + HEADER_H + row * ROW_H;
	}

	/** The row whose button is under the cursor, or -1. */
	public int buttonAt(double mouseX, double mouseY) {
		ApiaryMapPoints points = this.menu.points();
		int left = buttonLeft();
		for (int row = 0; row < points.size(); row++) {
			int top = rowTop(row) + (ROW_H - BUTTON_H) / 2;
			if (mouseX >= left && mouseX < left + buttonWidth() && mouseY >= top && mouseY < top + BUTTON_H) {
				return row;
			}
		}
		return -1;
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, PAD, 4, GuiStyle.LABEL, false);
		Component count = Component.translatable("gui.alaindustrial.apiary_map.count", this.menu.points().size(),
				ApiaryMapPoints.MAX);
		graphics.text(this.font, count, PANEL_W - PAD - this.font.width(count), 4, GuiStyle.TEXT_DIM, false);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		GuiStyle.panel(graphics, this.leftPos, this.topPos, PANEL_W, PANEL_H);
		ApiaryMapPoints points = this.menu.points();
		if (points.isEmpty()) {
			drawEmpty(graphics);
			return;
		}
		int hovered = buttonAt(mouseX, mouseY);
		for (int row = 0; row < points.size(); row++) {
			drawRow(graphics, row, points.get(row), row == hovered);
		}
	}

	private void drawEmpty(GuiGraphicsExtractor graphics) {
		int y = rowTop(0) + 4;
		for (String key : new String[] {"gui.alaindustrial.apiary_map.empty",
				"gui.alaindustrial.apiary_map.empty_how", "gui.alaindustrial.apiary_map.empty_cross_out"}) {
			for (var line : this.font.split(Component.translatable(key), PANEL_W - PAD * 2)) {
				graphics.text(this.font, line, this.leftPos + PAD, y, GuiStyle.TEXT, false);
				y += 10;
			}
			y += 4;
		}
	}

	private void drawRow(GuiGraphicsExtractor graphics, int row, GlobalPos point, boolean hovered) {
		int top = rowTop(row);
		int left = this.leftPos + PAD;
		if (row % 2 == 1) {
			graphics.fill(left - 2, top, this.leftPos + PANEL_W - PAD + 2, top + ROW_H, GuiStyle.CARD);
		}
		int textY = top + (ROW_H - 8) / 2;
		BlockPos pos = point.pos();
		graphics.text(this.font, Component.literal((row + 1) + "."), left, textY, GuiStyle.TEXT_DIM, false);
		Component coords = Component.translatable("gui.alaindustrial.apiary_map.coords", pos.getX(), pos.getY(),
				pos.getZ());
		graphics.text(this.font, coords, left + 14, textY, GuiStyle.TEXT, false);
		if (!point.dimension().equals(this.menu.player().level().dimension())) {
			Component dimension = Component.translatable("gui.alaindustrial.apiary_map.other_dimension",
					point.dimension().identifier().toString());
			int dimX = left + 14 + this.font.width(coords) + 6;
			int room = buttonLeft() - 4 - dimX;
			if (room > 8) {
				graphics.text(this.font, fitted(dimension.getString(), room), dimX, textY, GuiStyle.STATUS_BLOCKING,
						false);
			}
		}
		int bx = buttonLeft();
		int by = top + (ROW_H - BUTTON_H) / 2;
		graphics.fill(bx, by, bx + buttonWidth(), by + BUTTON_H, GuiStyle.BUTTON);
		if (hovered) {
			graphics.fill(bx, by, bx + buttonWidth(), by + BUTTON_H, GuiStyle.HOVER_WASH);
		}
		graphics.text(this.font, crossOutLabel(), bx + BUTTON_PAD, by + 2, GuiStyle.HEADER_TEXT, false);
	}

	/** {@code text} cut to {@code width} pixels with an ellipsis when it does not fit. */
	private String fitted(String text, int width) {
		if (this.font.width(text) <= width) {
			return text;
		}
		return this.font.plainSubstrByWidth(text, width - this.font.width("...")) + "...";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int row = buttonAt(event.x(), event.y());
			if (row >= 0) {
				this.minecraft.getSoundManager()
						.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
				this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, row);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
}
