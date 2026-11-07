package dev.alaindustrial.core.tooltip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The order of a tooltip's lines and which of them wait for [Shift] (MOD-958, the owner's style "09b") — one
 * rule for blocks and items alike.
 *
 * <p>Top to bottom: what it is for, its state, its figures; under [Shift] the tier, the details, the modules
 * and the key actions; then warnings and remarks, which never hide; last the "hold [Shift]" hint. Key lines are
 * the exception the owner made: when an item has nothing but its purpose and its keys, the keys show without
 * [Shift] (a [Shift] that only reveals "right-click" is a detour).
 *
 * <p>Minecraft-free and generic in the line type, so the block descriptions ({@code MachineTooltipSpec}) and the
 * item builder ({@code ItemTooltip}) lay out through the same code and the L1 lane pins it.
 */
public final class TooltipLayout {

	private TooltipLayout() {
	}

	/** A place in the tooltip, in display order. */
	public enum Slot {
		PURPOSE, STATE, STAT, TIER, DETAIL, MODULE, KEY, WARNING, NOTE;

		/** Whether a line here waits for [Shift]. */
		public boolean underShift() {
			return this == TIER || this == DETAIL || this == MODULE || this == KEY;
		}
	}

	/** A line and where it stands. */
	public record Placed<T>(Slot slot, T line) {
	}

	/** The state of the "hold [Shift] for details" line. */
	public enum Hint {
		/** No hint: [Shift] would add nothing, or the player asked for details always. */
		NONE,
		/** The hint with the key grey: [Shift] is up. */
		KEY_UP,
		/** The hint with the key white: [Shift] is held. */
		KEY_HELD
	}

	/**
	 * The lines a player sees, in display order (the order of {@link Slot}, and within a slot the order given).
	 *
	 * @param detailed whether [Shift] is held or the player asked for details always
	 */
	public static <T> List<T> arrange(List<Placed<T>> placed, boolean detailed) {
		boolean keysAlone = placed.stream().allMatch(p -> p.slot() == Slot.PURPOSE || p.slot() == Slot.KEY);
		List<Placed<T>> visible = new ArrayList<>();
		for (Placed<T> p : placed) {
			boolean waits = p.slot().underShift() && !(p.slot() == Slot.KEY && keysAlone);
			if (detailed || !waits) {
				visible.add(p);
			}
		}
		visible.sort(Comparator.comparingInt(p -> p.slot().ordinal()));
		List<T> out = new ArrayList<>(visible.size());
		for (Placed<T> p : visible) {
			out.add(p.line());
		}
		return out;
	}

	/**
	 * The hint under the lines: none when [Shift] would show nothing more ({@code expanded} lines against
	 * {@code collapsed}) or the player asked for details always; otherwise with the key grey or, while [Shift] is
	 * held, white.
	 */
	public static Hint hint(int collapsed, int expanded, boolean shiftHeld, boolean alwaysDetailed) {
		if (alwaysDetailed || expanded <= collapsed) {
			return Hint.NONE;
		}
		return shiftHeld ? Hint.KEY_HELD : Hint.KEY_UP;
	}
}
