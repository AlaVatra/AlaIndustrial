package dev.alaindustrial.core.todo;

import java.util.HashMap;
import java.util.Map;

/**
 * The "have / need" counter of a to-do clipboard's item lines (MOD-905, owner decision 2026-11-12): how many of
 * a line's item the viewing player carries. A tally of what the player holds, counted by the client from its own
 * inventory — the server neither counts nor sends anything — and read by the window and the sheet alike.
 *
 * <p><b>What counts as the same item:</b> the same item id. Nothing else is compared: an enchanted pickaxe, a
 * worn one and one with a custom name are all "a pickaxe", and a shulker box counts as one box, not as what is
 * inside it. A line names an item to gather, so whatever the player would hand over for it counts.
 *
 * <p>Minecraft-free on purpose: the L1 test pins the rule without the game; the client feeds it ids.
 */
public final class TodoHave {

	/** The largest number a counter shows: four digits, like a line's count. More is still "enough". */
	public static final int MAX_SHOWN = TodoListRules.MAX_COUNT;

	private final Map<String, Integer> byItem = new HashMap<>();

	/** Counts {@code count} more of the item {@code itemId}; an empty id or a count below one adds nothing. */
	public void add(String itemId, int count) {
		if (!itemId.isEmpty() && count > 0) {
			byItem.merge(itemId, count, TodoHave::saturatedSum);
		}
	}

	/** Forgets everything counted so far. */
	public void clear() {
		byItem.clear();
	}

	/** How many of {@code entry}'s item were counted; {@code 0} for a note. */
	public int of(TodoEntry entry) {
		return entry.isNote() ? 0 : byItem.getOrDefault(entry.item(), 0);
	}

	/** Whether {@code have} covers {@code entry}'s count. A note is never "enough": it has no counter. */
	public static boolean enough(int have, TodoEntry entry) {
		return !entry.isNote() && have >= entry.count();
	}

	/** {@code have} as the counter shows it: at most {@link #MAX_SHOWN}, which already covers any count. */
	public static int shown(int have) {
		return Math.min(have, MAX_SHOWN);
	}

	private static int saturatedSum(int a, int b) {
		long sum = (long) a + b;
		return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
	}
}
