package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidTank;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Another mod's {@link IFluidHandler} seen as a {@link FluidPort}. Every move goes through the transaction's
 * {@link ForeignLedger} for this handler and fluid, so a fill is deferred to commit and a drain is real at once,
 * exactly as for energy. Forge counts fluid in millibuckets ({@code FluidType.BUCKET_VOLUME == 1000}), our unit,
 * so only the {@code long} to {@code int} narrowing is needed.
 *
 * <p>The tank read is the first non-empty one; for the single-tank handlers this port is used with that is the
 * NeoForge port's tank 0, and a multi-tank handler is not reported as empty while its first tank is.
 */
public final class ForeignFluidPort implements FluidPort {

	private final IFluidHandler handler;

	private ForeignFluidPort(IFluidHandler handler) {
		this.handler = handler;
	}

	public static FluidPort of(IFluidHandler handler) {
		return handler == null ? null : new ForeignFluidPort(handler);
	}

	@Override
	public long insert(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
		if (fluid == null || fluid.isEmpty() || maxAmount <= 0) {
			return 0;
		}
		return ledger(txn, fluid.fluid()).insert(maxAmount);
	}

	@Override
	public long extract(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
		if (fluid == null || fluid.isEmpty() || maxAmount <= 0) {
			return 0;
		}
		return ledger(txn, fluid.fluid()).extract(maxAmount);
	}

	@Override
	public FluidHolder fluid() {
		for (int tank = 0; tank < handler.getTanks(); tank++) {
			FluidStack stack = handler.getFluidInTank(tank);
			if (!stack.isEmpty()) {
				return FluidHolder.of(stack.getFluid());
			}
		}
		return FluidHolder.EMPTY;
	}

	@Override
	public long getAmount() {
		return handler.getTanks() == 0 ? 0 : handler.getFluidInTank(0).getAmount();
	}

	@Override
	public long getCapacity() {
		return handler.getTanks() == 0 ? 0 : handler.getTankCapacity(0);
	}

	@Override
	public boolean supportsInsertion() {
		return handler.getTanks() > 0;
	}

	@Override
	public boolean supportsExtraction() {
		return handler.getTanks() > 0;
	}

	private ForeignLedger ledger(EnergyPort.Txn txn, Fluid fluid) {
		ForgeTxn forgeTxn = ForgeTxn.require(txn);
		return forgeTxn.ledger(handler, fluid, () -> ForeignLedger.fluid(calls(fluid), fluid,
				p -> p instanceof FluidTank tank && tank.fluid().is(fluid), forgeTxn.simulation()));
	}

	/**
	 * The handler's two calls bound to one fluid. A drain that hands back a different fluid is refused: the
	 * wrong fluid is filled straight back (a real drain only), and what the handler will not take back is counted
	 * as lost, never credited to us.
	 */
	private ForeignLedger.Storage calls(Fluid fluid) {
		return new ForeignLedger.Storage() {
			@Override
			public int insert(int amount, boolean simulate) {
				return handler.fill(new FluidStack(fluid, amount), action(simulate));
			}

			@Override
			public int extract(int amount, boolean simulate) {
				FluidStack got = handler.drain(new FluidStack(fluid, amount), action(simulate));
				if (got.isEmpty()) {
					return 0;
				}
				if (got.getFluid() != fluid) {
					if (!simulate) {
						int back = handler.fill(got, FluidAction.EXECUTE);
						ForgeTransferLosses.add(ForgeTransferLosses.Kind.FLUID, got.getAmount() - back);
					}
					return 0;
				}
				return got.getAmount();
			}
		};
	}

	private static FluidAction action(boolean simulate) {
		return simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE;
	}
}
