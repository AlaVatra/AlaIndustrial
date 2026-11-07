package dev.alaindustrial.core.tooltip;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The style-09b look of the stat keys the blocks have always declared by name (MOD-958): which role a
 * {@code MachineTooltipSpec.stat("buffer", ...)} line takes, which copper unit follows its value, and whether its
 * value is a tick count the tooltip shows in seconds. One table, so the fifty-odd blocks that declare these figures
 * change look without a line of their own, and the lang value of each key here is only the label and the value
 * ({@code "Buffer %s"}), the unit coming from {@link #unit}.
 *
 * <p>A key not in the table keeps the old look until its block is migrated. Minecraft-free.
 */
public final class StatStyles {

	/** How one stat key is drawn. */
	public record Style(TooltipRole role, @Nullable String unit, boolean ticksAsSeconds) {
	}

	private static final Style ENERGY = new Style(TooltipRole.ENERGY, TooltipUnits.EU, false);
	private static final Style ENERGY_RATE = new Style(TooltipRole.ENERGY, TooltipUnits.EU_PER_TICK, false);

	private static final Map<String, Style> STYLES = Map.ofEntries(
			Map.entry("buffer", ENERGY),
			Map.entry("capacity", ENERGY),
			Map.entry("energy_per_op", ENERGY),
			Map.entry("energy_input", ENERGY_RATE),
			Map.entry("energy_output", ENERGY_RATE),
			Map.entry("energy_output_day", ENERGY_RATE),
			Map.entry("energy_output_max", ENERGY_RATE),
			Map.entry("energy_output_fuel", ENERGY_RATE),
			Map.entry("energy_output_lava", ENERGY_RATE),
			Map.entry("energy_output_day_only", ENERGY_RATE),
			Map.entry("energy_output_night_only", ENERGY_RATE),
			Map.entry("solar_day", ENERGY_RATE),
			Map.entry("solar_night", ENERGY_RATE),
			Map.entry("duration_ticks", new Style(TooltipRole.TIME, TooltipUnits.SECONDS, true)),
			Map.entry("range", new Style(TooltipRole.RANGE, TooltipUnits.BLOCKS, false)),
			Map.entry("garden_drone_range", new Style(TooltipRole.RANGE, TooltipUnits.BLOCKS, false)),
			Map.entry("apiary_station_range", new Style(TooltipRole.RANGE, TooltipUnits.BLOCKS, false)),
			Map.entry("tank_mb", new Style(TooltipRole.FLUID, TooltipUnits.MILLIBUCKETS, false)),
			// Figures whose unit is part of a longer phrase ("EU/bucket", "%/block"): the role and its icon, the
			// phrase kept in the label.
			Map.entry("cable_loss", new Style(TooltipRole.ENERGY, null, false)),
			Map.entry("pump_cost", new Style(TooltipRole.ENERGY, null, false)),
			Map.entry("garden_drone_action_cost", new Style(TooltipRole.ENERGY, null, false)),
			Map.entry("apiary_drone_flight_cost", new Style(TooltipRole.ENERGY, null, false)),
			Map.entry("piezo_press", new Style(TooltipRole.ENERGY, null, false)),
			Map.entry("geo_burn_ticks", new Style(TooltipRole.TIME, null, false)));

	private StatStyles() {
	}

	/** The style of stat key {@code key} (without the {@code tooltip.alaindustrial.} prefix), or null. */
	public static @Nullable Style of(String key) {
		return STYLES.get(key);
	}

	/** {@code value} as the line shows it: a tick count in seconds when the style says so, else itself. */
	public static Object shown(Style style, Object value) {
		if (style.ticksAsSeconds() && value instanceof Number ticks) {
			return TooltipUnits.seconds(ticks.longValue());
		}
		return value;
	}
}
