package dev.alaindustrial.client.tooltip;

import dev.alaindustrial.core.style.AlaPalette;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.TooltipRole;
import dev.alaindustrial.core.tooltip.TooltipUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

/**
 * The one place a tooltip role becomes a chat colour (MOD-958, the owner's style "09b"): the role's icon in the
 * role's colour, the label grey, a value white, a unit copper, the hint dim with its key grey or white.
 * Blocks ({@link MachineTooltips}), items ({@link ItemTooltip}) and the blueprint line all draw through here, so
 * a colour changes in one file.
 *
 * <p>Links no client-only type (only chat components): item code, which also runs on a dedicated server, may
 * build its lines with it.
 */
public final class TooltipRender {

	/** The key the hint names: a keyboard label, the same in every language. */
	private static final String SHIFT = "Shift";
	/** The segment of a progress bar: a glyph of the game's bitmap font. */
	private static final String BAR_SEGMENT = "▌";

	private TooltipRender() {
	}

	/** One described line as a chat component in style 09b. */
	public static Component line(MachineTooltipSpec.Line line) {
		TooltipRole role = line.role();
		if (role == TooltipRole.HINT) {
			Object[] args = line.argValues();
			return hint(args.length > 0 && Boolean.TRUE.equals(args[0]));
		}
		if (line.label() != null) {
			// A figure under a label of its own (the incubator's modes): "<icon> <label> <value> <unit>".
			MutableComponent body = Component.translatable(line.label()).withColor(AlaPalette.TEXT_LABEL);
			for (Object arg : line.argValues()) {
				body.append(" ").append(value(arg));
			}
			if (line.unit() != null) {
				body.append(" ").append(Component.translatable(line.unit()).withColor(AlaPalette.COPPER));
			}
			return withIcon(role, body);
		}
		Object[] args = line.argValues();
		int iconRgb = role == TooltipRole.TIER && args.length > 0 && args[0] instanceof MachineTooltipSpec.Tier tier
				? tier.rgb() : role.rgb();
		return roleLine(role, iconRgb, line.key(), line.unit(), args);
	}

	/**
	 * {@code <icon> <label(values)> <unit>}: the icon in the role's colour, the label's text grey, each value
	 * white ({@link #value}), the unit copper.
	 */
	public static MutableComponent roleLine(TooltipRole role, String labelKey, @Nullable String unitKey,
			Object... values) {
		return roleLine(role, role.rgb(), labelKey, unitKey, values);
	}

	private static MutableComponent roleLine(TooltipRole role, int iconRgb, String labelKey, @Nullable String unitKey,
			Object[] values) {
		Object[] shown = new Object[values.length];
		for (int i = 0; i < values.length; i++) {
			shown[i] = value(values[i]);
		}
		MutableComponent body = Component.translatable(labelKey, shown).withColor(AlaPalette.TEXT_LABEL);
		if (unitKey != null) {
			body.append(" ").append(Component.translatable(unitKey).withColor(AlaPalette.COPPER));
		}
		return withIcon(role, iconRgb, body);
	}

	/** {@code body} after the role's icon. The body keeps its own colours. */
	public static MutableComponent withIcon(TooltipRole role, Component body) {
		return withIcon(role, role.rgb(), body);
	}

	private static MutableComponent withIcon(TooltipRole role, int iconRgb, Component body) {
		String glyph = role.glyph();
		if (glyph == null) {
			return Component.empty().append(body);
		}
		return Component.literal(glyph + " ").withColor(iconRgb).append(body);
	}

	/**
	 * A value as the tooltip writes it, white: a whole number grouped ("3 200"), a {@link TooltipUnits.Decimal}
	 * through the language's decimal pattern ("7,5" / "7.5"), a tier as its name in its colour, a component as it
	 * is (white unless it has a colour of its own).
	 */
	public static Component value(Object value) {
		if (value instanceof MachineTooltipSpec.Tier tier) {
			return Component.literal(tier.name()).withColor(tier.rgb());
		}
		if (value instanceof TooltipUnits.Decimal decimal) {
			MutableComponent out = decimal.fraction() == null
					? Component.literal(decimal.whole())
					: Component.translatable(TooltipUnits.DECIMAL_KEY, decimal.whole(), decimal.fraction());
			return out.withColor(AlaPalette.TEXT_VALUE);
		}
		if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
			return Component.literal(TooltipUnits.grouped(((Number) value).longValue()))
					.withColor(AlaPalette.TEXT_VALUE);
		}
		if (value instanceof Component component) {
			return component.getStyle().getColor() == null
					? component.copy().withColor(AlaPalette.TEXT_VALUE) : component;
		}
		return Component.literal(String.valueOf(value)).withColor(AlaPalette.TEXT_VALUE);
	}

	/**
	 * "Hold [Shift] for details", dim, the key grey while [Shift] is up and white while it is held
	 * ({@link MachineTooltipSpec#HOLD_SHIFT_KEY}).
	 */
	public static Component hint(boolean held) {
		return Component.translatable(MachineTooltipSpec.HOLD_SHIFT_KEY, keyName(Component.literal(SHIFT), held))
				.withColor(AlaPalette.TEXT_DIM);
	}

	/** A line that continues the one above it (a purpose said in two lines): grey, no icon. */
	public static Component continuation(String key) {
		return Component.translatable(key).withColor(AlaPalette.TEXT_LABEL);
	}

	/** {@code text} in the role's colour: a value that names something of that role (a module's name). */
	public static Component inRoleColour(TooltipRole role, Component text) {
		return text.copy().withColor(role.rgb());
	}

	/** A key's name in a hint: grey while the key is up, white while it is held. */
	public static Component keyName(Component key, boolean held) {
		return key.copy().withColor(held ? AlaPalette.TEXT_VALUE : AlaPalette.TEXT_LABEL);
	}

	/** A short stamp after a line ("NEW"): bold, in the key colour. */
	public static Component stamp(Component text) {
		return text.copy().withColor(AlaPalette.ENERGY_HI).withStyle(ChatFormatting.BOLD);
	}

	/** A bar of {@code of} segments, {@code filled} of them orange and the rest dim. */
	public static Component bar(int filled, int of) {
		int done = Math.max(0, Math.min(of, filled));
		return Component.empty()
				.append(Component.literal(BAR_SEGMENT.repeat(done)).withColor(AlaPalette.ENERGY))
				.append(Component.literal(BAR_SEGMENT.repeat(of - done)).withColor(AlaPalette.TEXT_DIM));
	}

	/**
	 * The translation key a tooltip line was built from: the first translatable text of the line, the line
	 * itself first and then its parts in order (a style-09b line starts with its icon, so its key sits in a
	 * part). Null for a line with no translatable text. A hint is found by this, and tests read lines by it.
	 */
	public static @Nullable String lineKey(Component line) {
		TranslatableContents contents = lineContents(line);
		return contents == null ? null : contents.getKey();
	}

	/** The translatable text {@link #lineKey} names, with its arguments; null when there is none. */
	public static @Nullable TranslatableContents lineContents(Component line) {
		if (line.getContents() instanceof TranslatableContents translatable) {
			return translatable;
		}
		for (Component part : line.getSiblings()) {
			TranslatableContents found = lineContents(part);
			if (found != null) {
				return found;
			}
		}
		return null;
	}
}
