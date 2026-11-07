package dev.alaindustrial.core.style;

/**
 * The mod's colours, named once (MOD-958): the screens' {@code GuiStyle} and the tooltips' roles read the same
 * tokens, so "the energy orange" is one number. Minecraft-free on purpose: the core and the L1 lane read it.
 *
 * <p>Every constant is a plain {@code 0xRRGGBB}, the form a chat component's colour takes; a GUI fill adds the
 * opaque alpha itself ({@code 0xFF000000 | AlaPalette.ENERGY}, as {@code GuiStyle} does).
 */
public final class AlaPalette {
	private AlaPalette() {
	}

	/** The energy orange: the energy bar's fill and the tooltip's energy icon. */
	public static final int ENERGY = 0xFF8A1E;
	/** The bright leading edge of the energy bar, and the key icon of a tooltip. */
	public static final int ENERGY_HI = 0xFFC15A;
	/** The copper accent: the strip under a screen's header and a tooltip's units. */
	public static final int COPPER = 0xB87333;
	/** A level bar's fill, and the tooltip's fluid icon. */
	public static final int FLUID = 0x57C7FF;
	/** A level bar's bright edge, and the tooltip's time icon. */
	public static final int FLUID_HI = 0xBFEBFF;
	/** A heat bar's fill. */
	public static final int HEAT = 0xE0582A;
	/** A heat bar's bright edge. */
	public static final int HEAT_HI = 0xFFB27A;

	/** Tooltip text: the label of a line (vanilla's {@code ChatFormatting.GRAY}). */
	public static final int TEXT_LABEL = 0xAAAAAA;
	/** Tooltip text: a value (vanilla's {@code ChatFormatting.WHITE}). */
	public static final int TEXT_VALUE = 0xFFFFFF;
	/** Tooltip text: the quiet hint line (vanilla's {@code ChatFormatting.DARK_GRAY}). */
	public static final int TEXT_DIM = 0x555555;
	/** A good state, the LV and MV tiers (vanilla's {@code ChatFormatting.GREEN}). */
	public static final int GOOD = 0x55FF55;
	/** The HV tier (vanilla's {@code ChatFormatting.LIGHT_PURPLE}). */
	public static final int TIER_HV = 0xFF55FF;
	/** A fitted module (vanilla's {@code ChatFormatting.DARK_AQUA}). */
	public static final int MODULE = 0x00AAAA;
	/** A reach or a speed (vanilla's {@code ChatFormatting.AQUA}). */
	public static final int AQUA = 0x55FFFF;
	/** A warning (vanilla's {@code ChatFormatting.RED}). */
	public static final int WARNING = 0xFF5555;
	/** A remark (vanilla's {@code ChatFormatting.YELLOW}). */
	public static final int NOTE = 0xFFFF55;
}
