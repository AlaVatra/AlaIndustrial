package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * MOD-1027: {@link LinePlanner#plan} finds the receiver and the generator of a host by a map, not by a pass over the
 * ones collected so far (MOD-1023 audit, C1). A bus of {@value #BUS} cables with a machine beside every cable: the old
 * pass compared hosts {@code n²/2} times a tick, about half a million here.
 *
 * <p>Two checks. The count: positions are compared a bounded number of times per receiver, whatever the line's
 * length (red on the pass: it grows with the square). The plan: what every receiver gets over {@value #TICKS} ticks is
 * the same, to the EU, as the plan written before the change (the digest below was taken on that code; ADR-049 says
 * the order of receivers is the plan's priority, so a map that reordered them would show here). Two cells of one
 * host and a host that gives and takes (ADR-055) are on the bus, so the merge and the {@code self} mark are both
 * exercised. The time a tick takes is printed, not asserted.
 */
class LinePlannerHostLookupTest {

	private static final int BUS = 1000;
	private static final int TICKS = 40;
	private static final long PACKET = 32;

	/** A grid position that counts how often it is compared. */
	record Pos(int x, int z) {
		static long compared;

		@Override
		public boolean equals(Object o) {
			compared++;
			return o instanceof Pos p && p.x == x && p.z == z;
		}

		@Override
		public int hashCode() {
			return x * 31 + z;
		}
	}

	private static UnaryOperator<Pos> step(int dx, int dz) {
		return p -> new Pos(p.x() + dx, p.z() + dz);
	}

	private static final List<UnaryOperator<Pos>> FACES = List.of(step(0, -1), step(0, 1), step(-1, 0), step(1, 0));

	private static final class Txn implements EnergyPort.Txn {
		@Override
		public void enlist(EnergyPort.Participant participant) {
		}
	}

	/** One endpoint of the bus: where it stands, the host whose buffer it fills, its buffer and its need. */
	private record End(Pos pos, Pos host, EnergyBuffer buf, long need) {
	}

	private final Set<Pos> cables = new LinkedHashSet<>();
	private final Map<Pos, EnergyBuffer> buffers = new LinkedHashMap<>();
	private final List<End> ends = new ArrayList<>();
	private final LinePlanner<Pos> planner = new LinePlanner<>();
	private EnergyBuffer generator;
	private EnergyBuffer dual;
	private long planNanos;

	private void build() {
		for (int x = 0; x < BUS; x++) {
			Pos c = new Pos(x, 0);
			cables.add(c);
			buffers.put(c, new EnergyBuffer(12, 12, 12, () -> { }));
		}
		generator = new EnergyBuffer(Long.MAX_VALUE / 4, 0, PACKET, () -> { });
		for (int x = 0; x < BUS; x++) {
			// Machines of 1 to 4 EU/t on the north side of every cable; cells 10 and 11 north are one host (ADR-027).
			Pos at = new Pos(x, -1);
			Pos host = x == 11 ? new Pos(10, -1) : at;
			EnergyBuffer buf = x == 11 ? ends.get(10).buf() : new EnergyBuffer(64, 16, 0, () -> { });
			ends.add(new End(at, host, buf, 1 + x % 4));
		}
		// South of cable 500 a block that gives and takes (a foreign battery, ADR-055).
		dual = new EnergyBuffer(10_000, 16, 16, () -> { });
		dual.setAmountUntracked(5_000);
	}

	private long tick(int cursor) {
		Txn txn = new Txn();
		List<EnergyLineDistributor.LiveConsumer<Pos>> machines = new ArrayList<>();
		for (End e : ends) {
			long room = Math.min(e.buf().maxInsert, e.buf().getCapacity() - e.buf().getAmount());
			if (room > 0) {
				machines.add(new EnergyLineDistributor.LiveConsumer<>(e.pos(), e.buf(), room, e.need(), e.host()));
			}
		}
		Pos dualPos = new Pos(500, 1);
		long dualRoom = Math.min(dual.maxInsert, dual.getCapacity() - dual.getAmount());
		if (dualRoom > 0) {
			machines.add(new EnergyLineDistributor.LiveConsumer<>(dualPos, dual, dualRoom, 0));
		}
		generator.setAmountUntracked(PACKET);
		List<EnergyLineDistributor.LiveProducer<Pos>> producers = new ArrayList<>();
		producers.add(new EnergyLineDistributor.LiveProducer<>(new Pos(-1, 0), generator));
		producers.add(new EnergyLineDistributor.LiveProducer<>(dualPos, dual, dualPos, dual.maxExtract));
		List<Pos> cableList = List.copyOf(cables);
		EnergyLineDistributor<Pos> kernel = new EnergyLineDistributor<>(new LineView<>(FACES, cables::contains,
				buffers::get, p -> Math.max(1, p.x() + 1), p -> cables.contains(p) ? p.x() + 1 : null, cableList,
				(p, f) -> true, (p, f) -> true, planner));
		long t0 = System.nanoTime();
		kernel.distribute(machines, List.of(), List.of(), 0L, producers, PACKET, 0.0, txn, cursor);
		planNanos += System.nanoTime() - t0;
		long digest = 17;
		for (End e : ends) {
			digest = digest * 31 + e.buf().getAmount();
			if (e.buf().getAmount() >= e.need()) {
				e.buf().drainInternal(e.need()); // the machine works
			}
		}
		digest = digest * 31 + dual.getAmount();
		for (EnergyBuffer b : buffers.values()) {
			digest = digest * 31 + b.getAmount();
		}
		return digest;
	}

	@Test
	void aLongBusPlansWithoutComparingEveryHostWithEveryOther() {
		build();
		long digest = 1;
		long comparedPerTick = 0;
		for (int t = 0; t < TICKS; t++) {
			long before = Pos.compared;
			digest = digest * 1_000_003 + tick(t);
			comparedPerTick = Math.max(comparedPerTick, Pos.compared - before);
		}
		System.out.printf(Locale.ROOT, "MOD-1027 bus of %d machines: %.3f ms a tick, %d position compares at most"
				+ " a tick, digest %d%n", BUS, planNanos / 1e6 / TICKS, comparedPerTick, digest);
		assertTrue(comparedPerTick < 40L * BUS,
				"positions compared in one tick of a " + BUS + "-machine bus: " + comparedPerTick);
		assertEquals(EXPECTED_DIGEST, digest, "the plan must give every receiver what it gave before MOD-1027");
	}

	/** What the plan gave over the {@value #TICKS} ticks before MOD-1027, taken on that code. */
	private static final long EXPECTED_DIGEST = 4652914414195777311L;
}
