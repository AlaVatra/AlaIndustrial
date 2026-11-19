package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link FuelRuntime} and {@link DrawMeter} (MOD-990): how long a fuel generator's fuel lasts
 * at the load the line really draws, and how the span is rounded for the screen.
 *
 * <p>Numbers are the shipped defaults written out (8 EU/t, coal 1600 ticks, a block of coal 16000, the
 * 4000 EU buffer), because the point is the arithmetic, not the balance.
 */
class FuelRuntimeTest {

	private static final int RATE = 8;
	private static final long COAL = 1600L;
	private static final long COAL_BLOCK = 16_000L;
	private static final long BUFFER = 4000L;

	/** Draw {@code euPerTick} for a long window, as the meter would report it. */
	private static long ticksLeft(long fuelTicks, long buffered, long euPerTick) {
		return FuelRuntime.ticksLeft(fuelTicks, RATE, buffered, euPerTick * 600L, 600);
	}

	/** @implements MOD-990-RUNTIME — an empty slot and an empty buffer: nothing to show a span for */
	@Test
	void emptySlotAndEmptyBufferIsEmpty() {
		assertEquals(FuelRuntime.EMPTY, ticksLeft(0L, 0L, 2L));
		assertEquals(FuelRuntime.EMPTY, FuelRuntime.ticksLeft(0L, RATE, 0L, 0L, 0));
	}

	/** @implements MOD-990-RUNTIME — an empty slot still carries the line on what the buffer holds */
	@Test
	void emptySlotCountsTheBuffer() {
		assertEquals(2000L, ticksLeft(0L, BUFFER, 2L), "4000 EU at 2 EU/t");
	}

	/** @implements MOD-990-RUNTIME — a full stack at the full output burns item after item without a pause */
	@Test
	void fullStackAtFullDrawBurnsTicksForTicks() {
		assertEquals(64L * COAL, ticksLeft(64L * COAL, 0L, RATE));
		assertEquals(64L * COAL + BUFFER / RATE, ticksLeft(64L * COAL, BUFFER, RATE), "plus the buffer at 8 EU/t");
	}

	/**
	 * @implements MOD-990-RUNTIME — the owner's case: a line drawing 2 of 8 EU/t keeps the generator paused three
	 *     ticks in four, so the same fuel lasts four times longer than at the full output
	 */
	@Test
	void quarterDrawLastsFourTimesLonger() {
		long full = ticksLeft(64L * COAL, BUFFER, RATE);
		long quarter = ticksLeft(64L * COAL, BUFFER, 2L);
		assertEquals(4L * full, quarter);
		assertEquals((64L * COAL * RATE + BUFFER) / 2L, quarter, "every EU still to come, at 2 EU/t");
	}

	/** @implements MOD-990-RUNTIME — nothing draws: the fuel waits, so there is no span, not an endless one */
	@Test
	void noDrawIsWaitingNotEndless() {
		assertEquals(FuelRuntime.WAITING, ticksLeft(64L * COAL, BUFFER, 0L));
		assertEquals(FuelRuntime.WAITING, FuelRuntime.ticksLeft(COAL, RATE, 0L, 0L, 0), "no window yet");
	}

	/**
	 * @implements MOD-990-RUNTIME — a change of fuel changes the span by the fuel's burn time: the rest of the
	 *     item burning now plus the items in the slot, each at its own length
	 */
	@Test
	void changingTheFuelChangesTheSpan() {
		long burningPlanks = 150L;
		long onCoal = ticksLeft(burningPlanks + 3L * COAL, 0L, 4L);
		long onBlocks = ticksLeft(burningPlanks + 3L * COAL_BLOCK, 0L, 4L);
		assertEquals((burningPlanks + 3L * COAL) * 2L, onCoal, "4 of 8 EU/t: twice the burn ticks");
		assertEquals((burningPlanks + 3L * COAL_BLOCK) * 2L, onBlocks);
	}

	/** @implements MOD-990-RUNTIME — a draw above the output empties the buffer while the fuel burns */
	@Test
	void drawAboveTheOutputDrainsTheBufferMeanwhile() {
		// 100 burn ticks at 8 EU/t; the buffer loses 16 - 8 = 8 EU a tick meanwhile (800), and the 3200 left
		// carry the line 200 ticks more at 16 EU/t.
		assertEquals(300L, ticksLeft(100L, BUFFER, 16L));
		// A buffer that runs dry before the fuel: the fuel alone, at the full output.
		assertEquals(1000L, ticksLeft(1000L, 100L, 32L));
	}

	/** @implements MOD-990-RUNTIME — spans read as seconds, minutes, hours and minutes, or hours */
	@Test
	void shownRoundsToAPlainUnit() {
		assertEquals(new FuelRuntime.Shown(FuelRuntime.Unit.SECONDS, 1L, 0L), FuelRuntime.shown(0L), "never ~0 s");
		assertEquals(new FuelRuntime.Shown(FuelRuntime.Unit.SECONDS, 2L, 0L), FuelRuntime.shown(30L), "1.5 s");
		assertEquals(new FuelRuntime.Shown(FuelRuntime.Unit.SECONDS, 59L, 0L), FuelRuntime.shown(59L * 20L));
		assertEquals(new FuelRuntime.Shown(FuelRuntime.Unit.MINUTES, 1L, 0L), FuelRuntime.shown(60L * 20L));
		assertEquals(new FuelRuntime.Shown(FuelRuntime.Unit.MINUTES, 12L, 0L), FuelRuntime.shown(12L * 1200L + 500L));
		assertEquals(new FuelRuntime.Shown(FuelRuntime.Unit.HOURS_MINUTES, 1L, 0L), FuelRuntime.shown(3576L * 20L),
				"59.6 min rounds up into the next unit");
		assertEquals(new FuelRuntime.Shown(FuelRuntime.Unit.HOURS_MINUTES, 2L, 5L), FuelRuntime.shown(125L * 1200L));
		assertEquals(new FuelRuntime.Shown(FuelRuntime.Unit.HOURS, 14L, 0L), FuelRuntime.shown(14L * 72_000L + 1000L));
	}

	/** @implements MOD-990-DRAW — a steady draw reads as itself, from the second tick on */
	@Test
	void meterReadsASteadyDraw() {
		DrawMeter meter = new DrawMeter();
		assertEquals(new DrawMeter.Window(0L, 0), meter.window(), "nothing seen yet");
		long total = 0L;
		for (int i = 0; i < 100; i++) {
			meter.tick(total);
			total += 2L;
		}
		assertEquals(new DrawMeter.Window(198L, 99), meter.window());
	}

	/** @implements MOD-990-DRAW — the window is half a minute: an old load ages out */
	@Test
	void meterForgetsAnOldLoad() {
		DrawMeter meter = new DrawMeter();
		long total = 0L;
		for (int i = 0; i < 2000; i++) {
			meter.tick(total);
			total += 8L;
		}
		for (int i = 0; i < 1000; i++) {
			meter.tick(total);
			total += 2L;
		}
		DrawMeter.Window window = meter.window();
		assertTrue(window.ticks() <= DrawMeter.SAMPLE_EVERY * DrawMeter.SAMPLES, "window " + window.ticks());
		assertTrue(window.ticks() >= DrawMeter.SAMPLE_EVERY * (DrawMeter.SAMPLES - 1), "window " + window.ticks());
		assertEquals(2L * window.ticks(), window.eu(), "only the new load is left in the window");
	}

	/** @implements MOD-990-DRAW — a reset, or a counter that went backwards, starts a new window */
	@Test
	void meterStartsOverOnResetOrABackwardsCounter() {
		DrawMeter meter = new DrawMeter();
		for (int i = 0; i < 50; i++) {
			meter.tick(i * 5L);
		}
		meter.reset();
		assertEquals(new DrawMeter.Window(0L, 0), meter.window());
		meter.tick(1000L);
		meter.tick(1003L);
		meter.tick(10L);
		meter.tick(11L);
		assertEquals(new DrawMeter.Window(1L, 1), meter.window(), "the counter went back: only 10 -> 11 counts");
	}
}
