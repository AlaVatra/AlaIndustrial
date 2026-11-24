package dev.alaindustrial.core.elevator;

/**
 * One run of the elevator cabin (MOD-769): where it set off, where it goes, how fast and when it started. The cabin's
 * height at any moment is a pure function of this record and the game clock, so the server and every client compute
 * the same pose from the same four numbers, the way the quarry's bridge is placed from its phase (MOD-952): the client
 * is told a trip once, when it starts, and never per tick.
 *
 * <p>The speed profile is a trapezoid: the cabin speeds up over {@code rampTicks}, runs at {@code speed} and slows
 * down over the same time. A trip too short to reach full speed is a triangle with the same acceleration. A trip with
 * no ramp (the safety catches lowering the cabin) runs at constant speed from the first tick.
 *
 * <p>Minecraft-free: heights are world blocks of the cabin's floor, times are game ticks.
 *
 * @param start     game time the trip set off at
 * @param from      the cabin floor's height at the start
 * @param to        the cabin floor's height at the end
 * @param speed     top speed, blocks per tick (positive)
 * @param rampTicks ticks to reach top speed from standing; 0 for a constant-speed run
 * @param catching  whether this is the safety catches' slow descent rather than a paid trip
 */
public record ElevatorTrip(long start, double from, double to, double speed, int rampTicks, boolean catching) {

	public ElevatorTrip {
		if (!(speed > 0.0D)) {
			throw new IllegalArgumentException("speed must be positive, got " + speed);
		}
		if (rampTicks < 0) {
			throw new IllegalArgumentException("rampTicks must not be negative, got " + rampTicks);
		}
	}

	/** How far the trip goes, blocks, always positive or zero. */
	public double distance() {
		return Math.abs(to - from);
	}

	/** Whether the cabin goes up on this trip. */
	public boolean up() {
		return to > from;
	}

	/** How many ticks the whole trip takes, rounded up to a whole tick. */
	public int ticks() {
		return (int) Math.ceil(duration());
	}

	/** The game time the cabin stands at {@link #to}. */
	public long end() {
		return start + ticks();
	}

	/** Whether the cabin has arrived by game time {@code now}. */
	public boolean arrivedBy(double now) {
		return now >= end();
	}

	/** The cabin floor's height at game time {@code now} (fractional for a frame between ticks). */
	public double heightAt(double now) {
		double travelled = travelled(now - start);
		return up() ? from + travelled : from - travelled;
	}

	/** The cabin's vertical speed at game time {@code now}, blocks per tick, signed (up is positive). */
	public double velocityAt(double now) {
		return heightAt(now + 1.0D) - heightAt(now);
	}

	/** The exact length of the trip in ticks, unrounded. */
	double duration() {
		double d = distance();
		if (d <= 0.0D) {
			return 0.0D;
		}
		if (rampTicks == 0) {
			return d / speed;
		}
		double rampDistance = speed * rampTicks;
		if (d >= rampDistance) {
			return 2.0D * rampTicks + (d - rampDistance) / speed;
		}
		// Triangle: the peak speed v' with v'^2 / a = d, a = speed / rampTicks, reached after v' / a ticks.
		double accel = speed / rampTicks;
		double peak = Math.sqrt(d * accel);
		return 2.0D * peak / accel;
	}

	/** Distance covered {@code elapsed} ticks into the trip, clamped to 0 .. {@link #distance()}. */
	double travelled(double elapsed) {
		double d = distance();
		double total = duration();
		if (elapsed <= 0.0D || d <= 0.0D) {
			return 0.0D;
		}
		if (elapsed >= total) {
			return d;
		}
		if (rampTicks == 0) {
			return Math.min(d, speed * elapsed);
		}
		double accel = speed / rampTicks;
		double ramp = total / 2.0D < rampTicks ? total / 2.0D : rampTicks;
		double peak = accel * ramp;
		if (elapsed < ramp) {
			return 0.5D * accel * elapsed * elapsed;
		}
		if (elapsed <= total - ramp) {
			return 0.5D * accel * ramp * ramp + peak * (elapsed - ramp);
		}
		double left = total - elapsed;
		return d - 0.5D * accel * left * left;
	}
}
