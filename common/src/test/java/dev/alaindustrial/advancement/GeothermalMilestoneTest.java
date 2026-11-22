package dev.alaindustrial.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The geothermal chain (MOD-1001): the datapack names of its steps and the numbers that define them, and the
 * "running at the same time" rule of the Station step ({@link GeothermalStation}).
 */
class GeothermalMilestoneTest {

	private static final UUID ALICE = new UUID(0L, 1L);
	private static final UUID BOB = new UUID(0L, 2L);
	private static final int FIVE = GeothermalMilestone.STATION_SIZE;
	private static final int WINDOW = GeothermalMilestone.STATION_WINDOW_TICKS;

	/** A generator with identity only, like a block entity. */
	private static final class Gen {
	}

	/** @implements MOD-1001 — the names the advancement JSON files carry, both ways */
	@Test
	void everyStepRoundTripsThroughItsDatapackName() {
		for (GeothermalMilestone milestone : GeothermalMilestone.values()) {
			assertEquals(milestone, GeothermalMilestone.byId(milestone.id()), milestone.name());
		}
		assertEquals("first_lava", GeothermalMilestone.FIRST_LAVA.id());
		assertEquals("lava_million", GeothermalMilestone.LAVA_MILLION.id());
		assertEquals("station", GeothermalMilestone.STATION.id());
		assertNull(GeothermalMilestone.byId("Station"), "a misspelt name matches nothing");
	}

	/** @implements MOD-1001 — the owner's decision: a million EU from lava, five generators */
	@Test
	void thresholdsAreTheOwnersDecision() {
		assertEquals(1_000_000L, GeothermalMilestone.LAVA_MILLION_EU);
		assertEquals(5, GeothermalMilestone.STATION_SIZE);
		assertEquals(20, GeothermalMilestone.STATION_WINDOW_TICKS);
	}

	/** @implements MOD-1001 — five generators of one owner burning within the window are a station */
	@Test
	void fiveGeneratorsWithinTheWindowAreAStation() {
		GeothermalStation<Gen> station = new GeothermalStation<>();
		List<Gen> gens = gens(FIVE);
		int running = 0;
		for (int i = 0; i < FIVE; i++) {
			running = station.burnt(ALICE, gens.get(i), 100L + i, g -> true, FIVE);
		}
		assertEquals(FIVE, running);
	}

	/** @implements MOD-1001 — a generator that last burnt a window ago no longer runs */
	@Test
	void aBurnOlderThanTheWindowDropsOut() {
		GeothermalStation<Gen> station = new GeothermalStation<>();
		List<Gen> gens = gens(FIVE);
		station.burnt(ALICE, gens.get(0), 0L, g -> true, FIVE);
		for (int i = 1; i < FIVE - 1; i++) {
			station.burnt(ALICE, gens.get(i), WINDOW - 1L, g -> true, FIVE);
		}
		assertEquals(FIVE, station.burnt(ALICE, gens.get(FIVE - 1), WINDOW - 1L, g -> true, FIVE),
				"the first burnt WINDOW - 1 ticks ago and still runs");
		assertEquals(FIVE - 1, station.burnt(ALICE, gens.get(FIVE - 1), WINDOW, g -> true, FIVE),
				"a whole window after its burn the first no longer runs");
	}

	/** @implements MOD-1001 — moving a generator does not make two: the broken block stops counting at once */
	@Test
	void aRemovedGeneratorStopsCountingAtOnce() {
		GeothermalStation<Gen> station = new GeothermalStation<>();
		Set<Gen> broken = new HashSet<>();
		List<Gen> gens = gens(FIVE);
		for (int i = 0; i < FIVE - 1; i++) {
			station.burnt(ALICE, gens.get(i), 10L, g -> !broken.contains(g), FIVE);
		}
		broken.add(gens.get(0));
		// The first generator, broken and placed again, is a new block: four blocks, not five.
		assertEquals(FIVE - 1, station.burnt(ALICE, gens.get(FIVE - 1), 11L, g -> !broken.contains(g), FIVE));
	}

	/** @implements MOD-1001 — another player's generators are not part of this player's station */
	@Test
	void ownersAreCountedApart() {
		GeothermalStation<Gen> station = new GeothermalStation<>();
		List<Gen> gens = gens(FIVE);
		for (int i = 0; i < FIVE - 1; i++) {
			station.burnt(ALICE, gens.get(i), 5L, g -> true, FIVE);
		}
		assertEquals(1, station.burnt(BOB, gens.get(FIVE - 1), 5L, g -> true, FIVE));
		assertEquals(FIVE - 1, station.burnt(ALICE, gens.get(0), 5L, g -> true, FIVE));
	}

	/** @implements MOD-1001 — a clock that started again forgets the burns "from the future" */
	@Test
	void aBurnAfterNowIsForgotten() {
		GeothermalStation<Gen> station = new GeothermalStation<>();
		List<Gen> gens = gens(FIVE);
		for (int i = 0; i < FIVE - 1; i++) {
			station.burnt(ALICE, gens.get(i), 1000L, g -> true, FIVE);
		}
		assertEquals(1, station.burnt(ALICE, gens.get(FIVE - 1), 3L, g -> true, FIVE));
	}

	/** @implements MOD-1001 — the count stops at what is asked for */
	@Test
	void countStopsAtEnough() {
		GeothermalStation<Gen> station = new GeothermalStation<>();
		List<Gen> gens = gens(12);
		int running = 0;
		for (Gen gen : gens) {
			running = station.burnt(ALICE, gen, 7L, g -> true, FIVE);
		}
		assertEquals(FIVE, running);
	}

	/** {@code n} generators, held strongly by the list: the station keeps its keys weakly. */
	private static List<Gen> gens(int n) {
		List<Gen> gens = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			gens.add(new Gen());
		}
		return gens;
	}
}
