package dev.alaindustrial.block.entity;

import java.util.Locale;

/**
 * The state of one jar on the station's map (MOD-923), for the list on the station's screen: whether the station can
 * serve it and, if so, whether it is waiting, full or being served.
 *
 * <p>Ordinals travel over a sync channel: append, never reorder.
 */
public enum ApiaryEntryStatus {
	/** No row: the map has fewer entries. */
	NONE,
	/** Served, the hive is not full yet. */
	WAITING,
	/** Served, the hive is full: a drone will go. */
	FULL,
	/** A drone of this station is on its way to it, docked at it or flying back from it. */
	DRONE_OUT,
	/** Further than the station's range. */
	TOO_FAR,
	/** In an unloaded chunk. */
	UNLOADED,
	/** Written in another dimension. */
	OTHER_DIMENSION,
	/** Its cell holds no jar on a hive any more. */
	NO_JAR;

	private static final ApiaryEntryStatus[] VALUES = values();

	public String translationKey() {
		return "gui.alaindustrial.apiary_drone_station.entry." + name().toLowerCase(Locale.ROOT);
	}

	public static ApiaryEntryStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
	}

	/** Whether the station may send a drone to this jar at all (whatever the hive's level). */
	public boolean served() {
		return this == WAITING || this == FULL || this == DRONE_OUT;
	}
}
