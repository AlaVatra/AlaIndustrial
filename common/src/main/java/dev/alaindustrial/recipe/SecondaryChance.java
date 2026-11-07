package dev.alaindustrial.recipe;

import java.util.function.DoubleSupplier;

/**
 * The roll for a recipe's bonus output (MOD-960): whether one completed operation pays its
 * {@code secondary_result}. Pure and Minecraft-free so the boundaries are pinned by an L1 test; the
 * machine passes {@code level.getRandom()::nextDouble}.
 */
public final class SecondaryChance {

	private SecondaryChance() {
	}

	/**
	 * Whether the bonus is granted this operation. A chance of 1 or more is granted without drawing, so a
	 * recipe that states no chance (the JSON default 1.0, every shipped recipe) leaves the level's random
	 * sequence exactly as it was before the roll existed. Anything lower draws once from {@code rng}, a value
	 * in {@code [0, 1)}: the bonus is granted when the draw is below the chance, so 0 or less never pays.
	 */
	public static boolean granted(double chance, DoubleSupplier rng) {
		if (chance >= 1.0) {
			return true;
		}
		return rng.getAsDouble() < chance;
	}
}
