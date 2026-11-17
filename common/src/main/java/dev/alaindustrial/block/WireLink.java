package dev.alaindustrial.block;

import net.minecraft.util.StringRepresentable;

/**
 * What one face of a smart wire is joined to (MOD-1014).
 *
 * <p>{@link #WIRE} is decided by the wire itself from its neighbour; {@link #DEVICE} is decided only by
 * the monitor network, because whether a container is plugged in depends on the free channels of the
 * whole wall, not on the neighbour alone. A container the wall cannot serve stays {@link #NONE}.
 */
public enum WireLink implements StringRepresentable {
	/** Nothing drawn on this face: no neighbour, a foreign block, or a container left without a channel. */
	NONE("none"),
	/** Another smart wire or a monitor core: the ribbon continues. */
	WIRE("wire"),
	/** A container the wall reads through one of its channels: the plug goes into it. */
	DEVICE("device");

	private final String name;

	WireLink(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
