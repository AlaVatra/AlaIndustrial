package dev.alaindustrial.core.elevator;

/**
 * What one elevator trip costs and what it gives back (MOD-769). The whole trip is paid when the cabin sets off,
 * the way a teleporter jump is: a cabin that set off never stops in the shaft for want of power.
 *
 * <ul>
 *   <li><b>Up:</b> the launch, plus the climb: every block of height costs the cabin's per-block price and the same
 *       again per passenger.</li>
 *   <li><b>Down:</b> the launch and the brake: a share of what the same climb would cost.</li>
 * </ul>
 *
 * <p>There is no counterweight: the glass shaft (owner's choice of variant A, 2026-11-22) has no channel for one, and
 * a fourth kind of block for a few hundred EU a ride was not worth the harder build.
 *
 * <p>Minecraft-free: the prices come in as a {@link Tariff}, which the block entity builds from the config knobs.
 */
public final class ElevatorFare {

	/**
	 * The prices of the elevator.
	 *
	 * @param launch       EU every trip pays to set off
	 * @param perBlock     EU per block of height for the empty cabin
	 * @param perRider     EU per block of height for every passenger
	 * @param descentShare share of the climb a descent pays (the brake)
	 */
	public record Tariff(int launch, int perBlock, int perRider, double descentShare) {
	}

	private ElevatorFare() {
	}

	/** EU of the climb over {@code blocks} with {@code riders} passengers: the base of the descent's share. */
	public static long climb(Tariff tariff, int blocks, int riders) {
		return (long) Math.max(0, blocks) * (tariff.perBlock() + (long) tariff.perRider() * Math.max(0, riders));
	}

	/** EU paid up front for a trip of {@code blocks} up or down. */
	public static long fare(Tariff tariff, int blocks, boolean up, int riders) {
		long climb = climb(tariff, blocks, riders);
		return tariff.launch() + (up ? climb : (long) Math.ceil(climb * tariff.descentShare()));
	}
}
