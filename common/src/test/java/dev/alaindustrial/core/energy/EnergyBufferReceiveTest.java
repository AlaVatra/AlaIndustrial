package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link EnergyBuffer#receiveInternal}: EU a battery in the block's own drawer hands over (MOD-679). It fills up
 * to the capacity and no further, books what it stored as received rather than generated, and settles at once,
 * so the next commit does not count the same EU a second time.
 *
 * @implements MOD-976 the mutation gate kills the drawer-battery intake
 */
class EnergyBufferReceiveTest {

	private static EnergyBuffer metered(long capacity) {
		EnergyBuffer b = new EnergyBuffer(capacity, 100, 100, () -> {
		});
		b.setCountersEnabled(true);
		return b;
	}

	@Test
	void storesWhatFitsAndBooksItAsReceived() {
		EnergyBuffer b = metered(100);
		b.setAmountUntracked(70);
		b.onFinalCommit();
		long baseline = b.getTotalEnergyIn();

		assertEquals(30, b.receiveInternal(50), "only the room left is stored");
		assertEquals(100, b.getAmount());
		assertEquals(baseline + 30, b.getTotalEnergyIn(), "booked as energy in");
		assertEquals(0, b.getTotalEnergyGenerated(), "a battery is an outside source, not generation");

		b.onFinalCommit();
		assertEquals(baseline + 30, b.getTotalEnergyIn(), "already settled: the commit adds nothing");
		assertEquals(0, b.getTotalEnergyOut());
	}

	@Test
	void anEmptyBufferTakesTheWholeOffer() {
		EnergyBuffer b = metered(100);
		assertEquals(40, b.receiveInternal(40));
		assertEquals(40, b.getAmount());
		assertEquals(40, b.getTotalEnergyIn());
	}

	@Test
	void nothingOrLessIsRefused() {
		EnergyBuffer b = metered(100);
		b.setAmountUntracked(10);
		b.onFinalCommit();
		long in = b.getTotalEnergyIn();
		assertEquals(0, b.receiveInternal(0));
		assertEquals(0, b.receiveInternal(-5));
		assertEquals(10, b.getAmount(), "a negative offer must not drain the buffer");
		assertEquals(in, b.getTotalEnergyIn());
	}

	@Test
	void unmeteredBufferStillFillsButCountsNothing() {
		EnergyBuffer b = new EnergyBuffer(100, 100, 100, () -> {
		});
		assertEquals(25, b.receiveInternal(25));
		assertEquals(25, b.getAmount());
		assertEquals(0, b.getTotalEnergyIn());
	}
}
