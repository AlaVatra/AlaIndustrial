package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyTransactions;
import java.util.function.Consumer;
import java.util.function.Function;

/** {@link EnergyTransactions} on MinecraftForge: a {@link ForgeTxn} journal instead of a loader transaction. */
public final class ForgeEnergyTransactions implements EnergyTransactions {

	@Override
	public void runCommitting(Consumer<EnergyPort.Txn> body) {
		ForgeTxn txn = ForgeTxn.openRoot(false);
		try {
			body.accept(txn);
		} catch (RuntimeException | Error e) {
			txn.abort();
			throw e;
		}
		txn.commit();
	}

	@Override
	public <T> T simulate(Function<EnergyPort.Txn, T> body) {
		ForgeTxn txn = ForgeTxn.openRoot(true);
		try {
			return body.apply(txn);
		} finally {
			txn.abort();
		}
	}
}
