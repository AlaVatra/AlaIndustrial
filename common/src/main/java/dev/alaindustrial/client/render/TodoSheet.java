package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.compat.client.Poses;
import dev.alaindustrial.core.todo.TodoEntry;
import dev.alaindustrial.core.todo.TodoHave;
import dev.alaindustrial.core.todo.TodoList;
import dev.alaindustrial.core.todo.TodoListRules;
import dev.alaindustrial.item.misc.TodoClipboardItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The to-do clipboard's sheet with its list written on it (MOD-905): one painter for the clipboard on a wall
 * ({@link TodoClipboardRenderer}) and the clipboard in the hand ({@link TodoSheetItemModel}), so both show the
 * same page — a header with the block's name and "done/total", then one row per line: a tick box, the item's
 * icon, its name and the "have/need" counter ({@link TodoHave}), or the note; done rows grey and struck through.
 *
 * <p><b>Two halves, as every renderer of the game.</b> {@link #extract} turns a list into a {@link Page} (text
 * laid out, icons resolved) while the world or the hand is extracted; {@link #submit} draws that page.
 *
 * <p><b>Frame.</b> {@link #submit} draws in the block model's own frame — the clipboard lying on the floor of a
 * unit cell, its clip at +Z, the sheet facing +Y — which is also the item's model frame. Inside it the page runs
 * toward -X, its top toward +Z. The text area's numbers are the sheet's, measured in the model generator
 * ({@code tools/model_sources/todo_clipboard}, {@code TEXT_AREA}).
 *
 * <p><b>Depth.</b> Glyphs lie a tenth of a pixel above the sheet and are drawn with {@code POLYGON_OFFSET}, as
 * the sign renderer does, so they never share the sheet's plane; icons are flattened to a sliver whose back is
 * clear of the sheet too.
 */
public final class TodoSheet {

	/** Model units (1/16 block): the text area's left (+X) and top (+Z) edge, and the sheet's face. */
	private static final float AREA_LEFT = 12.6F;
	private static final float AREA_TOP = 12.3F;
	private static final float AREA_WIDTH = 9.2F;
	private static final float SHEET_FACE = 1.25F;
	private static final float TEXT_LIFT = 0.1F;

	/** Font units per row; eleven rows (header + {@link TodoListRules#MAX_ENTRIES}) fill the 11 px area. */
	private static final int ROW = 10;
	private static final float SCALE = 1.0F / 16.0F / ROW;
	private static final int AREA_UNITS = Math.round(AREA_WIDTH / 16.0F / SCALE);
	private static final int ICON_X = 9;
	private static final int ICON_SIZE = 8;
	private static final int ITEM_TEXT_X = 19;
	/** Font units between a name and its counter. */
	private static final int COUNTER_GAP = 3;
	/**
	 * Font units along the sheet's normal: the icon's centre a little below the glyph plane, and its whole depth.
	 * With the glyphs 0.1 px over the sheet this keeps the flattened model between 0.07 and 0.09 px over it.
	 */
	private static final float ICON_DEPTH_CENTRE = -0.2F;
	private static final float ICON_DEPTH = 0.2F;

	private static final int INK = 0xFF2B2B30;
	private static final int INK_HEADER = 0xFF4A3216;
	private static final int INK_DONE = 0xFF8A8A8A;
	private static final String ELLIPSIS = "…";
	private static final String BOX_OPEN = "□";
	private static final String BOX_DONE = "✔";

	private TodoSheet() {
	}

	/** What one sheet shows this frame: reused from frame to frame by its owner, refilled by {@link #extract}. */
	public static final class Page {
		@Nullable FormattedCharSequence title;
		@Nullable FormattedCharSequence progress;
		int progressX;
		final List<Row> rows = new ArrayList<>();
		final ItemStackRenderState[] icons = new ItemStackRenderState[TodoListRules.MAX_ENTRIES];

		public Page() {
			for (int i = 0; i < icons.length; i++) {
				icons[i] = new ItemStackRenderState();
			}
		}

		/** Leaves the sheet blank: nothing is drawn until the next {@link #extract}. */
		public void clear() {
			title = null;
			progress = null;
			rows.clear();
		}
	}

	/**
	 * One row: its box, its text, the item's counter if it has one, and whether an icon stands before it.
	 *
	 * @param counter       the "have/need" counter, or {@code null} for a note
	 * @param counterX      where the counter starts, in font units
	 * @param counterColour the counter's colour: green when the player has enough
	 */
	record Row(FormattedCharSequence box, FormattedCharSequence text, int textX, boolean done, boolean icon,
			@Nullable FormattedCharSequence counter, int counterX, int counterColour) {
	}

	/**
	 * Lays {@code list} out on {@code page}: the header ({@code title} and "done/total"), every line with its
	 * counter against {@code have}, and the item icons resolved through {@code resolver} as the GUI draws them.
	 */
	public static void extract(Page page, Font font, Component title, TodoList list, TodoHave have,
			ItemModelResolver resolver, @Nullable Level level, int seed) {
		page.clear();
		FormattedCharSequence progress = Component.translatable("gui.alaindustrial.todo_clipboard.progress",
				list.doneCount(), list.size()).getVisualOrderText();
		page.progress = progress;
		page.progressX = AREA_UNITS - font.width(progress);
		page.title = fit(font, title.getString(), page.progressX - 4, false);
		int count = Math.min(list.size(), TodoListRules.MAX_ENTRIES);
		for (int i = 0; i < count; i++) {
			TodoEntry entry = list.entries().get(i);
			ItemStack stack = TodoClipboardItem.stackOf(entry.item());
			ItemStackRenderState icon = page.icons[i];
			if (stack.isEmpty()) {
				icon.clear();
			} else {
				// GUI, as on the monitoring panel: the icon the player knows from the inventory.
				resolver.updateForTopItem(icon, stack, ItemDisplayContext.GUI, level, null, seed + i);
			}
			page.rows.add(row(font, entry, have, !stack.isEmpty()));
		}
	}

	private static Row row(Font font, TodoEntry entry, TodoHave have, boolean icon) {
		FormattedCharSequence box = Component.literal(entry.done() ? BOX_DONE : BOX_OPEN).getVisualOrderText();
		if (entry.isNote()) {
			return new Row(box, fit(font, entry.note(), AREA_UNITS - ICON_X, entry.done()), ICON_X, entry.done(),
					false, null, 0, 0);
		}
		int owned = have.of(entry);
		FormattedCharSequence counter = counterText(owned, entry).getVisualOrderText();
		int counterX = AREA_UNITS - font.width(counter);
		int colour = TodoHave.enough(owned, entry) ? GuiStyle.STATUS_GOOD : entry.done() ? INK_DONE : INK;
		FormattedCharSequence name = fit(font, TodoClipboardItem.itemName(entry),
				counterX - COUNTER_GAP - ITEM_TEXT_X, entry.done());
		return new Row(box, name, ITEM_TEXT_X, entry.done(), icon, counter, counterX, colour);
	}

	/** "12/32": how many the player has (at most {@link TodoHave#MAX_SHOWN}) over how many the line wants. */
	public static Component counterText(int have, TodoEntry entry) {
		return Component.translatable("gui.alaindustrial.todo_clipboard.have_need", TodoHave.shown(have),
				entry.count());
	}

	/** {@code text} cut with an ellipsis to {@code width} font units, struck through when done. */
	private static FormattedCharSequence fit(Font font, String text, int width, boolean struck) {
		String shown = text;
		if (font.width(text) > width) {
			shown = font.plainSubstrByWidth(text, Math.max(0, width - font.width(ELLIPSIS))) + ELLIPSIS;
		}
		return Component.literal(shown).withStyle(style -> style.withStrikethrough(struck)).getVisualOrderText();
	}

	/**
	 * Draws {@code page} with {@code poseStack} in the block model's frame (see the class comment). A blank page
	 * draws nothing.
	 */
	public static void submit(Page page, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		if (page.title == null || page.progress == null) {
			return;
		}
		poseStack.pushPose();
		// Onto the sheet's top-left corner; then page X -> model -X, page up -> model +Z, page normal -> model +Y.
		poseStack.translate(AREA_LEFT / 16.0F, (SHEET_FACE + TEXT_LIFT) / 16.0F, AREA_TOP / 16.0F);
		Poses.rotate(poseStack, Axis.XP.rotationDegrees(90.0F));
		Poses.rotate(poseStack, Axis.YP.rotationDegrees(180.0F));
		poseStack.scale(SCALE, -SCALE, SCALE);

		text(collector, poseStack, page.title, 0, 1, INK_HEADER, light);
		text(collector, poseStack, page.progress, page.progressX, 1, INK_HEADER, light);
		for (int i = 0; i < page.rows.size(); i++) {
			Row row = page.rows.get(i);
			int y = (i + 1) * ROW + 1;
			int colour = row.done() ? INK_DONE : INK;
			text(collector, poseStack, row.box(), 0, y, colour, light);
			text(collector, poseStack, row.text(), row.textX(), y, colour, light);
			if (row.counter() != null) {
				text(collector, poseStack, row.counter(), row.counterX(), y, row.counterColour(), light);
			}
			if (row.icon()) {
				icon(collector, poseStack, page.icons[i], y, light);
			}
		}
		poseStack.popPose();
	}

	private static void text(SubmitNodeCollector collector, PoseStack poseStack, FormattedCharSequence text, float x,
			float y, int colour, int light) {
		collector.submitText(poseStack, x, y, text, false, Font.DisplayMode.POLYGON_OFFSET, light, colour, 0, 0);
	}

	/**
	 * An item icon in the row starting at font {@code y}: the GUI model is a unit cube centred on the origin, so it
	 * is moved to the icon's centre, scaled back to font units (the frame is already flipped in Y) and flattened to
	 * a sliver lifted clear of the sheet.
	 */
	private static void icon(SubmitNodeCollector collector, PoseStack poseStack, ItemStackRenderState icon, int y,
			int light) {
		if (icon.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(ICON_X + ICON_SIZE / 2.0F, y + ICON_SIZE / 2.0F - 1.0F, ICON_DEPTH_CENTRE);
		poseStack.scale(ICON_SIZE, -ICON_SIZE, ICON_DEPTH);
		icon.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
