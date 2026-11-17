package dev.alaindustrial.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/**
 * What a monitor panel stands on (MOD-1013): nothing, or a monitor core facing one of four ways.
 *
 * <p>The direction is the CORE's facing, not the panel's. The stand's base sits on the core's top, whose
 * pattern turns with the core, so the base is turned by this value while the struts up to the panel's
 * junction are turned by the panel's own facing. A base turned with the panel would sit across the
 * core's top lines whenever the two blocks face different ways.
 */
public enum MonitorStand implements StringRepresentable {
	NONE("none"),
	NORTH("north"),
	EAST("east"),
	SOUTH("south"),
	WEST("west");

	private final String name;

	MonitorStand(String name) {
		this.name = name;
	}

	/** The stand for a core facing {@code coreFacing}; a vertical direction has no stand. */
	public static MonitorStand onCoreFacing(Direction coreFacing) {
		return switch (coreFacing) {
			case NORTH -> NORTH;
			case EAST -> EAST;
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			default -> NONE;
		};
	}

	public boolean present() {
		return this != NONE;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
