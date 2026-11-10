package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.energy.EnergyPort;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The item move of {@link ForgeItemPort}, free of Minecraft types so it can be tested on plain records.
 *
 * <p>Forge's {@code IItemHandler} hands out at most one stack per {@code extractItem} call and has no transaction,
 * so the move walks the source slots: simulate an extraction, simulate the stacked insert of what came out, then
 * do both for real for the amount both sides agreed to. Items move for real at once, not at commit: both ends are
 * known at the call and both simulate exactly, and the only caller ({@code ItemMover}) puts nothing else into that
 * transaction. If the target takes less for real than it promised, the rest goes back into the source slot, then
 * anywhere in the source, and only as a last resort is dropped into the world; items never vanish and are never
 * created. If the transaction aborts, the move is undone as far as the target lets it ({@link Undo}).
 */
final class ItemMoveKernel {

	/** What the kernel needs to know about a stack. */
	interface Stacks<S> {
		boolean isEmpty(S stack);

		int count(S stack);

		/** Same item and components, count ignored. */
		boolean sameItem(S a, S b);
	}

	/** The four calls of an item handler the move uses; each returns the extracted stack or the remainder. */
	interface Handler<S> {
		int slots();

		S extract(int slot, int amount, boolean simulate);

		S insert(int slot, S stack, boolean simulate);

		S insertStacked(S stack, boolean simulate);
	}

	private ItemMoveKernel() {
	}

	/** Moves up to {@code maxAmount}; returns how many items landed (planned, in a dry run). */
	static <S> int move(Handler<S> from, Handler<S> to, Stacks<S> stacks, int maxAmount, ForgeTxn txn,
			Consumer<S> spill) {
		if (maxAmount <= 0) {
			return 0;
		}
		int remaining = maxAmount;
		int moved = 0;
		Undo<S> undo = new Undo<>(from, to, stacks, spill);
		// Read once: Forge's SidedInvWrapper asks getSlotsForFace on every call, and a container that crafts on
		// that call (the advanced workbench's bottom face, MOD-810) would craft a batch nobody asked for after the
		// last extraction.
		int slots = from.slots();
		for (int slot = 0; slot < slots && remaining > 0; slot++) {
			S peek = from.extract(slot, remaining, true);
			if (stacks.isEmpty(peek)) {
				continue;
			}
			int can = stacks.count(peek) - stacks.count(to.insertStacked(peek, true));
			if (can <= 0) {
				continue;
			}
			if (txn.simulation()) {
				moved += can;
				remaining -= can;
				continue;
			}
			S taken = from.extract(slot, can, false);
			if (stacks.isEmpty(taken)) {
				continue;
			}
			S left = to.insertStacked(taken, false);
			int landed = stacks.count(taken) - stacks.count(left);
			moved += landed;
			remaining -= landed;
			if (!stacks.isEmpty(left)) {
				drop(stacks, spill, returnToSource(from, slot, left, stacks)); // the target lied
			}
			if (landed > 0) {
				undo.landed.add(new Landed<>(slot, taken, landed));
			}
		}
		if (!undo.landed.isEmpty()) {
			txn.enlist(undo);
		}
		return moved;
	}

	/** Back into the slot it came from, else anywhere in the source; returns what still has no place. */
	private static <S> S returnToSource(Handler<S> from, int slot, S stack, Stacks<S> stacks) {
		S left = from.insert(slot, stack, false);
		return stacks.isEmpty(left) ? left : from.insertStacked(left, false);
	}

	private static <S> void drop(Stacks<S> stacks, Consumer<S> spill, S stack) {
		if (!stacks.isEmpty(stack)) {
			ForgeTransferLosses.add(ForgeTransferLosses.Kind.ITEMS_SPILLED, stacks.count(stack));
			spill.accept(stack);
		}
	}

	private record Landed<S>(int slot, S item, int count) {
	}

	/** The journal entry of one move: on abort, takes what landed back out of the target, best effort. */
	private static final class Undo<S> implements EnergyPort.Participant {
		final Handler<S> from;
		final Handler<S> to;
		final Stacks<S> stacks;
		final Consumer<S> spill;
		final List<Landed<S>> landed = new ArrayList<>();

		Undo(Handler<S> from, Handler<S> to, Stacks<S> stacks, Consumer<S> spill) {
			this.from = from;
			this.to = to;
			this.stacks = stacks;
			this.spill = spill;
		}

		@Override
		public long createSnapshot() {
			return 0L;
		}

		@Override
		public void readSnapshot(long snapshot) {
			for (int i = landed.size() - 1; i >= 0; i--) {
				Landed<S> move = landed.get(i);
				int need = move.count();
				for (int slot = 0; slot < to.slots() && need > 0; slot++) {
					S peek = to.extract(slot, need, true);
					if (stacks.isEmpty(peek) || !stacks.sameItem(peek, move.item())) {
						continue;
					}
					S back = to.extract(slot, stacks.count(peek), false);
					if (stacks.isEmpty(back)) {
						continue;
					}
					need -= stacks.count(back);
					drop(stacks, spill, returnToSource(from, move.slot(), back, stacks));
				}
			}
			landed.clear();
		}

		@Override
		public void onFinalCommit() {
		}
	}
}
