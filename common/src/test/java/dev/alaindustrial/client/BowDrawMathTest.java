package dev.alaindustrial.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the bow's first-person numbers the slingshot and the Electric Bow share (MOD-961): the zoom curve, its
 * composition into a NeoForge/Forge {@code ComputeFovModifierEvent} — the same value as Fabric on a clean game, and
 * another FOV mod's change kept — and which hand a drawn bow hides.
 */
class BowDrawMathTest {

	private static final float EPS = 1.0e-6f;
	/** Effect scales (the "FOV effects" slider) and raw modifiers (walking, flying, sprinting, slowed) to sweep. */
	private static final float[] SCALES = {0.0f, 0.25f, 0.5f, 1.0f};
	private static final float[] MODIFIERS = {1.0f, 1.1f, 1.15f, 1.265f, 0.85f, 0.55f};
	private static final float[] FACTORS = {1.0f, 0.9625f, 0.9f, 0.85f};

	@Test
	void zoomIsTheBowsCurve() {
		float bow = BowDrawMath.BOW_DRAW_TICKS;
		assertEquals(1.0f, BowDrawMath.zoomFactor(0, bow), EPS, "no zoom at the start of the draw");
		assertEquals(0.9625f, BowDrawMath.zoomFactor(10, bow), EPS, "half the draw: 1 - 0.5^2 * 0.15");
		assertEquals(0.85f, BowDrawMath.zoomFactor(20, bow), EPS, "full zoom after 20 ticks, as a bow");
		assertEquals(0.85f, BowDrawMath.zoomFactor(100, bow), EPS, "the zoom holds after 20 ticks");
		assertEquals(1.0f, BowDrawMath.zoomFactor(-5, bow), EPS, "a negative tick count is no draw");
		for (int t = 1; t <= 25; t++) {
			assertTrue(BowDrawMath.zoomFactor(t, bow) <= BowDrawMath.zoomFactor(t - 1, bow),
					"the zoom only deepens while drawing, tick " + t);
		}
	}

	@Test
	void aShorterDrawCompletesTheZoomSooner() {
		assertEquals(0.9625f, BowDrawMath.zoomFactor(8, 16.0f), EPS, "half of a 16-tick draw");
		assertEquals(0.85f, BowDrawMath.zoomFactor(16, 16.0f), EPS, "full zoom when the charged Electric Bow is drawn");
		assertEquals(0.85f, BowDrawMath.zoomFactor(20, 16.0f), EPS, "and it holds");
		assertTrue(BowDrawMath.zoomFactor(12, 16.0f) < BowDrawMath.zoomFactor(12, BowDrawMath.BOW_DRAW_TICKS),
				"at the same tick the shorter draw zooms deeper");
	}

	@Test
	void lerpByEffectScaleIsVanillasLastStep() {
		assertEquals(1.0f, BowDrawMath.lerpByEffectScale(0.0f, 1.3f), "slider at 0: no FOV effect at all");
		assertEquals(1.3f, BowDrawMath.lerpByEffectScale(1.0f, 1.3f), "slider at 1: the modifier itself");
		assertEquals(1.05f, BowDrawMath.lerpByEffectScale(0.5f, 1.1f), EPS, "half way");
		for (float s : SCALES) {
			for (float m : MODIFIERS) {
				assertEquals(1.0f + s * (m - 1.0f), BowDrawMath.lerpByEffectScale(s, m),
						"Mth.lerp(s, 1, m) = 1 + s * (m - 1), bit for bit");
			}
		}
	}

	@Test
	void onACleanGameTheEventGivesExactlyTheFabricValue() {
		for (float s : SCALES) {
			for (float m : MODIFIERS) {
				for (float f : FACTORS) {
					// NeoForge and Forge start the event at lerp(s, 1, m); Fabric narrows m before that lerp.
					float eventStart = 1.0f + s * (m - 1.0f);
					float fabric = 1.0f + s * (m * f - 1.0f);
					assertEquals(fabric, BowDrawMath.composedFovModifier(eventStart, m, s, f),
							"s=" + s + " m=" + m + " f=" + f);
				}
			}
		}
	}

	@Test
	void anotherModsChangeIsKeptAndComposesInEitherOrder() {
		float s = 1.0f;
		float m = 1.1f;
		float f = 0.85f;
		float base = BowDrawMath.lerpByEffectScale(s, m);
		float zoomed = BowDrawMath.lerpByEffectScale(s, m * f);

		// A zoom mod listening first quarters the view; the bow's zoom then narrows that, it does not reset it.
		float afterForeign = BowDrawMath.composedFovModifier(base * 0.25f, m, s, f);
		assertEquals(zoomed * 0.25f, afterForeign, EPS, "the foreign quarter survives the bow's zoom");
		assertNotEquals(zoomed, afterForeign, "the foreign change is not overwritten");

		// The same mod listening after ours multiplies our value: the same picture either way.
		float foreignAfter = BowDrawMath.composedFovModifier(base, m, s, f) * 0.25f;
		assertEquals(foreignAfter, afterForeign, EPS, "listener order does not matter");

		// A foreign value that is not a multiple of the start still comes out scaled by the bow's zoom alone.
		float shifted = base + 0.1f;
		assertEquals(shifted * (zoomed / base), BowDrawMath.composedFovModifier(shifted, m, s, f), EPS);
	}

	@Test
	void fovEffectsSliderAtZeroMeansNoZoom() {
		assertEquals(1.0f, BowDrawMath.composedFovModifier(1.0f, 1.1f, 0.0f, 0.85f), "vanilla: lerp(0, 1, x) = 1");
		assertEquals(0.7f, BowDrawMath.composedFovModifier(0.7f, 1.1f, 0.0f, 0.85f),
				"another mod's value stays exactly as it set it");
	}

	@Test
	void noZoomLeavesTheValueAlone() {
		assertEquals(0.7f, BowDrawMath.composedFovModifier(0.7f, 1.1f, 1.0f, 1.0f), EPS,
				"a factor of 1 changes nothing");
	}

	@Test
	void nothingToScaleAgainstLeavesTheValueAlone() {
		// lerp(1, 1, 0) = 0: the zoom cannot be expressed as a ratio, so another mod's value is returned as is.
		assertEquals(0.5f, BowDrawMath.composedFovModifier(0.5f, 0.0f, 1.0f, 0.85f));
		assertEquals(0.0f, BowDrawMath.composedFovModifier(0.0f, 0.0f, 1.0f, 0.85f), "a clean event is still exact");
	}

	@Test
	void aDrawnBowHidesOnlyTheOtherHand() {
		assertTrue(BowDrawMath.hidesOtherHand(true, true, false), "the free hand while drawing");
		assertFalse(BowDrawMath.hidesOtherHand(true, true, true), "the hand holding the drawn weapon");
		assertFalse(BowDrawMath.hidesOtherHand(false, true, false), "not drawing");
		assertFalse(BowDrawMath.hidesOtherHand(false, true, true), "not drawing, own hand");
		assertFalse(BowDrawMath.hidesOtherHand(true, false, false), "drawing something else");
		assertFalse(BowDrawMath.hidesOtherHand(true, false, true), "drawing something else, own hand");
		assertFalse(BowDrawMath.hidesOtherHand(false, false, false), "at rest");
		assertFalse(BowDrawMath.hidesOtherHand(false, false, true), "at rest, own hand");
	}
}
