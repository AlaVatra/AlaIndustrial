package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyTransactions;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * One face of our block seen by another mod as {@link IEnergyStorage}. Each call is its own root transaction:
 * {@code simulate=true} is a dry run that rolls back, {@code simulate=false} commits. Our buffer's own journal
 * makes both exact; the amount is capped to {@code int}, as the interface demands.
 *
 * <p>The port is resolved again on every call, so a handler another mod cached keeps following the face: after a
 * rotation, a breaker opened or a role change it pushes into whatever the face is now, or into nothing. A call
 * made while one of our transactions is open on this thread is refused ({@link ForgeTxn#isOpenOnThisThread()}).
 */
public final class PortAsEnergyStorage implements IEnergyStorage {

	private final Supplier<EnergyPort> port;

	public PortAsEnergyStorage(Supplier<EnergyPort> port) {
		this.port = port;
	}

	@Override
	public int receiveEnergy(int maxReceive, boolean simulate) {
		EnergyPort face = port.get();
		if (maxReceive <= 0 || face == null || !face.supportsInsertion() || ForgeTxn.isOpenOnThisThread()) {
			return 0;
		}
		return toInt(run(simulate, txn -> face.insert(maxReceive, txn)));
	}

	@Override
	public int extractEnergy(int maxExtract, boolean simulate) {
		EnergyPort face = port.get();
		if (maxExtract <= 0 || face == null || !face.supportsExtraction() || ForgeTxn.isOpenOnThisThread()) {
			return 0;
		}
		return toInt(run(simulate, txn -> face.extract(maxExtract, txn)));
	}

	@Override
	public int getEnergyStored() {
		EnergyPort face = port.get();
		return face == null ? 0 : toInt(face.getAmount());
	}

	@Override
	public int getMaxEnergyStored() {
		EnergyPort face = port.get();
		return face == null ? 0 : toInt(face.getCapacity());
	}

	@Override
	public boolean canExtract() {
		EnergyPort face = port.get();
		return face != null && face.supportsExtraction();
	}

	@Override
	public boolean canReceive() {
		EnergyPort face = port.get();
		return face != null && face.supportsInsertion();
	}

	/** One root transaction around {@code move}: a dry run when {@code simulate}, committed otherwise. */
	static long run(boolean simulate, Function<EnergyPort.Txn, Long> move) {
		EnergyTransactions transactions = EnergyTransactions.get();
		if (simulate) {
			return transactions.simulate(move);
		}
		long[] moved = new long[1];
		transactions.runCommitting(txn -> moved[0] = move.apply(txn));
		return moved[0];
	}

	static int toInt(long value) {
		return (int) Math.max(0, Math.min(value, Integer.MAX_VALUE));
	}
}
