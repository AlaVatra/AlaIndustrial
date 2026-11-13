package dev.alaindustrial.client.screen;

/**
 * What a key press does in the to-do clipboard's window (MOD-905), decided without Minecraft so the L1 lane can
 * test it.
 *
 * <p><b>Why it exists.</b> The window used to hand every key to its text field first while the field had focus, and
 * a focused field claims every key — Escape included — so a player who had clicked the free line by mistake (the
 * item search opens and takes the focus) could not leave the window at all. Escape is now decided first: inside a
 * sub-mode (the search, typing a count, rewriting a note) it goes back to the list, on the list it closes the
 * window. Only then does a focused field get the key, so the inventory key types its letter there instead of
 * closing the window mid-word, as in vanilla's anvil; with the field not focused it closes the window as usual.
 */
public final class TodoWindowKeys {

	/** The outcome of one key press. */
	public enum Action {
		/** Close the window. */
		CLOSE,
		/** Leave the search, the count or the note being rewritten, and show the list. */
		BACK_TO_LIST,
		/** Save the field: add the note, set the count, or leave the search. */
		CONFIRM,
		/** The focused field takes the key, and no one else sees it. */
		TYPE,
		/** The window's usual handling: the inventory key closes it, number keys swap hotbar slots. */
		PASS
	}

	private TodoWindowKeys() {
	}

	/**
	 * @param escape       the key is Escape
	 * @param confirm      the key is Enter
	 * @param fieldFocused the text field below the list has the focus
	 * @param inSubMode    the window shows the search, a count being typed or a note being rewritten
	 */
	public static Action of(boolean escape, boolean confirm, boolean fieldFocused, boolean inSubMode) {
		if (escape) {
			return inSubMode ? Action.BACK_TO_LIST : Action.CLOSE;
		}
		if (!fieldFocused) {
			return Action.PASS;
		}
		return confirm ? Action.CONFIRM : Action.TYPE;
	}
}
