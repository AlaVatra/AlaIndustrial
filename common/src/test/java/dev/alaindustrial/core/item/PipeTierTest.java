package dev.alaindustrial.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.alaindustrial.Config;
import org.junit.jupiter.api.Test;

/**
 * L1 contract for the item pipe grades (MOD-581): each grade reads its own live knob, and a network runs at
 * the grade that moves fewer items — decided by the numbers, not by the declaration order.
 *
 * @implements MOD-976 the mutation gate kills the pipe grade rule
 */
class PipeTierTest {

	@Test
	void eachGradeReadsItsOwnKnob() {
		assertEquals(Config.itemPipeItemsPerTransfer, PipeTier.BASIC.itemsPerTransfer());
		assertEquals(Config.itemPipeAdvancedItemsPerTransfer, PipeTier.ADVANCED.itemsPerTransfer());
		assertEquals(5, PipeTier.BASIC.itemsPerTransfer(5, 9), "the tooltip's numbers, basic grade");
		assertEquals(9, PipeTier.ADVANCED.itemsPerTransfer(5, 9), "the tooltip's numbers, advanced grade");
	}

	@Test
	void networkRunsAtTheWeakerGrade() {
		int basic = Config.itemPipeItemsPerTransfer;
		int advanced = Config.itemPipeAdvancedItemsPerTransfer;
		try {
			Config.itemPipeItemsPerTransfer = 2;
			Config.itemPipeAdvancedItemsPerTransfer = 4;
			assertSame(PipeTier.BASIC, PipeTier.min(PipeTier.BASIC, PipeTier.ADVANCED));
			assertSame(PipeTier.BASIC, PipeTier.min(PipeTier.ADVANCED, PipeTier.BASIC));
			assertSame(PipeTier.ADVANCED, PipeTier.min(PipeTier.ADVANCED, PipeTier.ADVANCED));

			// A pack that makes the basic grade the faster one gets the ladder its numbers describe.
			Config.itemPipeItemsPerTransfer = 8;
			assertSame(PipeTier.ADVANCED, PipeTier.min(PipeTier.BASIC, PipeTier.ADVANCED));
			assertSame(PipeTier.ADVANCED, PipeTier.min(PipeTier.ADVANCED, PipeTier.BASIC));

			// Equal batches: the first argument stands, so a fold over a uniform network never flips.
			Config.itemPipeItemsPerTransfer = 4;
			assertSame(PipeTier.ADVANCED, PipeTier.min(PipeTier.ADVANCED, PipeTier.BASIC));
			assertSame(PipeTier.BASIC, PipeTier.min(PipeTier.BASIC, PipeTier.ADVANCED));
		} finally {
			Config.itemPipeItemsPerTransfer = basic;
			Config.itemPipeAdvancedItemsPerTransfer = advanced;
		}
	}

	@Test
	void aMissingGradeYieldsToThePresentOne() {
		assertSame(PipeTier.ADVANCED, PipeTier.min(null, PipeTier.ADVANCED));
		assertSame(PipeTier.BASIC, PipeTier.min(PipeTier.BASIC, null));
		assertNull(PipeTier.min(null, null));
	}
}
