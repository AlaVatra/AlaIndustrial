package dev.alaindustrial.client.screen;

import java.util.List;

/**
 * Where everything of the to-do clipboard's window sits (MOD-905), in pixels from the window's top-left corner, and
 * which translated lines must fit where. The screen draws from these numbers, the atlas generator
 * ({@code tools/gen_todo_clipboard_gui.py}) paints the same rectangles, and {@code TodoWindowLayoutTest} fits every
 * line of every language of the mod into its box with the vanilla font's own advances — so a long translation is
 * caught by a test, not by a player who sees a word cut in half.
 *
 * <p>Minecraft-free on purpose: the L1 lane has no game to load.
 */
public final class TodoWindowLayout {

	public static final int WIDTH = 176;
	public static final int HEIGHT = 222;
	public static final String KEY = "gui.alaindustrial.todo_clipboard.";

	/** The title's baseline row and the row under it (owner or rights on the left, "done/total" on the right). */
	public static final int TITLE_Y = 6;
	public static final int SUBTITLE_Y = 16;
	public static final int TEXT_LEFT = 8;
	public static final int TEXT_RIGHT = WIDTH - 8;

	/** The list: a sheet of five ruled rows inside a recessed well. */
	public static final int LIST_X = 7;
	public static final int LIST_Y = 27;
	public static final int LIST_W = 162;
	public static final int ROWS = 5;
	public static final int ROW_H = 16;
	public static final int LIST_BOTTOM = LIST_Y + ROWS * ROW_H;

	/** Columns of a row: the tick box, the item's icon, the item's name, the remove cross. */
	public static final int TICK_X = 10;
	public static final int BOX = 10;
	public static final int ICON_X = 24;
	public static final int TEXT_X = 42;
	public static final int REMOVE_X = 158;
	public static final int CROSS = 8;
	/** Where a row's own text may run: from the tick column to the remove cross. */
	public static final int ROW_TEXT_W = REMOVE_X - 3 - TICK_X;

	/** The hint an empty list shows in the rows under the free one. */
	public static final int HINT_X = TICK_X;
	public static final int HINT_Y = LIST_Y + ROW_H + 4;
	public static final int HINT_W = LIST_X + LIST_W - 4 - HINT_X;
	public static final int HINT_LINE = 10;
	public static final int HINT_MAX_LINES = 6;

	/** The control row under the list: the text field in its well, then four square buttons. */
	public static final int CONTROLS_Y = 110;
	public static final int BUTTON = 16;
	public static final int BUTTON_GAP = 2;
	public static final int ICON = 12;
	/** Left to right: the field's own button (add, OK or back), the item search, clear, the owner's access. */
	public static final int FIELD_BUTTON_X = 99;
	public static final int SEARCH_BUTTON_X = FIELD_BUTTON_X + BUTTON + BUTTON_GAP;
	public static final int CLEAR_BUTTON_X = SEARCH_BUTTON_X + BUTTON + BUTTON_GAP;
	public static final int ACCESS_BUTTON_X = CLEAR_BUTTON_X + BUTTON + BUTTON_GAP;
	/** The field's well, and the editable text inside it. */
	public static final int FIELD_WELL_X = LIST_X;
	public static final int FIELD_WELL_W = FIELD_BUTTON_X - 3 - FIELD_WELL_X;
	public static final int FIELD_X = FIELD_WELL_X + 3;
	public static final int FIELD_W = FIELD_WELL_W - 5;
	public static final int FIELD_Y = CONTROLS_Y + 4;
	public static final int FIELD_H = 9;

	/** The "Inventory" label sits where vanilla puts it, eleven pixels over the first inventory row. */
	public static final int INV_Y = 140;

	/** Wrap width of a tooltip, so a long hint becomes lines instead of a band across the screen. */
	public static final int TOOLTIP_W = 170;

	// --- the atlas: the window at (0, 0), its sprites in the free area to the right ---
	public static final int TEX_SIZE = 256;
	public static final int PLATE_U = 176;
	public static final int PLATE_V = 0;
	public static final int PLATE = BUTTON + 2;
	public static final int BOX_OPEN_U = 194;
	public static final int BOX_DONE_U = 204;
	public static final int BOX_V = 0;
	public static final int CROSS_U = 214;
	public static final int CROSS_V = 0;
	public static final int ICON_ROW_V = 18;
	public static final int ACCESS_ROW_V = 30;
	public static final int ICON_ADD_U = 176;
	public static final int ICON_OK_U = 188;
	public static final int ICON_BACK_U = 200;
	public static final int ICON_SEARCH_U = 212;
	public static final int ICON_CLEAR_U = 224;
	public static final int ICON_CLEAR_ARMED_U = 236;
	/** Access icons, in the order of {@code TodoAccess}: only me, others tick, others edit. */
	public static final int ACCESS_ICON_U = 176;

	/** One-line texts and the width each has; an argument in the key is filled with its widest likely value. */
	public static final List<String> ROW_LINES = List.of(KEY + "add_hint", KEY + "full", KEY + "search_empty",
			KEY + "search_start", KEY + "empty_list");
	public static final List<String> SUBTITLE_LINES = List.of(KEY + "owner");
	/**
	 * What a viewer who may not edit is told, written in the control row (empty for him: no field, no buttons), on
	 * at most two lines.
	 */
	public static final List<String> RIGHTS_LINES = List.of(KEY + "read_only", KEY + "tick_only");
	public static final int RIGHTS_W = LIST_W - 2;
	public static final int RIGHTS_MAX_LINES = 2;
	public static final List<String> FIELD_HINTS = List.of(KEY + "note_hint", KEY + "search_hint", KEY + "count_hint");
	public static final String EMPTY_HINT = KEY + "empty_hint";

	private TodoWindowLayout() {
	}

	/** Room for the subtitle once the widest "done/total" ({@code "10/10"}) and a gap are kept free at the right. */
	public static int subtitleWidth(int progressWidth) {
		return TEXT_RIGHT - TEXT_LEFT - progressWidth - 4;
	}

	/** Room for the title: the frame less its borders. */
	public static int titleWidth() {
		return WIDTH - 2 * TEXT_LEFT;
	}
}
