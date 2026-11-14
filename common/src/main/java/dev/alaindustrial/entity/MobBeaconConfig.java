package dev.alaindustrial.entity;

import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.Section;

/**
 * Knobs of the Mob Beacon (MOD-968), in the holder of their own subsystem (ADR-034). The json key of each knob is
 * its field name; {@code Config.REGISTRY} scans this class next to {@code Config}. The numbers are the owner's of
 * 2026-11-13 (radius 16, 32, 48) and the task's (upkeep half the repeller's of the same tier), mirrored in
 * {@code docs/PERFORMANCE.md}.
 *
 * <p>The glow's length and its renewal threshold are not knobs: {@link GlowRenewal} derives both from
 * {@link #mobBeaconScanIntervalTicks}, so a threshold shorter than the scan interval cannot be configured.
 */
public final class MobBeaconConfig {

	private MobBeaconConfig() {
	}

	/** Zone radius of the LV beacon in blocks (a cube around the block). */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Small Mob Beacon zone radius in blocks (a cube around the block).")
	public static int mobBeaconRange = 16;
	/** Zone radius of the MV beacon. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Medium Mob Beacon zone radius in blocks.")
	public static int mobBeaconRangeMv = 32;
	/** Zone radius of the HV beacon. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Large Mob Beacon zone radius in blocks.")
	public static int mobBeaconRangeHv = 48;

	/** Upkeep of the LV beacon while it runs: half the LV repeller's. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Small Mob Beacon upkeep in EU per tick while enabled (constant drain, mobs or not).")
	public static int mobBeaconEuPerTick = 4;
	/** Upkeep of the MV beacon: x4 of LV, the voltage ladder's step. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Medium Mob Beacon upkeep in EU per tick.")
	public static int mobBeaconEuPerTickMv = 16;
	/** Upkeep of the HV beacon: x2 of MV, as the repeller's. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Large Mob Beacon upkeep in EU per tick.")
	public static int mobBeaconEuPerTickHv = 32;

	/** Internal EU buffer of the LV beacon. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Small Mob Beacon internal EU buffer.")
	public static int mobBeaconBuffer = 2000;
	/** Internal EU buffer of the MV beacon. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Medium Mob Beacon internal EU buffer.")
	public static int mobBeaconBufferMv = 8000;
	/** Internal EU buffer of the HV beacon. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Large Mob Beacon internal EU buffer.")
	public static int mobBeaconBufferHv = 32000;

	/** Ticks between two searches of the zone; the glow lasts four of them and is renewed at two. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between Mob Beacon zone searches; the glow lasts 4 intervals and is renewed at 2 or less"
					+ " (the upkeep drain still applies every tick).")
	public static int mobBeaconScanIntervalTicks = 20;
}
