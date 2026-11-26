package dev.alaindustrial.item.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.item.ToolConfig;
import org.junit.jupiter.api.Test;

/**
 * The magnet's two grades (MOD-580): each grade reads its own live knob, the advanced one reaches further and
 * has more module slots, and only a grade that pays for experience orbs pulls them.
 *
 * @implements MOD-976 the mutation gate kills the magnet grade catalogue
 */
class MagnetTierTest {

	@Test
	void eachGradeReadsItsOwnKnob() {
		assertEquals(ToolConfig.magnetRange, MagnetTier.BASIC.range());
		assertEquals(ToolConfig.magnetAdvancedRange, MagnetTier.ADVANCED.range());
		assertEquals(ToolConfig.magnetBuffer, MagnetTier.BASIC.buffer());
		assertEquals(ToolConfig.magnetAdvancedBuffer, MagnetTier.ADVANCED.buffer());
		assertEquals(ToolConfig.magnetInputRate, MagnetTier.BASIC.inputRate());
		assertEquals(ToolConfig.magnetAdvancedInputRate, MagnetTier.ADVANCED.inputRate());
		assertEquals(ToolConfig.magnetEuPerItem, MagnetTier.BASIC.euPerItem());
		assertEquals(ToolConfig.magnetAdvancedEuPerItem, MagnetTier.ADVANCED.euPerItem());
		assertEquals(7, MagnetTier.BASIC.pick(7, 11));
		assertEquals(11, MagnetTier.ADVANCED.pick(7, 11));
	}

	@Test
	void advancedReachesFurtherAndHoldsMoreModules() {
		assertTrue(MagnetTier.ADVANCED.range() > MagnetTier.BASIC.range());
		assertEquals(1, MagnetTier.BASIC.moduleSlots());
		assertEquals(3, MagnetTier.ADVANCED.moduleSlots());
	}

	@Test
	void onlyAGradeThatPaysForOrbsPullsExperience() {
		int saved = ToolConfig.magnetAdvancedEuPerOrb;
		try {
			ToolConfig.magnetAdvancedEuPerOrb = 4;
			assertEquals(0, MagnetTier.BASIC.euPerOrb(), "the basic grade never pays for an orb");
			assertEquals(4, MagnetTier.ADVANCED.euPerOrb());
			assertFalse(MagnetTier.BASIC.pullsExperience());
			assertTrue(MagnetTier.ADVANCED.pullsExperience());
			ToolConfig.magnetAdvancedEuPerOrb = 0;
			assertFalse(MagnetTier.ADVANCED.pullsExperience(), "a free orb price switches the pull off");
		} finally {
			ToolConfig.magnetAdvancedEuPerOrb = saved;
		}
	}
}
