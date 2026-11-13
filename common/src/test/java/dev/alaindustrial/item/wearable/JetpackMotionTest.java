package dev.alaindustrial.item.wearable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.wearable.JetpackMotion.Mode;
import org.junit.jupiter.api.Test;

/**
 * The jetpack's flight rules (MOD-148, MOD-977) as numbers. The vanilla steps are modelled as in the 26.3 bytecode
 * of {@code LivingEntity.travelInAir}: an airborne player adds the air acceleration to its speed, moves by that
 * speed, then keeps 0.91 of it sideways and {@code (vy - 0.08) * 0.98} vertically; the jetpack's client tick runs
 * after that step.
 */
class JetpackMotionTest {

	private static final double GRAVITY = 0.08;
	private static final double VERTICAL_DRAG = 0.98;
	private static final double AIR_FRICTION = 0.91;
	/** The default hover sink, blocks per tick. */
	private static final double SINK = 0.02;

	/** Height after {@code ticks} ticks of {@code mode} from a 0.4 b/t climb, the vanilla air step in between. */
	private static double heightAfter(Mode mode, int ticks, double sink) {
		double y = 100.0;
		double vy = JetpackMotion.verticalSpeed(mode, 0.4, 1.5, sink);
		for (int tick = 0; tick < ticks; tick++) {
			y += vy;
			vy = (vy - GRAVITY) * VERTICAL_DRAG;
			vy = JetpackMotion.verticalSpeed(mode, vy, 1.5, sink);
		}
		return y;
	}

	/** Where the horizontal speed settles: blocks moved per tick after many ticks of steering one way. */
	private static double settledSideways(float air, float boost) {
		double v = 0.0;
		double moved = 0.0;
		for (int tick = 0; tick < 400; tick++) {
			v += air;
			moved = v;
			v *= AIR_FRICTION;
			v += boost;
		}
		return moved;
	}

	@Test
	void modeFollowsTheTierOneRulesWithHoverOff() {
		assertEquals(Mode.THRUST, JetpackMotion.mode(true, false, true, false, false));
		assertEquals(Mode.GLIDE, JetpackMotion.mode(true, false, false, false, true),
				"a cutout after this flight's thrust glides");
		assertEquals(Mode.OFF, JetpackMotion.mode(true, false, false, false, false),
				"an empty jetpack that never fired falls normally");
		assertEquals(Mode.OFF, JetpackMotion.mode(false, false, true, false, true),
				"a released jump with hover off is a plain fall");
	}

	@Test
	void hoverHoldsOnlyAfterTheFirstThrustOfAFlight() {
		assertEquals(Mode.OFF, JetpackMotion.mode(false, false, true, true, false),
				"hover on but no thrust yet: a plain jump or a step off a ledge stays vanilla");
		assertEquals(Mode.HOVER, JetpackMotion.mode(false, false, true, true, true));
		assertEquals(Mode.HOVER_DESCEND, JetpackMotion.mode(false, true, true, true, true));
		assertEquals(Mode.THRUST, JetpackMotion.mode(true, true, true, true, true), "jump wins over hover");
		assertEquals(Mode.GLIDE, JetpackMotion.mode(false, false, false, true, true),
				"a hover that runs dry glides without a held jump");
		assertTrue(Mode.HOVER.engineRuns());
		assertTrue(Mode.HOVER_DESCEND.engineRuns());
		assertFalse(Mode.GLIDE.engineRuns());
		assertFalse(Mode.OFF.engineRuns());
	}

	@Test
	void tierOneThrustIsUnchanged() {
		assertEquals(0.15, JetpackMotion.verticalSpeed(Mode.THRUST, 0.0, 1.0, 0.0), 1e-12);
		assertEquals(0.5, JetpackMotion.verticalSpeed(Mode.THRUST, 0.45, 1.0, 0.0), 1e-12);
		assertEquals(-0.3, JetpackMotion.verticalSpeed(Mode.GLIDE, -1.2, 1.0, 0.0), 1e-12);
		assertEquals(0.2, JetpackMotion.verticalSpeed(Mode.GLIDE, 0.2, 1.0, 0.0), 1e-12,
				"a glide leaves an updraft alone");
	}

	@Test
	void mk2RisesHalfAgainAsFast() {
		assertEquals(0.225, JetpackMotion.verticalSpeed(Mode.THRUST, 0.0, 1.5, SINK), 1e-12);
		double vy = 0.0;
		for (int tick = 0; tick < 60; tick++) {
			vy = (vy - GRAVITY) * VERTICAL_DRAG;
			vy = JetpackMotion.verticalSpeed(Mode.THRUST, vy, 1.5, SINK);
		}
		assertEquals(0.75, vy, 1e-12, "the Mk2's rise cap is 1.5 times the tier-one 0.5");
	}

	@Test
	void hoverSinksSlowlyAtTheKnobRate() {
		// Owner's review 2026-11-12: ten seconds of hover lose about four blocks, against gravity, from any climb.
		assertEquals(100.0 - 200 * SINK, heightAfter(Mode.HOVER, 200, SINK), 1e-9);
		assertEquals(96.0, heightAfter(Mode.HOVER, 200, SINK), 1e-9, "0.02 b/t is four blocks in ten seconds");
		assertEquals(-SINK, JetpackMotion.verticalSpeed(Mode.HOVER, -2.0, 1.5, SINK), 1e-12, "a fall is caught");
	}

	@Test
	void hoverWithTheKnobAtZeroHoldsTheHeightExactly() {
		assertEquals(100.0, heightAfter(Mode.HOVER, 200, 0.0), 1e-12, "a sink of 0 must not drift");
		assertEquals(100.0, heightAfter(Mode.HOVER, 200, -0.5), 1e-12, "a negative sink never lifts");
	}

	@Test
	void sneakNeverSinksSlowerThanAPlainHover() {
		assertEquals(-0.3, JetpackMotion.verticalSpeed(Mode.HOVER_DESCEND, 0.6, 1.5, SINK), 1e-12);
		assertEquals(-0.5, JetpackMotion.verticalSpeed(Mode.HOVER_DESCEND, 0.6, 1.5, 0.5), 1e-12);
	}

	@Test
	void theDefaultSinkStillCountsAsFloatingForVanilla() {
		// Under the bound, so the server must keep resetting the kick counter on a powered hover tick.
		assertEquals(SINK, ToolConfig.jetpackMk2HoverSinkPerTick, 1e-12);
		assertTrue(-ToolConfig.jetpackMk2HoverSinkPerTick > JetpackMotion.VANILLA_FLOATING_BOUND);
	}

	@Test
	void horizontalBoostSettlesAtTheMultiplier() {
		for (boolean sprinting : new boolean[] {false, true}) {
			float air = sprinting ? JetpackMotion.AIR_SPEED_SPRINTING : JetpackMotion.AIR_SPEED;
			double vanilla = settledSideways(air, 0.0f);
			double mk2 = settledSideways(air, JetpackMotion.horizontalBoost(1.5, sprinting));
			assertEquals(1.5, mk2 / vanilla, 1e-6, "sprinting=" + sprinting);
		}
		assertEquals(0.0f, JetpackMotion.horizontalBoost(1.0, false), "a multiplier of 1 adds nothing");
		assertEquals(0.0f, JetpackMotion.horizontalBoost(0.5, true), "a multiplier under 1 never brakes");
	}
}
