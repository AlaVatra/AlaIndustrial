package dev.alaindustrial.core.todo;

import org.jspecify.annotations.Nullable;

/**
 * Who besides the owner may touch a to-do clipboard's list (MOD-905, owner decision 2026-11-12): three steps
 * instead of one "everyone may edit" switch. Only the owner moves the step; the server checks it on every edit
 * ({@link TodoListRules#mayApply}).
 *
 * <p>The ordinal travels on the wire and the name is saved: append, never reorder or rename.
 */
public enum TodoAccess {
	/** Only the owner changes anything. */
	OWNER_ONLY("owner_only"),
	/** Anyone ticks and unticks lines; only the owner adds, removes or rewrites them. */
	OTHERS_TICK("others_tick"),
	/** Anyone changes anything but the step itself. */
	OTHERS_EDIT("others_edit");

	private static final TodoAccess[] VALUES = values();

	private final String serialName;

	TodoAccess(String serialName) {
		this.serialName = serialName;
	}

	/** The name a save keeps, also the last part of the window's lang key for this step. */
	public String serialName() {
		return serialName;
	}

	public int id() {
		return ordinal();
	}

	/** Whether a player who is not the owner may tick and untick lines. */
	public boolean othersTick() {
		return this != OWNER_ONLY;
	}

	/** Whether a player who is not the owner may add, remove and rewrite lines. */
	public boolean othersEdit() {
		return this == OTHERS_EDIT;
	}

	/** The step the owner's switch moves to next: only me, others tick, others edit, and round again. */
	public TodoAccess next() {
		return VALUES[(ordinal() + 1) % VALUES.length];
	}

	/** The step with this wire number, or {@code null} for a number this build does not know. */
	public static @Nullable TodoAccess byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : null;
	}

	/** The step saved under {@code name}; an unknown name reads as {@link #OWNER_ONLY}, the safe end. */
	public static TodoAccess byName(String name) {
		for (TodoAccess access : VALUES) {
			if (access.serialName.equals(name)) {
				return access;
			}
		}
		return OWNER_ONLY;
	}

	/**
	 * The step a list saved before the ladder had: its "everyone may edit" switch on meant everyone edited, off
	 * meant only the owner did.
	 */
	public static TodoAccess fromShared(boolean shared) {
		return shared ? OTHERS_EDIT : OWNER_ONLY;
	}
}
