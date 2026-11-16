package dev.alaindustrial.client.guide;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * The Guide Book's category column (MOD-980): the category buttons that fit between the column's top and the
 * panel's bottom edge, and, when they do not all fit, an arrow row and mouse-wheel scrolling. The row math is
 * {@link GuideTabWindow}; this class only turns it into buttons and remembers where the column is scrolled to.
 */
final class GuideTabColumn {
	private final int buttonW;
	private final int buttonH;
	private final int row;
	private final Runnable rebuild;

	private int offset = 0;
	private boolean followSelection = true;
	private int x, top, bottom, rows, count;

	GuideTabColumn(int buttonW, int buttonH, int row, Runnable rebuild) {
		this.buttonW = buttonW;
		this.buttonH = buttonH;
		this.row = row;
		this.rebuild = rebuild;
	}

	/** The next build scrolls the open category into view (a category was chosen, or the book opened). */
	void followSelection() {
		followSelection = true;
	}

	/** Adds the column's buttons for a column from {@code top} to {@code bottom} at {@code x}. */
	void build(List<String> titles, int selected, int x, int top, int bottom, IntConsumer select,
			Consumer<Button> add) {
		this.x = x;
		this.top = top;
		this.bottom = bottom;
		this.count = titles.size();
		this.rows = GuideTabWindow.rowsThatFit(bottom - top, buttonH, row);
		offset = GuideTabWindow.start(offset, followSelection ? selected : -1, count, rows);
		int slots = GuideTabWindow.slots(count, rows);
		for (int k = 0; k < slots && offset + k < count; k++) {
			int idx = offset + k;
			Button b = Button.builder(Component.literal(titles.get(idx)), p -> select.accept(idx))
					.bounds(x, top + k * row, buttonW, buttonH).build();
			b.active = idx != selected;
			add.accept(b);
		}
		if (GuideTabWindow.scrolls(count, rows)) {
			int navY = top + slots * row;
			int half = (buttonW - 2) / 2;
			Button up = Button.builder(Component.literal("▲"), b -> scroll(-1))
					.bounds(x, navY, half, buttonH).build();
			up.active = offset > 0;
			add.accept(up);
			Button down = Button.builder(Component.literal("▼"), b -> scroll(1))
					.bounds(x + buttonW - half, navY, half, buttonH).build();
			down.active = offset + slots < count;
			add.accept(down);
		}
	}

	/** Scrolls the column if the wheel turned over it and it has more categories than rows; true if it did. */
	boolean wheel(double mouseX, double mouseY, double scrollY) {
		boolean over = mouseX >= x && mouseX < x + buttonW && mouseY >= top && mouseY < bottom;
		if (!over || scrollY == 0 || !GuideTabWindow.scrolls(count, rows)) {
			return false;
		}
		scroll(scrollY > 0 ? -1 : 1);
		return true;
	}

	private void scroll(int steps) {
		int next = GuideTabWindow.scrolled(offset, steps, count, rows);
		if (next != offset) {
			offset = next;
			followSelection = false;
			rebuild.run();
		}
	}
}
