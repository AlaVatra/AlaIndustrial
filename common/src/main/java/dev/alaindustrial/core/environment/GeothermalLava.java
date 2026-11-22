package dev.alaindustrial.core.environment;

import dev.alaindustrial.core.fluid.FluidAmounts;

/**
 * The geothermal generator's lava as one volume (MOD-998): the intake tank in mB plus the burn reserve in
 * ticks, folded into millibuckets for the dropped item and unfolded again when it is placed.
 *
 * <p>Both directions round DOWN, so a break-and-place cycle can never create lava; with the default burn of
 * 1000 ticks per bucket a tick is exactly one mB and the cycle loses nothing either. Minecraft-free on purpose:
 * the arithmetic is pinned on the L1 lane ({@code GeothermalLavaTest}).
 */
public final class GeothermalLava {
	private GeothermalLava() {
	}

	/** The burn reserve in mB: {@code ticks × 1000 / burnTicks}, rounded down. */
	public static long reserveMb(int lavaTicks, int burnTicks) {
		return (long) Math.max(0, lavaTicks) * FluidAmounts.BUCKET / Math.max(1, burnTicks);
	}

	/** All the lava a generator holds, in mB: the tank plus the burn reserve. */
	public static long totalMb(long tankMb, int lavaTicks, int burnTicks) {
		return Math.max(0L, tankMb) + reserveMb(lavaTicks, burnTicks);
	}

	/**
	 * Where a stored volume goes back in: the burn reserve first, up to {@code reserveCapacityTicks}, the rest
	 * into the tank, up to {@code tankCapacityMb}; anything over that is dropped (the portable tank's rule).
	 *
	 * @param lavaTicks the burn reserve to set
	 * @param tankMb    the tank amount to set
	 */
	public record Split(int lavaTicks, long tankMb) {
	}

	/** Unfold {@code storedMb} into the reserve and the tank — see {@link Split}. */
	public static Split split(long storedMb, int reserveCapacityTicks, long tankCapacityMb, int burnTicks) {
		long mb = Math.max(0L, storedMb);
		int burn = Math.max(1, burnTicks);
		long reserveCapMb = reserveMb(reserveCapacityTicks, burn);
		long toReserve = Math.min(mb, reserveCapMb);
		int ticks = (int) Math.min(reserveCapacityTicks, toReserve * burn / FluidAmounts.BUCKET);
		long tank = Math.min(mb - toReserve, Math.max(0L, tankCapacityMb));
		return new Split(ticks, tank);
	}
}
