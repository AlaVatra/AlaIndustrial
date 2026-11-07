package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link ApiaryStationStatus} (MOD-923) — the apiary station's status line: an ordinal on a sync
 * channel and in the saved block entity, a translation key on screen, and the blocking flag the status row colours by.
 */
class ApiaryStationStatusTest {

	/** The ordinal is the wire and the save format: append, never reorder. */
	@Test
	void ordinalsAreTheWireFormat() {
		assertEquals(0, ApiaryStationStatus.NO_HIVE.ordinal());
		assertEquals(1, ApiaryStationStatus.NO_MAP.ordinal());
		assertEquals(2, ApiaryStationStatus.NO_DRONES.ordinal());
		assertEquals(3, ApiaryStationStatus.NO_ENERGY.ordinal());
		assertEquals(4, ApiaryStationStatus.TANK_FULL.ordinal());
		assertEquals(5, ApiaryStationStatus.OUTPUT_FULL.ordinal());
		assertEquals(6, ApiaryStationStatus.WAITING.ordinal());
		assertEquals(7, ApiaryStationStatus.WORKING.ordinal());
	}

	@Test
	void byOrdinalIsTheInverseOfOrdinal() {
		for (ApiaryStationStatus status : ApiaryStationStatus.values()) {
			assertSame(status, ApiaryStationStatus.byOrdinal(status.ordinal()));
		}
	}

	/** A number outside the enum reads as WAITING — not a fault, so a stale packet never paints the row red. */
	@Test
	void outOfRangeOrdinalsFallBackToWaiting() {
		assertSame(ApiaryStationStatus.WAITING, ApiaryStationStatus.byOrdinal(-1));
		assertSame(ApiaryStationStatus.WAITING, ApiaryStationStatus.byOrdinal(ApiaryStationStatus.values().length));
		assertSame(ApiaryStationStatus.WAITING, ApiaryStationStatus.byOrdinal(Integer.MAX_VALUE));
		assertSame(ApiaryStationStatus.WAITING, ApiaryStationStatus.byOrdinal(Integer.MIN_VALUE));
	}

	/** Only WORKING and WAITING are not stops; every other line names a reason the station stands. */
	@Test
	void onlyWorkingAndWaitingAreNotBlocking() {
		EnumSet<ApiaryStationStatus> running = EnumSet.of(ApiaryStationStatus.WORKING, ApiaryStationStatus.WAITING);
		for (ApiaryStationStatus status : ApiaryStationStatus.values()) {
			if (running.contains(status)) {
				assertFalse(status.isBlocking(), status + " must not read as a stop");
			} else {
				assertTrue(status.isBlocking(), status + " must read as a stop");
			}
		}
	}

	@Test
	void translationKeysAreDistinctAndNamespaced() {
		for (ApiaryStationStatus status : ApiaryStationStatus.values()) {
			String key = status.translationKey();
			assertEquals("gui.alaindustrial.apiary_drone_station.status." + status.name().toLowerCase(Locale.ROOT),
					key);
		}
		long distinct = Arrays.stream(ApiaryStationStatus.values())
				.map(ApiaryStationStatus::translationKey)
				.distinct()
				.count();
		assertEquals(ApiaryStationStatus.values().length, distinct);
	}
}
