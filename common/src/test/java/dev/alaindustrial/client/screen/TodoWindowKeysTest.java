package dev.alaindustrial.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.client.screen.TodoWindowKeys.Action;
import org.junit.jupiter.api.Test;

/**
 * L1: the to-do clipboard window's keys (MOD-905). The playtest bug was a focused field swallowing Escape, so the
 * player could not leave the window after opening the item search by mistake.
 */
class TodoWindowKeysTest {

	@Test
	void escapeInTheListClosesTheWindowEvenWithTheFieldFocused() {
		assertEquals(Action.CLOSE, TodoWindowKeys.of(true, false, true, false));
		assertEquals(Action.CLOSE, TodoWindowKeys.of(true, false, false, false));
	}

	@Test
	void escapeInASubModeGoesBackToTheListFirst() {
		assertEquals(Action.BACK_TO_LIST, TodoWindowKeys.of(true, false, true, true));
		assertEquals(Action.BACK_TO_LIST, TodoWindowKeys.of(true, false, false, true));
	}

	@Test
	void aFocusedFieldTakesLettersAndEnterConfirms() {
		assertEquals(Action.TYPE, TodoWindowKeys.of(false, false, true, false));
		assertEquals(Action.TYPE, TodoWindowKeys.of(false, false, true, true));
		assertEquals(Action.CONFIRM, TodoWindowKeys.of(false, true, true, true));
	}

	@Test
	void withoutFocusEveryOtherKeyIsTheWindowsUsualOne() {
		// The inventory key closes the window this way; Enter on the list does nothing of ours.
		assertEquals(Action.PASS, TodoWindowKeys.of(false, false, false, false));
		assertEquals(Action.PASS, TodoWindowKeys.of(false, true, false, true));
	}
}
