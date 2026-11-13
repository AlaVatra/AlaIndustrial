package dev.alaindustrial.block.entity;

/**
 * How many experience points a furnace pays for a number of smelts of one recipe (MOD-971).
 *
 * <p>The vanilla furnace's rule, re-implemented: the whole part of {@code count * experience} is paid, and the
 * fractional part becomes one more point with exactly that probability. Kept free of game types so the rule has
 * an L1 test; the caller passes the random roll.
 */
public final class SmeltingExperience {

	private SmeltingExperience() {
	}

	/**
	 * Points for {@code count} smelts of a recipe worth {@code experience} each.
	 *
	 * @param roll a uniform random number in {@code [0, 1)}; the fractional point is paid when it is below the
	 *             fraction
	 */
	public static int points(int count, float experience, float roll) {
		float total = (float) count * experience;
		int whole = (int) Math.floor(total);
		float fraction = total - whole;
		if (fraction != 0.0F && roll < fraction) {
			whole++;
		}
		return whole;
	}
}
