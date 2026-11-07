package dev.alaindustrial.core.tooltip;

import dev.alaindustrial.core.style.AlaPalette;
import org.jspecify.annotations.Nullable;

/**
 * What a tooltip line says, and so how it looks (MOD-958, the owner's style "09b"): every line starts with
 * the role's icon in the role's colour; the label is grey, a value white, a unit copper. The client's
 * {@code TooltipRender} is the one place a role becomes a chat colour.
 *
 * <p>Every icon is a glyph of the game's bitmap font (the {@code nonlatin_european}, {@code accented} and
 * {@code ascii} providers of {@code font/include/default.json}, checked against the 26.3 client jar): a glyph
 * missing there falls back to unifont and looks foreign. That is why "off" is U+2718, not U+2716 — the latter is
 * not in the bitmap font.
 *
 * <p>Minecraft-free: the core declares lines by role, the L1 lane tests the table.
 */
public enum TooltipRole {
	/** What the item or block is for: the line right under the name. */
	PURPOSE("ℹ", AlaPalette.TEXT_LABEL),
	/**
	 * Any other line of words: how the block is fed, what it takes, a rule it keeps. The purpose's icon and grey,
	 * placed among the figures (or under [Shift]) instead of under the name.
	 */
	INFO("ℹ", AlaPalette.TEXT_LABEL),
	/** An energy figure: a draw, a buffer, a charge. */
	ENERGY("⚡", AlaPalette.ENERGY),
	/** A duration, always in seconds. */
	TIME("⌛", AlaPalette.FLUID_HI),
	/** A state that is on (a magnet switched on, a safe cable). */
	STATE_ON("✔", AlaPalette.GOOD),
	/** A state that is off (switched off, depleted). */
	STATE_OFF("✘", AlaPalette.TEXT_LABEL),
	/** The voltage tier; its colour is the tier's own ({@code MachineTooltipSpec.Tier#rgb}). */
	TIER("■", AlaPalette.GOOD),
	/** A fluid and its amount. */
	FLUID("🌊", AlaPalette.FLUID),
	/** A fitted module or what fits where. */
	MODULE("◆", AlaPalette.MODULE),
	/** A key action: "Right-click: open the screen". */
	KEY("▶", AlaPalette.ENERGY_HI),
	/** A reach. */
	RANGE("◎", AlaPalette.AQUA),
	/** A speed or a time factor. */
	SPEED("⏩", AlaPalette.AQUA),
	/** A danger: shown in every mode. */
	WARNING("⚠", AlaPalette.WARNING),
	/** A remark or a limit. */
	NOTE("※", AlaPalette.NOTE),
	/** The line that leads to a blueprint (MOD-797). */
	BLUEPRINT("✎", AlaPalette.ENERGY_HI),
	/** The "hold [Shift] for details" line: no icon, dim. */
	HINT(null, AlaPalette.TEXT_DIM);

	private final @Nullable String glyph;
	private final int rgb;

	TooltipRole(@Nullable String glyph, int rgb) {
		this.glyph = glyph;
		this.rgb = rgb;
	}

	/** The icon the line starts with, or null for a line without one (the hint). */
	public @Nullable String glyph() {
		return glyph;
	}

	/** The icon's colour, {@code 0xRRGGBB}. */
	public int rgb() {
		return rgb;
	}

	/**
	 * Where a line of this role stands when nothing else says so ({@link TooltipLayout}): a purpose first, a
	 * state next, a tier, a module and a key under [Shift], a warning and a note after them; every figure is a
	 * stat.
	 */
	public TooltipLayout.Slot defaultSlot() {
		return switch (this) {
			case PURPOSE -> TooltipLayout.Slot.PURPOSE;
			case STATE_ON, STATE_OFF -> TooltipLayout.Slot.STATE;
			case TIER -> TooltipLayout.Slot.TIER;
			case MODULE -> TooltipLayout.Slot.MODULE;
			case KEY -> TooltipLayout.Slot.KEY;
			case WARNING -> TooltipLayout.Slot.WARNING;
			case NOTE, BLUEPRINT, HINT -> TooltipLayout.Slot.NOTE;
			case INFO, ENERGY, TIME, FLUID, RANGE, SPEED -> TooltipLayout.Slot.STAT;
		};
	}
}
