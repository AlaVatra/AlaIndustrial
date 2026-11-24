package dev.alaindustrial.core.escalator;

import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.Section;

/**
 * Knobs of the escalator (MOD-969), in the holder of their own subsystem (ADR-034); {@code Config.REGISTRY} scans
 * this class next to {@code Config}. The numbers are the task research's, accepted by the owner on 2026-11-22, and
 * are mirrored in {@code docs/PERFORMANCE.md}.
 *
 * <p>The band speeds are client-visible on purpose: the player's own client moves the player along the band, so it
 * must use the server's speed, read through {@code ServerBalance}.
 */
public final class EscalatorConfig {

	private EscalatorConfig() {
	}

	/** Band speed of the LV drive along the run, blocks per tick horizontally (the slope is 1:1). */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 0.0, exclusive = true,
			doc = "Escalator band speed with an LV drive, blocks per tick along the run (as much again vertically).")
	public static double escalatorSpeedLv = 0.10;
	/** Band speed of the MV drive. */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 0.0, exclusive = true,
			doc = "Escalator band speed with an MV drive, blocks per tick along the run.")
	public static double escalatorSpeedMv = 0.16;

	/** Longest chain an LV drive pulls, in sections. */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 1,
			doc = "Most escalator sections an LV drive can run; a longer chain does not start.")
	public static int escalatorMaxSectionsLv = 8;
	/** Longest chain an MV drive pulls. */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 1,
			doc = "Most escalator sections an MV drive can run.")
	public static int escalatorMaxSectionsMv = 16;

	/** Fixed draw of a running LV drive, EU per tick, before the per-section part. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Escalator LV drive draw while the band moves, EU per tick, plus the per-section part.")
	public static int escalatorEuPerTickLv = 1;
	/** Fixed draw of a running MV drive. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Escalator MV drive draw while the band moves, EU per tick, plus the per-section part.")
	public static int escalatorEuPerTickMv = 2;
	/** Draw of every section while the band moves, in thousandths of an EU per tick (500 = 0.5 EU/t). */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Escalator draw per section while the band moves, in thousandths of an EU per tick (500 = 0.5 EU/t).")
	public static int escalatorMilliEuPerSection = 500;

	/** Internal buffer of the LV drive. */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 1,
			doc = "Escalator LV drive internal EU buffer. Applies to newly placed blocks.")
	public static int escalatorBufferLv = 2000;
	/** Internal buffer of the MV drive. */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 1,
			doc = "Escalator MV drive internal EU buffer. Applies to newly placed blocks.")
	public static int escalatorBufferMv = 8000;

	/** How long an empty band keeps moving before the drive sleeps, ticks. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Ticks an empty escalator band keeps moving before the drive goes to sleep and stops drawing.")
	public static int escalatorIdleTicks = 100;
	/** Ticks between two checks of the chain while nothing wakes the drive. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Ticks between two checks of the escalator chain (placing or removing a section checks at once).")
	public static int escalatorScanIntervalTicks = 40;
}
