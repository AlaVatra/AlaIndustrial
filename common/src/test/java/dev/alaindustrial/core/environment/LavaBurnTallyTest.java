package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link LavaBurnTally} (MOD-999): lava counted in whole mB with the part below one mB carried and
 * never rounded up, whole buckets and the part, and the average EU per bucket from the energy really credited.
 */
class LavaBurnTallyTest {

	/** @implements MOD-999-TALLY — a new tally holds nothing, so a block writes no key for it */
	@Test
	void newTallyIsEmpty() {
		LavaBurnTally tally = new LavaBurnTally();
		assertTrue(tally.isEmpty());
		assertEquals(new LavaBurnTally.Shown(0L, 0L), tally.shown());
		assertEquals(-1L, tally.shown().euPerBucket(), "no average of nothing");
	}

	/** @implements MOD-999-TALLY — one reserve tick of 1000 per bucket is exactly one mB */
	@Test
	void wholeBucketAtTheDefaultRate() {
		LavaBurnTally tally = new LavaBurnTally();
		for (int i = 0; i < 1000; i++) {
			tally.burn(1, 1000);
			tally.addEu(16);
		}
		assertEquals(1000L, tally.mb());
		assertEquals(1L, tally.shown().buckets());
		assertEquals(0L, tally.shown().partMb());
		assertEquals(16_000L, tally.shown().euPerBucket());
	}

	/** @implements MOD-999-TALLY — the part below one mB is carried, never rounded up */
	@Test
	void partBelowOneMbIsCarriedNotRoundedUp() {
		LavaBurnTally tally = new LavaBurnTally();
		// 3000 reserve ticks to the bucket: each is a third of a mB.
		tally.burn(1, 3000);
		tally.burn(1, 3000);
		assertEquals(0L, tally.mb(), "two thirds of a mB is still no whole mB");
		assertFalse(tally.isEmpty(), "but something burnt");
		tally.burn(1, 3000);
		assertEquals(1L, tally.mb(), "the third third makes the mB");
		// A third of a bucket at 3000 per bucket: 1000 reserve ticks = 333 mB and a third, shown as 333.
		LavaBurnTally third = new LavaBurnTally();
		third.burn(1000, 3000);
		assertEquals(333L, third.mb());
		assertEquals(0L, third.shown().buckets());
		assertEquals(333L, third.shown().partMb());
	}

	/** @implements MOD-999-TALLY — buckets and the part split the mB exactly */
	@Test
	void bucketsAndPart() {
		LavaBurnTally.Shown shown = new LavaBurnTally.Shown(3250L, 52_000L);
		assertEquals(3L, shown.buckets());
		assertEquals(250L, shown.partMb());
		assertEquals(16_000L, shown.euPerBucket());
	}

	/**
	 * @implements MOD-999-AVERAGE — the average follows the energy really credited: a catalyst that gives more per
	 *     bucket raises it, a buffer that took a tick only in part lowers it; rounded down
	 */
	@Test
	void averageFollowsTheEnergyReallyCredited() {
		LavaBurnTally boosted = new LavaBurnTally();
		// 20 000 EU from a bucket burnt over 833 reserve ticks of a 833-tick bucket.
		boosted.burn(833, 833);
		boosted.addEu(20_000L);
		assertEquals(1000L, boosted.mb());
		assertEquals(20_000L, boosted.shown().euPerBucket());

		LavaBurnTally cut = new LavaBurnTally();
		cut.burn(1000, 1000);
		cut.addEu(16_000L - 9L);
		assertEquals(15_991L, cut.shown().euPerBucket());

		assertEquals(5_333L, LavaBurnTally.euPerBucket(16_000L, 3000L), "rounded down, not to 5 334");
	}

	/** @implements MOD-999-TALLY — nonsense in is no count: a negative tick, a zero divisor, a negative EU */
	@Test
	void ignoresNonsense() {
		LavaBurnTally tally = new LavaBurnTally();
		tally.burn(-5, 1000);
		tally.burn(5, 0);
		tally.addEu(-10L);
		assertTrue(tally.isEmpty());
	}

	/** @implements MOD-999-SAVE — a restore takes the saved mB and EU and drops the unsaved part */
	@Test
	void restoreDropsThePartAndClampsNegatives() {
		LavaBurnTally tally = new LavaBurnTally();
		tally.burn(1, 3000);
		tally.restore(1500L, 24_000L);
		assertEquals(new LavaBurnTally.Shown(1500L, 24_000L), tally.shown());
		tally.burn(2, 3000);
		assertEquals(1500L, tally.mb(), "the third saved before the restore is gone");
		tally.restore(-1L, -1L);
		assertTrue(tally.isEmpty());
	}

	/** @implements MOD-999-AVERAGE — a huge EU figure saturates instead of wrapping negative */
	@Test
	void averageDoesNotOverflow() {
		assertEquals(Long.MAX_VALUE, LavaBurnTally.euPerBucket(Long.MAX_VALUE, 1L));
		assertEquals(Long.MAX_VALUE / 1000L, LavaBurnTally.euPerBucket(Long.MAX_VALUE / 1000L, 1000L));
	}
}
