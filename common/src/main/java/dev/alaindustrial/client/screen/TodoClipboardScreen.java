package dev.alaindustrial.client.screen;

import static dev.alaindustrial.client.screen.TodoWindowLayout.ACCESS_BUTTON_X;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ACCESS_ICON_U;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ACCESS_ROW_V;
import static dev.alaindustrial.client.screen.TodoWindowLayout.BOX;
import static dev.alaindustrial.client.screen.TodoWindowLayout.BUTTON;
import static dev.alaindustrial.client.screen.TodoWindowLayout.CLEAR_BUTTON_X;
import static dev.alaindustrial.client.screen.TodoWindowLayout.CONTROLS_Y;
import static dev.alaindustrial.client.screen.TodoWindowLayout.CROSS;
import static dev.alaindustrial.client.screen.TodoWindowLayout.FIELD_BUTTON_X;
import static dev.alaindustrial.client.screen.TodoWindowLayout.FIELD_H;
import static dev.alaindustrial.client.screen.TodoWindowLayout.FIELD_W;
import static dev.alaindustrial.client.screen.TodoWindowLayout.FIELD_WELL_W;
import static dev.alaindustrial.client.screen.TodoWindowLayout.FIELD_WELL_X;
import static dev.alaindustrial.client.screen.TodoWindowLayout.FIELD_X;
import static dev.alaindustrial.client.screen.TodoWindowLayout.FIELD_Y;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ICON;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ICON_ADD_U;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ICON_BACK_U;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ICON_CLEAR_ARMED_U;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ICON_CLEAR_U;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ICON_OK_U;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ICON_ROW_V;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ICON_SEARCH_U;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ICON_X;
import static dev.alaindustrial.client.screen.TodoWindowLayout.KEY;
import static dev.alaindustrial.client.screen.TodoWindowLayout.LIST_W;
import static dev.alaindustrial.client.screen.TodoWindowLayout.LIST_X;
import static dev.alaindustrial.client.screen.TodoWindowLayout.LIST_Y;
import static dev.alaindustrial.client.screen.TodoWindowLayout.PLATE;
import static dev.alaindustrial.client.screen.TodoWindowLayout.PLATE_U;
import static dev.alaindustrial.client.screen.TodoWindowLayout.PLATE_V;
import static dev.alaindustrial.client.screen.TodoWindowLayout.REMOVE_X;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ROWS;
import static dev.alaindustrial.client.screen.TodoWindowLayout.ROW_H;
import static dev.alaindustrial.client.screen.TodoWindowLayout.SEARCH_BUTTON_X;
import static dev.alaindustrial.client.screen.TodoWindowLayout.TEX_SIZE;
import static dev.alaindustrial.client.screen.TodoWindowLayout.TICK_X;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.TodoInventoryTally;
import dev.alaindustrial.core.todo.TodoAccess;
import dev.alaindustrial.core.todo.TodoEntry;
import dev.alaindustrial.core.todo.TodoHave;
import dev.alaindustrial.core.todo.TodoList;
import dev.alaindustrial.core.todo.TodoListRules;
import dev.alaindustrial.core.todo.TodoListRules.Edit;
import dev.alaindustrial.core.todo.TodoListRules.Op;
import dev.alaindustrial.item.misc.TodoClipboardItem;
import dev.alaindustrial.item.misc.TooltipKeys;
import dev.alaindustrial.menu.TodoClipboardMenu;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.TodoClipboardEditPayload;
import dev.alaindustrial.network.TodoClipboardSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

// size-justified: one window with three modes (list, item search, count) and the hover help of every control the
// owner asked for (MOD-905, playtest 2026-11-12); the layout numbers, the keys and the row painter live apart.
/**
 * The to-do clipboard's window (MOD-905): the list on a ruled sheet, the player's inventory below, a text field and
 * four icon buttons between them, all from the window's atlas ({@code tools/gen_todo_clipboard_gui.py}) in the frame
 * and wells the mod's other item screens use. Every number is {@link TodoWindowLayout}'s.
 *
 * <p>The screen owns no list: it draws the last snapshot ({@link TodoClipboardMenu#snapshot}) and turns every
 * click into a {@link TodoClipboardEditPayload} that names the snapshot's revision. The server decides; a refused
 * edit comes back as a fresh snapshot.
 *
 * <p><b>The window explains itself</b> (owner's playtest: "it is not clear what happens here"). An empty list shows
 * in its rows what to do; every control — tick box, cross, item icon, name, count, the free line, each button and
 * the counters — has a tooltip saying what it is and what a click does. The buttons are icons, so no language cuts
 * their label.
 *
 * <p>What a click does: the tick box flips the done tick, the cross removes the line, the item icon takes the item
 * and count of the stack on the cursor. The free line below the list adds the stack on the cursor as a new line or,
 * with nothing on the cursor, opens the item search, whose matches replace the list until one is picked; the
 * magnifier button opens it too. A note's text opens it in the field, an item line's text opens its count there.
 * The mouse wheel over a count changes it by one (by 64 with Shift) and over the rest of the list scrolls it. Enter
 * in the field saves the note or the count; Escape goes back from a sub-mode to the list, and from the list closes
 * the window ({@link TodoWindowKeys}).
 */
public class TodoClipboardScreen extends AbstractContainerScreen<TodoClipboardMenu> {

	static final Identifier TEXTURE = Industrialization.id("textures/gui/container/todo_clipboard.png");

	private static final int STEP_SHIFT = 64;
	private static final long CLEAR_CONFIRM_MS = 3000L;
	/** Laid over the row whose note or count the field is rewriting: the sheet's paper gone amber. */
	private static final int ROW_EDITING = 0x50E0B040;
	/** Laid over the row under the cursor, so a click's target is visible. */
	private static final int ROW_HOVER = 0x18000000;
	/** The field's well: the inventory slots' bevel around a dark inside, as vanilla's text boxes are dark. */
	private static final int FIELD_INSIDE = 0xFF1E1E1E;
	private static final int FIELD_TEXT = 0xFFE0E0E0;
	/** Laid over a button that cannot be used right now (no room for another line). */
	private static final int BUTTON_VEIL = 0xA0AAAAAA;
	/** The search grid's corner, centred in the list's sheet. */
	private static final int RESULT_X = LIST_X + 9;
	private static final int RESULT_Y = LIST_Y + 4;
	private static final int SEARCH_MAX = 32;
	private static final int COUNT_DIGITS = 4;
	private static final String ELLIPSIS = "…";

	/** What the field below the list is for. */
	private enum Mode {
		/** Writing a new note, or rewriting line {@code editing}. */
		NOTE,
		/** Finding an item by name; the matches replace the list. */
		SEARCH,
		/** Typing the count of item line {@code editing}. */
		COUNT
	}

	/** The four buttons of the control row, left to right. */
	private enum Control {
		FIELD(FIELD_BUTTON_X), SEARCH(SEARCH_BUTTON_X), CLEAR(CLEAR_BUTTON_X), ACCESS(ACCESS_BUTTON_X);

		private final int x;

		Control(int x) {
			this.x = x;
		}
	}

	private Mode mode = Mode.NOTE;
	private final TodoItemSearch search = new TodoItemSearch();
	private int scroll;
	/** The line the field is rewriting (a note, or an item's count), or -1 when the field adds a new note. */
	private int editing = -1;
	private long clearArmedUntil;
	private @Nullable EditBox field;

	public TodoClipboardScreen(TodoClipboardMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, TodoClipboardMenu.WIDTH, TodoClipboardMenu.HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
		this.titleLabelY = TodoWindowLayout.TITLE_Y;
		this.inventoryLabelY = TodoClipboardMenu.INV_Y - 11;
		field = addRenderableWidget(new EditBox(this.font, this.leftPos + FIELD_X, this.topPos + FIELD_Y, FIELD_W,
				FIELD_H, Component.translatable(KEY + "note_hint")));
		field.setBordered(false);
		field.setTextColor(FIELD_TEXT);
		field.setResponder(value -> {
			if (mode == Mode.SEARCH) {
				search.search(value);
			}
		});
		setMode(mode);
		refreshControls();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		refreshControls();
	}

	private boolean canEdit() {
		TodoClipboardSnapshotPayload shown = this.menu.snapshot();
		return shown != null && shown.canEdit();
	}

	/** Shows the field only to a viewer who may edit, and keeps the scroll and the field's target in the list. */
	private void refreshControls() {
		boolean canEdit = canEdit();
		if (field != null) {
			field.setEditable(canEdit);
			field.setVisible(canEdit);
		}
		// A list cut short (a removal, "Clear", another player's edit) must not leave the window scrolled past it.
		scroll = Math.min(scroll, Math.max(0, this.menu.shownList().size() + 1 - ROWS));
		if (editing >= this.menu.shownList().size() || !canEdit && inSubMode()) {
			backToNote();
		}
	}

	private boolean clearArmed() {
		return System.currentTimeMillis() < clearArmedUntil;
	}

	/** The search, a count being typed or a note being rewritten — where Escape goes back to the list. */
	private boolean inSubMode() {
		return mode != Mode.NOTE || editing >= 0;
	}

	/** Points the field at {@code next}. */
	private void setMode(Mode next) {
		mode = next;
		if (field == null) {
			return;
		}
		switch (next) {
			case NOTE -> {
				field.setMaxLength(TodoListRules.MAX_NOTE);
				field.setHint(Component.translatable(KEY + "note_hint"));
			}
			case SEARCH -> {
				field.setMaxLength(SEARCH_MAX);
				field.setHint(Component.translatable(KEY + "search_hint"));
				search.search(field.getValue());
			}
			case COUNT -> {
				field.setMaxLength(COUNT_DIGITS);
				field.setHint(Component.translatable(KEY + "count_hint"));
			}
		}
	}

	/** Leaves the search or the count for a blank note field. */
	private void backToNote() {
		editing = -1;
		search.clear();
		if (field != null) {
			field.setValue("");
		}
		setMode(Mode.NOTE);
	}

	// ── sending ──────────────────────────────────────────────────────────────────────────────────────────

	/** Sends {@code edit} when this viewer may make it; the server checks again either way. */
	private void send(Edit edit) {
		TodoClipboardSnapshotPayload shown = this.menu.snapshot();
		if (shown != null && may(shown, edit.op())) {
			NetworkDispatcher.get().sendToServer(TodoClipboardEditPayload.of(this.menu.containerId, shown.revision(),
					edit));
		}
	}

	/** The field's button and Enter: save the note, leave the search, or set the count. */
	private void confirm() {
		if (field == null) {
			return;
		}
		switch (mode) {
			case NOTE -> {
				if (field.getValue().isBlank()) {
					return;
				}
				if (editing < 0 && this.menu.shownList().size() >= TodoListRules.MAX_ENTRIES) {
					return;
				}
				send(editing >= 0 ? Edit.setNote(editing, field.getValue()) : Edit.addNote(field.getValue()));
				backToNote();
			}
			case SEARCH -> backToNote();
			case COUNT -> {
				String digits = field.getValue().replaceAll("[^0-9]", "");
				if (!digits.isEmpty() && editing >= 0) {
					send(Edit.setCount(editing, TodoListRules.clampCount(Integer.parseInt(digits))));
				}
				backToNote();
			}
		}
	}

	private void clear() {
		if (clearArmed()) {
			clearArmedUntil = 0L;
			send(Edit.clear());
		} else {
			clearArmedUntil = System.currentTimeMillis() + CLEAR_CONFIRM_MS;
		}
	}

	private static boolean may(TodoClipboardSnapshotPayload shown, Op op) {
		return switch (op) {
			case SET_ACCESS -> shown.isOwner();
			case TOGGLE_DONE -> shown.canTick();
			default -> shown.canEdit();
		};
	}

	/** Opens the item search, or closes it when it is open. */
	private void toggleSearch() {
		if (mode == Mode.SEARCH) {
			backToNote();
			unfocusField();
			return;
		}
		backToNote();
		setMode(Mode.SEARCH);
		focusField();
	}

	private boolean roomForAnother() {
		return this.menu.shownList().size() < TodoListRules.MAX_ENTRIES;
	}

	// ── the control row ──────────────────────────────────────────────────────────────────────────────────

	private boolean shown(Control control) {
		TodoClipboardSnapshotPayload snapshot = this.menu.snapshot();
		if (control == Control.ACCESS) {
			return snapshot != null && snapshot.isOwner();
		}
		// In the search the magnifier itself turns into "back"; a second arrow beside it would only puzzle.
		return canEdit() && !(control == Control.FIELD && mode == Mode.SEARCH);
	}

	/** A button that is shown but would do nothing now: adding with the list full, searching with the list full. */
	private boolean enabled(Control control) {
		return switch (control) {
			case FIELD -> mode != Mode.NOTE || editing >= 0 || roomForAnother();
			case SEARCH -> mode == Mode.SEARCH || roomForAnother();
			default -> true;
		};
	}

	/** The icon's column in the atlas for what {@code control} does right now. */
	private int iconU(Control control) {
		return switch (control) {
			case FIELD -> switch (mode) {
				case SEARCH -> ICON_BACK_U;
				case COUNT -> ICON_OK_U;
				case NOTE -> editing >= 0 ? ICON_OK_U : ICON_ADD_U;
			};
			case SEARCH -> mode == Mode.SEARCH ? ICON_BACK_U : ICON_SEARCH_U;
			case CLEAR -> clearArmed() ? ICON_CLEAR_ARMED_U : ICON_CLEAR_U;
			case ACCESS -> ACCESS_ICON_U + this.menu.shownList().access().ordinal() * ICON;
		};
	}

	private @Nullable Control controlAt(double mouseX, double mouseY) {
		for (Control control : Control.values()) {
			if (shown(control) && isHovering(control.x, CONTROLS_Y, BUTTON, BUTTON, mouseX, mouseY)) {
				return control;
			}
		}
		return null;
	}

	private void press(Control control) {
		if (!enabled(control)) {
			return;
		}
		AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
		switch (control) {
			case FIELD -> confirm();
			case SEARCH -> toggleSearch();
			case CLEAR -> clear();
			case ACCESS -> send(Edit.setAccess(this.menu.shownList().access().next()));
		}
	}

	// ── input ────────────────────────────────────────────────────────────────────────────────────────────

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			Control control = controlAt(event.x(), event.y());
			if (control != null) {
				press(control);
				return true;
			}
			if (!isHovering(FIELD_WELL_X, CONTROLS_Y, FIELD_WELL_W, BUTTON, event.x(), event.y())) {
				// A click anywhere else takes the focus off the field, so the inventory key closes the window again.
				unfocusField();
			}
			if (listClick(event.x(), event.y())) {
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean listClick(double mouseX, double mouseY) {
		if (mode == Mode.SEARCH) {
			return resultClick(mouseX, mouseY);
		}
		int row = rowAt(mouseX, mouseY);
		if (row < 0) {
			return false;
		}
		TodoList list = this.menu.shownList();
		int index = scroll + row;
		double localX = mouseX - this.leftPos;
		ItemStack carried = this.menu.getCarried();
		if (index >= list.size()) {
			if (index == list.size() && roomForAnother()) {
				if (!carried.isEmpty()) {
					send(Edit.addItem(idOf(carried), carried.getCount()));
				} else if (field != null && field.visible) {
					backToNote();
					setMode(Mode.SEARCH);
					focusField();
				}
			}
			return true;
		}
		TodoEntry entry = list.entries().get(index);
		if (localX >= TICK_X && localX < TICK_X + BOX) {
			send(Edit.toggleDone(index));
		} else if (localX >= REMOVE_X && localX < REMOVE_X + CROSS) {
			send(Edit.remove(index));
		} else if (localX >= ICON_X && localX < ICON_X + 16 && !carried.isEmpty()) {
			send(Edit.setItem(index, idOf(carried), carried.getCount()));
		} else if (field != null && field.visible && localX >= ICON_X && localX < REMOVE_X) {
			backToNote();
			editing = index;
			setMode(entry.isNote() ? Mode.NOTE : Mode.COUNT);
			field.setValue(entry.isNote() ? entry.note() : Integer.toString(entry.count()));
			focusField();
		}
		return true;
	}

	/** A click on a search match adds one of it as a new line and closes the search. */
	private boolean resultClick(double mouseX, double mouseY) {
		ItemStack result = search.at(mouseX - this.leftPos - RESULT_X, mouseY - this.topPos - RESULT_Y);
		if (result == null) {
			return rowAt(mouseX, mouseY) >= 0;
		}
		if (roomForAnother()) {
			send(Edit.addItem(idOf(result), 1));
		}
		backToNote();
		unfocusField();
		return true;
	}

	private void focusField() {
		if (field != null) {
			setFocused(field);
			field.setFocused(true);
		}
	}

	private void unfocusField() {
		if (field != null && field.isFocused()) {
			field.setFocused(false);
			setFocused(null);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int row = rowAt(mouseX, mouseY);
		if (row < 0 || scrollY == 0) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		if (mode == Mode.SEARCH) {
			search.scroll(scrollY);
			return true;
		}
		TodoList list = this.menu.shownList();
		int index = scroll + row;
		double localX = mouseX - this.leftPos;
		if (canEdit() && index < list.size() && !list.entries().get(index).isNote() && localX >= ICON_X
				&& localX < REMOVE_X) {
			int step = (TooltipKeys.shiftDown() ? STEP_SHIFT : 1) * (scrollY > 0 ? 1 : -1);
			send(Edit.setCount(index, TodoListRules.clampCount(list.entries().get(index).count() + step)));
			return true;
		}
		int maxScroll = Math.max(0, list.size() + 1 - ROWS);
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY)));
		return true;
	}

	/** Escape first, then the focused field, then the window's usual keys — see {@link TodoWindowKeys}. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean focused = field != null && field.isFocused();
		switch (TodoWindowKeys.of(event.isEscape(), event.isConfirmation(), focused, inSubMode())) {
			case CLOSE -> {
				onClose();
				return true;
			}
			case BACK_TO_LIST -> {
				backToNote();
				unfocusField();
				return true;
			}
			case CONFIRM -> {
				confirm();
				return true;
			}
			case TYPE -> {
				if (field != null) {
					field.keyPressed(event);
				}
				return true;
			}
			default -> {
				return super.keyPressed(event);
			}
		}
	}

	/** The visible row under the cursor, or -1 outside the list. */
	private int rowAt(double mouseX, double mouseY) {
		double localX = mouseX - this.leftPos;
		double localY = mouseY - this.topPos - LIST_Y;
		if (localX < LIST_X || localX >= LIST_X + LIST_W || localY < 0 || localY >= ROWS * ROW_H) {
			return -1;
		}
		return (int) (localY / ROW_H);
	}

	private static String idOf(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	// ── drawing ──────────────────────────────────────────────────────────────────────────────────────────

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, TEX_SIZE,
				TEX_SIZE);
		if (canEdit()) {
			int left = x + FIELD_WELL_X;
			int top = y + CONTROLS_Y;
			graphics.fill(left, top, left + FIELD_WELL_W, top + BUTTON, GuiStyle.PANEL_HI);
			graphics.fill(left, top, left + FIELD_WELL_W - 1, top + BUTTON - 1, GuiStyle.SLOT_EDGE);
			graphics.fill(left + 1, top + 1, left + FIELD_WELL_W - 1, top + BUTTON - 1, FIELD_INSIDE);
		}
		Control hovered = controlAt(mouseX, mouseY);
		for (Control control : Control.values()) {
			if (!shown(control)) {
				continue;
			}
			int bx = x + control.x;
			int by = y + CONTROLS_Y;
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, bx - 1, by - 1, PLATE_U, PLATE_V, PLATE, PLATE,
					TEX_SIZE, TEX_SIZE);
			int v = control == Control.ACCESS ? ACCESS_ROW_V : ICON_ROW_V;
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, bx + 2, by + 2, iconU(control), v, ICON, ICON,
					TEX_SIZE, TEX_SIZE);
			if (!enabled(control)) {
				graphics.fill(bx, by, bx + BUTTON, by + BUTTON, BUTTON_VEIL);
			} else if (control == hovered) {
				graphics.fill(bx, by, bx + BUTTON, by + BUTTON, GuiStyle.HOVER_WASH);
			}
		}
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		if (mode == Mode.SEARCH) {
			search.draw(graphics, this.font, x + RESULT_X, y + RESULT_Y, x + TICK_X, y + LIST_Y + 4,
					Component.translatable(KEY + "search_start"), Component.translatable(KEY + "search_empty"), mouseX,
					mouseY);
			return;
		}
		TodoList list = this.menu.shownList();
		TodoClipboardSnapshotPayload shown = this.menu.snapshot();
		boolean canEdit = shown != null && shown.canEdit();
		boolean canTick = shown != null && shown.canTick();
		TodoHave have = TodoInventoryTally.current();
		int hoveredRow = canTick ? rowAt(mouseX, mouseY) : -1;
		for (int row = 0; row < ROWS; row++) {
			int index = scroll + row;
			int top = y + LIST_Y + row * ROW_H;
			if (index == editing) {
				graphics.fill(x + LIST_X, top, x + LIST_X + LIST_W, top + ROW_H - 1, ROW_EDITING);
			} else if (row == hoveredRow && (index < list.size() || canEdit && index == list.size())) {
				graphics.fill(x + LIST_X, top, x + LIST_X + LIST_W, top + ROW_H - 1, ROW_HOVER);
			}
			if (index < list.size()) {
				TodoWindowRow.draw(graphics, this.font, TEXTURE, list.entries().get(index), x, top, canEdit, have);
			} else if (canEdit && index == list.size()) {
				Component free = roomForAnother() ? Component.translatable(KEY + "add_hint")
						: Component.translatable(KEY + "full", TodoListRules.MAX_ENTRIES);
				graphics.text(this.font, free, x + TICK_X, top + 4, GuiStyle.TEXT_DIM, false);
			}
		}
		if (list.isEmpty()) {
			drawEmptyHint(graphics, x, y, canEdit);
		}
	}

	/** An empty list says, in the rows under the free one, how to fill it — or, to a reader, that it is empty. */
	private void drawEmptyHint(GuiGraphicsExtractor graphics, int x, int y, boolean canEdit) {
		String text = Component.translatable(KEY + (canEdit ? "empty_hint" : "empty_list")).getString();
		TextFit.Fit fit = TextFit.fit(text, TodoWindowLayout.HINT_W, TodoWindowLayout.HINT_MAX_LINES, this.font::width);
		float lineY = y + (canEdit ? TodoWindowLayout.HINT_Y : LIST_Y + 4);
		for (String line : fit.lines()) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + TodoWindowLayout.HINT_X, lineY);
			graphics.pose().scale(fit.scale(), fit.scale());
			graphics.text(this.font, Component.literal(line), 0, 0, GuiStyle.TEXT_DIM, false);
			graphics.pose().popMatrix();
			lineY += TodoWindowLayout.HINT_LINE * fit.scale();
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);
		TodoClipboardSnapshotPayload shown = this.menu.snapshot();
		if (shown != null && !shown.canEdit()) {
			drawRights(graphics, shown.canTick() ? "tick_only" : "read_only");
		}
		Component line;
		if (shown != null && !shown.ownerName().isEmpty()) {
			line = Component.translatable(KEY + "owner", shown.ownerName());
		} else {
			line = Component.empty();
		}
		Component progress = progress();
		int room = TodoWindowLayout.subtitleWidth(this.font.width(progress));
		if (this.font.width(line) > room) {
			// A long owner's name: its label stays, the name is cut (TodoWindowLayoutTest keeps the label in room).
			line = Component.literal(this.font.plainSubstrByWidth(line.getString(), room - this.font.width(ELLIPSIS))
					+ ELLIPSIS);
		}
		graphics.text(this.font, line, TodoWindowLayout.TEXT_LEFT, TodoWindowLayout.SUBTITLE_Y, GuiStyle.TEXT_DIM,
				false);
		graphics.text(this.font, progress, TodoWindowLayout.TEXT_RIGHT - this.font.width(progress),
				TodoWindowLayout.SUBTITLE_Y, GuiStyle.LABEL, false);
	}

	/** A viewer who may not edit has no field and no buttons; the control row says why, on up to two lines. */
	private void drawRights(GuiGraphicsExtractor graphics, String key) {
		TextFit.Fit fit = TextFit.fit(Component.translatable(KEY + key).getString(), TodoWindowLayout.RIGHTS_W,
				TodoWindowLayout.RIGHTS_MAX_LINES, this.font::width);
		float step = 9 * fit.scale();
		float y = CONTROLS_Y + (BUTTON - step * fit.lines().size()) / 2.0f + 1;
		for (String part : fit.lines()) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(LIST_X + 1, y);
			graphics.pose().scale(fit.scale(), fit.scale());
			graphics.text(this.font, Component.literal(part), 0, 0, GuiStyle.TEXT_DIM, false);
			graphics.pose().popMatrix();
			y += step;
		}
	}

	private Component progress() {
		TodoList list = this.menu.shownList();
		return Component.translatable(KEY + "progress", list.doneCount(), list.size());
	}

	// ── help on hover ────────────────────────────────────────────────────────────────────────────────────

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (this.hoveredSlot != null && this.hoveredSlot.hasItem() || mode == Mode.SEARCH && rowAt(mouseX,
				mouseY) >= 0) {
			return;
		}
		List<Component> lines = helpAt(mouseX, mouseY);
		if (lines.isEmpty()) {
			return;
		}
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) {
			wrapped.addAll(this.font.split(line, TodoWindowLayout.TOOLTIP_W));
		}
		graphics.setTooltipForNextFrame(this.font, wrapped, mouseX, mouseY);
	}

	/** What the control under the cursor is and does: a title line, then grey hint lines. */
	private List<Component> helpAt(double mouseX, double mouseY) {
		Control control = controlAt(mouseX, mouseY);
		if (control != null) {
			return controlHelp(control);
		}
		int progressW = this.font.width(progress());
		if (isHovering(TodoWindowLayout.TEXT_RIGHT - progressW, TodoWindowLayout.SUBTITLE_Y, progressW, 9, mouseX,
				mouseY)) {
			TodoList list = this.menu.shownList();
			return List.of(Component.translatable("tooltip.alaindustrial.todo_clipboard.progress", list.doneCount(),
					list.size()));
		}
		int row = rowAt(mouseX, mouseY);
		if (row < 0) {
			return List.of();
		}
		TodoClipboardSnapshotPayload shown = this.menu.snapshot();
		boolean canEdit = shown != null && shown.canEdit();
		boolean canTick = shown != null && shown.canTick();
		TodoList list = this.menu.shownList();
		int index = scroll + row;
		if (index == list.size() && canEdit && roomForAnother()) {
			return List.of(Component.translatable(KEY + "tip.free"), hint("tip.free.hint"));
		}
		if (index >= list.size()) {
			return List.of();
		}
		TodoEntry entry = list.entries().get(index);
		double localX = mouseX - this.leftPos;
		if (localX >= TICK_X && localX < TICK_X + BOX) {
			return canTick ? List.of(Component.translatable(KEY + (entry.done() ? "tip.untick" : "tip.tick")))
					: List.of();
		}
		if (canEdit && localX >= REMOVE_X && localX < REMOVE_X + CROSS) {
			return List.of(Component.translatable(KEY + "tip.remove"));
		}
		if (localX < ICON_X || localX >= REMOVE_X) {
			return List.of();
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(TodoClipboardItem.itemName(entry)));
		if (entry.isNote()) {
			if (canEdit) {
				lines.add(hint("tip.note"));
			}
			return lines;
		}
		TodoHave have = TodoInventoryTally.current();
		lines.add(Component.translatable(KEY + "tip.have_need", have.of(entry), entry.count())
				.withStyle(TodoHave.enough(have.of(entry), entry) ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY));
		if (canEdit) {
			lines.add(hint(localX < ICON_X + 16 ? "tip.icon" : "tip.count"));
		}
		return lines;
	}

	private List<Component> controlHelp(Control control) {
		return switch (control) {
			case FIELD -> switch (mode) {
				case SEARCH -> List.of(Component.translatable(KEY + "back"), hint("tip.back"));
				case COUNT -> List.of(Component.translatable(KEY + "ok"), hint("tip.ok_count"));
				case NOTE -> editing >= 0 ? List.of(Component.translatable(KEY + "ok"), hint("tip.ok_note"))
						: List.of(Component.translatable(KEY + "add_note"), hint(roomForAnother() ? "tip.add_note"
								: "tip.list_full"));
			};
			case SEARCH -> mode == Mode.SEARCH ? List.of(Component.translatable(KEY + "back"), hint("tip.back"))
					: List.of(Component.translatable(KEY + "tip.search"), hint(roomForAnother() ? "tip.search.hint"
							: "tip.list_full"));
			case CLEAR -> clearArmed() ? List.of(Component.translatable(KEY + "clear_confirm")
					.withStyle(ChatFormatting.RED), hint("tip.clear_again"))
					: List.of(Component.translatable(KEY + "clear"), hint("tip.clear"));
			case ACCESS -> {
				TodoAccess access = this.menu.shownList().access();
				yield List.of(Component.translatable(KEY + "access." + access.serialName()),
						hint("access_hint"), Component.translatable(KEY + "tip.switch")
								.withStyle(ChatFormatting.DARK_GRAY));
			}
		};
	}

	private static Component hint(String key) {
		return Component.translatable(KEY + key).withStyle(ChatFormatting.GRAY);
	}
}
