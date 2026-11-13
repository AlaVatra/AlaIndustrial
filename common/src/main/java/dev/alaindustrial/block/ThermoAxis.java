package dev.alaindustrial.block;

import dev.alaindustrial.core.environment.ThermoelectricOutput;
import net.minecraft.util.StringRepresentable;

/**
 * What one axis of the thermoelectric generator shows on the block (MOD-811): idle, or working with its hot end
 * at the positive face (up, south, east) or at the negative one (down, north, west). The model paints the hot end
 * red and the other end blue, so a farm shows at a glance which pair broke. One property per axis, 27 states.
 */
public enum ThermoAxis implements StringRepresentable {
	/** No hot and cold pair across this axis: both faces stay neutral. */
	OFF("off"),
	/** Working, the positive face (up, south, east) is the hot one. */
	HOT_POSITIVE("hot_positive"),
	/** Working, the negative face (down, north, west) is the hot one. */
	HOT_NEGATIVE("hot_negative");

	private final String serializedName;

	ThermoAxis(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	/** The state for one of the {@code ThermoelectricOutput.AXIS_*} codes. */
	public static ThermoAxis of(int code) {
		return switch (code) {
			case ThermoelectricOutput.AXIS_HOT_POSITIVE -> HOT_POSITIVE;
			case ThermoelectricOutput.AXIS_HOT_NEGATIVE -> HOT_NEGATIVE;
			default -> OFF;
		};
	}
}
