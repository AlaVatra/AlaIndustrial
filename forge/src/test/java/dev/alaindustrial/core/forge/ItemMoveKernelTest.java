package dev.alaindustrial.core.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Forge item move on plain records (MOD-962): slot loop, a lying target, best-effort undo, conservation. */
class ItemMoveKernelTest {

	private static final Stack EMPTY = new Stack("", 0);
	private static final ItemMoveKernel.Stacks<Stack> STACKS = new ItemMoveKernel.Stacks<>() {
		@Override
		public boolean isEmpty(Stack stack) {
			return stack.count() <= 0;
		}

		@Override
		public int count(Stack stack) {
			return stack.count();
		}

		@Override
		public boolean sameItem(Stack a, Stack b) {
			return a.id().equals(b.id());
		}
	};

	private final ForgeEnergyTransactions transactions = new ForgeEnergyTransactions();
	private final List<Stack> spilled = new ArrayList<>();

	/** T-I1: one stack per extract call, so the move walks the slots. */
	@Test
	void loopsOverSlots() {
		Inv from = new Inv(3, 64);
		from.put(0, "iron", 4).put(1, "iron", 4).put(2, "iron", 4);
		Inv to = new Inv(1, 64);
		assertEquals(10, move(from, to, 10));
		assertEquals(2, from.count(2));
		assertEquals(10, to.count(0));
	}

	/** T-I2: unstackable items take one target slot each. */
	@Test
	void unstackableItemsTakeOneSlotEach() {
		Inv from = new Inv(3, 1);
		from.put(0, "pick", 1).put(1, "pick", 1).put(2, "pick", 1);
		Inv to = new Inv(5, 1);
		assertEquals(3, move(from, to, 3));
		assertEquals(0, from.total());
		assertEquals(1, to.count(2));
	}

	/** T-I3: a target that takes less for real than it promised leaves the rest in the source slot. */
	@Test
	void lyingTargetLeftoverGoesBackToSource() {
		Inv from = new Inv(1, 64).put(0, "iron", 5);
		Inv to = new Inv(1, 64);
		to.realInsertCap = 3;
		assertEquals(3, move(from, to, 5));
		assertEquals(2, from.count(0));
		assertEquals(3, to.count(0));
		assertEquals(0, spilled.size());
	}

	/** T-I4: an output-only source refuses the return; the rest is dropped, never destroyed. */
	@Test
	void outputOnlySourceRefusesReturnSpills() {
		Inv from = new Inv(1, 64).put(0, "iron", 5);
		from.refuseInsert = true;
		Inv to = new Inv(1, 64);
		to.realInsertCap = 3;
		assertEquals(3, move(from, to, 5));
		assertEquals(List.of(new Stack("iron", 2)), spilled);
		assertEquals(5, from.total() + to.total() + spilled.stream().mapToInt(Stack::count).sum());
	}

	/** T-I5: the source decides for real; only what it really gave is inserted. */
	@Test
	void sourceGivesLessThanSimulated() {
		Inv from = new Inv(1, 64).put(0, "iron", 5);
		from.realExtractCap = 2;
		Inv to = new Inv(1, 64);
		assertEquals(2, move(from, to, 5));
		assertEquals(3, from.count(0));
		assertEquals(2, to.count(0));
	}

	/** T-I6: a dry run plans and moves nothing. */
	@Test
	void dryRunMovesNothing() {
		Inv from = new Inv(2, 64).put(0, "iron", 4).put(1, "gold", 4);
		Inv to = new Inv(2, 64);
		int planned = transactions.simulate(txn -> ItemMoveKernel.move(from, to, STACKS, 6, (ForgeTxn) txn,
				spilled::add));
		assertEquals(6, planned);
		assertEquals(0, from.realCalls + to.realCalls);
		assertEquals(8, from.total());
	}

	/** T-I7: an abort after the move takes the items back out; a target that will not give them up keeps them. */
	@Test
	void abortReversesTheMove() {
		Inv from = new Inv(1, 64).put(0, "iron", 6);
		Inv to = new Inv(1, 64);
		assertThrows(IllegalStateException.class, () -> transactions.runCommitting(txn -> {
			assertEquals(6, ItemMoveKernel.move(from, to, STACKS, 6, (ForgeTxn) txn, spilled::add));
			throw new IllegalStateException("abort");
		}));
		assertEquals(6, from.count(0));
		assertEquals(0, to.total());

		Inv sticky = new Inv(1, 64);
		sticky.refuseExtract = true;
		assertThrows(IllegalStateException.class, () -> transactions.runCommitting(txn -> {
			ItemMoveKernel.move(from, sticky, STACKS, 6, (ForgeTxn) txn, spilled::add);
			throw new IllegalStateException("abort");
		}));
		assertEquals(6, from.total() + sticky.total(), "best effort: nothing created, nothing lost");
		assertEquals(6, sticky.total());
	}

	/** T-I8. */
	@Test
	void maxZeroOrNegativeMakesNoCall() {
		Inv from = new Inv(1, 64).put(0, "iron", 6);
		Inv to = new Inv(1, 64);
		assertEquals(0, move(from, to, 0));
		assertEquals(0, move(from, to, -3));
		assertEquals(0, from.calls + to.calls);
	}

	private int move(Inv from, Inv to, int max) {
		int[] moved = new int[1];
		transactions.runCommitting(txn -> moved[0] = ItemMoveKernel.move(from, to, STACKS, max, (ForgeTxn) txn,
				spilled::add));
		return moved[0];
	}

	record Stack(String id, int count) {
	}

	/** A scripted inventory: one max stack size for every item, and switches for each way a handler can refuse. */
	private static final class Inv implements ItemMoveKernel.Handler<Stack> {
		final Stack[] slots;
		final int maxStack;
		boolean refuseInsert;
		boolean refuseExtract;
		int realInsertCap = -1;
		int realExtractCap = -1;
		int realCalls;
		int calls;

		Inv(int size, int maxStack) {
			this.slots = new Stack[size];
			this.maxStack = maxStack;
			java.util.Arrays.fill(slots, EMPTY);
		}

		Inv put(int slot, String id, int count) {
			slots[slot] = new Stack(id, count);
			return this;
		}

		int count(int slot) {
			return slots[slot].count();
		}

		int total() {
			int total = 0;
			for (Stack stack : slots) {
				total += stack.count();
			}
			return total;
		}

		@Override
		public int slots() {
			calls++;
			return slots.length;
		}

		@Override
		public Stack extract(int slot, int amount, boolean simulate) {
			calls++;
			Stack held = slots[slot];
			if (refuseExtract || held.count() == 0 || amount <= 0) {
				return EMPTY;
			}
			int n = Math.min(amount, Math.min(held.count(), maxStack));
			if (!simulate) {
				realCalls++;
				if (realExtractCap >= 0) {
					n = Math.min(n, realExtractCap);
				}
				slots[slot] = held.count() == n ? EMPTY : new Stack(held.id(), held.count() - n);
			}
			return new Stack(held.id(), n);
		}

		@Override
		public Stack insert(int slot, Stack stack, boolean simulate) {
			calls++;
			Stack held = slots[slot];
			if (refuseInsert || stack.count() == 0 || (held.count() > 0 && !held.id().equals(stack.id()))) {
				return stack;
			}
			int n = Math.min(stack.count(), maxStack - held.count());
			if (n <= 0) {
				return stack;
			}
			if (!simulate) {
				realCalls++;
				slots[slot] = new Stack(stack.id(), held.count() + n);
			}
			return stack.count() == n ? EMPTY : new Stack(stack.id(), stack.count() - n);
		}

		@Override
		public Stack insertStacked(Stack stack, boolean simulate) {
			Stack left = stack;
			int cap = !simulate && realInsertCap >= 0 ? realInsertCap : Integer.MAX_VALUE;
			for (int pass = 0; pass < 2; pass++) {
				for (int slot = 0; slot < slots.length && left.count() > 0 && cap > 0; slot++) {
					boolean matching = slots[slot].count() > 0;
					if (matching != (pass == 0)) {
						continue;
					}
					Stack offer = new Stack(left.id(), Math.min(left.count(), cap));
					Stack rest = insert(slot, offer, simulate);
					int took = offer.count() - rest.count();
					cap -= took;
					left = left.count() == took ? EMPTY : new Stack(left.id(), left.count() - took);
				}
			}
			return left;
		}
	}
}
