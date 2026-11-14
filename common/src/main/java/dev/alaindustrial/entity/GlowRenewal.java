package dev.alaindustrial.entity;

/**
 * When the Mob Beacon (MOD-968) gives a mob its glow, and for how long: one rule, free of Minecraft so the L1 lane can
 * hold it.
 *
 * <p><b>Both numbers follow the scan interval.</b> The glow lasts {@value #GLOW_INTERVALS} intervals and is renewed
 * once {@value #RENEW_INTERVALS} intervals or fewer are left. The threshold is above one interval, so the outline never
 * lapses between two searches; it is below the length, so the same mob is not re-given the effect on every search;
 * and a beacon that stops (no energy, a redstone signal, broken) leaves no outline after one full length.
 *
 * <p><b>Never shorter.</b> A mob glowing for longer than the threshold is left alone, and an infinite glow (a command,
 * another mod) is never touched: renewing only at or under the threshold can only lengthen what is there.
 */
public final class GlowRenewal {

	/** The glow's length, in scan intervals. */
	public static final int GLOW_INTERVALS = 4;
	/** The remaining length, in scan intervals, at or under which the glow is renewed. */
	public static final int RENEW_INTERVALS = 2;

	private GlowRenewal() {
	}

	/** Ticks of glow one renewal gives, for a scan every {@code intervalTicks}. */
	public static int glowTicks(int intervalTicks) {
		return GLOW_INTERVALS * Math.max(1, intervalTicks);
	}

	/** Remaining ticks at or under which the glow is renewed, for a scan every {@code intervalTicks}. */
	public static int renewAtTicks(int intervalTicks) {
		return RENEW_INTERVALS * Math.max(1, intervalTicks);
	}

	/**
	 * Whether a mob in the zone gets the glow on this search.
	 *
	 * @param glowing whether the mob glows already, from any source
	 * @param infinite whether that glow never ends
	 * @param remainingTicks ticks that glow has left; ignored when the mob does not glow
	 * @param intervalTicks the beacon's scan interval
	 */
	public static boolean shouldRenew(boolean glowing, boolean infinite, int remainingTicks, int intervalTicks) {
		if (!glowing) {
			return true;
		}
		return !infinite && remainingTicks <= renewAtTicks(intervalTicks);
	}
}
