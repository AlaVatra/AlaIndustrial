package dev.alaindustrial.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 contract for the Upgrade Table's status (MOD-482): the order {@link ToolUpgradeStatus#resolve} names the
 * first missing thing in, the two tick-loop questions, and the wire code round trip.
 *
 * @implements MOD-976 the mutation gate kills the Upgrade Table's status rule
 */
class ToolUpgradeStatusTest {

	@Test
	void resolveNamesTheFirstThingInTheWay() {
		assertEquals(ToolUpgradeStatus.NO_TOOL, ToolUpgradeStatus.resolve(false, true, false),
				"an empty tool slot comes first, even with a module waiting");
		assertEquals(ToolUpgradeStatus.NO_TOOL, ToolUpgradeStatus.resolve(false, false, true));
		assertEquals(ToolUpgradeStatus.NO_MODULE, ToolUpgradeStatus.resolve(true, false, true),
				"a tool without a module asks for the module, whatever the tool carries");
		assertEquals(ToolUpgradeStatus.ALREADY_INSTALLED, ToolUpgradeStatus.resolve(true, true, true));
		assertEquals(ToolUpgradeStatus.READY, ToolUpgradeStatus.resolve(true, true, false));
	}

	@Test
	void onlyReadyWorksAndKeepsItsJob() {
		for (ToolUpgradeStatus status : ToolUpgradeStatus.values()) {
			boolean ready = status == ToolUpgradeStatus.READY;
			assertEquals(ready, status.canWork(), status + ".canWork");
			assertEquals(ready, status.jobIntact(), status + ".jobIntact");
			assertEquals(!ready, status.isBlocking(), status + ".isBlocking");
		}
		assertTrue(ToolUpgradeStatus.READY.canWork());
		assertFalse(ToolUpgradeStatus.NO_TOOL.jobIntact());
	}

	@Test
	void wireCodeRoundTripsAndOutOfRangeFallsBackToNoTool() {
		for (ToolUpgradeStatus status : ToolUpgradeStatus.values()) {
			assertEquals(status.ordinal(), status.code());
			assertEquals(status, ToolUpgradeStatus.byCode(status.code()));
		}
		assertEquals(3, ToolUpgradeStatus.READY.code(), "READY is the last constant and travels as 3");
		assertEquals(ToolUpgradeStatus.NO_TOOL, ToolUpgradeStatus.byCode(-1));
		assertEquals(ToolUpgradeStatus.NO_TOOL, ToolUpgradeStatus.byCode(ToolUpgradeStatus.values().length));
		assertEquals(ToolUpgradeStatus.NO_MODULE, ToolUpgradeStatus.byCode(1));
	}
}
