package dev.alaindustrial.core.apiary;

/**
 * The apiary station's arithmetic (MOD-923, slice 2): what a flight costs, how long a leg takes, how high it arcs,
 * which bay flies, when the docking steps the jar down, and what has to be held back for the drones already out.
 * Minecraft-free, so the L1 lane checks every number ({@code ApiaryRulesTest}) and the server and the renderer read the
 * same timings.
 *
 * <p>Four bays, in the fixed order north, east, south, west: an index here is a bay of the station and the order in
 * which a tie between equally near bays is broken (owner decision 2026-11-07, item 17).
 */
public final class ApiaryRules {

	/** Bays of a station: north, east, south, west. */
	public static final int BAYS = 4;

	/** Ticks of flight per block of distance, as the garden drone flies. */
	public static final int FLIGHT_TICKS_PER_BLOCK = 11;

	/** Shortest leg in ticks: a hive next door is still a flight the eye can follow. */
	public static final int MIN_LEG_TICKS = 26;

	/** The flight's arc: {@code clamp(1.5 + 0.2 * d, 1.5, 4.5)} blocks over the straight line (owner, item 13). */
	public static final double ARC_MIN = 1.5;
	public static final double ARC_MAX = 4.5;
	public static final double ARC_PER_BLOCK = 0.2;

	/** Levels a full hive's jar steps down through during one docking: 5 to 0. */
	public static final int DRAIN_STEPS = 5;

	/** How far a pad's centre sits from the block's vertical axis, in blocks (5.4 px: the owner's pick). */
	public static final double PAD_REACH = 5.4 / 16.0;

	/** Pad tops over the upper block's floor, in blocks: north and south low, east and west high. */
	public static final double PAD_TOP_LOW = 6.0 / 16.0;
	public static final double PAD_TOP_HIGH = 11.0 / 16.0;

	/** Step of each bay from the block's axis: north, east, south, west. */
	private static final int[] BAY_DX = {0, 1, 0, -1};
	private static final int[] BAY_DZ = {-1, 0, 1, 0};

	/** Tolerance of a distance comparison, so 15.0 computed as 14.999999 still counts as 15. */
	private static final double EPSILON = 1.0e-9;

	private ApiaryRules() {
	}

	/**
	 * Where bay {@code bay}'s drone stands, relative to the station's LOWER block corner, in blocks: x, the pad's top
	 * (in the upper block), z.
	 */
	public static double[] padSpot(int bay) {
		int i = Math.floorMod(bay, BAYS);
		double top = i % 2 == 0 ? PAD_TOP_LOW : PAD_TOP_HIGH;
		return new double[] {0.5 + BAY_DX[i] * PAD_REACH, 1.0 + top, 0.5 + BAY_DZ[i] * PAD_REACH};
	}

	/**
	 * The drone's heading on its pad, in degrees about the vertical: head out, away from the column, so a player
	 * walking round the station sees the drones' faces (owner, in-game round of 2026-11-07).
	 */
	public static float padYawDegrees(int bay) {
		return -90.0F * Math.floorMod(bay, BAYS);
	}

	/** EU one flight costs: the base plus the per-block price for every started block of distance. */
	public static long launchCost(int base, int perBlock, double distance) {
		return (long) base + (long) perBlock * blocksOf(distance);
	}

	/** Whole blocks of {@code distance}, any started block counted (15.0 is 15, 14.2 is 15). */
	public static int blocksOf(double distance) {
		return (int) Math.ceil(Math.max(0.0, distance) - EPSILON);
	}

	/** Ticks of one leg over {@code distance} blocks: 11 per block, never fewer than {@link #MIN_LEG_TICKS}. */
	public static int legTicks(double distance) {
		return Math.max(MIN_LEG_TICKS, (int) Math.round(FLIGHT_TICKS_PER_BLOCK * Math.max(0.0, distance)));
	}

	/** Height of the flight's arc over its straight line, in blocks. */
	public static double arcHeight(double distance) {
		return Math.clamp(ARC_MIN + ARC_PER_BLOCK * Math.max(0.0, distance), ARC_MIN, ARC_MAX);
	}

	/** Whether a jar {@code distance} blocks away is in a station's {@code range}. */
	public static boolean inRange(double distance, int range) {
		return distance <= range + EPSILON;
	}

	/**
	 * The bay that flies to a hive: the free bay nearest to it, a tie going to the earlier bay (north, east, south,
	 * west). Returns -1 when no bay is free.
	 *
	 * @param distanceSq squared distance from each bay's pad to the hive's jar
	 * @param free whether each bay holds a parked drone
	 */
	public static int chooseBay(double[] distanceSq, boolean[] free) {
		int best = -1;
		for (int i = 0; i < Math.min(distanceSq.length, free.length); i++) {
			if (free[i] && (best < 0 || distanceSq[i] < distanceSq[best] - EPSILON)) {
				best = i;
			}
		}
		return best;
	}

	/** Whether the EU not held back for the drones already out covers one more flight of {@code cost}. */
	public static boolean energyCovers(long stored, long reserved, long cost) {
		return stored - reserved >= cost;
	}

	/**
	 * Whether the tank takes the honey of one more hive on top of what the drones already out may bring back: every
	 * drone not yet poured counts as a full hive's worth.
	 */
	public static boolean tankHasRoom(long capacity, long amount, int honeyOut, long perHive) {
		return amount + (long) (honeyOut + 1) * perHive <= capacity;
	}

	/**
	 * Whether the comb slot takes one more comb on top of one for every drone still out: a drone may come back with
	 * one, so a launch never leaves a comb with nowhere to go (owner, item 17: "the comb output is full — stand").
	 */
	public static boolean combSlotHasRoom(int count, int maxStack, int combsOut) {
		return count + combsOut + 1 <= maxStack;
	}

	/** Whether a roll in [0, 1) wins a comb at {@code chance}; 0 never does, 1 always does. */
	public static boolean combWon(double chance, double roll) {
		return roll < chance;
	}

	/** Ticks the sting takes to go down into the funnel (and to come up again) in a docking of {@code dockTicks}. */
	public static int stingTicks(int dockTicks) {
		return Math.max(1, dockTicks / 5);
	}

	/**
	 * How many of the {@link #DRAIN_STEPS} the jar has stepped down after {@code elapsed} ticks of a docking of
	 * {@code dockTicks}: none while the sting goes down, then evenly over the pumping, all five by the time it comes
	 * up. The station steps the jar each time this number grows.
	 */
	public static int drainStepsDone(int elapsed, int dockTicks) {
		int sting = stingTicks(dockTicks);
		int pump = Math.max(1, dockTicks - 2 * sting);
		int into = elapsed - sting;
		if (into <= 0) {
			return 0;
		}
		return Math.min(DRAIN_STEPS, into * DRAIN_STEPS / pump);
	}

	/**
	 * How far the sting is down at {@code elapsed} ticks of a docking, 0 (tucked behind) to 1 (in the funnel): down
	 * over the first {@link #stingTicks}, held while pumping, up over the last. The renderer's pose.
	 */
	public static float stingPose(float elapsed, int dockTicks) {
		int sting = stingTicks(dockTicks);
		float down = Math.clamp(elapsed / sting, 0.0F, 1.0F);
		float up = Math.clamp((dockTicks - elapsed) / sting, 0.0F, 1.0F);
		return Math.min(down, up);
	}

	/** How full the drone's belly is at {@code elapsed} ticks of a docking: 0 until pumping, 1 once it is done. */
	public static float bellyWhileDocking(float elapsed, int dockTicks) {
		int sting = stingTicks(dockTicks);
		int pump = Math.max(1, dockTicks - 2 * sting);
		return Math.clamp((elapsed - sting) / pump, 0.0F, 1.0F);
	}
}
