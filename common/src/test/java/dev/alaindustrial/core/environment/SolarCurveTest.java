package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The output curves of the full-block third rungs (MOD-1031), checked against the owner's numbers: the day panel
 * 12 EU/t at noon, about 6 at sunrise and sunset, about 9.8 on average over the day; the night panel 10 under a
 * full moon, 5 at the new moon, 7.5 on average over the eight phases.
 */
class SolarCurveTest {

	private static final int DAY_PEAK = 12;
	private static final double DAY_EDGE = 0.5;
	private static final int NIGHT_PEAK = 10;
	private static final double NEW_MOON = 0.5;
	/** {@code DimensionType.MOON_BRIGHTNESS_PER_PHASE} of the game (26.3), copied for an L1 lane without the jar. */
	private static final float[] MOON = {1.0f, 0.75f, 0.5f, 0.25f, 0.0f, 0.25f, 0.5f, 0.75f};

	@Test
	void dayPeaksAtNoonAndHalvesAtTheEdges() {
		assertEquals(1.0, SolarCurve.dayShare(6000, DAY_EDGE), 1e-9, "noon");
		assertEquals(0.5, SolarCurve.dayShare(0, DAY_EDGE), 1e-9, "sunrise");
		assertEquals(0.5, SolarCurve.dayShare(12000, DAY_EDGE), 1e-9, "sunset");
		assertEquals(SolarCurve.dayShare(3000, DAY_EDGE), SolarCurve.dayShare(9000, DAY_EDGE), 1e-9, "symmetric");
		assertEquals(12, SolarCurve.scaled(DAY_PEAK, SolarCurve.dayShare(6000, DAY_EDGE)));
		assertEquals(6, SolarCurve.scaled(DAY_PEAK, SolarCurve.dayShare(0, DAY_EDGE)));
		assertEquals(8, SolarCurve.scaled(DAY_PEAK, SolarCurve.dayShare(1000, DAY_EDGE)));
	}

	@Test
	void dayCurveIsTheClockTimeOfAnyDay() {
		assertEquals(SolarCurve.dayShare(6000, DAY_EDGE), SolarCurve.dayShare(6000 + 7 * 24000L, DAY_EDGE), 1e-9);
		assertEquals(DAY_EDGE, SolarCurve.dayShare(13000, DAY_EDGE), 1e-9, "bright ticks past sunset stay at the edge");
		assertEquals(DAY_EDGE, SolarCurve.dayShare(23500, DAY_EDGE), 1e-9, "bright ticks before sunrise too");
	}

	/** The average over the sunlit half is about 9.8 EU/t — the owner's figure, and 2.45 times the T2 panel. */
	@Test
	void dayAverageIsAboutNinePointEight() {
		double sum = 0;
		for (int t = 0; t < 12000; t++) {
			sum += SolarCurve.scaled(DAY_PEAK, SolarCurve.dayShare(t, DAY_EDGE));
		}
		double average = sum / 12000;
		assertTrue(average > 9.6 && average < 10.0, "average " + average);
	}

	@Test
	void nightFollowsTheMoon() {
		int[] expected = {10, 9, 8, 6, 5, 6, 8, 9};
		double sum = 0;
		for (int phase = 0; phase < MOON.length; phase++) {
			int out = SolarCurve.scaled(NIGHT_PEAK, SolarCurve.moonShare(MOON[phase], NEW_MOON));
			assertEquals(expected[phase], out, "phase " + phase);
			sum += SolarCurve.moonShare(MOON[phase], NEW_MOON) * NIGHT_PEAK;
		}
		assertEquals(7.5, sum / MOON.length, 1e-9, "the average over the eight phases");
	}

	@Test
	void rainHalvesTheNightPanelButKeepsTheFloor() {
		assertEquals(5, SolarCurve.weatherOutput(10, 0.5, 2), "full moon in rain");
		assertEquals(3, SolarCurve.weatherOutput(5, 0.5, 2), "new moon in rain: half of 5, rounded");
		assertEquals(2, SolarCurve.weatherOutput(3, 0.5, 2), "the floor");
		assertEquals(1, SolarCurve.weatherOutput(1, 0.5, 2), "never more than it makes now");
		assertEquals(0, SolarCurve.weatherOutput(0, 0.5, 2), "nothing stays nothing");
	}

	@Test
	void snowKeepsAShareAndAtLeastOne() {
		assertEquals(2, SolarCurve.snowOutput(12, 0.2), "a fifth of noon");
		assertEquals(1, SolarCurve.snowOutput(3, 0.2), "never below 1 while it works");
		assertEquals(5, SolarCurve.snowOutput(10, 0.5), "the night branch keeps half");
		assertEquals(0, SolarCurve.snowOutput(0, 0.5));
	}

	@Test
	void sharesAreClamped() {
		assertEquals(1.0, SolarCurve.dayShare(6000, 2.0), 1e-9);
		assertEquals(0.0, SolarCurve.moonShare(-1.0, -1.0), 1e-9);
		assertEquals(1.0, SolarCurve.moonShare(2.0, 0.5), 1e-9);
	}
}
