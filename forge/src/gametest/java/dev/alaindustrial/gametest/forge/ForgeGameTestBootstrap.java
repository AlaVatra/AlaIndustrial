package dev.alaindustrial.gametest.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The single wiring point between the Forge entry point and the gametest source set (MOD-962 stage 3), the twin of
 * {@code NeoForgeGameTestBootstrap}. {@code IndustrializationForge} reaches {@link #init} by name: in dev runs the
 * gametest source set is on the classpath as the {@code alaindustrial_gametest} mod, in a shipped jar it is absent
 * and the lookup is a silent no-op.
 *
 * <p>Everything here is dev-only by construction: the test bodies go into {@code TEST_FUNCTION} in every dev run
 * (as on the other lanes); the test instances exist only on the game-test server ({@link RosterPack}); the foreign
 * stand-ins additionally guard themselves with {@code FMLLoader.isProduction()}.
 */
public final class ForgeGameTestBootstrap {

	private ForgeGameTestBootstrap() {
	}

	/** Entry point, invoked reflectively from {@code IndustrializationForge} with the mod bus group. */
	public static void init(BusGroup modBus) {
		ScenarioRosterForge.init(modBus); // the roster bodies into TEST_FUNCTION
		ForgeGameTests.init(modBus);
		RosterPack.install();
		GameTestServerLanguages.install();
		ForeignMaterialStandIn.register(modBus);
		ForeignEnergyItemStandIn.install();
		ForeignWaterItemStandIn.install();
		// The client class is touched only on the physical client.
		if (FMLEnvironment.dist == Dist.CLIENT) {
			ForgeClientAudit.install();
		}
	}
}
