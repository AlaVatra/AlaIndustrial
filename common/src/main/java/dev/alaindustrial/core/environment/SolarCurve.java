package dev.alaindustrial.core.environment;

/**
 * The output curves of the third solar rung (MOD-1031): how much of its peak a full-block panel makes at a given
 * moment. Minecraft-free on purpose, so the L1 suite checks the curves and their daily averages without a world.
 *
 * <p><b>Day: the height of the sun.</b> On the overworld clock the sun rises at {@code 0}, stands highest at
 * {@code 6000} and sets at {@code 12000}. The share of the peak is {@code edge + (1 - edge) * sin(pi * t / 12000)}:
 * {@code edge} at sunrise and sunset, all of it at noon. The few bright ticks the clock places outside that window
 * (the sky is still light just after {@code 12000} and just before {@code 24000}) stay at {@code edge}, so the curve
 * never drops below the figure the player reads at sunset.
 *
 * <p><b>Night: the phase of the moon.</b> The share is {@code newMoon + (1 - newMoon) * brightness}, where
 * {@code brightness} is the game's own moon brightness of the night's phase: 1 at the full moon, 0 at the new moon.
 */
public final class SolarCurve {

	/** Length of the overworld day on its clock. */
	public static final long DAY_LENGTH = 24000L;
	/** Sunset on that clock: from sunrise at 0 to here the sun is up. */
	public static final long SUNSET = 12000L;

	private SolarCurve() {
	}

	/**
	 * Share of the noon output at {@code clockTime}, between {@code edge} and 1.
	 *
	 * @param clockTime the overworld clock, any value; only the time of day counts
	 * @param edge the share at sunrise and sunset, clamped to {@code [0, 1]}
	 */
	public static double dayShare(long clockTime, double edge) {
		double e = clamp01(edge);
		long t = Math.floorMod(clockTime, DAY_LENGTH);
		if (t >= SUNSET) {
			return e;
		}
		return e + (1.0 - e) * Math.sin(Math.PI * t / SUNSET);
	}

	/**
	 * Share of the full-moon output under a moon of {@code brightness}, between {@code newMoon} and 1.
	 *
	 * @param brightness the moon brightness of the night's phase, 0 (new moon) to 1 (full moon), clamped
	 * @param newMoon the share at the new moon, clamped to {@code [0, 1]}
	 */
	public static double moonShare(double brightness, double newMoon) {
		double n = clamp01(newMoon);
		return n + (1.0 - n) * clamp01(brightness);
	}

	/** {@code peak * share}, rounded to the nearest whole EU/t, never negative. */
	public static int scaled(int peak, double share) {
		return (int) Math.max(0L, Math.round(peak * share));
	}

	/**
	 * Output under rain or thunder for the night branch: {@code factor} of what it makes now, but never less than
	 * {@code floor} — and never more than it makes now, so a floor above a weak new-moon figure cannot raise it.
	 */
	public static int weatherOutput(int current, double factor, int floor) {
		int kept = scaled(current, factor);
		return Math.min(current, Math.max(floor, kept));
	}

	/** Output under snow: {@code factor} of what it makes now, floored at 1 while it makes anything at all. */
	public static int snowOutput(int current, double factor) {
		if (current <= 0) {
			return 0;
		}
		return Math.max(1, scaled(current, factor));
	}

	private static double clamp01(double v) {
		return v < 0.0 ? 0.0 : Math.min(v, 1.0);
	}
}
