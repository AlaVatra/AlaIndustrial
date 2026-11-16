package dev.alaindustrial.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link GuideTabWindow} — the Guide Book category column on a small window (MOD-980). Pure ints,
 * no Minecraft on the classpath.
 *
 * <p>The reported case: a window of about 854 x 480 at GUI scale 2 is a 427 x 240 screen, the panel is
 * {@code 240 - 32 = 208} px tall, the column starts 28 px below the panel's top and ends 10 px above its bottom,
 * so it has 170 px for ten 18 px buttons at a 20 px pitch. The old column drew all ten and the last one landed
 * below the frame.
 */
class GuideTabWindowTest {
	private static final int TAB_H = 18;
	private static final int ROW = 20;

	/** Bottom edge (exclusive) of the k-th row of a column that starts at 0. */
	private static int rowBottom(int k) {
		return k * ROW + TAB_H;
	}

	@Test
	void theReportedWindowFitsEightRowsNotTen() {
		int height = 208 - 28 - 10;
		int rows = GuideTabWindow.rowsThatFit(height, TAB_H, ROW);
		assertEquals(8, rows);
		assertTrue(rowBottom(rows - 1) <= height, "the last row must end inside the column");
		assertTrue(rowBottom(rows) > height, "one more row would cross the frame");
		assertTrue(GuideTabWindow.scrolls(10, rows));
		assertEquals(7, GuideTabWindow.slots(10, rows), "seven categories and the arrow row");
	}

	@Test
	void aTallWindowShowsEveryCategoryWithoutArrows() {
		int rows = GuideTabWindow.rowsThatFit(300 - 28 - 10, TAB_H, ROW);
		assertFalse(GuideTabWindow.scrolls(10, rows));
		assertEquals(10, GuideTabWindow.slots(10, rows));
		assertEquals(0, GuideTabWindow.start(0, 9, 10, rows));
	}

	@Test
	void noRowEverEndsBelowTheColumn() {
		for (int height = 0; height <= 400; height++) {
			int rows = GuideTabWindow.rowsThatFit(height, TAB_H, ROW);
			assertTrue(rows >= 1);
			if (height >= TAB_H) {
				assertTrue(rowBottom(rows - 1) <= height, "height " + height);
			}
		}
	}

	@Test
	void theOpenCategoryIsAlwaysShown() {
		int rows = 5; // four categories and the arrow row
		for (int selected = 0; selected < 10; selected++) {
			for (int offset = -3; offset < 13; offset++) {
				int start = GuideTabWindow.start(offset, selected, 10, rows);
				int slots = GuideTabWindow.slots(10, rows);
				assertTrue(start >= 0 && start + slots <= 10, "range at " + offset);
				assertTrue(selected >= start && selected < start + slots, "selected " + selected + " at " + offset);
			}
		}
	}

	@Test
	void scrollingIgnoresTheSelectionAndStopsAtBothEnds() {
		int rows = 5;
		assertEquals(0, GuideTabWindow.scrolled(0, -1, 10, rows));
		assertEquals(1, GuideTabWindow.scrolled(0, 1, 10, rows));
		assertEquals(6, GuideTabWindow.scrolled(6, 1, 10, rows), "the last four categories are the end");
		assertEquals(6, GuideTabWindow.start(9, -1, 10, rows), "an offset past the end is pulled back");
		assertEquals(3, GuideTabWindow.start(3, -1, 10, rows), "no selection to follow: the offset stays");
	}

	@Test
	void aColumnTooShortForOneButtonStillShowsOne() {
		assertEquals(1, GuideTabWindow.rowsThatFit(5, TAB_H, ROW));
		assertEquals(1, GuideTabWindow.slots(10, 1));
		assertEquals(4, GuideTabWindow.start(0, 4, 10, 1));
	}
}
