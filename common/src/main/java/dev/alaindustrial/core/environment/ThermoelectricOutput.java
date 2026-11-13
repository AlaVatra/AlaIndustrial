package dev.alaindustrial.core.environment;

/**
 * Pure arithmetic of the thermoelectric generator (MOD-811): how six face temperatures become a rate. No
 * Minecraft imports and no side effects, so the L1 lane pins the rule; the block entity calls these methods, so
 * the tested code is the production code.
 *
 * <p><b>A temperature is a signed level.</b> {@code +1..+4} is hot, {@code -1..-4} is cold, {@code 0} neutral;
 * a value outside {@code -4..4} is clamped. The six faces are indexed like Minecraft's {@code Direction}
 * (down, up, north, south, west, east), so the three axes are the pairs {@code (0,1)} vertical,
 * {@code (2,3)} north-south and {@code (4,5)} west-east, each written as (negative end, positive end).
 *
 * <p><b>An axis works only when one end is hot and the other cold.</b> Its difference is then the hot level
 * plus the cold level (2..8); two hot ends, two cold ends or a hot end against a neutral one give 0. The block
 * makes the sum of its working axes times the per-degree rate, in thousandths of an EU, and banks the fraction
 * until it adds up to a whole EU ({@link #step}).
 */
public final class ThermoelectricOutput {

	/** The highest level on either side. */
	public static final int MAX_LEVEL = 4;

	/** Faces of the block, in Minecraft's {@code Direction} order. */
	public static final int FACES = 6;

	/** Thousandths of an EU in one EU. */
	public static final int MILLI_PER_EU = 1000;

	/** What an axis shows on the block: idle, hot at its positive end (up, south, east), hot at its negative end. */
	public static final int AXIS_OFF = 0;
	public static final int AXIS_HOT_POSITIVE = 1;
	public static final int AXIS_HOT_NEGATIVE = 2;

	private ThermoelectricOutput() {
	}

	/** {@code level} clamped to {@code -MAX_LEVEL..MAX_LEVEL}. */
	public static int clamp(int level) {
		return Math.max(-MAX_LEVEL, Math.min(MAX_LEVEL, level));
	}

	/**
	 * The temperature difference of one axis: hot level plus cold level when one end is hot and the other cold,
	 * else 0. Symmetric in its two arguments.
	 */
	public static int axisDifference(int negativeEnd, int positiveEnd) {
		int a = clamp(negativeEnd);
		int b = clamp(positiveEnd);
		if (a > 0 && b < 0) {
			return a - b;
		}
		if (b > 0 && a < 0) {
			return b - a;
		}
		return 0;
	}

	/** What the axis between {@code negativeEnd} and {@code positiveEnd} shows: one of the {@code AXIS_*} codes. */
	public static int axisState(int negativeEnd, int positiveEnd) {
		if (axisDifference(negativeEnd, positiveEnd) == 0) {
			return AXIS_OFF;
		}
		return clamp(positiveEnd) > 0 ? AXIS_HOT_POSITIVE : AXIS_HOT_NEGATIVE;
	}

	/** The difference of axis {@code axis} (0 vertical, 1 north-south, 2 west-east) of six face temperatures. */
	public static int axisDifference(int[] faces, int axis) {
		return axisDifference(faces[2 * axis], faces[2 * axis + 1]);
	}

	/** The summed difference of every working axis of six face temperatures (0..24). */
	public static int totalDifference(int[] faces) {
		requireSix(faces);
		return axisDifference(faces, 0) + axisDifference(faces, 1) + axisDifference(faces, 2);
	}

	/** How many of the three axes work (0..3). */
	public static int workingAxes(int[] faces) {
		requireSix(faces);
		int working = 0;
		for (int axis = 0; axis < 3; axis++) {
			if (axisDifference(faces, axis) > 0) {
				working++;
			}
		}
		return working;
	}

	/** The block's rate in thousandths of an EU per tick; a negative per-degree rate counts as 0. */
	public static int milliEuPerTick(int[] faces, int milliPerDegree) {
		return totalDifference(faces) * Math.max(0, milliPerDegree);
	}

	/** One tick of production: whole EU out, the fraction left for the next tick. */
	public record Step(int eu, int remainderMilli) {
	}

	/**
	 * Add one tick of {@code milliPerTick} to the banked {@code remainderMilli} and take the whole EU out. A rate
	 * of 500 gives 0, 1, 0, 1, ... — exactly half an EU a tick on average, never more and never less.
	 */
	public static Step step(int remainderMilli, int milliPerTick) {
		long total = (long) Math.max(0, remainderMilli) + Math.max(0, milliPerTick);
		return new Step((int) (total / MILLI_PER_EU), (int) (total % MILLI_PER_EU));
	}

	private static void requireSix(int[] faces) {
		if (faces.length != FACES) {
			throw new IllegalArgumentException("expected " + FACES + " face temperatures, got " + faces.length);
		}
	}
}
