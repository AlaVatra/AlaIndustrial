package dev.alaindustrial.core.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the to-do clipboard's "have/need" counter (MOD-905): what counts as the same item, when the
 * counter says "enough" and how large a number it shows.
 */
class TodoHaveTest {

	private static final String OAK = "minecraft:oak_log";

	@Test
	void stacksOfTheSameIdAddUpWhateverTheirComponents() {
		TodoHave have = new TodoHave();
		// Three stacks of one item — say plain, renamed and enchanted: the client hands in the id only.
		have.add(OAK, 64);
		have.add(OAK, 10);
		have.add(OAK, 1);
		have.add("minecraft:birch_log", 50);
		assertEquals(75, have.of(TodoEntry.item(OAK, 32)), "every stack of the same id counts");
		assertEquals(50, have.of(TodoEntry.item("minecraft:birch_log", 1)), "another id is another item");
		assertEquals(0, have.of(TodoEntry.item("minecraft:stone", 1)), "an item not carried");
		assertEquals(0, have.of(TodoEntry.note("build a generator")), "a note has no counter");
	}

	@Test
	void nothingIsCountedForAnEmptyIdOrAnEmptyStack() {
		TodoHave have = new TodoHave();
		have.add("", 5);
		have.add(OAK, 0);
		have.add(OAK, -3);
		assertEquals(0, have.of(TodoEntry.item(OAK, 1)));
		assertEquals(0, have.of(new TodoEntry("", 1, "", false)), "an empty id is a note, never a match");
	}

	@Test
	void enoughMeansAtLeastTheCount() {
		TodoEntry wants32 = TodoEntry.item(OAK, 32);
		assertFalse(TodoHave.enough(31, wants32));
		assertTrue(TodoHave.enough(32, wants32), "exactly the count is enough");
		assertTrue(TodoHave.enough(500, wants32));
		assertFalse(TodoHave.enough(999, TodoEntry.note("x")), "a note is never enough: it has no counter");
	}

	@Test
	void theCounterShowsFourDigitsAtMostAndNeverOverflows() {
		assertEquals(12, TodoHave.shown(12));
		assertEquals(9999, TodoHave.shown(9999));
		assertEquals(9999, TodoHave.shown(64 * 36 * 27), "a full inventory of boxes still fits the row");
		TodoHave have = new TodoHave();
		have.add(OAK, Integer.MAX_VALUE);
		have.add(OAK, 10);
		assertEquals(Integer.MAX_VALUE, have.of(TodoEntry.item(OAK, 1)), "the tally saturates, it never wraps");
		have.clear();
		assertEquals(0, have.of(TodoEntry.item(OAK, 1)), "clear forgets everything");
	}
}
