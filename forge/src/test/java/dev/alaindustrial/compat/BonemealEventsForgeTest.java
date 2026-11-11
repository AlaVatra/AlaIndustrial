package dev.alaindustrial.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.item.misc.ReinforcedBonemeal.Verdict;
import org.junit.jupiter.api.Test;

/** {@code ForgeEventFactory.onApplyBonemeal}'s answer read as the shared verdict (MOD-962, slice 2.5). */
class BonemealEventsForgeTest {

	@Test
	void cancelledIsDenied() {
		assertEquals(Verdict.DENIED, BonemealEventsForge.verdict(-1));
	}

	@Test
	void allowedIsHandledElsewhere() {
		assertEquals(Verdict.HANDLED, BonemealEventsForge.verdict(1));
	}

	@Test
	void defaultOrNoPlayerProceeds() {
		assertEquals(Verdict.PROCEED, BonemealEventsForge.verdict(0));
	}
}
