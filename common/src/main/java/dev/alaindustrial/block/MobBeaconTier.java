package dev.alaindustrial.block;

import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.entity.MobBeaconConfig;

/**
 * The three Mob Beacon tiers (MOD-968). The tier of a beacon is its voltage tier, as for the mob repeller (MOD-278):
 * radius, upkeep and buffer grow with it, each read from its knob at the moment it is asked, so a config reload
 * reaches the beacons already placed.
 */
public enum MobBeaconTier {
	LV(EnergyTier.LV),
	MV(EnergyTier.MV),
	HV(EnergyTier.HV);

	private final EnergyTier energyTier;

	MobBeaconTier(EnergyTier energyTier) {
		this.energyTier = energyTier;
	}

	/** The voltage tier: what the beacon accepts per tick. */
	public EnergyTier energyTier() {
		return energyTier;
	}

	/** Zone radius in blocks (a cube around the block). */
	public int range() {
		return switch (this) {
			case LV -> MobBeaconConfig.mobBeaconRange;
			case MV -> MobBeaconConfig.mobBeaconRangeMv;
			case HV -> MobBeaconConfig.mobBeaconRangeHv;
		};
	}

	/** Upkeep in EU per tick while the beacon runs. */
	public int euPerTick() {
		return switch (this) {
			case LV -> MobBeaconConfig.mobBeaconEuPerTick;
			case MV -> MobBeaconConfig.mobBeaconEuPerTickMv;
			case HV -> MobBeaconConfig.mobBeaconEuPerTickHv;
		};
	}

	/** Internal EU buffer. */
	public int buffer() {
		return switch (this) {
			case LV -> MobBeaconConfig.mobBeaconBuffer;
			case MV -> MobBeaconConfig.mobBeaconBufferMv;
			case HV -> MobBeaconConfig.mobBeaconBufferHv;
		};
	}
}
