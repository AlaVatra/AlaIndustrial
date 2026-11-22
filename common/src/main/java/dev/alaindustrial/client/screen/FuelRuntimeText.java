package dev.alaindustrial.client.screen;

import dev.alaindustrial.client.ReadoutFormat;
import dev.alaindustrial.core.environment.FuelRuntime;
import net.minecraft.network.chat.Component;

/**
 * The words a fuel generator's span and load are shown in (MOD-990; the geothermal generator's, MOD-1000), shared
 * by the statistics panel and the generator's own window so the two never say it differently. Every unit comes
 * from the language files.
 */
final class FuelRuntimeText {

	private FuelRuntimeText() {
	}

	/** "~12 min" and the like; only for a span, never for a sentinel. */
	static Component span(long ticksLeft) {
		FuelRuntime.Shown shown = FuelRuntime.shown(ticksLeft);
		return switch (shown.unit()) {
			case SECONDS -> Component.translatable("gui.alaindustrial.stats.time_seconds", shown.major());
			case MINUTES -> Component.translatable("gui.alaindustrial.stats.time_minutes", shown.major());
			case HOURS_MINUTES -> Component.translatable("gui.alaindustrial.stats.time_hours_minutes",
					shown.major(), shown.minor());
			case HOURS -> Component.translatable("gui.alaindustrial.stats.time_hours", shown.major());
		};
	}

	/**
	 * The line that stands in for a span: no fuel at all ("No lava" for the geothermal generator, MOD-1000), or
	 * nothing drawing from the generator.
	 */
	static Component noSpan(FuelRuntime.Readout fuel) {
		if (fuel.ticksLeft() != FuelRuntime.EMPTY) {
			return Component.translatable("gui.alaindustrial.stats.fuel_waiting");
		}
		return Component.translatable(fuel.kind() == FuelRuntime.Kind.LAVA
				? "gui.alaindustrial.stats.lava_empty" : "gui.alaindustrial.stats.fuel_empty");
	}

	/** The statistics row's label for a span: "Fuel lasts", or "Lava lasts" for the geothermal one (MOD-1000). */
	static String lastsLabelKey(FuelRuntime.Readout fuel) {
		return fuel.kind() == FuelRuntime.Kind.LAVA
				? "gui.alaindustrial.stats.lava_lasts" : "gui.alaindustrial.stats.fuel_lasts";
	}

	/** "2.5 of 8 EU/t": the line's draw against the full output. */
	static Component loadValue(FuelRuntime.Readout fuel) {
		return Component.translatable("gui.alaindustrial.stats.load_value",
				ReadoutFormat.rate(fuel.drawEu(), fuel.drawTicks()), fuel.euPerTick());
	}

	/** The window's first line: "Fuel lasts ~12 min", or the line that stands in for a span. */
	static Component lastsLine(FuelRuntime.Readout fuel) {
		return fuel.ticksLeft() >= 0
				? Component.translatable("gui.alaindustrial.generator.fuel_lasts", span(fuel.ticksLeft()))
				: noSpan(fuel);
	}

	/** The window's second line: "Load 2.5 of 8 EU/t". */
	static Component loadLine(FuelRuntime.Readout fuel) {
		return Component.translatable("gui.alaindustrial.generator.load", loadValue(fuel));
	}
}
