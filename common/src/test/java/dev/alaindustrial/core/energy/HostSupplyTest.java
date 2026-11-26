package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR-027 on the supply side: a multiblock's cells are separate endpoints over ONE buffer, so the generators' supply
 * the storage stage plans with counts each host once. Pinned here, on the kernel, since MOD-1031 withdrew the only
 * multiblock generator (the Mirror Concentrator) whose world scenarios used to hold it; the per-host packet cap is
 * {@code EnergyLineDistributorCoreTest#theCellsOfOneHostPutInOnePacket}.
 */
class HostSupplyTest {

	private static EnergyLineDistributor.LiveProducer<String> cell(String pos, String host, long supply) {
		return new EnergyLineDistributor.LiveProducer<>(pos, null, host, supply);
	}

	/** Three cells of one machine over one 100 EU buffer promise 100 EU, not 300 (red before MOD-608). */
	@Test
	void theCellsOfOneHostCountOnce() {
		List<EnergyLineDistributor.LiveProducer<String>> generators = List.of(
				cell("a", "core", 100), cell("b", "core", 100), cell("c", "core", 100));
		assertEquals(100L, EnergyLineDistributor.supplyOncePerHost(generators), "one host, one buffer");
	}

	/** Two machines are two buffers: both count. A cell between them does not merge them. */
	@Test
	void separateHostsAdd() {
		List<EnergyLineDistributor.LiveProducer<String>> generators = List.of(
				cell("a", "a", 40), cell("b", "core", 100), cell("c", "c", 7), cell("d", "core", 100));
		assertEquals(147L, EnergyLineDistributor.supplyOncePerHost(generators));
	}

	/** An ordinary producer is its own host. */
	@Test
	void anOrdinaryProducerIsItsOwnHost() {
		assertEquals(30L, EnergyLineDistributor.supplyOncePerHost(List.of(cell("x", "x", 10), cell("y", "y", 20))));
		assertEquals(0L, EnergyLineDistributor.supplyOncePerHost(List.of()));
	}
}
