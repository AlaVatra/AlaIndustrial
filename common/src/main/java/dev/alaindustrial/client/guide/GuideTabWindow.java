package dev.alaindustrial.client.guide;

/**
 * Which Guide Book categories fit in the left column (MOD-980). Pure integer arithmetic, no Minecraft on the
 * classpath, so it is covered directly on L1.
 *
 * <p>The column used to grow with the number of categories while the panel shrank with the window, so on a small
 * window the last buttons were drawn below the book's frame. Now the column shows as many category buttons as fit
 * between its top and the panel's bottom edge. When they do not all fit, its last row becomes a pair of arrows and
 * the rest scroll: by the arrows, by the mouse wheel over the column, and always far enough to keep the open
 * category in view.
 */
public final class GuideTabWindow {
	private GuideTabWindow() {
	}

	/** Rows of {@code buttonH} px at a pitch of {@code row} px that fit in {@code height} px (>= 1). */
	public static int rowsThatFit(int height, int buttonH, int row) {
		if (height < buttonH || row <= 0) {
			return 1;
		}
		return 1 + (height - buttonH) / row;
	}

	/** Whether {@code count} categories need the arrow row in a column of {@code rows} rows. */
	public static boolean scrolls(int count, int rows) {
		return count > Math.max(1, rows);
	}

	/** Category buttons shown at once: every row, or every row but the arrow row when the column scrolls. */
	public static int slots(int count, int rows) {
		int r = Math.max(1, rows);
		return scrolls(count, r) ? Math.max(1, r - 1) : count;
	}

	/**
	 * The first category shown: {@code offset} clamped into range and moved just enough that {@code selected}
	 * is among the shown ones.
	 */
	public static int start(int offset, int selected, int count, int rows) {
		int slots = slots(count, rows);
		int start = Math.max(0, Math.min(offset, count - slots));
		if (selected >= 0 && selected < count) {
			if (selected < start) {
				start = selected;
			} else if (selected >= start + slots) {
				start = selected - slots + 1;
			}
		}
		return start;
	}

	/** The first category shown after scrolling {@code steps} rows down (negative: up), clamped into range. */
	public static int scrolled(int start, int steps, int count, int rows) {
		int slots = slots(count, rows);
		return Math.max(0, Math.min(start + steps, count - slots));
	}
}
