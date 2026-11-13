package dev.alaindustrial.client.screen;

import static dev.alaindustrial.client.screen.TodoWindowLayout.BOX;
import static dev.alaindustrial.client.screen.TodoWindowLayout.BOX_DONE_U;
import static dev.alaindustrial.client.screen.TodoWindowLayout.BOX_OPEN_U;
import static dev.alaindustrial.client.screen.TodoWindowLayout.BOX_V;
import static dev.alaindustrial.client.screen.TodoWindowLayout.CROSS;
import static dev.alaindustrial.client.screen.TodoWindowLayout.CROSS_U;
import static dev.alaindustrial.client.screen.TodoWindowLayout.CROSS_V;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ICON_X;
import static dev.alaindustrial.client.screen.TodoWindowLayout.REMOVE_X;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ROW_H;
import static dev.alaindustrial.client.screen.TodoWindowLayout.TEX_SIZE;
import static dev.alaindustrial.client.screen.TodoWindowLayout.TEXT_X;
import static dev.alaindustrial.client.screen.TodoWindowLayout.TICK_X;

import dev.alaindustrial.client.render.TodoSheet;
import dev.alaindustrial.core.todo.TodoEntry;
import dev.alaindustrial.core.todo.TodoHave;
import dev.alaindustrial.item.misc.TodoClipboardItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * One line of the to-do clipboard's window (MOD-905), drawn on the sheet the atlas paints: the tick box, the item's
 * icon and name with its "have/need" counter at the right (green when the viewer carries enough), or the note, and
 * the remove cross for a viewer who may edit. The columns are {@link TodoWindowLayout}'s, which is also where the
 * screen reads its clicks.
 */
final class TodoWindowRow {

	/** Pixels between a name and its counter, and between the counter and the remove cross. */
	private static final int GAP = 3;
	private static final String ELLIPSIS = "…";

	private TodoWindowRow() {
	}

	/** Draws {@code entry} in the row whose top edge is {@code top}, the window's left edge at {@code x}. */
	static void draw(GuiGraphicsExtractor graphics, Font font, Identifier atlas, TodoEntry entry, int x, int top,
			boolean canEdit, TodoHave have) {
		int boxTop = top + (ROW_H - BOX) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, atlas, x + TICK_X, boxTop, entry.done() ? BOX_DONE_U : BOX_OPEN_U,
				BOX_V, BOX, BOX, TEX_SIZE, TEX_SIZE);
		int color = entry.done() ? GuiStyle.TEXT_DIM : GuiStyle.TEXT;
		int textX = x + (entry.isNote() ? ICON_X : TEXT_X);
		int textEnd = x + REMOVE_X - GAP;
		ItemStack stack = TodoClipboardItem.stackOf(entry.item());
		if (!stack.isEmpty()) {
			graphics.item(stack, x + ICON_X, top);
		}
		if (!entry.isNote()) {
			int owned = have.of(entry);
			Component counter = TodoSheet.counterText(owned, entry);
			int counterX = textEnd - font.width(counter);
			graphics.text(font, counter, counterX, top + 4, TodoHave.enough(owned, entry) ? GuiStyle.STATUS_GOOD
					: color, false);
			textEnd = counterX - GAP;
		}
		String fitted = fit(font, TodoClipboardItem.itemName(entry), textEnd - textX);
		Component shown = Component.literal(fitted).withStyle(style -> style.withStrikethrough(entry.done()));
		graphics.text(font, shown, textX, top + 4, color, false);
		if (canEdit) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, atlas, x + REMOVE_X, top + (ROW_H - CROSS) / 2, CROSS_U,
					CROSS_V, CROSS, CROSS, TEX_SIZE, TEX_SIZE);
		}
	}

	/** Whether the name or note of {@code entry} is cut in its row, so the tooltip should spell it out. */
	static boolean isCut(Font font, TodoEntry entry, TodoHave have) {
		int room = REMOVE_X - GAP - (entry.isNote() ? ICON_X : TEXT_X);
		if (!entry.isNote()) {
			room -= font.width(TodoSheet.counterText(have.of(entry), entry)) + GAP;
		}
		return font.width(TodoClipboardItem.itemName(entry)) > room;
	}

	/** {@code text} cut with an ellipsis to {@code width} pixels; the whole line is in the field or the tooltip. */
	private static String fit(Font font, String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		int end = text.length();
		while (end > 0 && font.width(text.substring(0, end) + ELLIPSIS) > width) {
			end--;
		}
		return text.substring(0, end) + ELLIPSIS;
	}
}
