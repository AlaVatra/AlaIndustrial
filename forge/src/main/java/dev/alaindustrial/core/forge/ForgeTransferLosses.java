package dev.alaindustrial.core.forge;

import java.util.concurrent.atomic.AtomicLong;

/**
 * What left our side and arrived nowhere, since start, per kind (MOD-962): a foreign storage that took less at
 * commit than it promised and that no payer could take back, an aborted extraction the storage refused to take
 * back, items a lying target forced us to drop into the world. Diagnostics only; nothing reads it to decide.
 */
public final class ForgeTransferLosses {

	public enum Kind {
		ENERGY, FLUID, ITEMS_SPILLED
	}

	private static final AtomicLong[] TOTALS = {new AtomicLong(), new AtomicLong(), new AtomicLong()};

	private ForgeTransferLosses() {
	}

	static void add(Kind kind, long amount) {
		if (amount > 0) {
			TOTALS[kind.ordinal()].addAndGet(amount);
		}
	}

	public static long get(Kind kind) {
		return TOTALS[kind.ordinal()].get();
	}
}
