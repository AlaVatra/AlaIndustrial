package dev.alaindustrial.core.todo;

import java.util.List;
import java.util.Objects;

/**
 * The list of a to-do clipboard (MOD-905): its lines in the order they were added and who besides the owner
 * may touch them. The value of the {@code alaindustrial:todo_list} data component and of the clipboard block
 * entity alike, so a list taken off a wall is the same value hung back up.
 *
 * <p>Immutable, with value equality: a data component must be both (NeoForge asserts it at runtime). Every
 * change goes through {@link TodoListRules}, which returns a new list.
 *
 * @param entries the lines, top to bottom
 * @param access  who besides the owner may tick or edit the lines
 */
public record TodoList(List<TodoEntry> entries, TodoAccess access) {

	/** A blank sheet. */
	public static final TodoList EMPTY = new TodoList(List.of(), TodoAccess.OWNER_ONLY);

	public TodoList {
		entries = List.copyOf(entries);
		Objects.requireNonNull(access, "access");
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public int size() {
		return entries.size();
	}

	/** How many lines carry the done tick. */
	public int doneCount() {
		int done = 0;
		for (TodoEntry entry : entries) {
			if (entry.done()) {
				done++;
			}
		}
		return done;
	}

	public TodoList withEntries(List<TodoEntry> value) {
		return new TodoList(value, access);
	}

	public TodoList withAccess(TodoAccess value) {
		return new TodoList(entries, value);
	}
}
