package dev.alaindustrial.core.environment;

import org.jspecify.annotations.Nullable;

/**
 * How much lava a geothermal generator has burnt and how much EU it got for it (MOD-999): "3 buckets 250 mB,
 * 16 000 EU per bucket". No Minecraft imports, so the L1 lane pins the rule; the block entity and the screens
 * call these methods, so the tested code is the production code.
 *
 * <p><b>Whole buckets and the part, never rounded up.</b> Lava burns out of a reserve counted in burn ticks, one
 * bucket being {@code ticksPerBucket} of them. Each burnt reserve tick is {@code 1000 / ticksPerBucket} mB, which
 * is rarely a whole number, so the tally keeps the remainder below one mB and moves it into the count only once it
 * makes a whole mB. The count is always at most what really burnt.
 *
 * <p><b>Real energy, not a table value.</b> The EU side is what the generator actually put in its buffer on the
 * ticks the lava burnt, after every multiplier and after the buffer's room cut it. So the average per bucket
 * follows a catalyst that raises it, and a tick the full buffer could only take in part lowers it, as it should.
 *
 * <p>Counted only while the statistics chip measures, like every other statistics counter (MOD-125); the block
 * entity checks that before calling in.
 */
public final class LavaBurnTally {

	/** Millibuckets in one bucket. */
	public static final long MB_PER_BUCKET = 1000L;

	/** A block whose statistics carry burnt lava; {@code null} while it measures nothing or before any burnt. */
	public interface Source {
		@Nullable
		Shown lavaBurnt();
	}

	/**
	 * What the statistics carry: the lava burnt in whole mB and the EU it gave. Everything the panel shows is
	 * derived from these two, on the client, by the methods below.
	 */
	public record Shown(long mb, long eu) {

		/** Whole buckets burnt. */
		public long buckets() {
			return LavaBurnTally.buckets(mb);
		}

		/** The mB burnt beyond the whole buckets, 0 to 999. */
		public long partMb() {
			return LavaBurnTally.partMb(mb);
		}

		/** The average EU one bucket gave, or -1 before a single mB burnt. */
		public long euPerBucket() {
			return LavaBurnTally.euPerBucket(eu, mb);
		}
	}

	private long mb;
	/** Burnt lava below one mB, in units of {@code 1 / ticksPerBucket} mB; always below the last divisor used. */
	private long rest;
	private long eu;

	/**
	 * {@code reserveTicks} burn ticks of lava left the reserve, one bucket being {@code ticksPerBucket} of them.
	 * Nothing is counted for a non-positive amount or divisor.
	 */
	public void burn(long reserveTicks, int ticksPerBucket) {
		if (reserveTicks <= 0L || ticksPerBucket <= 0) {
			return;
		}
		rest += reserveTicks * MB_PER_BUCKET;
		mb += rest / ticksPerBucket;
		rest %= ticksPerBucket;
	}

	/** {@code credited} EU of that lava reached the buffer. */
	public void addEu(long credited) {
		if (credited > 0L) {
			eu += credited;
		}
	}

	/** Nothing was ever counted: a block that writes no key for it. */
	public boolean isEmpty() {
		return mb == 0L && rest == 0L && eu == 0L;
	}

	/** Lava burnt, in whole mB. */
	public long mb() {
		return mb;
	}

	/** EU the counted lava gave. */
	public long eu() {
		return eu;
	}

	/** What the statistics carry. */
	public Shown shown() {
		return new Shown(mb, eu);
	}

	/**
	 * Put back what a save holds; everything counted before is dropped, the part below one mB included (it is
	 * not saved, so a reload never counts more than burnt). A negative value is read as zero.
	 */
	public void restore(long savedMb, long savedEu) {
		mb = Math.max(0L, savedMb);
		eu = Math.max(0L, savedEu);
		rest = 0L;
	}

	/** Whole buckets in {@code mb}. */
	public static long buckets(long mb) {
		return Math.max(0L, mb) / MB_PER_BUCKET;
	}

	/** The mB of {@code mb} beyond the whole buckets. */
	public static long partMb(long mb) {
		return Math.max(0L, mb) % MB_PER_BUCKET;
	}

	/**
	 * The average EU one bucket gave: {@code eu * 1000 / mb}, rounded down; -1 when no mB burnt yet, since there
	 * is no average of nothing. Exact for any EU a block can make: the multiplication is done without overflow.
	 */
	public static long euPerBucket(long eu, long mb) {
		if (mb <= 0L) {
			return -1L;
		}
		long whole = Math.max(0L, eu) / mb;
		long part = Math.max(0L, eu) % mb;
		return whole > Long.MAX_VALUE / MB_PER_BUCKET - 1L
				? Long.MAX_VALUE
				: whole * MB_PER_BUCKET + part * MB_PER_BUCKET / mb;
	}
}
