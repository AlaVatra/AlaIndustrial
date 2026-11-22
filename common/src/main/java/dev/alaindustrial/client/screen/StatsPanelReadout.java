package dev.alaindustrial.client.screen;

import dev.alaindustrial.client.ReadoutFormat;
import dev.alaindustrial.core.environment.FuelRuntime;
import dev.alaindustrial.core.environment.FuelTally;
import dev.alaindustrial.core.environment.LavaBurnTally;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.network.MachineStatsPayload;
import net.minecraft.client.gui.Font;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.FormattedCharSequence;

/**
 * The text inside the statistics panel (MOD-125): the readout rows, or the line that explains why there are
 * none yet. Split out of {@link StatsPanelController} by MOD-716 so the controller holds the panel and its
 * input, and this class the one frame of text it draws; one instance per frame.
 */
final class StatsPanelReadout {
	private static final int PANEL_W = StatsPanelController.PANEL_W;
	private static final int PANEL_H = StatsPanelController.PANEL_H;

	/** Row pitch inside the statistics panel: 8px glyphs with a 3px gutter, the vanilla readout rhythm. */
	private static final int STAT_ROW_H = 11;

	/** Inner width available to panel text: the box minus the same gutter on both sides. */
	private static final int STAT_TEXT_W = PANEL_W - 18;

	private final Font font;
	private final MachineMenu menu;
	private final GuiGraphicsExtractor graphics;
	private final int mouseX;
	private final int mouseY;

	StatsPanelReadout(Font font, MachineMenu menu, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		this.font = font;
		this.menu = menu;
		this.graphics = graphics;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
	}

	/** The panel's text, from the frame's top-left corner. */
	void draw(int px, int py) {
		MachineStatsPayload stats = menu.stats();
		if (stats == null) {
			// Two different silences, and telling them apart is the difference between "wait a moment" and
			// "this machine will never show you anything until you fit a chip".
			boolean chipFitted = menu.hasStatsChipInPanel();
			int wrapY = wrappedText(Component.translatable(chipFitted
					? "gui.alaindustrial.stats.waiting" : "gui.alaindustrial.stats.no_chip"), px, py + 26);
			if (!chipFitted) {
				wrappedText(Component.translatable("gui.alaindustrial.stats.no_chip_hint"),
						px, wrapY + 2);
			}
			return;
		}

		drawRows(px, py, stats);
	}

	/** The readout rows under the title: work done, energy, rates, connections, and the live/idle line. */
	private void drawRows(int px, int py, MachineStatsPayload stats) {
		int pw = PANEL_W;
		int ph = PANEL_H;
		int y = py + 26;
		long[] parts = ReadoutFormat.durationParts(stats.activeTicks());
		y = statRow(px, y, "gui.alaindustrial.stats.uptime",
				Component.translatable("gui.alaindustrial.stats.duration",
						parts[0], ReadoutFormat.clock(parts[1], parts[2])).getString(),
				stats.activeTicks(), false);
		// Processed count sits with the working time: both answer "what has this machine actually done",
		// as opposed to the energy block below, which answers "at what cost". Shown only where it can be
		// non-zero — a generator processes nothing and would just carry a permanent 0.
		if (stats.itemsProcessed() > 0) {
			y = statRow(px, y, "gui.alaindustrial.stats.processed",
					ReadoutFormat.compact(stats.itemsProcessed()),
					stats.itemsProcessed(), false);
		}
		// MOD-992, MOD-999: what a generator has done is the fuel or the lava it burnt, so its count sits here too.
		y = burntRow(px, y, stats);

		y = drawEnergyRows(px, y, stats);

		y += 3;
		graphics.fill(px + 7, y, px + pw - 7, y + 1, GuiStyle.PANEL_LO);
		y += 4;

		boolean stale = menu.statsAreStale();
		y = statRow(px, y, "gui.alaindustrial.stats.now",
				ReadoutFormat.rate(stats.euOverWindow(), stats.windowTicks()) + " EU/t",
				stats.euOverWindow(), false, stale);
		// A fuel or geothermal generator's peak is its full output, which the Load row below reads ("of 8 EU/t"); it
		// gives way to the burnt-fuel or burnt-lava row above, so the panel keeps its height (MOD-992, MOD-999).
		if (stats.peakEuRate() > 0 && stats.fuel().isEmpty()) {
			y = statRow(px, y, "gui.alaindustrial.stats.peak",
					stats.peakEuRate() + " EU/t", stats.peakEuRate(), false);
		}
		// MOD-990: a fuel generator says how hard the line pulls on it and how long its fuel lasts at that.
		// No separator of their own: they answer the same "at what rate" as the two rows above, and the
		// panel's height holds exactly these rows under a generator's energy block.
		if (stats.fuel().isPresent()) {
			FuelRuntime.Readout fuel = stats.fuel().get();
			y = statRow(px, y, "gui.alaindustrial.stats.load", FuelRuntimeText.loadValue(fuel).getString(),
					0, false, stale);
			y = fuel.ticksLeft() >= 0
					? statRow(px, y, FuelRuntimeText.lastsLabelKey(fuel),
							FuelRuntimeText.span(fuel.ticksLeft()).getString(), 0, false, stale)
					: noteRow(px, y, FuelRuntimeText.noSpan(fuel));
		}

		y += 3;
		graphics.fill(px + 7, y, px + pw - 7, y + 1, GuiStyle.PANEL_LO);
		y += 4;

		y = statRow(px, y, "gui.alaindustrial.stats.connections",
				stats.sources() + " / " + stats.sinks(), 0, false);

		graphics.text(font, Component.translatable(stale
						? "gui.alaindustrial.stats.idle" : "gui.alaindustrial.stats.live"),
				px + 9, py + ph - 14, GuiStyle.TEXT_DIM, false);
	}

	/** The energy block: only the rows this block can answer, each with its exact figure as a tooltip. */
	private int drawEnergyRows(int px, int y, MachineStatsPayload stats) {
		// Only the rows this block can actually answer. A generator has no "received", a consumer has no
		// "generated", and a row of zeroes is worse than no row: it invites the player to look for a fault.
		if (stats.energyGenerated() > 0) {
			y = statRow(px, y, "gui.alaindustrial.stats.generated",
					ReadoutFormat.compact(stats.energyGenerated()) + " EU",
					stats.energyGenerated(), true);
		}
		if (stats.energyOut() > 0) {
			y = statRow(px, y, "gui.alaindustrial.stats.sent",
					ReadoutFormat.compact(stats.energyOut()) + " EU",
					stats.energyOut(), true);
		}
		if (stats.energyIn() > 0) {
			y = statRow(px, y, "gui.alaindustrial.stats.received",
					ReadoutFormat.compact(stats.energyIn()) + " EU",
					stats.energyIn(), true);
		}
		if (stats.energyConsumed() > 0) {
			y = statRow(px, y, "gui.alaindustrial.stats.spent",
					ReadoutFormat.compact(stats.energyConsumed()) + " EU",
					stats.energyConsumed(), true);
		}
		return y;
	}

	/**
	 * Draw a line that may not fit, wrapped at the panel's inner width, and return the y below it.
	 *
	 * <p>Needed because the panel is a fixed 159px while its strings are translated into twenty
	 * languages: the Russian hint for fitting a chip already overflowed it, and German and Turkish
	 * run longer still. Wrapping is measured, not guessed at authoring time.
	 */
	private int wrappedText(Component text, int px, int y) {
		for (FormattedCharSequence line : font.split(text, STAT_TEXT_W)) {
			graphics.text(font, line, px + 9, y, GuiStyle.TEXT_DIM, false);
			y += 10;
		}
		return y;
	}

	/** The one row a generator's burnt fuel (MOD-992) or burnt lava (MOD-999) takes; none for any other block. */
	private int burntRow(int px, int y, MachineStatsPayload stats) {
		if (stats.fuelBurnt().isPresent()) {
			return fuelBurntRow(px, y, stats.fuelBurnt().get());
		}
		return stats.lavaBurnt().isPresent() ? lavaBurntRow(px, y, stats.lavaBurnt().get()) : y;
	}

	/**
	 * "Fuel burnt 46" (MOD-992), and on hover the kinds burnt most, each by its own name ("Coal: 34"), with the
	 * rest as one line: the panel has no height for a row per kind. A kind whose item is gone from the game (its
	 * mod was removed) shows its id.
	 */
	private int fuelBurntRow(int px, int y, FuelTally.Top burnt) {
		int next = statRow(px, y, "gui.alaindustrial.stats.fuel_burnt", ReadoutFormat.compact(burnt.total()),
				burnt.total(), false);
		if (mouseY >= y - 1 && mouseY < y + STAT_ROW_H - 1 && mouseX >= px + 7 && mouseX < px + PANEL_W - 7) {
			List<Component> lines = new ArrayList<>();
			for (FuelTally.Entry kind : burnt.kinds()) {
				lines.add(Component.translatable("gui.alaindustrial.stats.fuel_burnt_kind", fuelName(kind.id()),
						ReadoutFormat.exact(kind.count())));
			}
			if (burnt.rest() > 0L) {
				lines.add(Component.translatable("gui.alaindustrial.stats.fuel_burnt_other",
						ReadoutFormat.exact(burnt.rest())));
			}
			graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		}
		return next;
	}

	/**
	 * "Lava burnt 3 B 250 mB" (MOD-999): whole buckets and the part, never rounded up. On hover the exact figure and
	 * the EU one bucket gave on average; the panel has no height for a second row.
	 */
	private int lavaBurntRow(int px, int y, LavaBurnTally.Shown burnt) {
		String value = Component.translatable("gui.alaindustrial.stats.lava_burnt_value",
				ReadoutFormat.exact(burnt.buckets()), burnt.partMb()).getString();
		int next = statRow(px, y, "gui.alaindustrial.stats.lava_burnt", value, 0, false);
		if (mouseY >= y - 1 && mouseY < y + STAT_ROW_H - 1 && mouseX >= px + 7 && mouseX < px + PANEL_W - 7) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable("gui.alaindustrial.stats.lava_burnt_exact",
					ReadoutFormat.exact(burnt.buckets()), burnt.partMb()));
			long perBucket = burnt.euPerBucket();
			if (perBucket >= 0L) {
				lines.add(Component.translatable("gui.alaindustrial.stats.lava_eu_per_bucket",
						ReadoutFormat.exact(perBucket)));
			}
			graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		}
		return next;
	}

	/** The item's own name for a burnt kind's id, or the id itself when no such item exists. */
	private static Component fuelName(String id) {
		Identifier key = Identifier.tryParse(id);
		Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
		return item == Items.AIR ? Component.literal(id) : new ItemStack(item).getHoverName();
	}

	/** A row that is one phrase with no value column ("Waiting for a consumer"), cut to the panel's width. */
	private int noteRow(int px, int y, Component text) {
		FormattedCharSequence line = font.split(text, STAT_TEXT_W).stream().findFirst()
				.orElse(FormattedCharSequence.EMPTY);
		graphics.text(font, line, px + 9, y, GuiStyle.TEXT_DIM, false);
		return y + STAT_ROW_H;
	}

	private int statRow(int px, int y, String labelKey, String value, long exact, boolean tooltip) {
		return statRow(px, y, labelKey, value, exact, tooltip, false);
	}

	/**
	 * One label/value line. The value is right-aligned so the column of numbers can be scanned vertically,
	 * and an abbreviated figure carries its exact value in a tooltip — the panel has room for "1.2M EU",
	 * the player checking a build needs the digits.
	 */
	private int statRow(int px, int y, String labelKey, String value, long exact, boolean tooltip,
			boolean dim) {
		int pw = PANEL_W;
		int color = dim ? GuiStyle.TEXT_DIM : GuiStyle.TEXT;
		int valueW = font.width(value);
		int valueX = px + pw - 9 - valueW;
		// The value is the number the player came for, so the LABEL is what gives way when a translation
		// is too long for the row: it is ellipsised to whatever space the value leaves, instead of the two
		// running into each other.
		int labelRoom = Math.max(0, STAT_TEXT_W - valueW - 4);
		FormattedCharSequence label = font.split(Component.translatable(labelKey), labelRoom)
				.stream().findFirst().orElse(FormattedCharSequence.EMPTY);
		graphics.text(font, label, px + 9, y, color, false);
		graphics.text(font, Component.literal(value), valueX, y, color, false);
		if (tooltip && mouseY >= y - 1 && mouseY < y + STAT_ROW_H - 1 && mouseX >= px + 7 && mouseX < px + pw - 7) {
			graphics.setTooltipForNextFrame(font,
					Component.literal(ReadoutFormat.exact(exact) + " EU"), mouseX, mouseY);
		}
		return y + STAT_ROW_H;
	}
}
