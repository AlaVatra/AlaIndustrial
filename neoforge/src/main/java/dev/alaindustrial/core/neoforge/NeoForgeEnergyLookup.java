package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.core.energy.EnergyRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * NeoForge implementation of the neutral {@link EnergyLookup} SPI (MOD-022 Phase 2): resolves the
 * per-face energy port through {@code Capabilities.Energy.BLOCK} (verified against 26.2.0.67:
 * {@code BlockCapability<EnergyHandler, @Nullable Direction>}) and wraps the result as a neutral
 * {@link EnergyPort}. This is the per-loader capability-lookup seam.
 *
 * <p><b>Role preservation (this is load-bearing).</b> The network classifies each endpoint as producer
 * and/or consumer from its port's {@link EnergyPort#supportsInsertion()}/{@link EnergyPort#supportsExtraction()}.
 * On Fabric those survive the {@code EnergyStorage.SIDED} round-trip because Team Reborn's storage exposes
 * {@code supports*}. NeoForge's {@link EnergyHandler} has <em>no</em> {@code supports*} predicate, so routing
 * our own blocks through {@code Capabilities.Energy.BLOCK} would erase the per-face {@link EnergyRole} and
 * every face would read as {@link EnergyRole#BOTH} — the network would then treat every consumer as a
 * producer too, {@code computeProducerField} would seed distance 1 everywhere, and MOD-021 cable loss
 * would floor to 0 (diverging from Fabric). Since all our machines implement {@link EnergyPortHost}, we take
 * the face-scoped {@link EnergyPort} straight from the block entity (role intact) and only fall back to the
 * capability — with the role it shows in a dry run ({@link #foreign}, MOD-1026) — for blocks that
 * are not ours. Caught by the NeoForge world gametest {@code mod021_loss_over_ten_cables}.
 */
public final class NeoForgeEnergyLookup implements EnergyLookup {

	@Override
	public EnergyPort find(Level level, BlockPos pos, Direction side) {
		if (level.getBlockEntity(pos) instanceof EnergyPortHost host) {
			return host.energyPort(side); // role-carrying face port (null on a NONE face)
		}
		return foreign(level.getCapability(Capabilities.Energy.BLOCK, pos, side));
	}

	/**
	 * Another mod's handler as a port, with the role it shows (MOD-1026). NeoForge's {@link EnergyHandler} has no
	 * "can it ever take / give" predicate — "the only way to know if a handler will accept a resource, is to try to
	 * insert it" (its javadoc) — while Fabric's storage and Forge's {@code IEnergyStorage} answer it. Filed as
	 * {@link EnergyRole#BOTH}, a machine that only takes was a generator of the line too: the network never slept
	 * beside it and the analyzer listed it among the producers.
	 *
	 * <p>A direction is ruled out only where the handler had to say yes if it had it: it holds energy and a dry run
	 * of extracting one unit gives nothing, or it has room and a dry run of inserting one takes nothing. An empty or a
	 * full handler cannot be told, so it keeps the direction, as before. A handler that refuses both keeps both.
	 *
	 * <p>Every role read this way is {@linkplain EnergyPort#roleIsProvisional() provisional}: it answers for the
	 * moment of the try, so the network probes the block again every
	 * {@code ProvisionalRoleClock.RECHECK_TICKS} ticks rather than keeping a battery that had just spent its
	 * per-tick limit filed as a pure consumer until its cables change.
	 */
	static EnergyPort foreign(EnergyHandler handler) {
		if (handler == null) {
			return null;
		}
		long amount = handler.getAmountAsLong();
		boolean in = amount >= handler.getCapacityAsLong() || dryRun(handler, true);
		boolean out = amount <= 0 || dryRun(handler, false);
		EnergyRole role = in && out || !in && !out ? EnergyRole.BOTH : in ? EnergyRole.IN : EnergyRole.OUT;
		return new NeoForgeEnergyPort(handler, role, true);
	}

	/**
	 * Whether one unit goes in ({@code insert}) or out, in a transaction that is never committed: nested in the
	 * caller's when one is open (a lookup runs inside the network's simulations too), a root otherwise. While the
	 * thread's transactions are closing nothing may be opened, and the answer is "yes", the old role. A handler that
	 * throws answers "yes" as well.
	 */
	@SuppressWarnings("deprecation") // NeoForge's own way in for a call that is not handed the open transaction
	private static boolean dryRun(EnergyHandler handler, boolean insert) {
		Transaction.Lifecycle life = Transaction.getLifecycle();
		if (life != Transaction.Lifecycle.NONE && life != Transaction.Lifecycle.OPEN) {
			return true;
		}
		try (Transaction probe = life == Transaction.Lifecycle.NONE ? Transaction.openRoot()
				: Transaction.open(Transaction.getCurrentOpenedTransaction())) {
			return (insert ? handler.insert(1, probe) : handler.extract(1, probe)) > 0;
		} catch (RuntimeException e) {
			return true;
		}
	}
}
