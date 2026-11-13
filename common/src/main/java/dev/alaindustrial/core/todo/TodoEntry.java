package dev.alaindustrial.core.todo;

import java.util.Objects;

/**
 * One line of a to-do clipboard (MOD-905): an item with a count, or a text note, and its done tick.
 *
 * <p><b>The item is a string, not an item.</b> A save from a build that had an item this one does not must
 * still load: an unknown id decodes fine and stays a line of the list (the rule {@code MagnetFilter} follows
 * for the same reason). Nothing is stored in the clipboard: a line names what to gather, it holds none of it.
 *
 * <p>Minecraft-free on purpose: the rules ({@link TodoListRules}) and their L1 test load this record without
 * the game on the class path. The codecs live in {@code TodoListCodecs}.
 *
 * @param item  the item id ({@code "minecraft:oak_log"}), or {@code ""} for a note
 * @param count how many of the item; meaningless for a note (kept at {@code 0})
 * @param note  the note text; {@code ""} for an item line
 * @param done  the done tick
 */
public record TodoEntry(String item, int count, String note, boolean done) {

	public TodoEntry {
		Objects.requireNonNull(item, "item");
		Objects.requireNonNull(note, "note");
	}

	/** An item line, not done. */
	public static TodoEntry item(String item, int count) {
		return new TodoEntry(item, count, "", false);
	}

	/** A note line, not done. */
	public static TodoEntry note(String note) {
		return new TodoEntry("", 0, note, false);
	}

	/** Whether this line is a text note rather than an item with a count. */
	public boolean isNote() {
		return item.isEmpty();
	}

	public TodoEntry withDone(boolean value) {
		return new TodoEntry(item, count, note, value);
	}

	public TodoEntry withCount(int value) {
		return new TodoEntry(item, value, note, done);
	}
}
