package dev.alaindustrial.core.environment;

import org.jspecify.annotations.Nullable;

/**
 * How long a fuel generator's fuel lasts at the load the line really draws (MOD-990), and how that span is
 * shown in plain units. No Minecraft imports, so the L1 lane pins the rule; the block entity and the screens
 * call these methods, so the tested code is the production code.
 *
 * <p><b>Why the real draw and not the full output.</b> The generator burns only while its buffer has room: with
 * one machine drawing 2 EU/t from an 8 EU/t generator it pauses three ticks in four, and the fuel lasts four
 * times longer than "fuel EU / 8" says. So the span is the EU still to come (the fuel plus what the buffer
 * holds) divided by the average draw of the line, and a generator nothing draws from has no span at all: its
 * fuel waits, it does not run out.
 *
 * <p><b>The rule.</b> The fuel burns at {@code min(draw, rate)} EU per tick, so it lasts
 * {@code fuelTicks * rate / min(draw, rate)} ticks. A draw above the rate also empties the buffer meanwhile
 * at {@code draw - rate}; whatever is left of the buffer when the fuel is gone then carries the line on for
 * {@code left / draw} ticks more.
 */
public final class FuelRuntime {

	/** {@link #ticksLeft}: nothing draws from the generator, so its fuel waits instead of running out. */
	public static final long WAITING = -1L;

	/** {@link #ticksLeft}: no fuel in the generator and nothing in its buffer. */
	public static final long EMPTY = -2L;

	/** Game ticks in one second. */
	private static final long TICKS_PER_SECOND = 20L;

	/** From this many hours up, minutes are dropped: "~14 h", not "~14 h 3 min". */
	private static final long HOURS_WITHOUT_MINUTES = 10L;

	private FuelRuntime() {
	}

	/**
	 * What the statistics carry for a fuel generator: the span ({@link #ticksLeft}, with its two sentinels),
	 * the line's draw over the window, and the full output the draw is set against ("load 2 of 8 EU/t").
	 */
	public record Readout(long ticksLeft, long drawEu, int drawTicks, int euPerTick) {
	}

	/** A block that answers {@link Readout}; {@code null} while it measures nothing (no statistics chip). */
	public interface Source {
		@Nullable
		Readout fuelReadout();

		/**
		 * The fuel burnt by kind (MOD-992); {@code null} while it measures nothing or before anything burnt.
		 * A fuel block that keeps no tally answers {@code null}.
		 */
		default FuelTally.@Nullable Top fuelBurnt() {
			return null;
		}
	}

	/**
	 * Ticks until the generator stops feeding the line at the draw measured so far.
	 *
	 * @param fuelTicks burn ticks still to come: the rest of the item burning now plus every item in the slot
	 * @param euPerTick what one burn tick makes (the full output, after the global multiplier)
	 * @param buffered EU in the buffer now
	 * @param drawEu EU the line drew over the measuring window
	 * @param drawTicks the window's length in ticks
	 * @return the span in ticks, {@link #EMPTY} with no fuel and an empty buffer, {@link #WAITING} when
	 *     nothing was drawn over the window
	 */
	public static long ticksLeft(long fuelTicks, int euPerTick, long buffered, long drawEu, int drawTicks) {
		long fuel = Math.max(0L, fuelTicks);
		long buffer = Math.max(0L, buffered);
		if (fuel == 0L && buffer == 0L) {
			return EMPTY;
		}
		if (drawEu <= 0L || drawTicks <= 0) {
			return WAITING;
		}
		double draw = (double) drawEu / drawTicks;
		double rate = Math.max(1, euPerTick);
		double burning = fuel * rate / Math.min(draw, rate);
		double bufferLeft = Math.max(0.0, buffer - Math.max(0.0, draw - rate) * burning);
		double ticks = burning + bufferLeft / draw;
		return ticks >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(ticks);
	}

	/** The unit a span is shown in. */
	public enum Unit {
		/** "~N s", under a minute. */
		SECONDS,
		/** "~N min", under an hour. */
		MINUTES,
		/** "~N h M min", under ten hours. */
		HOURS_MINUTES,
		/** "~N h", from ten hours up. */
		HOURS
	}

	/**
	 * A span ready for a lang pattern: the unit and up to two numbers ({@code minor} is the minutes of
	 * {@link Unit#HOURS_MINUTES} and 0 otherwise).
	 */
	public record Shown(Unit unit, long major, long minor) {
	}

	/**
	 * {@code ticks} rounded to what a player reads at a glance: whole seconds under a minute (at least 1),
	 * whole minutes under an hour, hours and minutes under ten hours, whole hours above. Rounded to the
	 * nearest unit; a span that rounds up to the next unit is shown in that unit (59.6 min is "~1 h 0 min").
	 */
	public static Shown shown(long ticks) {
		long seconds = Math.max(1L, Math.round(Math.max(0L, ticks) / (double) TICKS_PER_SECOND));
		if (seconds < 60L) {
			return new Shown(Unit.SECONDS, seconds, 0L);
		}
		long minutes = Math.round(seconds / 60.0);
		if (minutes < 60L) {
			return new Shown(Unit.MINUTES, minutes, 0L);
		}
		if (minutes < HOURS_WITHOUT_MINUTES * 60L) {
			return new Shown(Unit.HOURS_MINUTES, minutes / 60L, minutes % 60L);
		}
		return new Shown(Unit.HOURS, Math.round(minutes / 60.0), 0L);
	}
}
