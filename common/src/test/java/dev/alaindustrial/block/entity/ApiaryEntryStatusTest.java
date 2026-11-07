package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link ApiaryEntryStatus} (MOD-923) — the state of one row of the station's map list: an ordinal
 * on a sync channel, a translation key, and whether the station may send a drone to that jar at all.
 */
class ApiaryEntryStatusTest {

	/** The ordinal is the wire format: append, never reorder. */
	@Test
	void ordinalsAreTheWireFormat() {
		assertEquals(0, ApiaryEntryStatus.NONE.ordinal());
		assertEquals(1, ApiaryEntryStatus.WAITING.ordinal());
		assertEquals(2, ApiaryEntryStatus.FULL.ordinal());
		assertEquals(3, ApiaryEntryStatus.DRONE_OUT.ordinal());
		assertEquals(4, ApiaryEntryStatus.TOO_FAR.ordinal());
		assertEquals(5, ApiaryEntryStatus.UNLOADED.ordinal());
		assertEquals(6, ApiaryEntryStatus.OTHER_DIMENSION.ordinal());
		assertEquals(7, ApiaryEntryStatus.NO_JAR.ordinal());
	}

	@Test
	void byOrdinalIsTheInverseOfOrdinal() {
		for (ApiaryEntryStatus status : ApiaryEntryStatus.values()) {
			assertSame(status, ApiaryEntryStatus.byOrdinal(status.ordinal()));
		}
	}

	/** A number outside the enum reads as NONE — an empty row, never an exception on the client. */
	@Test
	void outOfRangeOrdinalsFallBackToNone() {
		assertSame(ApiaryEntryStatus.NONE, ApiaryEntryStatus.byOrdinal(-1));
		assertSame(ApiaryEntryStatus.NONE, ApiaryEntryStatus.byOrdinal(ApiaryEntryStatus.values().length));
		assertSame(ApiaryEntryStatus.NONE, ApiaryEntryStatus.byOrdinal(Integer.MAX_VALUE));
		assertSame(ApiaryEntryStatus.NONE, ApiaryEntryStatus.byOrdinal(Integer.MIN_VALUE));
	}

	/** Exactly the three states of a reachable jar are served; every reason to skip a jar is not. */
	@Test
	void onlyReachableJarsAreServed() {
		EnumSet<ApiaryEntryStatus> served = EnumSet.of(ApiaryEntryStatus.WAITING, ApiaryEntryStatus.FULL,
				ApiaryEntryStatus.DRONE_OUT);
		for (ApiaryEntryStatus status : ApiaryEntryStatus.values()) {
			assertEquals(served.contains(status), status.served(), status.name());
		}
	}

	@Test
	void translationKeysAreDistinctAndNamespaced() {
		for (ApiaryEntryStatus status : ApiaryEntryStatus.values()) {
			assertEquals("gui.alaindustrial.apiary_drone_station.entry." + status.name().toLowerCase(Locale.ROOT),
					status.translationKey());
		}
		long distinct = Arrays.stream(ApiaryEntryStatus.values())
				.map(ApiaryEntryStatus::translationKey)
				.distinct()
				.count();
		assertEquals(ApiaryEntryStatus.values().length, distinct);
	}
}
