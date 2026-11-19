package dev.alaindustrial.core.environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How many pieces of each fuel a generator has burnt (MOD-992): "34 coal, 12 oak planks". Kept by item id, a
 * plain string, so no Minecraft imports and the L1 lane pins the rule; the block entity and the screens call
 * these methods, so the tested code is the production code.
 *
 * <p><b>A ceiling on what is kept.</b> A pack with many modded fuels could otherwise grow one save entry per
 * kind forever. At most {@link #MAX_KINDS} kinds are kept by name; a piece of a kind that finds the table full
 * is added to {@link #other()}, so the total stays exact while the save stays small. The kinds already in the
 * table keep their places: the common fuels arrive first and stay named.
 *
 * <p><b>What is shown.</b> {@link #top} names the kinds burnt most, most first (a tie goes by id, so the order is
 * the same on every start), and folds the rest, named or not, into one "other" figure.
 */
public final class FuelTally {

	/** Kinds kept by name; a further kind is counted under {@link #other()}. */
	public static final int MAX_KINDS = 16;

	/** Kinds the statistics name; the rest is one "other" figure. */
	public static final int SHOWN_KINDS = 5;

	/** One fuel and the pieces of it burnt. */
	public record Entry(String id, long count) {
	}

	/** What the statistics carry: the kinds burnt most, most first, and every other piece in one figure. */
	public record Top(List<Entry> kinds, long rest) {

		public Top {
			kinds = List.copyOf(kinds);
		}

		/** Every piece burnt: the named kinds and the rest. */
		public long total() {
			long sum = rest;
			for (Entry entry : kinds) {
				sum += entry.count();
			}
			return sum;
		}
	}

	/** Most first; a tie goes by id, so two starts of the same save show the same order. */
	private static final Comparator<Entry> MOST_FIRST =
			Comparator.comparingLong(Entry::count).reversed().thenComparing(Entry::id);

	private final Map<String, Long> counts = new LinkedHashMap<>();
	private long other;

	/** One piece of {@code id} was taken from the slot to burn. */
	public void record(String id) {
		Long count = counts.get(id);
		if (count != null) {
			counts.put(id, count + 1L);
		} else if (counts.size() < MAX_KINDS) {
			counts.put(id, 1L);
		} else {
			other++;
		}
	}

	/** Nothing was ever counted: a block that writes no key for it. */
	public boolean isEmpty() {
		return counts.isEmpty() && other == 0L;
	}

	/** The kinds kept by name, in the order they first burnt; read-only. */
	public Map<String, Long> counts() {
		return Collections.unmodifiableMap(counts);
	}

	/** Pieces of the kinds that found the table full. */
	public long other() {
		return other;
	}

	/** Every piece burnt. */
	public long total() {
		long sum = other;
		for (long count : counts.values()) {
			sum += count;
		}
		return sum;
	}

	/** The {@code n} kinds burnt most, most first, and every other piece as one figure. */
	public Top top(int n) {
		List<Entry> all = new ArrayList<>(counts.size());
		counts.forEach((id, count) -> all.add(new Entry(id, count)));
		all.sort(MOST_FIRST);
		int shown = Math.clamp(n, 0, all.size());
		long rest = other;
		for (Entry entry : all.subList(shown, all.size())) {
			rest += entry.count();
		}
		return new Top(all.subList(0, shown), rest);
	}

	/**
	 * Put back what a save holds; everything counted before is dropped. A table larger than {@link #MAX_KINDS}
	 * (a hand-edited save) keeps its biggest kinds and moves the rest under {@code other}; a count that is not
	 * positive is ignored.
	 */
	public void restore(Map<String, Long> saved, long savedOther) {
		counts.clear();
		other = Math.max(0L, savedOther);
		List<Entry> all = new ArrayList<>(saved.size());
		saved.forEach((id, count) -> {
			if (id != null && count != null && count > 0L) {
				all.add(new Entry(id, count));
			}
		});
		all.sort(MOST_FIRST);
		for (Entry entry : all) {
			if (counts.size() < MAX_KINDS) {
				counts.put(entry.id(), entry.count());
			} else {
				other += entry.count();
			}
		}
	}
}
