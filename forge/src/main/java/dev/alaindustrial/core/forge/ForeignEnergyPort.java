package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.energy.EnergyPort;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Another mod's {@link IEnergyStorage} seen as an {@link EnergyPort}. Every move goes through the
 * transaction's {@link ForeignLedger} for this storage; see there for what is atomic and what is not.
 * EU and Forge Energy are counted one to one, as on NeoForge.
 */
public final class ForeignEnergyPort implements EnergyPort {

	private final IEnergyStorage storage;
	private final ForeignLedger.Storage calls;

	private ForeignEnergyPort(IEnergyStorage storage) {
		this.storage = storage;
		this.calls = new ForeignLedger.Storage() {
			@Override
			public int insert(int amount, boolean simulate) {
				return storage.receiveEnergy(amount, simulate);
			}

			@Override
			public int extract(int amount, boolean simulate) {
				return storage.extractEnergy(amount, simulate);
			}
		};
	}

	public static EnergyPort of(IEnergyStorage storage) {
		return storage == null ? null : new ForeignEnergyPort(storage);
	}

	@Override
	public long insert(long maxAmount, Txn txn) {
		if (maxAmount <= 0 || !storage.canReceive()) {
			return 0;
		}
		return ledger(txn).insert(maxAmount);
	}

	@Override
	public long extract(long maxAmount, Txn txn) {
		if (maxAmount <= 0 || !storage.canExtract()) {
			return 0;
		}
		return ledger(txn).extract(maxAmount);
	}

	@Override
	public long getAmount() {
		return storage.getEnergyStored();
	}

	@Override
	public long getCapacity() {
		return storage.getMaxEnergyStored();
	}

	@Override
	public boolean supportsInsertion() {
		return storage.canReceive();
	}

	@Override
	public boolean supportsExtraction() {
		return storage.canExtract();
	}

	private ForeignLedger ledger(Txn txn) {
		ForgeTxn forgeTxn = ForgeTxn.require(txn);
		// Keyed by the foreign storage object, not by this port: two lookups of one block share a ledger.
		return forgeTxn.ledger(storage, ForeignLedger.ENERGY, () -> ForeignLedger.energy(calls, forgeTxn.simulation()));
	}
}
