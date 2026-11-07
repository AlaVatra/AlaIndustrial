package dev.alaindustrial.core.apiary;

import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.Section;

/**
 * Knobs of the apiary station and its drones (MOD-923), in the holder of their own subsystem (ADR-034). The json
 * key of each knob is its field name; {@code Config.REGISTRY} scans this class next to {@code Config}. The numbers
 * are the owner's of 2026-11-07 and are mirrored in {@code docs/PERFORMANCE.md}.
 *
 * <p>Minecraft-free, next to {@link ApiaryRules}, so the lowest reader can import it.
 */
public final class ApiaryConfig {

	private ApiaryConfig() {
	}

	/** EU buffer of the station: room for the reserve of four far flights and then some. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "EU buffer of the apiary station.")
	public static int apiaryStationBuffer = 4000;

	/** The fixed part of a flight's price. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 0,
			doc = "EU an apiary drone's flight costs before distance; charged only when it docks at a full hive.")
	public static int apiaryDroneLaunchEu = 160;

	/** The distance part of a flight's price: 15 blocks make 220 EU in all. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 0,
			doc = "EU an apiary drone's flight costs per block of distance to the hive attachment.")
	public static int apiaryDroneEuPerBlock = 4;

	/** How far the station sends its drones. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Blocks from the apiary station to a hive attachment it still serves.")
	public static int apiaryStationRange = 15;

	/** Honey one full hive gives: a bottle's worth. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Liquid honey in mB an apiary drone brings back from one full hive.")
	public static int apiaryHoneyPerHive = 250;

	/** The chance of a comb on top of the honey. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Chance (0..1) that an apiary drone also brings a honeycomb from a full hive"
					+ " (0 = never, 1 = always).")
	public static double apiaryCombChance = 0.10;

	/** How often an idle station looks at the hives on its map. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between two looks of an idle apiary station at the hives on its map.")
	public static int apiaryScanIntervalTicks = 20;

	/** The docking at the jar: sting down, pumping, sting up. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 5,
			doc = "Ticks an apiary drone stays docked at a hive attachment while it drains the jar.")
	public static int apiaryDockTicks = 40;

	/** The pour on the pad: the belly empties into the tank. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Ticks an apiary drone stands on its pad pouring its honey into the station's tank.")
	public static int apiaryPourTicks = 20;
}
