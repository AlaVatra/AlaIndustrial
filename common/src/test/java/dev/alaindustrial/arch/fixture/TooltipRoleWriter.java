package dev.alaindustrial.arch.fixture;

/** Clean twin of {@link TooltipColourWriter} (MOD-958): its tooltip body names a role, never the enum. */
public final class TooltipRoleWriter {

	Object appendHoverText() {
		return "ENERGY";
	}
}
