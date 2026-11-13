package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * L1 tests of {@link ThermoelectricOutput} (MOD-811): an axis works only between a hot and a cold end, its
 * difference is hot level plus cold level, the axes add, and the fraction of an EU is banked rather than lost
 * or rounded up. Each test goes red when its rule drifts: a loosened axis rule, a dropped axis, a lost
 * remainder or a broken clamp.
 *
 * @implements thermoelectric generator: axis rule, sum of axes, milli-EU accumulation
 */
class ThermoelectricOutputTest {

	private static final int DEFAULT_MILLI_PER_DEGREE = 250;

	/** The spec's table: every pair's difference and its EU/t at 0.25 EU per degree. */
	@ParameterizedTest
	@CsvSource({
		"1, -1, 2, 500", // campfire + water
		"1, -4, 5, 1250", // campfire + blue ice
		"3, -1, 4, 1000", // magma + water
		"3, -3, 6, 1500", // magma + packed ice
		"4, -1, 5, 1250", // lava + water
		"4, -2, 6, 1500", // lava + ice
		"4, -3, 7, 1750", // lava + packed ice
		"4, -4, 8, 2000", // lava + blue ice
	})
	void axisDifferenceIsHotPlusCold(int hot, int cold, int difference, int milli) {
		assertEquals(difference, ThermoelectricOutput.axisDifference(hot, cold));
		assertEquals(difference, ThermoelectricOutput.axisDifference(cold, hot), "the rule is symmetric");
		int[] faces = {0, 0, hot, cold, 0, 0};
		assertEquals(milli, ThermoelectricOutput.milliEuPerTick(faces, DEFAULT_MILLI_PER_DEGREE));
	}

	@ParameterizedTest
	@CsvSource({
		"4, 4", // two hot ends
		"-4, -4", // two cold ends
		"4, 0", // hot against neutral
		"0, -4", // neutral against cold
		"0, 0", // nothing at all
	})
	void axisWithoutAHotAndAColdEndGivesNothing(int a, int b) {
		assertEquals(0, ThermoelectricOutput.axisDifference(a, b));
		assertEquals(ThermoelectricOutput.AXIS_OFF, ThermoelectricOutput.axisState(a, b));
	}

	@Test
	void levelsOutsideTheLadderAreClamped() {
		assertEquals(8, ThermoelectricOutput.axisDifference(99, -99), "a level past 4 counts as 4");
		assertEquals(4, ThermoelectricOutput.clamp(17));
		assertEquals(-4, ThermoelectricOutput.clamp(-17));
	}

	@Test
	void axesAddUp() {
		// Vertical: magma on top, blue ice below (7); north-south: lava north, blue ice south (8).
		int[] faces = {-4, 3, 4, -4, 0, 0};
		assertEquals(15, ThermoelectricOutput.totalDifference(faces));
		assertEquals(2, ThermoelectricOutput.workingAxes(faces));
		assertEquals(3750, ThermoelectricOutput.milliEuPerTick(faces, DEFAULT_MILLI_PER_DEGREE));
		// The third axis too: every axis works.
		int[] all = {-4, 4, 4, -4, -4, 4};
		assertEquals(24, ThermoelectricOutput.totalDifference(all));
		assertEquals(3, ThermoelectricOutput.workingAxes(all));
	}

	@Test
	void axisStateNamesTheHotEnd() {
		assertEquals(ThermoelectricOutput.AXIS_HOT_POSITIVE, ThermoelectricOutput.axisState(-2, 3));
		assertEquals(ThermoelectricOutput.AXIS_HOT_NEGATIVE, ThermoelectricOutput.axisState(3, -2));
	}

	@Test
	void halfAnEuAlternatesAndAveragesExactly() {
		int remainder = 0;
		int[] out = new int[4];
		for (int tick = 0; tick < out.length; tick++) {
			ThermoelectricOutput.Step step = ThermoelectricOutput.step(remainder, 500);
			out[tick] = step.eu();
			remainder = step.remainderMilli();
		}
		assertEquals("[0, 1, 0, 1]", java.util.Arrays.toString(out));
		assertEquals(0, remainder);
	}

	@Test
	void fractionalRateIsNeverLostOverTime() {
		int remainder = 0;
		long total = 0;
		for (int tick = 0; tick < 400; tick++) {
			ThermoelectricOutput.Step step = ThermoelectricOutput.step(remainder, 3750);
			total += step.eu();
			remainder = step.remainderMilli();
		}
		assertEquals(1500, total, "400 ticks at 3.75 EU/t are exactly 1500 EU");
		assertEquals(0, remainder);
	}

	@Test
	void zeroOrNegativeRateMakesNothing() {
		int[] faces = {0, 0, 4, -4, 0, 0};
		assertEquals(0, ThermoelectricOutput.milliEuPerTick(faces, 0));
		assertEquals(0, ThermoelectricOutput.milliEuPerTick(faces, -250));
		assertEquals(new ThermoelectricOutput.Step(0, 0), ThermoelectricOutput.step(0, 0));
	}

	@Test
	void sixFacesAreRequired() {
		assertThrows(IllegalArgumentException.class, () -> ThermoelectricOutput.totalDifference(new int[5]));
	}
}
