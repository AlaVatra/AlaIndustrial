package dev.alaindustrial.core.todo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Every rule of a to-do clipboard's list (MOD-905): the limits, what an edit does, who may make it and what
 * a note may contain. The server applies an edit only through {@link #apply}, after {@link #mayApply} let the
 * player make it; the window merely hides the buttons a player may not use.
 *
 * <p>Minecraft-free, so the L1 test pins each limit without the game: whether an item id names a real item
 * is decided by the caller, which hands in the canonical id or {@code ""} for an unknown one.
 *
 * <p><b>Two sets of limits.</b> The edit limits ({@link #MAX_ENTRIES}, {@link #MAX_NOTE}, the count range) are
 * the owner's numbers and are what a player can reach. The wire limits ({@link #WIRE_ENTRIES},
 * {@link #WIRE_NOTE}) bound what a save or a packet may carry and sit above them on purpose: a wire limit can
 * be raised later but never lowered, because a list saved under a larger one would fail to sync and
 * disconnect its owner. {@link #fitWire} cuts a loaded list to the wire limits before anything sends it.
 */
public final class TodoListRules {

	/** Lines on one sheet (owner decision 2026-11-12). */
	public static final int MAX_ENTRIES = 10;
	/** Characters of a note, counted in code points (owner decision 2026-11-12). */
	public static final int MAX_NOTE = 40;
	/** The smallest count of an item line. */
	public static final int MIN_COUNT = 1;
	/** The largest count of an item line: four digits fit the row. */
	public static final int MAX_COUNT = 9999;
	/** Lines a save or a packet may carry, above {@link #MAX_ENTRIES}. */
	public static final int WIRE_ENTRIES = 32;
	/** Characters of a note a save or a packet may carry, above {@link #MAX_NOTE}. */
	public static final int WIRE_NOTE = 128;
	/** Characters of an item id a save or a packet may carry. */
	public static final int WIRE_ID = 256;

	private static final int SECTION_SIGN = 0xA7;
	private static final int DELETE = 0x7F;
	private static final int FIRST_PRINTABLE = 0x20;

	private TodoListRules() {
	}

	/**
	 * What an edit asks for. The ordinal travels on the wire: append, never reorder.
	 */
	public enum Op {
		/** Add an item line: {@code item}, {@code count}. */
		ADD_ITEM,
		/** Add a note line: {@code text}. */
		ADD_NOTE,
		/** Make line {@code index} an item line: {@code item}, {@code count}; the done tick stays. */
		SET_ITEM,
		/** Set the count of item line {@code index}. */
		SET_COUNT,
		/** Rewrite note line {@code index}: {@code text}. */
		SET_NOTE,
		/** Flip the done tick of line {@code index}. */
		TOGGLE_DONE,
		/** Remove line {@code index}; the lines below move up. */
		REMOVE,
		/** Remove every line; the access step stays. */
		CLEAR,
		/** Set the access step: {@code count} is its {@link TodoAccess#id()}. Owner only. */
		SET_ACCESS;

		private static final Op[] VALUES = values();

		/** The op with this wire number, or {@code null} for a number this build does not know. */
		public static @Nullable Op byId(int id) {
			return id >= 0 && id < VALUES.length ? VALUES[id] : null;
		}

		public int id() {
			return ordinal();
		}
	}

	/**
	 * One edit as the server receives it. Fields an op does not use are ignored.
	 *
	 * @param op    what to do
	 * @param index the line it acts on
	 * @param item  the canonical item id, or {@code ""} when the id named no item
	 * @param count the count, or the access step's id for {@link Op#SET_ACCESS}
	 * @param text  the note text, not yet cleaned
	 */
	public record Edit(Op op, int index, String item, int count, String text) {

		public static Edit addItem(String item, int count) {
			return new Edit(Op.ADD_ITEM, -1, item, count, "");
		}

		public static Edit addNote(String text) {
			return new Edit(Op.ADD_NOTE, -1, "", 0, text);
		}

		public static Edit setItem(int index, String item, int count) {
			return new Edit(Op.SET_ITEM, index, item, count, "");
		}

		public static Edit setCount(int index, int count) {
			return new Edit(Op.SET_COUNT, index, "", count, "");
		}

		public static Edit setNote(int index, String text) {
			return new Edit(Op.SET_NOTE, index, "", 0, text);
		}

		public static Edit toggleDone(int index) {
			return new Edit(Op.TOGGLE_DONE, index, "", 0, "");
		}

		public static Edit remove(int index) {
			return new Edit(Op.REMOVE, index, "", 0, "");
		}

		public static Edit clear() {
			return new Edit(Op.CLEAR, -1, "", 0, "");
		}

		public static Edit setAccess(TodoAccess access) {
			return new Edit(Op.SET_ACCESS, -1, "", access.id(), "");
		}
	}

	/** Why the server turned an edit down, or that it went through. */
	public enum Outcome {
		/** The list changed. */
		APPLIED,
		/** The edit was made against an older list than the one on the wall; the window is refreshed instead. */
		STALE,
		/** The player may not make this edit. */
		DENIED,
		/** The edit breaks a limit or names a line or an item that is not there. */
		INVALID
	}

	/**
	 * The list after {@code edit}, or {@code null} when the edit breaks a rule. Rights are not checked here —
	 * see {@link #mayApply}.
	 */
	public static @Nullable TodoList apply(TodoList list, Edit edit) {
		List<TodoEntry> lines = new ArrayList<>(list.entries());
		switch (edit.op()) {
			case ADD_ITEM -> {
				if (lines.size() >= MAX_ENTRIES || edit.item().isEmpty()) {
					return null;
				}
				lines.add(TodoEntry.item(edit.item(), clampCount(edit.count())));
			}
			case ADD_NOTE -> {
				String note = cleanNote(edit.text());
				if (lines.size() >= MAX_ENTRIES || note.isEmpty()) {
					return null;
				}
				lines.add(TodoEntry.note(note));
			}
			case SET_ITEM -> {
				if (!inRange(lines, edit.index()) || edit.item().isEmpty()) {
					return null;
				}
				TodoEntry old = lines.get(edit.index());
				lines.set(edit.index(), new TodoEntry(edit.item(), clampCount(edit.count()), "", old.done()));
			}
			case SET_COUNT -> {
				if (!inRange(lines, edit.index()) || lines.get(edit.index()).isNote()) {
					return null;
				}
				lines.set(edit.index(), lines.get(edit.index()).withCount(clampCount(edit.count())));
			}
			case SET_NOTE -> {
				String note = cleanNote(edit.text());
				if (!inRange(lines, edit.index()) || !lines.get(edit.index()).isNote() || note.isEmpty()) {
					return null;
				}
				lines.set(edit.index(), new TodoEntry("", 0, note, lines.get(edit.index()).done()));
			}
			case TOGGLE_DONE -> {
				if (!inRange(lines, edit.index())) {
					return null;
				}
				TodoEntry old = lines.get(edit.index());
				lines.set(edit.index(), old.withDone(!old.done()));
			}
			case REMOVE -> {
				if (!inRange(lines, edit.index())) {
					return null;
				}
				lines.remove(edit.index());
			}
			case CLEAR -> lines.clear();
			case SET_ACCESS -> {
				TodoAccess access = TodoAccess.byId(edit.count());
				return access == null ? null : list.withAccess(access);
			}
			default -> {
				return null;
			}
		}
		return list.withEntries(lines);
	}

	/**
	 * Whether {@code actor} may make an edit of kind {@code op} — the one rights check the server runs before
	 * every edit. Never a spectator. The owner may do everything. On a clipboard nobody owns (hung by a
	 * dispenser or a command) anyone may do everything but move the access step, which has no owner to move it.
	 * Anyone else: a tick when the step lets others tick, any other change of the lines when it lets others
	 * edit, the step itself never.
	 */
	public static boolean mayApply(Op op, @Nullable UUID owner, UUID actor, TodoAccess access, boolean spectator) {
		if (spectator) {
			return false;
		}
		boolean isOwner = owner != null && owner.equals(actor);
		return switch (op) {
			case SET_ACCESS -> isOwner;
			case TOGGLE_DONE -> isOwner || owner == null || access.othersTick();
			default -> isOwner || owner == null || access.othersEdit();
		};
	}

	/** A count pulled into {@link #MIN_COUNT}..{@link #MAX_COUNT}. */
	public static int clampCount(int count) {
		return Math.max(MIN_COUNT, Math.min(MAX_COUNT, count));
	}

	/**
	 * A note as the list keeps it: without the characters chat refuses (control characters, DEL and the
	 * formatting sign), without spaces at either end and at most {@link #MAX_NOTE} code points long.
	 */
	public static String cleanNote(String raw) {
		StringBuilder kept = new StringBuilder(raw.length());
		raw.codePoints().filter(TodoListRules::allowedInNote).forEach(kept::appendCodePoint);
		return cutCodePoints(kept.toString().strip(), MAX_NOTE).strip();
	}

	/** The same test as chat's allowed-character rule: printable, not DEL, not the formatting sign. */
	public static boolean allowedInNote(int codePoint) {
		return codePoint >= FIRST_PRINTABLE && codePoint != DELETE && codePoint != SECTION_SIGN;
	}

	/**
	 * A loaded list cut to the wire limits, so that sending it can never fail: at most {@link #WIRE_ENTRIES}
	 * lines, notes of at most {@link #WIRE_NOTE} and ids of at most {@link #WIRE_ID} code points. Counts are
	 * pulled into range. A list already inside the limits comes back equal.
	 */
	public static TodoList fitWire(TodoList list) {
		List<TodoEntry> lines = new ArrayList<>(Math.min(list.size(), WIRE_ENTRIES));
		for (TodoEntry entry : list.entries()) {
			if (lines.size() == WIRE_ENTRIES) {
				break;
			}
			String item = cutCodePoints(entry.item(), WIRE_ID);
			lines.add(new TodoEntry(item, item.isEmpty() ? 0 : clampCount(entry.count()),
					cutCodePoints(entry.note(), WIRE_NOTE), entry.done()));
		}
		return list.withEntries(lines);
	}

	private static boolean inRange(List<TodoEntry> lines, int index) {
		return index >= 0 && index < lines.size();
	}

	private static String cutCodePoints(String text, int max) {
		if (text.codePointCount(0, text.length()) <= max) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, max));
	}
}
