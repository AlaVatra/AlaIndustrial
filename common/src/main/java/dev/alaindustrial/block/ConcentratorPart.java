package dev.alaindustrial.block;

import net.minecraft.util.StringRepresentable;

/**
 * The {@code part} values a withdrawn Concentrator Section (MOD-603, withdrawn by MOD-1031) was saved with.
 *
 * <p>Kept only so a chunk written before the withdrawal decodes: a block state is stored by its property values,
 * and a value the property no longer knows would not load. The names are frozen; nothing reads the cell geometry
 * any more, because the section turns into a machine casing the moment the world touches it
 * ({@link ConcentratorSectionBlock}).
 */
public enum ConcentratorPart implements StringRepresentable {
	LOOSE("loose"),
	CORE("core"),
	RIGHT("right"),
	BACK("back"),
	BACK_RIGHT("back_right"),
	TOP("top"),
	TOP_RIGHT("top_right"),
	TOP_BACK("top_back"),
	TOP_BACK_RIGHT("top_back_right");

	private final String serializedName;

	ConcentratorPart(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
