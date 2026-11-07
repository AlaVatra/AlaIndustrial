package dev.alaindustrial.arch.fixture;

/**
 * Stand-in for {@code net.minecraft.ChatFormatting} (MOD-958): the L1 lane has no Minecraft jar, so the negative
 * control proves {@code ArchitectureRules.nameClassIn} on this enum instead.
 */
public enum ChatFormattingStandIn {
	GRAY;

	public ChatFormattingStandIn bold() {
		return this;
	}
}
