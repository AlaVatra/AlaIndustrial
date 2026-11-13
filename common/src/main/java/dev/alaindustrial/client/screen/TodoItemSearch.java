package dev.alaindustrial.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The to-do clipboard window's item search (MOD-905): the items whose name in the player's language, or whose
 * id, contains the query, shown as a grid of slots over the list's panel and scrolled a row at a time — the way
 * recipe viewers find an item. Client-side only; picking a match is the screen's business.
 */
final class TodoItemSearch {

	static final int CELL = 18;
	static final int COLS = 8;
	static final int ROWS = 4;
	private static final int MAX_RESULTS = 256;

	private final List<ItemStack> results = new ArrayList<>();
	private int scroll;
	/** Whether the last query was blank: the window then asks for a name instead of saying nothing was found. */
	private boolean blank = true;

	/** Runs {@code query} over the item registry; an empty query lists nothing rather than every item. */
	void search(String query) {
		results.clear();
		scroll = 0;
		String needle = query.strip().toLowerCase(Locale.ROOT);
		blank = needle.isEmpty();
		if (needle.isEmpty()) {
			return;
		}
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}
			ItemStack stack = new ItemStack(item);
			if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)
					|| BuiltInRegistries.ITEM.getKey(item).toString().contains(needle)) {
				results.add(stack);
				if (results.size() >= MAX_RESULTS) {
					return;
				}
			}
		}
	}

	void clear() {
		results.clear();
		scroll = 0;
		blank = true;
	}

	/** Scrolls one row per wheel notch, within the matches. */
	void scroll(double amount) {
		int maxRows = Math.max(0, (results.size() + COLS - 1) / COLS - ROWS);
		scroll = Math.max(0, Math.min(maxRows, scroll - (int) Math.signum(amount)));
	}

	/** The match at grid-local {@code (x, y)}, or {@code null}. */
	@Nullable ItemStack at(double x, double y) {
		if (x < 0 || y < 0 || x >= COLS * CELL || y >= ROWS * CELL) {
			return null;
		}
		int index = (scroll + (int) (y / CELL)) * COLS + (int) (x / CELL);
		return index < results.size() ? results.get(index) : null;
	}

	/**
	 * Draws the grid with its top-left at {@code (x, y)} — each match in a sunk slot like the inventory's — or, with
	 * no match, {@code start} (nothing typed yet) or {@code empty} (nothing found) at {@code (textX, textY)}.
	 */
	void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int textX, int textY, Component start,
			Component empty, int mouseX, int mouseY) {
		if (results.isEmpty()) {
			graphics.text(font, blank ? start : empty, textX, textY, GuiStyle.TEXT_DIM, false);
			return;
		}
		int first = scroll * COLS;
		int last = Math.min(results.size(), first + COLS * ROWS);
		for (int i = first; i < last; i++) {
			int cell = i - first;
			int cellX = x + (cell % COLS) * CELL;
			int cellY = y + (cell / COLS) * CELL;
			sunkSlot(graphics, cellX, cellY);
			graphics.item(results.get(i), cellX + 1, cellY + 1);
		}
		ItemStack hovered = at(mouseX - x, mouseY - y);
		if (hovered != null) {
			graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
		}
	}

	/** An 18x18 slot sunk into the panel, as the atlas paints the inventory's: dark above and left, white below. */
	static void sunkSlot(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x, y, x + 18, y + 18, GuiStyle.SLOT);
		graphics.fill(x, y, x + 17, y + 1, GuiStyle.SLOT_EDGE);
		graphics.fill(x, y, x + 1, y + 17, GuiStyle.SLOT_EDGE);
		graphics.fill(x + 1, y + 17, x + 18, y + 18, GuiStyle.PANEL_HI);
		graphics.fill(x + 17, y + 1, x + 18, y + 18, GuiStyle.PANEL_HI);
	}
}
