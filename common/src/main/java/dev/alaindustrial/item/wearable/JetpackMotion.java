package dev.alaindustrial.item.wearable;

/**
 * The jetpack's flight rules as plain numbers (MOD-148, MOD-977): which mode the engine is in this tick, and what
 * that mode does to the vertical speed and the air control. Minecraft-free, so L1 pins the rules; the server tick
 * ({@link JetpackItem#serverFlightTick}) and the client motion ({@code dev.alaindustrial.client.JetpackFlight}) both
 * ask {@link #mode} so the two sides cannot disagree about which mode a tick is in.
 */
public final class JetpackMotion {

	/** Upward acceleration per thrust tick at tier one: comfortably over vanilla gravity (~0.08/tick). */
	public static final double THRUST_ACCELERATION = 0.15;
	/** Ascent speed cap at tier one, blocks/tick (~10 m/s). */
	public static final double MAX_RISE_SPEED = 0.5;
	/** Sink-rate floor of the powerless glide, blocks/tick (~6 m/s down, slow enough to land soft). */
	public static final double GLIDE_FALL_SPEED = -0.3;
	/** Descent speed of a hover with sneak held, blocks/tick: the glide's rate, under power. */
	public static final double HOVER_DESCENT_SPEED = -0.3;
	/**
	 * Vanilla's "floating" bound ({@code ServerGamePacketListenerImpl}): a tick that falls by less than this counts
	 * toward the kick of a player with no right to fly. The hover's slow sink is under it on purpose, so the server's
	 * reset of that counter on a powered tick ({@code JetpackItem}) is still what keeps a hovering player connected.
	 */
	public static final double VANILLA_FLOATING_BOUND = -0.03125;

	/**
	 * Vanilla's air acceleration of a walking player that is not flying, from {@code Player.getFlyingSpeed()}
	 * (protected, so repeated here). If Mojang changes it the Mk2 boost drifts away from its multiplier; the
	 * {@code :neoforge:test} lane, which has Minecraft on the classpath, reads the method's bytecode
	 * ({@code JetpackAirSpeedTest}) and goes red on the game version that changed it.
	 */
	public static final float AIR_SPEED = 0.02f;
	/** The same for a sprinting player ({@code Player.getFlyingSpeed()}, 0.025999999f in 26.3). */
	public static final float AIR_SPEED_SPRINTING = 0.025999999f;

	/** What the engine does this tick. */
	public enum Mode {
		/** Nothing: on the ground, or airborne with no input that the jetpack answers. */
		OFF,
		/** Jump held and the engine powered: accelerate upward, burn the thrust price. */
		THRUST,
		/** Hover on, nothing held: sink by {@code jetpackMk2HoverSinkPerTick}, burn the hover price. */
		HOVER,
		/** Hover on, sneak held: sink slowly, burn the hover price. */
		HOVER_DESCEND,
		/** The engine cannot run, but it ran this flight: a free, damped, harmless fall. */
		GLIDE;

		/** Whether the engine burns EU and pushes this tick. */
		public boolean engineRuns() {
			return this == THRUST || this == HOVER || this == HOVER_DESCEND;
		}
	}

	private JetpackMotion() {
	}

	/**
	 * The mode of one airborne tick.
	 *
	 * @param jumpHeld   the player holds jump
	 * @param sneakHeld  the player holds sneak
	 * @param powered    the engine may run: charge left and under the ceiling
	 * @param hoverOn    hover is switched on for this jetpack (only the Mk2 ever has it)
	 * @param thrusted   the engine thrust at least once during this airborne stretch, before this tick
	 */
	public static Mode mode(boolean jumpHeld, boolean sneakHeld, boolean powered, boolean hoverOn, boolean thrusted) {
		if (jumpHeld) {
			if (powered) {
				return Mode.THRUST;
			}
			return thrusted ? Mode.GLIDE : Mode.OFF;
		}
		// Hover picks up only after the first thrust of this flight: a plain jump stays a jump, a step off a ledge
		// stays a fall.
		if (hoverOn && thrusted) {
			if (!powered) {
				return Mode.GLIDE;
			}
			return sneakHeld ? Mode.HOVER_DESCEND : Mode.HOVER;
		}
		return Mode.OFF;
	}

	/**
	 * The vertical speed after this tick's push, given the speed {@code vy} the tick ended with. Vanilla moves the
	 * player by it on the next tick before gravity touches it, so a hover moves by exactly {@code -hoverSink} a tick.
	 *
	 * @param rise      the rise multiplier: 1 at tier one, the server's {@code jetpackMk2RiseMultiplier} on the Mk2
	 * @param hoverSink blocks a hovering player sinks per tick, the server's {@code jetpackMk2HoverSinkPerTick}
	 *                  (MOD-977): a hover holds the player, it does not pin them to a point. Sneak never sinks
	 *                  slower than that.
	 */
	public static double verticalSpeed(Mode mode, double vy, double rise, double hoverSink) {
		double sink = Math.max(0.0, hoverSink);
		return switch (mode) {
			case THRUST -> Math.min(vy + THRUST_ACCELERATION * rise, MAX_RISE_SPEED * rise);
			case HOVER -> -sink;
			case HOVER_DESCEND -> Math.min(HOVER_DESCENT_SPEED, -sink);
			case GLIDE -> Math.max(vy, GLIDE_FALL_SPEED);
			case OFF -> vy;
		};
	}

	/**
	 * Extra air acceleration added along the player's input while the engine runs. Vanilla moves an airborne player
	 * by {@code v + a} and keeps {@code 0.91} of it, so it settles at {@code a / 0.09}; adding {@code (m - 1) · a}
	 * once a tick after that step settles at exactly {@code m · a / 0.09}, {@code m} times the vanilla speed.
	 *
	 * @param horizontal the multiplier: 1 at tier one (no boost), the server's {@code jetpackMk2HorizontalMultiplier}
	 */
	public static float horizontalBoost(double horizontal, boolean sprinting) {
		float air = sprinting ? AIR_SPEED_SPRINTING : AIR_SPEED;
		return (float) (Math.max(0.0, horizontal - 1.0) * air);
	}
}
