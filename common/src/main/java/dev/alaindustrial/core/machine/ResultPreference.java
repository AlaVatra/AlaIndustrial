package dev.alaindustrial.core.machine;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The rule of a furnace's result button (MOD-1018), free of Minecraft types so L1 can pin it.
 *
 * <p>A machine whose input matches several recipes lists the distinct results in a fixed order, the default
 * first ({@code dev.alaindustrial.recipe.ResultOptions}). It stores the player's choice as the KEY of the chosen
 * result (the result item's id), never as a position: a position means a different result for every input,
 * a key means "this item, whenever the input offers it". The stored key is {@code null} while the player has
 * not chosen, and is cleared again when the button wraps round to the default.
 */
public final class ResultPreference {

	private ResultPreference() {
	}

	/**
	 * The option the machine runs: the position of {@code preferred} among {@code keys}, or 0 (the default) when
	 * nothing is preferred or this input does not offer the preferred result. The preference itself is left
	 * alone in the second case, so the old input brings the old choice back.
	 *
	 * @return the selected position, or -1 when there are no options at all
	 */
	public static int selectedIndex(List<String> keys, @Nullable String preferred) {
		if (keys.isEmpty()) {
			return -1;
		}
		if (preferred == null) {
			return 0;
		}
		int at = keys.indexOf(preferred);
		return at < 0 ? 0 : at;
	}

	/**
	 * The preference after one press of the button: the key of the next option, or {@code null} when the press
	 * wraps round to the default (option 0). With fewer than two options the button does nothing and the
	 * preference is returned unchanged.
	 */
	public static @Nullable String afterCycle(List<String> keys, @Nullable String preferred) {
		if (keys.size() < 2) {
			return preferred;
		}
		int next = (selectedIndex(keys, preferred) + 1) % keys.size();
		return next == 0 ? null : keys.get(next);
	}
}
