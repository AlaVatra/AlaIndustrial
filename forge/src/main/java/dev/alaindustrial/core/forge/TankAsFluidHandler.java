package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import java.util.function.Supplier;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * One face of our tank seen by another mod as {@link IFluidHandler}, the Forge twin of NeoForge's
 * {@code TankAsResourceHandler}: one tank (index 0), each fill or drain its own root transaction ({@code SIMULATE}
 * a dry run, {@code EXECUTE} committed). The port is resolved on every call, as in {@link PortAsEnergyStorage},
 * and a call made while one of our transactions is open on this thread is refused.
 *
 * <p>A {@link FluidStack} carrying a tag is refused: our tanks hold untagged fluid, and storing it would strip the
 * tag without a word.
 */
public final class TankAsFluidHandler implements IFluidHandler {

	private final Supplier<FluidPort> port;

	public TankAsFluidHandler(Supplier<FluidPort> port) {
		this.port = port;
	}

	@Override
	public int getTanks() {
		return port.get() == null ? 0 : 1;
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		FluidPort face = port.get();
		if (face == null || tank != 0 || face.fluid().isEmpty() || face.getAmount() <= 0) {
			return FluidStack.EMPTY;
		}
		return new FluidStack(face.fluid().fluid(), PortAsEnergyStorage.toInt(face.getAmount()));
	}

	@Override
	public int getTankCapacity(int tank) {
		FluidPort face = port.get();
		return face == null || tank != 0 ? 0 : PortAsEnergyStorage.toInt(face.getCapacity());
	}

	/** A hint only, as the interface allows: the tank's own predicate decides on the real fill. */
	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		FluidPort face = port.get();
		if (face == null || tank != 0 || stack.isEmpty() || tagged(stack)) {
			return false;
		}
		FluidHolder current = face.fluid();
		return current.isEmpty() || current.is(stack.getFluid());
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		FluidPort face = port.get();
		if (face == null || resource.isEmpty() || tagged(resource) || ForgeTxn.isOpenOnThisThread()) {
			return 0;
		}
		FluidHolder fluid = FluidHolder.of(resource.getFluid());
		int amount = resource.getAmount();
		return PortAsEnergyStorage.toInt(PortAsEnergyStorage.run(action.simulate(),
				txn -> face.insert(fluid, amount, txn)));
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		if (resource.isEmpty() || tagged(resource)) {
			return FluidStack.EMPTY;
		}
		return drain(resource.getFluid(), resource.getAmount(), action);
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		FluidPort face = port.get();
		if (face == null || face.fluid().isEmpty()) {
			return FluidStack.EMPTY;
		}
		return drain(face.fluid().fluid(), maxDrain, action);
	}

	private FluidStack drain(Fluid fluid, int amount, FluidAction action) {
		FluidPort face = port.get();
		if (face == null || amount <= 0 || ForgeTxn.isOpenOnThisThread()) {
			return FluidStack.EMPTY;
		}
		FluidHolder holder = FluidHolder.of(fluid);
		int drained = PortAsEnergyStorage.toInt(PortAsEnergyStorage.run(action.simulate(),
				txn -> face.extract(holder, amount, txn)));
		return drained <= 0 ? FluidStack.EMPTY : new FluidStack(fluid, drained);
	}

	private static boolean tagged(FluidStack stack) {
		return stack.hasTag() && !stack.getTag().isEmpty();
	}
}
