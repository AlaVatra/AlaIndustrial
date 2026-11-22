package dev.alaindustrial.block.entity.machine;

import dev.alaindustrial.core.heat.HeatConsumer;
import dev.alaindustrial.core.heat.HeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The heat a burning generator gives the machine standing on it (MOD-988 for the fuel generator, MOD-994 for the
 * geothermal one): one rule written once and held by one field of each generator.
 *
 * <ul>
 *   <li>The generator heats only while it really burned on its last tick ({@link #isBurning}), never on the burn
 *       left: a paused generator has burn left and no fire.</li>
 *   <li>With a full buffer it keeps burning while the machine on top takes this heat and waits for it
 *       ({@link #wantedAbove}): the heat stays steady instead of blinking with the buffer.</li>
 *   <li>A change of the fire reaches the machine on top at once ({@link #update}).</li>
 * </ul>
 *
 * <p>{@code WorldHeatSources.resolve} reads a {@link Host} below a machine. Nothing is saved: the first tick
 * after a load sets the fire again.
 */
public final class FireHeat {

	/** A block entity whose fire heats the machine on top. */
	public interface Host {
		FireHeat fireHeat();
	}

	private final HeatSource source;
	private boolean burning;

	/** {@code source} is what the machine on top sees while the fire burns. */
	public FireHeat(HeatSource source) {
		this.source = source;
	}

	/** What this fire is to the machine on top. */
	public HeatSource source() {
		return source;
	}

	/** Whether fuel burned on the last tick: then this generator is a heat source for the machine on top. */
	public boolean isBurning() {
		return burning;
	}

	/**
	 * Whether the machine on top takes this heat and waits for it now. Asked only with a full buffer, where the
	 * answer decides between burning and pausing: one block-entity lookup and the machine's own status, nothing
	 * heavier.
	 */
	public boolean wantedAbove(Level level, BlockPos pos) {
		return level.getBlockEntity(pos.above()) instanceof HeatConsumer consumer
				&& consumer.acceptsHeatFrom(source) && consumer.isWaitingOnHeat();
	}

	/**
	 * Records whether the fire burned this tick and tells the machine on top the moment that changes. The lit
	 * state is written with {@code UPDATE_CLIENTS} only and notifies no neighbour, so without this a machine asleep
	 * on "no heat" would notice the fire one idle sleep late, and one still running would keep a heat that is gone.
	 * The flag is set before the call, so the machine's resolve already sees the new value.
	 */
	public void update(Level level, BlockPos pos, boolean now) {
		if (burning == now) {
			return;
		}
		burning = now;
		if (level.getBlockEntity(pos.above()) instanceof HeatConsumer consumer) {
			consumer.onHeatNeighbourChanged();
		}
	}
}
