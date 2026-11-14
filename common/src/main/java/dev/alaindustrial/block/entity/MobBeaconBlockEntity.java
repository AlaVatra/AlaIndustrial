package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.MobBeaconBlock;
import dev.alaindustrial.block.MobBeaconTier;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.PerTickDemand;
import dev.alaindustrial.entity.MobBeaconConfig;
import dev.alaindustrial.entity.MobBeaconGlow;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Mob Beacon's buffer and its upkeep (MOD-968). While it runs it pays its tier's upkeep every tick and, once per
 * {@link MobBeaconConfig#mobBeaconScanIntervalTicks}, has {@link MobBeaconGlow} give every mob in its zone a short
 * glow. It holds no item and opens no window, so it is an energy block entity, not a machine.
 *
 * <p><b>Off.</b> A redstone signal switches it off (the mob repeller's polarity, MOD-278), and so does an empty
 * buffer: it goes dark, pays nothing and sleeps; an energy delivery wakes it through the buffer's commit hook. Nothing
 * is taken back from the mobs: the glow it gave runs out on its own, which is the whole of "the outlines fade when the
 * beacon stops".
 */
public class MobBeaconBlockEntity extends EnergyBlockEntity implements PerTickDemand {

	private final MobBeaconTier beaconTier;

	/** Ticks until the next search of the zone; runtime only, a loaded beacon searches on its first powered tick. */
	private int scanCooldown;

	public MobBeaconBlockEntity(BlockPos pos, BlockState state) {
		this(tierOf(state), pos, state);
	}

	private MobBeaconBlockEntity(MobBeaconTier tier, BlockPos pos, BlockState state) {
		// A consumer: maxInsert = the tier's voltage, nothing flows back out.
		super(ModContent.MOB_BEACON_BE.get(), pos, state, tier.energyTier(), tier.buffer(),
				tier.energyTier().maxVoltage(), 0L);
		this.beaconTier = tier;
	}

	private static MobBeaconTier tierOf(BlockState state) {
		return state.getBlock() instanceof MobBeaconBlock beacon ? beacon.tier() : MobBeaconTier.LV;
	}

	/** Which of the three beacons this is. */
	public MobBeaconTier beaconTier() {
		return beaconTier;
	}

	/**
	 * Power comes in through the bottom face only (owner, 2026-11-14): the beacon stands on its socket like the
	 * vanilla beacon on its base, and the glass sides and top carry no port. {@link MobBeaconBlock} draws a cable
	 * arm toward the same single face.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == Direction.DOWN ? EnergyRole.IN : EnergyRole.NONE;
	}

	/** Its per-tick need for the network (MOD-789, ADR-049): the upkeep of its tier. */
	@Override
	public long perTickDemand() {
		return Math.max(1, beaconTier.euPerTick());
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		int cost = beaconTier.euPerTick();
		if (level.hasNeighborSignal(pos) || energy.getAmount() < cost) {
			updateLit(false);
			// Search at once when it comes back on, not a whole interval later.
			scanCooldown = 0;
			return IDLE_SLEEP_TICKS;
		}
		energy.drainInternal(cost);
		updateLit(true);
		setChanged();
		if (--scanCooldown <= 0) {
			int interval = Math.max(1, MobBeaconConfig.mobBeaconScanIntervalTicks);
			scanCooldown = interval;
			if (level instanceof ServerLevel serverLevel) {
				MobBeaconGlow.sweep(serverLevel, pos, beaconTier.range(), interval);
			}
		}
		return 0;
	}
}
