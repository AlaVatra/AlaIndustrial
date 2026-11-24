package dev.alaindustrial.block;

import net.minecraft.util.StringRepresentable;

/**
 * What an escalator section and its drive show (MOD-969): the arrows along the steps and the drive's light.
 *
 * <p>Three states, not a {@code lit} boolean, because "ready" is a state of its own: a built, powered band that
 * sleeps because nobody rides it. Its arrows are cyan and still; a stopped band's are red, a moving band's run.
 */
public enum EscalatorMode implements StringRepresentable {
	/** Not built, no energy, or stopped by redstone: red arrows, the steps do not move. */
	OFF("off"),
	/** Built and powered, nobody on it: the drive sleeps, cyan arrows stand still. */
	READY("ready"),
	/** The band moves and carries whoever stands on it. */
	RUNNING("running");

	private final String serializedName;

	EscalatorMode(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
