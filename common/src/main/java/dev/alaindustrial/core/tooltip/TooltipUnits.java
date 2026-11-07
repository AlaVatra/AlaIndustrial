package dev.alaindustrial.core.tooltip;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * How a tooltip writes a number (MOD-958): thousands split by a space in every language ("3 200"), a fraction
 * with the language's own decimal mark ("7,5" / "7.5"), a duration always in seconds.
 *
 * <p>The decimal mark is not decided here: a number with a fraction is a {@link Decimal}, and the client writes
 * it through the translation pattern {@link #DECIMAL_KEY} ({@code "%s.%s"} in English, {@code "%s,%s"} in
 * Russian), so each language file owns its mark. Minecraft-free, for the L1 lane.
 */
public final class TooltipUnits {

	/** The pattern of a number with a fraction: arguments the whole part and the fraction digits. */
	public static final String DECIMAL_KEY = "tooltip.alaindustrial.number.decimal";

	/** Unit keys: what the copper word after a value says. */
	public static final String EU = "tooltip.alaindustrial.unit.eu";
	public static final String EU_PER_TICK = "tooltip.alaindustrial.unit.eu_per_tick";
	public static final String SECONDS = "tooltip.alaindustrial.unit.seconds";
	public static final String MILLIBUCKETS = "tooltip.alaindustrial.unit.mb";
	public static final String BLOCKS = "tooltip.alaindustrial.unit.blocks";

	private static final int TICKS_PER_SECOND = 20;

	private TooltipUnits() {
	}

	/**
	 * A number as the tooltip shows it: the whole part with its thousands grouped, and up to two fraction digits
	 * (trailing zeros dropped), or none.
	 *
	 * @param whole    the whole part, grouped ("3 200", "-12")
	 * @param fraction the fraction digits without the mark ("5" for .5, "25" for .25), or null for a whole number
	 */
	public record Decimal(String whole, @Nullable String fraction) {
		/** The number in the English form, for logs and tests: "7.5", "3 200". */
		@Override
		public String toString() {
			return fraction == null ? whole : whole + "." + fraction;
		}
	}

	/** {@code value} with its thousands split by a space: "3 200", "1 000 000". */
	public static String grouped(long value) {
		return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
	}

	/** A span of game ticks in seconds: 150 ticks are "7.5", 13 ticks "0.65", 40 ticks "2". */
	public static Decimal seconds(long ticks) {
		// A tick is five hundredths of a second, so two digits are always exact.
		return hundredths(ticks * (100 / TICKS_PER_SECOND));
	}

	/** A factor rounded to two digits: 0.8 is "0.8", 0.64 "0.64", 2.0 "2". */
	public static Decimal factor(double value) {
		return hundredths(Math.round(value * 100.0));
	}

	private static Decimal hundredths(long hundredths) {
		long magnitude = Math.abs(hundredths);
		String whole = (hundredths < 0 ? "-" : "") + grouped(magnitude / 100);
		long cents = magnitude % 100;
		if (cents == 0) {
			return new Decimal(whole, null);
		}
		String digits = cents % 10 == 0 ? Long.toString(cents / 10) : String.format(Locale.ROOT, "%02d", cents);
		return new Decimal(whole, digits);
	}
}
