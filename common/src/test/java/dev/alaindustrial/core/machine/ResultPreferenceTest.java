package dev.alaindustrial.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link ResultPreference} — the result button of the electric and iron furnaces (MOD-1018). Every
 * expected value is a literal.
 */
class ResultPreferenceTest {

	private static final List<String> IRON = List.of("alaindustrial:tempered_iron", "techreborn:refined_iron_ingot",
			"othermod:iron_sheet");

	@Test
	void noPreferenceRunsTheDefault() {
		assertEquals(0, ResultPreference.selectedIndex(IRON, null));
	}

	@Test
	void aStoredKeyRunsItsOption() {
		assertEquals(1, ResultPreference.selectedIndex(IRON, "techreborn:refined_iron_ingot"));
		assertEquals(2, ResultPreference.selectedIndex(IRON, "othermod:iron_sheet"));
	}

	@Test
	void anInputWithoutThePreferredResultRunsTheDefault() {
		assertEquals(0, ResultPreference.selectedIndex(List.of("minecraft:iron_ingot", "othermod:iron_sheet"),
				"techreborn:refined_iron_ingot"));
	}

	@Test
	void noOptionsSelectsNothing() {
		assertEquals(-1, ResultPreference.selectedIndex(List.of(), null));
		assertEquals(-1, ResultPreference.selectedIndex(List.of(), "techreborn:refined_iron_ingot"));
	}

	@Test
	void pressesStepRoundAndClearTheChoiceOnTheDefault() {
		String first = ResultPreference.afterCycle(IRON, null);
		assertEquals("techreborn:refined_iron_ingot", first);
		String second = ResultPreference.afterCycle(IRON, first);
		assertEquals("othermod:iron_sheet", second);
		assertNull(ResultPreference.afterCycle(IRON, second), "wrapping round to the default clears the choice");
	}

	@Test
	void aPressOnAnInputWithoutThePreferredResultStartsFromTheDefault() {
		// The stored key is absent here, so the machine shows option 0; one press goes to option 1 of THIS input.
		assertEquals("othermod:iron_sheet", ResultPreference.afterCycle(
				List.of("minecraft:iron_ingot", "othermod:iron_sheet"), "techreborn:refined_iron_ingot"));
	}

	@Test
	void aSingleOptionIgnoresThePressAndKeepsThePreference() {
		assertEquals("techreborn:refined_iron_ingot", ResultPreference.afterCycle(
				List.of("minecraft:iron_ingot"), "techreborn:refined_iron_ingot"));
		assertNull(ResultPreference.afterCycle(List.of(), null));
	}
}
