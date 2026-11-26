package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 for {@link ProvisionalRoleClock} (MOD-1026 follow-up): a network that filed a foreign energy block by a dry
 * run probes it again on a clock, so a battery read while it had spent its per-tick limit is not a pure consumer
 * for good. Before the clock the endpoint scan ran only on a topology change.
 */
class ProvisionalRoleClockTest {

	@Test
	void aProvisionalRoleIsProbedAgainAfterTheInterval() {
		ProvisionalRoleClock clock = new ProvisionalRoleClock();
		assertFalse(clock.due(true, 1_000), "the first call only arms the clock");
		assertFalse(clock.due(true, 1_000 + ProvisionalRoleClock.RECHECK_TICKS - 1), "one tick early");
		assertTrue(clock.due(true, 1_000 + ProvisionalRoleClock.RECHECK_TICKS), "due after the interval");
		assertFalse(clock.due(true, 1_000 + ProvisionalRoleClock.RECHECK_TICKS + 1),
				"due once, then it arms again for the next interval");
		assertTrue(clock.due(true, 1_001 + 2L * ProvisionalRoleClock.RECHECK_TICKS), "and fires again");
	}

	@Test
	void noProvisionalRoleNeverAsksForAScan() {
		ProvisionalRoleClock clock = new ProvisionalRoleClock();
		for (long t = 0; t < 10L * ProvisionalRoleClock.RECHECK_TICKS; t++) {
			assertFalse(clock.due(false, t), "a network of fixed roles rescanned at tick " + t);
		}
	}

	@Test
	void aTopologyChangeInBetweenRestartsTheInterval() {
		ProvisionalRoleClock clock = new ProvisionalRoleClock();
		clock.due(true, 0);
		assertFalse(clock.due(false, 50), "the cables changed: the scan is already coming");
		assertFalse(clock.due(true, 60), "re-armed after that scan");
		assertFalse(clock.due(true, 60 + ProvisionalRoleClock.RECHECK_TICKS - 1));
		assertTrue(clock.due(true, 60 + ProvisionalRoleClock.RECHECK_TICKS));
	}

	@Test
	void theIntervalIsBoundedAndNotEveryTick() {
		assertTrue(ProvisionalRoleClock.RECHECK_TICKS > 1 && ProvisionalRoleClock.RECHECK_TICKS <= 200,
				"bounded cost and a bounded wait: " + ProvisionalRoleClock.RECHECK_TICKS);
		assertEquals(100, ProvisionalRoleClock.RECHECK_TICKS, "five seconds, as documented in NEOFORGE.md");
	}
}
