package dev.alaindustrial.arch.fixture;

/**
 * Deliberate violator for {@code ArchitectureRules.itemTooltipsUseRolesNotChatFormatting} (MOD-958): a tooltip body
 * that names the formatting enum (its stand-in), as a field and through a method; outside the tooltip it may.
 */
public final class TooltipColourWriter {

	Object appendHoverText() {
		return ChatFormattingStandIn.GRAY.bold();
	}

	Object chatMessage() {
		return ChatFormattingStandIn.GRAY;
	}
}
