package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The damage monitor's rule, Forge's form of NeoForge's {@code getHealthDamage() > 0} (MOD-962, slice 2.4). */
class ForgeServerWiringTest {

	@Test
	void aHitThatDealsDamageIsReported() {
		assertTrue(ForgeServerWiring.reportsHurt(false, 0.5f));
	}

	@Test
	void aCancelledHitIsNot() {
		assertFalse(ForgeServerWiring.reportsHurt(true, 4.0f));
	}

	@Test
	void aHitAbsorbedToNothingIsNot() {
		assertFalse(ForgeServerWiring.reportsHurt(false, 0.0f));
	}
}
