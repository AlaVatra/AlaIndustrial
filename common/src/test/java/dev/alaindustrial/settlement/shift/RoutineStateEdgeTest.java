package dev.alaindustrial.settlement.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.shift.RoutineState.Progress;
import org.junit.jupiter.api.Test;

/**
 * The shift worker's routine memory at its exact edges (MOD-889): what adopting an activity keeps, the switch cap
 * and the stuck count on the very tick they fall due, the first backoff wait, and the bookkeeping the tick budget
 * and the chronicle read.
 *
 * @implements MOD-976 the mutation gate kills the routine memory's edges
 */
class RoutineStateEdgeTest {

	@Test
	void adoptingAnActivityRemembersItAndWhen() {
		RoutineState s = new RoutineState();
		assertNull(s.activity());
		s.adopt(RoutineActivity.WORK, 1234L);
		assertSame(RoutineActivity.WORK, s.activity());
		assertEquals(1234L, s.activitySince());
	}

	@Test
	void aBusyWorkerSwitchesExactlyWhenTheCapRunsOut() {
		RoutineState s = new RoutineState();
		s.adopt(RoutineActivity.WORK, 0L);
		assertFalse(s.shouldSwitch(RoutineActivity.REST, 1000L, true), "the switch falls due now");
		assertFalse(s.shouldSwitch(RoutineActivity.REST, 1000L + RoutineState.FINISH_CAP - 1, true));
		assertTrue(s.shouldSwitch(RoutineActivity.REST, 1000L + RoutineState.FINISH_CAP, true),
				"FINISH_CAP ticks after it fell due the worker stops what it does");
	}

	@Test
	void progressCountsFromAWholeBlockCloserAndStuckFromTheExactTick() {
		RoutineState s = new RoutineState();
		long target = 42L;
		assertSame(Progress.ON_WAY, s.track(target, 10.0, 0L));
		// exactly one block closer is progress and restarts the clock
		assertSame(Progress.ON_WAY, s.track(target, 9.0, 100L));
		assertSame(Progress.ON_WAY, s.track(target, 8.5, 100L + RoutineState.STUCK_TICKS - 1),
				"half a block is not progress, but the count has not run out yet");
		assertSame(Progress.STUCK, s.track(target, 8.5, 100L + RoutineState.STUCK_TICKS),
				"had the full block not restarted the clock at tick 100, this would have come earlier");
		assertTrue(s.gaveUp(target));
	}

	@Test
	void theFirstFailureWaitsTheBaseAndIsCounted() {
		RoutineState s = new RoutineState();
		long target = 7L;
		assertEquals(0, s.failures(target));
		s.planFailed(target, 500L);
		assertEquals(1, s.failures(target));
		assertFalse(s.mayPlan(target, 500L + RoutineState.BACKOFF_BASE - 1));
		assertTrue(s.mayPlan(target, 500L + RoutineState.BACKOFF_BASE));
		s.planFailed(target, 1000L);
		assertEquals(2, s.failures(target));
		assertFalse(s.mayPlan(target, 1000L + 2L * RoutineState.BACKOFF_BASE - 1));
		assertTrue(s.mayPlan(target, 1000L + 2L * RoutineState.BACKOFF_BASE));
	}

	@Test
	void rankSpotPathsAndChargingAreKept() {
		RoutineState s = new RoutineState();
		assertFalse(s.rankCountedOn(3L));
		s.setRank(5, 3L);
		assertEquals(5, s.rank());
		assertTrue(s.rankCountedOn(3L));
		assertFalse(s.rankCountedOn(4L), "ranks are counted once a day");

		assertEquals(RoutineState.NONE, s.spot());
		s.setSpot(99L);
		assertEquals(99L, s.spot());

		assertEquals(0, s.pathsPlanned());
		s.notePathPlanned();
		s.notePathPlanned();
		assertEquals(2, s.pathsPlanned());

		assertFalse(s.chargingByDay());
		s.setChargingByDay(true);
		assertTrue(s.chargingByDay());
		s.setChargingByDay(false);
		assertFalse(s.chargingByDay());
	}
}
