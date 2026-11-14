package dev.alaindustrial.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.energy.EnergyTier;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage of the Mob Beacon tier ladder (MOD-968): each tier reads its own knobs, the owner's radii 16, 32, 48,
 * and everything grows with the tier.
 */
class MobBeaconTierTest {

	@Test
	void eachTierReadsItsOwnDefaults() {
		assertEquals(16, MobBeaconTier.LV.range());
		assertEquals(32, MobBeaconTier.MV.range());
		assertEquals(48, MobBeaconTier.HV.range());
		assertEquals(4, MobBeaconTier.LV.euPerTick());
		assertEquals(16, MobBeaconTier.MV.euPerTick());
		assertEquals(32, MobBeaconTier.HV.euPerTick());
		assertEquals(2000, MobBeaconTier.LV.buffer());
		assertEquals(8000, MobBeaconTier.MV.buffer());
		assertEquals(32000, MobBeaconTier.HV.buffer());
	}

	@Test
	void theVoltageTierIsTheBeaconTier() {
		assertEquals(EnergyTier.LV, MobBeaconTier.LV.energyTier());
		assertEquals(EnergyTier.MV, MobBeaconTier.MV.energyTier());
		assertEquals(EnergyTier.HV, MobBeaconTier.HV.energyTier());
	}

	@Test
	void everythingGrowsWithTheTier() {
		MobBeaconTier[] tiers = MobBeaconTier.values();
		for (int i = 1; i < tiers.length; i++) {
			MobBeaconTier lower = tiers[i - 1];
			MobBeaconTier upper = tiers[i];
			assertTrue(upper.range() > lower.range(), upper + " must reach further than " + lower);
			assertTrue(upper.euPerTick() > lower.euPerTick(), upper + " must cost more than " + lower);
			assertTrue(upper.buffer() > lower.buffer(), upper + " must hold more than " + lower);
		}
	}
}
