package dev.alaindustrial.core.energy;

/**
 * When a network probes again the roles it filed from a dry run ({@link EnergyPort#roleIsProvisional()}, MOD-1026).
 *
 * <p>A foreign NeoForge energy block is filed as producer, consumer or both by a single try, and the network keeps
 * that filing until its cables change. A try taken at the wrong moment (a battery that has already given its
 * per-tick limit, a generator switched off) would then pin a wrong role for good: a battery read as a pure consumer
 * never feeds the line again, and a network whose only source it was falls asleep and never wakes. This clock arms
 * itself when the last endpoint scan found a provisional role and answers "due" once {@link #RECHECK_TICKS} ticks
 * have passed, so the scan runs again. The price is one endpoint scan per network per {@link #RECHECK_TICKS} ticks,
 * and only on a network that touches such a block; a pure consumer read again is still a pure consumer, so it still
 * does not keep the network awake between the scans.
 *
 * <p>Minecraft-free on purpose, so its timing is pinned by an L1 test.
 */
final class ProvisionalRoleClock {
	/** Ticks between two scans of a network that holds a provisional role: five seconds. */
	static final int RECHECK_TICKS = 100;

	private static final long DISARMED = Long.MIN_VALUE;

	private long dueAt = DISARMED;

	/**
	 * Whether the roles found by the last endpoint scan should be probed again now.
	 *
	 * @param provisional the last scan found a provisional role and the endpoints are not already due for a rescan
	 * @param now the game time
	 * @return {@code true} once, {@link #RECHECK_TICKS} ticks after the clock armed; the clock then disarms and arms
	 *         again on the next call that still sees a provisional role (after the rescan)
	 */
	boolean due(boolean provisional, long now) {
		if (!provisional) {
			dueAt = DISARMED;
			return false;
		}
		if (dueAt == DISARMED) {
			dueAt = now + RECHECK_TICKS;
			return false;
		}
		if (now < dueAt) {
			return false;
		}
		dueAt = DISARMED;
		return true;
	}
}
