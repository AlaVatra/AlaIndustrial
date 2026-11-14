package dev.alaindustrial.core.energy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * One row of the MOD-940 server stand ({@code research.md} section 4.4): blocks of three mods side by side along x,
 * ticked in the server's order. Our cables are {@link LineRig} networks, one per run of our copper cable; the foreign
 * blocks follow the contracts read from the shipped jars (Tech Reborn 6.2.1, Energized Power 3.0.5, Team Reborn Energy
 * 5.0.0) — who starts a transfer, in which order, on which tick. Only the behaviour is restated here, never their code.
 *
 * <p>The row is a string, one letter a block: {@code S} our creative source, {@code a} our copper cable, {@code B} our
 * Battery Box (input face toward the cable); {@code P} the Tech Reborn creative solar panel, {@code T} a Tech Reborn
 * copper cable, {@code U} a Tech Reborn LV storage unit (output face away from the cable); {@code C} the Energized
 * Power creative battery box, {@code e} an Energized Power copper cable, {@code X} an Energized Power battery box.
 *
 * <p>Order of one server tick: the block entities tick in the order they were placed — the stand places the receiver
 * first and the source last, so from the last cell to the first — and our networks tick after all of them, at the end
 * of the tick ({@code ServerHookRoster.SERVER_TICK_PER_LEVEL}).
 */
final class ForeignJunctionRow {

	static final int Z = 3;
	private static final long MAX = Long.MAX_VALUE;
	private static final EnergyPort.Txn TXN = participant -> { };

	/** One face of a block as a neighbour reaches it: a Team Reborn {@code EnergyStorage} side, with its dry runs. */
	interface Side extends EnergyPort {
		long simInsert(long max);

		long simExtract(long max);

		@Override
		default long getAmount() {
			return 0;
		}

		@Override
		default long getCapacity() {
			return 0;
		}
	}

	/** What our cable's port does for a foreign block: today both roles on every face ({@code EnergyRole.BOTH}). */
	enum OurFace {
		BOTH(true, true), IN(true, false), OUT(false, true);

		final boolean in;
		final boolean out;

		OurFace(boolean in, boolean out) {
			this.in = in;
			this.out = out;
		}
	}

	private final String row;
	private final OurFace ourFace;
	private final Random shuffle = new Random(940);
	private final Map<Integer, LineRig> rigOf = new LinkedHashMap<>();
	private final List<LineRig> rigs = new ArrayList<>();
	private final Map<Integer, Foreign> foreign = new LinkedHashMap<>();

	ForeignJunctionRow(String row, OurFace ourFace) {
		this.row = row;
		this.ourFace = ourFace;
		for (int x = 0; x < row.length(); x++) {
			switch (row.charAt(x)) {
				case 'P' -> foreign.put(x, new Panel());
				case 'U' -> foreign.put(x, new StorageUnit());
				case 'C' -> foreign.put(x, new CreativeBox(x));
				case 'X' -> foreign.put(x, new Battery(x));
				case 'T' -> {
					if (x == 0 || row.charAt(x - 1) != 'T') {
						TrNetwork net = new TrNetwork(x, runEnd(x, 'T'));
						for (int c = net.x0; c <= net.x1; c++) {
							foreign.put(c, net);
						}
					}
				}
				case 'e' -> {
					if (x == 0 || row.charAt(x - 1) != 'e') {
						EpNetwork net = new EpNetwork(x, runEnd(x, 'e'));
						for (int c = net.x0; c <= net.x1; c++) {
							foreign.put(c, net);
						}
					}
				}
				case 'a' -> {
					if (x == 0 || row.charAt(x - 1) != 'a') {
						buildRig(x, runEnd(x, 'a'));
					}
				}
				default -> {
				}
			}
		}
	}

	private int runEnd(int x0, char kind) {
		int x = x0;
		while (x + 1 < row.length() && row.charAt(x + 1) == kind) {
			x++;
		}
		return x;
	}

	private void buildRig(int x0, int x1) {
		LineRig r = new LineRig(row + "@" + x0, 0.0).run(x0, Z, x1, Z);
		for (int x : new int[] {x0 - 1, x1 + 1}) {
			if (x < 0 || x >= row.length()) {
				continue;
			}
			char k = row.charAt(x);
			if (k == 'S') {
				r.source(x, Z, 512);
			} else if (k == 'B') {
				r.box("B", x, Z, 20_000);
			} else if (k != 'a') {
				int dir = x < x0 ? 1 : -1; // the face of the foreign block that looks at the cable
				Side side = new LazySide(x, dir);
				r.port(k + "@" + x, x, Z, side, () -> side.simInsert(MAX), () -> side.simExtract(MAX));
			}
		}
		for (int x = x0; x <= x1; x++) {
			rigOf.put(x, r);
		}
		rigs.add(r);
	}

	/** One server tick: the block entities from the last cell to the first, then our networks. */
	void tick() {
		List<Foreign> ticked = new ArrayList<>();
		for (int x = row.length() - 1; x >= 0; x--) {
			Foreign f = foreign.get(x);
			if (f != null && !ticked.contains(f)) {
				ticked.add(f); // a cable network ticks once, on the first of its cables to tick
				f.tick();
			}
		}
		for (LineRig r : rigs) {
			r.tick();
		}
	}

	/** What the last block of the row holds now. */
	long sink() {
		int x = row.length() - 1;
		if (row.charAt(x) == 'B') {
			return rigOf.get(x - 1).end("B");
		}
		return foreign.get(x).held();
	}

	/** The side a block at {@code x} shows on its face toward {@code dir}, as its neighbour there finds it. */
	private Side sideAt(int x, int dir) {
		if (x < 0 || x >= row.length()) {
			return null;
		}
		if (row.charAt(x) == 'a') {
			return new OurCable(rigOf.get(x).cableAt(x, Z));
		}
		Foreign f = foreign.get(x);
		return f == null ? null : f.side(x, dir);
	}

	/** The neighbour of {@code x} toward {@code dir}, through its face looking back at {@code x}. */
	private Side neighbour(int x, int dir) {
		return sideAt(x + dir, -dir);
	}

	/** Lets a rig hold a foreign face before the block behind it exists. */
	private final class LazySide implements Side {
		private final int x;
		private final int dir;

		LazySide(int x, int dir) {
			this.x = x;
			this.dir = dir;
		}

		private Side live() {
			return foreign.get(x).side(x, dir);
		}

		@Override
		public long simInsert(long max) {
			return live().simInsert(max);
		}

		@Override
		public long simExtract(long max) {
			return live().simExtract(max);
		}

		@Override
		public long insert(long maxAmount, Txn txn) {
			return live().insert(maxAmount, txn);
		}

		@Override
		public long extract(long maxAmount, Txn txn) {
			return live().extract(maxAmount, txn);
		}

		@Override
		public boolean supportsInsertion() {
			return live().supportsInsertion();
		}

		@Override
		public boolean supportsExtraction() {
			return live().supportsExtraction();
		}

		@Override
		public long getAmount() {
			return live().getAmount();
		}

		@Override
		public long getCapacity() {
			return live().getCapacity();
		}
	}

	/** Our copper cable's segment buffer as a foreign block reaches it, with the role {@link #ourFace} gives it. */
	private final class OurCable implements Side {
		private final EnergyBuffer buf;

		OurCable(EnergyBuffer buf) {
			this.buf = buf;
		}

		@Override
		public long simInsert(long max) {
			return ourFace.in ? Math.min(buf.maxInsert, Math.min(max, buf.getCapacity() - buf.getAmount())) : 0;
		}

		@Override
		public long simExtract(long max) {
			return ourFace.out ? Math.min(buf.maxExtract, Math.min(max, buf.getAmount())) : 0;
		}

		@Override
		public long insert(long maxAmount, Txn txn) {
			return ourFace.in ? buf.insert(maxAmount, TXN) : 0;
		}

		@Override
		public long extract(long maxAmount, Txn txn) {
			return ourFace.out ? buf.extract(maxAmount, TXN) : 0;
		}

		@Override
		public boolean supportsInsertion() {
			return ourFace.in;
		}

		@Override
		public boolean supportsExtraction() {
			return ourFace.out;
		}
	}

	/** A foreign block: its faces and its own tick. */
	private abstract static class Foreign {
		abstract Side side(int x, int dir);

		void tick() {
		}

		long held() {
			throw new UnsupportedOperationException();
		}
	}

	/** A side that only gives, without end, at most {@code perCall} a call. */
	private static Side giver(long perCall) {
		return new Side() {
			@Override
			public long simInsert(long max) {
				return 0;
			}

			@Override
			public long simExtract(long max) {
				return Math.min(max, perCall);
			}

			@Override
			public long insert(long maxAmount, Txn txn) {
				return 0;
			}

			@Override
			public long extract(long maxAmount, Txn txn) {
				return Math.min(maxAmount, perCall);
			}

			@Override
			public boolean supportsInsertion() {
				return false;
			}

			@Override
			public boolean supportsExtraction() {
				return true;
			}

			@Override
			public long getAmount() {
				return perCall; // it holds what it gives: the network reads a producer's amount, not only its dry run
			}
		};
	}

	/**
	 * Tech Reborn's creative solar panel: gives on every face and takes nothing. Its own push into the cable is left
	 * out: it ticks last, after the cable network has pulled from it, which closes that face for the rest of the tick.
	 */
	private static final class Panel extends Foreign {
		@Override
		Side side(int x, int dir) {
			return giver(MAX / 4);
		}
	}

	/** Energized Power's creative battery box: fills every neighbour that takes, each tick; gives without end. */
	private final class CreativeBox extends Foreign {
		private final int x;

		CreativeBox(int x) {
			this.x = x;
		}

		@Override
		Side side(int at, int dir) {
			return giver(MAX / 4);
		}

		@Override
		void tick() {
			for (int dir : new int[] {-1, 1}) { // Direction.values(): west before east
				Side n = neighbour(x, dir);
				if (n != null && n.supportsInsertion()) {
					n.insert(n.simInsert(MAX / 4), TXN);
				}
			}
		}
	}

	/** A Tech Reborn LV storage unit, output face away from the row: takes 32 a call on its other faces, gives none. */
	private static final class StorageUnit extends Foreign {
		final EnergyBuffer buf = new EnergyBuffer(40_000, 32, 0, () -> { });

		@Override
		Side side(int x, int dir) {
			return new Side() {
				@Override
				public long simInsert(long max) {
					return Math.min(buf.maxInsert, Math.min(max, buf.getCapacity() - buf.getAmount()));
				}

				@Override
				public long simExtract(long max) {
					return 0;
				}

				@Override
				public long insert(long maxAmount, Txn txn) {
					return buf.insert(maxAmount, TXN);
				}

				@Override
				public long extract(long maxAmount, Txn txn) {
					return 0;
				}

				@Override
				public boolean supportsInsertion() {
					return true;
				}

				@Override
				public boolean supportsExtraction() {
					return false;
				}
			};
		}

		@Override
		long held() {
			return buf.getAmount();
		}
	}

	/**
	 * Energized Power's battery box: 65 536, 2048 a call each way, the same storage on every face. Each tick it offers
	 * what it holds to every neighbour that takes, splitting it evenly ({@code pushEnergyToOutputs}).
	 */
	private final class Battery extends Foreign {
		private final int x;
		final EnergyBuffer buf = new EnergyBuffer(65_536, 2048, 2048, () -> { });

		Battery(int x) {
			this.x = x;
		}

		@Override
		Side side(int at, int dir) {
			return bufferSide(buf);
		}

		@Override
		void tick() {
			List<Side> takers = new ArrayList<>();
			List<Long> takes = new ArrayList<>();
			long sum = 0;
			for (int dir : new int[] {-1, 1}) {
				Side n = neighbour(x, dir);
				if (n != null && n.supportsInsertion()) {
					long r = n.simInsert(Math.min(buf.maxExtract, buf.getAmount()));
					if (r > 0) {
						takers.add(n);
						takes.add(r);
						sum += r;
					}
				}
			}
			long left = Math.min(buf.maxExtract, Math.min(buf.getAmount(), sum));
			buf.drainInternal(left);
			long[] share = evenSplit(left, takes);
			for (int i = 0; i < takers.size(); i++) {
				if (share[i] > 0) {
					takers.get(i).insert(share[i], TXN);
				}
			}
		}

		@Override
		long held() {
			return buf.getAmount();
		}
	}

	private static Side bufferSide(EnergyBuffer buf) {
		return new Side() {
			@Override
			public long simInsert(long max) {
				return Math.min(buf.maxInsert, Math.min(max, buf.getCapacity() - buf.getAmount()));
			}

			@Override
			public long simExtract(long max) {
				return Math.min(buf.maxExtract, Math.min(max, buf.getAmount()));
			}

			@Override
			public long insert(long maxAmount, Txn txn) {
				return buf.insert(maxAmount, TXN);
			}

			@Override
			public long extract(long maxAmount, Txn txn) {
				return buf.extract(maxAmount, TXN);
			}

			@Override
			public boolean supportsInsertion() {
				return buf.maxInsert > 0;
			}

			@Override
			public boolean supportsExtraction() {
				return buf.maxExtract > 0;
			}

			@Override
			public long getAmount() {
				return buf.getAmount();
			}
		};
	}

	/**
	 * Energized Power's even split: {@code total} spread over the takers, each at most what it said it takes, the
	 * divisor shrinking when the share rounds to zero. The caller never asks for more than they take together.
	 */
	private static long[] evenSplit(long total, List<Long> takes) {
		long[] out = new long[takes.size()];
		long left = total;
		int divisor = takes.size();
		while (left > 0) {
			long per = left / divisor;
			if (per == 0) {
				divisor = Math.max(1, divisor - 1);
				per = left / divisor;
			}
			for (int i = 0; i < takes.size() && left > 0; i++) {
				long add = Math.min(takes.get(i) - out[i], Math.min(per, left));
				out[i] += add;
				left -= add;
			}
		}
		return out;
	}

	/**
	 * A run of Tech Reborn copper cables of one rate: one network, 128 a call, 512 a cable. It ticks once a tick, on
	 * the first of its cables to tick ({@code CableTickManager}): every non-cable neighbour with a storage is a
	 * target; it pulls from all targets, then pushes what the network holds out into all targets, each time least-first
	 * by a dry run of everything and an even share of what is left, at most 128 each; and a face that moved anything
	 * is closed to every other caller — both ways — until the network's next tick ({@code blockedSides}).
	 */
	private final class TrNetwork extends Foreign {
		private static final long RATE = 128;
		final int x0;
		final int x1;
		private final long[] amount;
		private final boolean[][] blocked;

		TrNetwork(int x0, int x1) {
			this.x0 = x0;
			this.x1 = x1;
			this.amount = new long[x1 - x0 + 1];
			this.blocked = new boolean[x1 - x0 + 1][2];
		}

		private boolean open(int x, int dir) {
			return !blocked[x - x0][dir < 0 ? 0 : 1];
		}

		@Override
		Side side(int x, int dir) {
			int i = x - x0;
			return new Side() {
				@Override
				public long simInsert(long max) {
					return open(x, dir) ? Math.min(RATE, Math.min(max, RATE * 4 - amount[i])) : 0;
				}

				@Override
				public long simExtract(long max) {
					return open(x, dir) ? Math.min(RATE, Math.min(max, amount[i])) : 0;
				}

				@Override
				public long insert(long maxAmount, Txn txn) {
					long put = simInsert(maxAmount);
					amount[i] += put;
					return put;
				}

				@Override
				public long extract(long maxAmount, Txn txn) {
					long got = simExtract(maxAmount);
					amount[i] -= got;
					return got;
				}

				@Override
				public boolean supportsInsertion() {
					return open(x, dir);
				}

				@Override
				public boolean supportsExtraction() {
					return open(x, dir);
				}

				@Override
				public long getAmount() {
					return amount[i];
				}
			};
		}

		private record Target(int cable, int dir, Side side) {
		}

		@Override
		void tick() {
			List<Target> targets = new ArrayList<>();
			for (int x = x1; x >= x0; x--) { // breadth first from the cable that ticked first, the last one
				for (int dir : new int[] {-1, 1}) {
					int n = x + dir;
					if (n >= x0 && n <= x1) {
						continue; // a cable of the same rate is the network, not a target
					}
					Side s = neighbour(x, dir);
					if (s != null) {
						targets.add(new Target(x, dir, s));
					}
				}
			}
			for (boolean[] b : blocked) {
				b[0] = false;
				b[1] = false;
			}
			long held = 0;
			for (long a : amount) {
				held += a;
			}
			long capacity = RATE * 4 * amount.length;
			held += dispatch(targets, true, capacity - held);
			held -= dispatch(targets, false, held);
			int left = amount.length;
			for (int i = 0; i < amount.length; i++) {
				amount[i] = held / left;
				held -= amount[i];
				left--;
			}
		}

		private long dispatch(List<Target> targets, boolean pull, long max) {
			List<Target> sorted = new ArrayList<>(targets);
			Map<Target, Long> dry = new LinkedHashMap<>();
			for (Target t : sorted) {
				dry.put(t, pull ? t.side().simExtract(MAX) : t.side().simInsert(MAX));
			}
			Collections.shuffle(sorted, shuffle);
			sorted.sort(Comparator.comparingLong(dry::get));
			long moved = 0;
			for (int i = 0; i < sorted.size(); i++) {
				Target t = sorted.get(i);
				long each = Math.min((max - moved) / (sorted.size() - i), RATE);
				long got = pull ? t.side().extract(each, TXN) : t.side().insert(each, TXN);
				if (got > 0) {
					moved += got;
					blocked[t.cable() - x0][t.dir() < 0 ? 0 : 1] = true;
				}
			}
			return moved;
		}
	}

	/**
	 * A run of Energized Power copper cables: one network, 1024 a cable, in the server's mode {@code BOTH} (push and
	 * pull). From outside a cable only takes ({@code maxExtract = 0}). Each cable files its neighbours once, on its
	 * first tick, by {@code supports*}: a producer if it gives, a consumer if it takes. The network ticks once a tick:
	 * it moves at most 1024 from what its cables hold and what its producers give (pulled evenly) into its consumers
	 * (shared evenly), each capped by its own dry run.
	 */
	private final class EpNetwork extends Foreign {
		private static final long RATE = 1024;
		final int x0;
		final int x1;
		private final EnergyBuffer[] cables;
		private final boolean[] loaded;
		private final List<List<Side>> producers = new ArrayList<>();
		private final List<List<Side>> consumers = new ArrayList<>();

		EpNetwork(int x0, int x1) {
			this.x0 = x0;
			this.x1 = x1;
			this.cables = new EnergyBuffer[x1 - x0 + 1];
			this.loaded = new boolean[cables.length];
			for (int i = 0; i < cables.length; i++) {
				cables[i] = new EnergyBuffer(RATE, RATE, RATE, () -> { });
				producers.add(new ArrayList<>());
				consumers.add(new ArrayList<>());
			}
		}

		@Override
		Side side(int x, int dir) {
			EnergyBuffer buf = cables[x - x0];
			Side whole = bufferSide(buf);
			return new Side() {
				@Override
				public long simInsert(long max) {
					return whole.simInsert(max);
				}

				@Override
				public long simExtract(long max) {
					return 0;
				}

				@Override
				public long insert(long maxAmount, Txn txn) {
					return whole.insert(maxAmount, txn);
				}

				@Override
				public long extract(long maxAmount, Txn txn) {
					return 0;
				}

				@Override
				public boolean supportsInsertion() {
					return true;
				}

				@Override
				public boolean supportsExtraction() {
					return false;
				}
			};
		}

		@Override
		void tick() {
			for (int x = x1; x >= x0; x--) {
				int i = x - x0;
				if (!loaded[i]) { // each cable files its neighbours on its own first tick
					loaded[i] = true;
					for (int dir : new int[] {-1, 1}) {
						int n = x + dir;
						Side s = n >= x0 && n <= x1 ? null : neighbour(x, dir);
						if (s != null && s.supportsExtraction()) {
							producers.get(i).add(s);
						}
						if (s != null && s.supportsInsertion()) {
							consumers.get(i).add(s);
						}
					}
				}
			}
			List<EnergyBuffer> stored = new ArrayList<>();
			List<Side> prod = new ArrayList<>();
			List<Side> cons = new ArrayList<>();
			for (int i = cables.length - 1; i >= 0; i--) {
				if (cables[i].getAmount() > 0) {
					stored.add(cables[i]);
				}
				prod.addAll(producers.get(i));
				cons.addAll(consumers.get(i));
			}
			long production = 0;
			for (EnergyBuffer b : stored) {
				production += b.getAmount();
			}
			List<Side> givers = new ArrayList<>();
			List<Long> gives = new ArrayList<>();
			for (Side p : prod) {
				long g = p.simExtract(RATE);
				if (g > 0) {
					givers.add(p);
					gives.add(g);
					production += g;
				}
			}
			if (production <= 0) {
				return;
			}
			List<Side> takers = new ArrayList<>();
			List<Long> takes = new ArrayList<>();
			long consumption = 0;
			for (Side c : cons) {
				long t = c.simInsert(RATE);
				if (t > 0) {
					takers.add(c);
					takes.add(t);
					consumption += t;
				}
			}
			if (consumption <= 0) {
				return;
			}
			long transfer = Math.min(Math.min(RATE, production), consumption);
			long internal = 0;
			for (EnergyBuffer b : stored) {
				long got = Math.min(b.getAmount(), transfer - internal);
				b.drainInternal(got);
				internal += got;
			}
			long[] pulled = evenSplit(transfer - internal, gives);
			for (int i = 0; i < givers.size(); i++) {
				if (pulled[i] > 0) {
					givers.get(i).extract(pulled[i], TXN);
				}
			}
			long[] pushed = evenSplit(transfer, takes);
			for (int i = 0; i < takers.size(); i++) {
				if (pushed[i] > 0) {
					takers.get(i).insert(pushed[i], TXN);
				}
			}
		}
	}
}
