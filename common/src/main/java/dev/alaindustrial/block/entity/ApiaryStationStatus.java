package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.machine.StatusLine;
import java.util.Locale;

/**
 * What the apiary station is doing (MOD-923) — the screen's status line. The table of the spec, in its order: each
 * stop says why, and none of them loses honey, combs or drones.
 *
 * <p>Ordinals travel over a sync channel and are persisted: append, never reorder.
 */
public enum ApiaryStationStatus implements StatusLine {
	/** The upper half is missing. */
	NO_HIVE,
	/** No apiary map in its slot. */
	NO_MAP,
	/** Every bay is empty. */
	NO_DRONES,
	/** A full hive waits, but the buffer does not cover the flight on top of what is held for the drones out. */
	NO_ENERGY,
	/** No room in the tank for one more hive's honey. */
	TANK_FULL,
	/** No room in the comb slot for one more comb. */
	OUTPUT_FULL,
	/** No hive on the map is full. Not a fault. */
	WAITING,
	/** At least one drone is out. */
	WORKING;

	private static final ApiaryStationStatus[] VALUES = values();

	@Override
	public String translationKey() {
		return "gui.alaindustrial.apiary_drone_station.status." + name().toLowerCase(Locale.ROOT);
	}

	public static ApiaryStationStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : WAITING;
	}

	@Override
	public boolean isBlocking() {
		return this != WORKING && this != WAITING;
	}
}
