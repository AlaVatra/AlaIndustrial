package dev.alaindustrial.core.escalator;

/**
 * How far the drawn escalator band has travelled (MOD-969), free of Minecraft so the L1 lane covers it. The drive's
 * block entity holds one per client and steps it every client tick; the renderer reads {@link #travel(float)}.
 *
 * <p><b>Winding up</b>: the speed eases towards the band's own by {@link #EASE} of the difference per tick, so it
 * never overshoots, and the travel between two ticks is drawn on the straight line between them.
 *
 * <p><b>Coming to rest</b>: the band does not coast to wherever it happens to stop. It brakes onto the next point
 * ahead where a tread stands exactly where the still model draws it (a multiple of {@link #PITCH}), along a cubic
 * that leaves with the band's speed and arrives with none, and is read at the exact partial tick. So the speed
 * never jumps, the band never moves back, and a band that stops shows the still treads' places to the pixel: a
 * swap to the still model, when the band loses power, moves nothing.
 *
 * <p>The travel is kept within one block (every drawn pattern repeats there); the renderer wraps it again anyway.
 */
public final class EscalatorBandMotion {

	/** A tread's pitch along the run, blocks: the band rests only at its multiples. */
	public static final float PITCH = 0.5F;

	/** Share of the gap to the band's speed the drawn speed closes per tick: about a second to full speed. */
	static final float EASE = 0.12F;

	/** Ticks a stop takes at the least, from full speed: the brake picks its rest point from this. */
	static final float BRAKE_TICKS = 16.0F;

	/** Ticks a stop takes at the most, however slow the band was when it was told to stop. */
	static final float MAX_BRAKE_TICKS = 40.0F;

	/** Travel at the last tick and at the one before, blocks. */
	private float position;
	private float previous;
	/** The last tick's step while winding (blocks per tick); the brake curve's slope while braking. */
	private float speed;

	private boolean braking;
	private float brakeFrom;
	private float brakeTo;
	private float brakeSpeed;
	private float brakeTicks;
	private int brakeAge;

	/** One client tick towards {@code target}, the band's speed in blocks per tick (negative: running down). */
	public void tick(float target) {
		previous = position;
		if (target != 0.0F) {
			braking = false;
			speed += (target - speed) * EASE;
			position += speed;
		} else {
			if (!braking && speed != 0.0F) {
				startBrake();
			}
			if (braking) {
				brakeAge++;
				if (brakeAge >= brakeTicks) {
					position = brakeTo;
					speed = 0.0F;
					braking = false;
				} else {
					position = brakeAt(brakeAge);
					speed = brakeSlopeAt(brakeAge);
				}
			}
		}
		wrap();
	}

	/** The travel at {@code partialTicks} past the last tick, blocks along the run, about {@code [0, 1)}. */
	public float travel(float partialTicks) {
		if (braking) {
			return brakeAt(brakeAge - 1 + partialTicks);
		}
		return previous + (position - previous) * partialTicks;
	}

	/** The drawn speed, blocks per tick. */
	public float speed() {
		return speed;
	}

	/** Whether the band stands still. */
	public boolean atRest() {
		return speed == 0.0F && !braking;
	}

	private void startBrake() {
		float direction = Math.signum(speed);
		float least = Math.abs(speed) * BRAKE_TICKS / 2.0F;
		brakeFrom = position;
		brakeTo = direction > 0.0F
				? (float) Math.ceil((position + least) / PITCH) * PITCH
				: (float) Math.floor((position - least) / PITCH) * PITCH;
		brakeSpeed = speed;
		// Even deceleration would take 2d/v; a band crawling when told to stop takes no longer than the cap.
		brakeTicks = Math.min(MAX_BRAKE_TICKS, 2.0F * (brakeTo - brakeFrom) / speed);
		brakeAge = 0;
		braking = true;
	}

	/**
	 * The brake's cubic (Hermite: from {@code brakeFrom} with the band's speed to {@code brakeTo} with none) at
	 * {@code t} ticks into the stop. Its speed never changes sign: the start slope is at most twice the mean one.
	 */
	private float brakeAt(float t) {
		float u = Math.min(1.0F, Math.max(0.0F, t / brakeTicks));
		float d = brakeTo - brakeFrom;
		float m = brakeSpeed * brakeTicks;
		return brakeFrom + (u * u * u - 2.0F * u * u + u) * m + (-2.0F * u * u * u + 3.0F * u * u) * d;
	}

	private float brakeSlopeAt(float t) {
		float u = Math.min(1.0F, Math.max(0.0F, t / brakeTicks));
		float d = brakeTo - brakeFrom;
		float m = brakeSpeed * brakeTicks;
		return ((3.0F * u * u - 4.0F * u + 1.0F) * m + (-6.0F * u * u + 6.0F * u) * d) / brakeTicks;
	}

	/** Keep the travel within one block, moving every stored point by the same whole block. */
	private void wrap() {
		float shift = position >= 1.0F ? -1.0F : position < 0.0F ? 1.0F : 0.0F;
		if (shift != 0.0F) {
			position += shift;
			previous += shift;
			brakeFrom += shift;
			brakeTo += shift;
		}
	}
}
