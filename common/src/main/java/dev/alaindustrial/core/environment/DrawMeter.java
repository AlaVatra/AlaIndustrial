package dev.alaindustrial.core.environment;

/**
 * The average EU per tick a line drew from a block over the last half minute (MOD-990): a moving window
 * over the block's lifetime "sent to the grid" counter, so a network that pulls in bursts (a battery box
 * topping up, a machine between operations) still reads as its steady load.
 *
 * <p>Fed once per block tick with the counter's value ({@link #tick}); a sample is kept every
 * {@link #SAMPLE_EVERY} ticks, {@link #SAMPLES} of them, and the window runs from the oldest kept sample to
 * the latest tick. Counted in block ticks, not game time, so a block that ticks is measured the same way in
 * the world and in a gametest that drives it by hand. No Minecraft imports: L1 pins it.
 *
 * <p>The owner {@link #reset}s it whenever measuring is off (no statistics chip), so a chip fitted later
 * starts a fresh window instead of averaging over a span nothing was counted in.
 */
public final class DrawMeter {

	/** Ticks between two kept samples. */
	public static final int SAMPLE_EVERY = 20;

	/** Kept samples: with {@link #SAMPLE_EVERY} the window is at most 30 seconds long. */
	public static final int SAMPLES = 30;

	/** The draw over the window: EU and the ticks it spans; both 0 before two ticks were seen. */
	public record Window(long eu, int ticks) {
	}

	private final long[] sampleTotal = new long[SAMPLES];
	private final long[] sampleTick = new long[SAMPLES];
	private int count;
	private int head;
	private long ticks;
	private long latestTotal;

	/**
	 * One block tick: {@code totalOut} is the block's lifetime EU sent to the grid now. A counter that went
	 * backwards (the block was reloaded with other numbers) starts the window over.
	 */
	public void tick(long totalOut) {
		if (count > 0 && totalOut < latestTotal) {
			reset();
		}
		if (count == 0 || ticks % SAMPLE_EVERY == 0) {
			sampleTotal[head] = totalOut;
			sampleTick[head] = ticks;
			head = (head + 1) % SAMPLES;
			count = Math.min(SAMPLES, count + 1);
		}
		latestTotal = totalOut;
		ticks++;
	}

	/** Forget everything: the next {@link #tick} opens a new window. */
	public void reset() {
		count = 0;
		head = 0;
		ticks = 0L;
		latestTotal = 0L;
	}

	/** The draw from the oldest kept sample to the latest tick. */
	public Window window() {
		if (count == 0) {
			return new Window(0L, 0);
		}
		int oldest = (head - count + SAMPLES) % SAMPLES;
		long span = (ticks - 1) - sampleTick[oldest];
		if (span <= 0L) {
			return new Window(0L, 0);
		}
		return new Window(Math.max(0L, latestTotal - sampleTotal[oldest]), (int) Math.min(Integer.MAX_VALUE, span));
	}
}
