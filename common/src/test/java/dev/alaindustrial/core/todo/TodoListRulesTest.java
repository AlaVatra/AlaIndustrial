package dev.alaindustrial.core.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.todo.TodoListRules.Edit;
import dev.alaindustrial.core.todo.TodoListRules.Op;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the to-do clipboard's rules (MOD-905): the owner's limits (10 lines, 40 characters, counts
 * 1..9999), what each edit does to the lines, the note cleaning, the rights and the wire fit. Each case names
 * the number it pins, so changing a limit turns exactly that case red.
 */
class TodoListRulesTest {

	private static final String OAK = "minecraft:oak_log";

	private static TodoList full() {
		TodoList list = TodoList.EMPTY;
		for (int i = 0; i < TodoListRules.MAX_ENTRIES; i++) {
			list = TodoListRules.apply(list, Edit.addItem(OAK, i + 1));
			assertNotNull(list, "line " + i + " of " + TodoListRules.MAX_ENTRIES + " is accepted");
		}
		return list;
	}

	@Test
	void limitsAreTheOwnersNumbers() {
		assertEquals(10, TodoListRules.MAX_ENTRIES);
		assertEquals(40, TodoListRules.MAX_NOTE);
		assertEquals(1, TodoListRules.MIN_COUNT);
		assertEquals(9999, TodoListRules.MAX_COUNT);
		assertTrue(TodoListRules.WIRE_ENTRIES > TodoListRules.MAX_ENTRIES, "the wire carries more than a sheet");
		assertTrue(TodoListRules.WIRE_NOTE > TodoListRules.MAX_NOTE, "the wire carries longer notes than a sheet");
	}

	@Test
	void eleventhLineIsRefusedForItemsAndNotes() {
		TodoList list = full();
		assertEquals(10, list.size());
		assertNull(TodoListRules.apply(list, Edit.addItem(OAK, 1)), "an 11th item line");
		assertNull(TodoListRules.apply(list, Edit.addNote("eleventh")), "an 11th note line");
	}

	@Test
	void countIsPulledIntoRange() {
		TodoList zero = TodoListRules.apply(TodoList.EMPTY, Edit.addItem(OAK, 0));
		TodoList huge = TodoListRules.apply(TodoList.EMPTY, Edit.addItem(OAK, 10_000));
		TodoList negative = TodoListRules.apply(TodoList.EMPTY, Edit.addItem(OAK, -5));
		assertEquals(1, zero.entries().get(0).count());
		assertEquals(9999, huge.entries().get(0).count());
		assertEquals(1, negative.entries().get(0).count());
		TodoList set = TodoListRules.apply(zero, Edit.setCount(0, 12_345));
		assertEquals(9999, set.entries().get(0).count());
	}

	@Test
	void unknownItemIsRefused() {
		assertNull(TodoListRules.apply(TodoList.EMPTY, Edit.addItem("", 4)), "an id the caller found no item for");
		TodoList one = TodoListRules.apply(TodoList.EMPTY, Edit.addItem(OAK, 4));
		assertNull(TodoListRules.apply(one, Edit.setItem(0, "", 4)));
	}

	@Test
	void noteIsCleanedAndCut() {
		assertEquals("build a cgenerator", TodoListRules.cleanNote("  build\n a §cgenerator\u007f\t "));
		String fortyOne = "a".repeat(41);
		assertEquals(40, TodoListRules.cleanNote(fortyOne).length(), "41 characters are cut to 40");
		String emoji = "😀".repeat(41);
		String cut = TodoListRules.cleanNote(emoji);
		assertEquals(40, cut.codePointCount(0, cut.length()), "the cut counts code points, not UTF-16 units");
		assertNull(TodoListRules.apply(TodoList.EMPTY, Edit.addNote(" \n§ ")), "a note empty after cleaning");
		TodoList added = TodoListRules.apply(TodoList.EMPTY, Edit.addNote(fortyOne));
		assertEquals(40, added.entries().get(0).note().length());
		assertTrue(added.entries().get(0).isNote());
	}

	@Test
	void editsActOnTheirLineOnly() {
		TodoList list = TodoListRules.apply(TodoList.EMPTY, Edit.addItem(OAK, 32));
		list = TodoListRules.apply(list, Edit.addNote("build a generator"));
		list = TodoListRules.apply(list, Edit.addItem("minecraft:cobblestone", 128));

		TodoList ticked = TodoListRules.apply(list, Edit.toggleDone(1));
		assertEquals(List.of(false, true, false), ticked.entries().stream().map(TodoEntry::done).toList());
		TodoList unticked = TodoListRules.apply(ticked, Edit.toggleDone(1));
		assertEquals(list, unticked, "a second click takes the tick off");

		TodoList removed = TodoListRules.apply(ticked, Edit.remove(0));
		assertEquals(2, removed.size());
		assertEquals("build a generator", removed.entries().get(0).note(), "the lines below move up");
		assertTrue(removed.entries().get(0).done(), "the moved line keeps its tick");

		TodoList renamed = TodoListRules.apply(ticked, Edit.setNote(1, "  build two  "));
		assertEquals("build two", renamed.entries().get(1).note());
		assertTrue(renamed.entries().get(1).done(), "rewriting a note keeps its tick");
		assertNull(TodoListRules.apply(list, Edit.setNote(0, "x")), "an item line has no note to rewrite");
		assertNull(TodoListRules.apply(list, Edit.setCount(1, 5)), "a note line has no count");

		TodoList swapped = TodoListRules.apply(ticked, Edit.setItem(1, "minecraft:paper", 3));
		assertEquals(TodoEntry.item("minecraft:paper", 3).withDone(true), swapped.entries().get(1));

		TodoList cleared = TodoListRules.apply(list.withAccess(TodoAccess.OTHERS_TICK), Edit.clear());
		assertTrue(cleared.isEmpty());
		assertEquals(TodoAccess.OTHERS_TICK, cleared.access(), "clearing keeps the access step");

		TodoList opened = TodoListRules.apply(list, Edit.setAccess(TodoAccess.OTHERS_EDIT));
		assertEquals(TodoAccess.OTHERS_EDIT, opened.access());
		assertEquals(list.entries(), opened.entries(), "moving the step leaves the lines alone");
		assertNull(TodoListRules.apply(list, new Edit(Op.SET_ACCESS, -1, "", 7, "")), "a step this build lacks");
	}

	@Test
	void indexOutsideTheListIsRefused() {
		TodoList list = TodoListRules.apply(TodoList.EMPTY, Edit.addItem(OAK, 1));
		for (int bad : new int[] {-1, 1, 99}) {
			assertNull(TodoListRules.apply(list, Edit.toggleDone(bad)), "toggle " + bad);
			assertNull(TodoListRules.apply(list, Edit.remove(bad)), "remove " + bad);
			assertNull(TodoListRules.apply(list, Edit.setCount(bad, 2)), "count " + bad);
			assertNull(TodoListRules.apply(list, Edit.setItem(bad, OAK, 2)), "item " + bad);
		}
	}

	@Test
	void opsKeepTheirWireNumbers() {
		Op[] expected = {Op.ADD_ITEM, Op.ADD_NOTE, Op.SET_ITEM, Op.SET_COUNT, Op.SET_NOTE, Op.TOGGLE_DONE, Op.REMOVE,
				Op.CLEAR, Op.SET_ACCESS};
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], Op.byId(i), "wire number " + i);
		}
		assertNull(Op.byId(expected.length), "a number this build does not know");
		assertNull(Op.byId(-1));
	}

	/**
	 * The access ladder, every step × (owner, guest) × (tick, change a line): the owner always; a guest ticks from
	 * "others tick" up and changes lines only at "others edit". Each row names its expectation, so a rule that
	 * drifts turns exactly the rows it moved red.
	 */
	@Test
	void accessLadderDecidesWhoTicksAndWhoEdits() {
		UUID owner = UUID.randomUUID();
		UUID guest = UUID.randomUUID();
		Op[] lineChanges = {Op.ADD_ITEM, Op.ADD_NOTE, Op.SET_ITEM, Op.SET_COUNT, Op.SET_NOTE, Op.REMOVE, Op.CLEAR};
		Object[][] table = {
				// step, owner ticks, owner edits, guest ticks, guest edits
				{TodoAccess.OWNER_ONLY, true, true, false, false},
				{TodoAccess.OTHERS_TICK, true, true, true, false},
				{TodoAccess.OTHERS_EDIT, true, true, true, true}};
		for (Object[] row : table) {
			TodoAccess step = (TodoAccess) row[0];
			assertEquals(row[1], TodoListRules.mayApply(Op.TOGGLE_DONE, owner, owner, step, false),
					step + " owner tick");
			assertEquals(row[3], TodoListRules.mayApply(Op.TOGGLE_DONE, owner, guest, step, false),
					step + " guest tick");
			for (Op op : lineChanges) {
				assertEquals(row[2], TodoListRules.mayApply(op, owner, owner, step, false), step + " owner " + op);
				assertEquals(row[4], TodoListRules.mayApply(op, owner, guest, step, false), step + " guest " + op);
			}
			assertTrue(TodoListRules.mayApply(Op.SET_ACCESS, owner, owner, step, false), step + ": the owner moves it");
			assertFalse(TodoListRules.mayApply(Op.SET_ACCESS, owner, guest, step, false), step + ": a guest never");
			for (Op op : Op.values()) {
				assertFalse(TodoListRules.mayApply(op, owner, owner, step, true), step + ": never a spectator, " + op);
				boolean ownerless = TodoListRules.mayApply(op, null, guest, step, false);
				assertEquals(op != Op.SET_ACCESS, ownerless, step + ": an ownerless clipboard is open but for " + op);
			}
		}
	}

	@Test
	void accessStepsCycleAndReadOldSavesSoftly() {
		assertEquals(TodoAccess.OTHERS_TICK, TodoAccess.OWNER_ONLY.next());
		assertEquals(TodoAccess.OTHERS_EDIT, TodoAccess.OTHERS_TICK.next());
		assertEquals(TodoAccess.OWNER_ONLY, TodoAccess.OTHERS_EDIT.next(), "the switch goes round");
		assertEquals(TodoAccess.OTHERS_EDIT, TodoAccess.fromShared(true), "an old \"everyone may edit\" save");
		assertEquals(TodoAccess.OWNER_ONLY, TodoAccess.fromShared(false));
		for (TodoAccess step : TodoAccess.values()) {
			assertEquals(step, TodoAccess.byName(step.serialName()), "saved name of " + step);
			assertEquals(step, TodoAccess.byId(step.id()), "wire number of " + step);
		}
		assertEquals(List.of("owner_only", "others_tick", "others_edit"),
				List.of(TodoAccess.values()).stream().map(TodoAccess::serialName).toList(), "saved names never move");
		assertEquals(TodoAccess.OWNER_ONLY, TodoAccess.byName("from_a_newer_build"), "an unknown name is the safe end");
		assertNull(TodoAccess.byId(TodoAccess.values().length));
	}

	@Test
	void wireFitCutsAnOversizedLoad() {
		List<TodoEntry> lines = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			lines.add(new TodoEntry("", 0, "n".repeat(300), false));
		}
		lines.set(0, new TodoEntry(OAK, 50_000, "", true));
		TodoList fitted = TodoListRules.fitWire(new TodoList(lines, TodoAccess.OTHERS_EDIT));
		assertEquals(TodoListRules.WIRE_ENTRIES, fitted.size());
		assertEquals(TodoListRules.WIRE_NOTE, fitted.entries().get(1).note().length());
		assertEquals(9999, fitted.entries().get(0).count());
		assertEquals(TodoAccess.OTHERS_EDIT, fitted.access());
		TodoList small = TodoListRules.apply(TodoList.EMPTY, Edit.addItem(OAK, 3));
		assertEquals(small, TodoListRules.fitWire(small), "a list inside the limits comes back equal");
	}
}
