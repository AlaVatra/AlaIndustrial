package dev.alaindustrial.advancement;

import org.jspecify.annotations.Nullable;

/**
 * The steps the geothermal advancement chain marks (MOD-1001), and the numbers that define them.
 *
 * <p>Minecraft-free on purpose, like {@link ReactorMilestone}: the name-to-constant mapping the datapack depends on
 * and the two thresholds are pinned on the L1 lane. The trigger that carries these values is
 * {@link GeothermalMilestoneTrigger}.
 *
 * <p>The serialized names are datapack surface: they appear verbatim in
 * {@code data/alaindustrial/advancement/*.json}, so renaming one breaks every world that has not earned that
 * advancement yet.
 */
public enum GeothermalMilestone {
	/** A geothermal generator owned by the player burnt lava into EU for the first time. */
	FIRST_LAVA("first_lava"),
	/** The player's geothermal generators together put {@link #LAVA_MILLION_EU} EU into their buffers. */
	LAVA_MILLION("lava_million"),
	/** {@link #STATION_SIZE} geothermal generators of the player ran at the same time ({@link GeothermalStation}). */
	STATION("station");

	/** EU from lava the second step asks for: the owner's decision of 2026-11-19, "the first million EU from lava". */
	public static final long LAVA_MILLION_EU = 1_000_000L;

	/** Generators that must run at the same time for the third step. */
	public static final int STATION_SIZE = 5;

	/**
	 * How recently a generator must have burnt lava to count as running, in game ticks: one second. A generator
	 * feeding a line that draws less than its output pauses between burn ticks while its buffer is full, so "burnt
	 * on this very tick" would miss a station that is plainly working; a second covers that and still means now.
	 */
	public static final int STATION_WINDOW_TICKS = 20;

	private final String id;

	GeothermalMilestone(String id) {
		this.id = id;
	}

	/** The name written in the datapack. */
	public String id() {
		return id;
	}

	/** The step with this datapack name, or {@code null} for a name no version ever wrote. */
	@Nullable
	public static GeothermalMilestone byId(String id) {
		for (GeothermalMilestone milestone : values()) {
			if (milestone.id.equals(id)) {
				return milestone;
			}
		}
		return null;
	}
}
