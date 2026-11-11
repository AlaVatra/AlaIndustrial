package dev.alaindustrial;

import dev.alaindustrial.compat.BonemealEventsForge;
import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.forge.ForgeBridges;
import dev.alaindustrial.core.forge.ForgeEnergyLookup;
import dev.alaindustrial.core.forge.ForgeEnergyTransactions;
import dev.alaindustrial.core.forge.ForgeFakePlayers;
import dev.alaindustrial.core.forge.ForgePlaceEvents;
import dev.alaindustrial.core.world.FakePlayers;
import dev.alaindustrial.core.world.PlaceEvents;
import dev.alaindustrial.lifecycle.CommonSetup;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.forge.ForgeNetworkDispatcher;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.forge.ModBlockEntitiesForge;
import dev.alaindustrial.registry.forge.ModBlocksForge;
import dev.alaindustrial.registry.forge.ModCreativeTabEventsForge;
import dev.alaindustrial.registry.forge.ModCreativeTabForge;
import dev.alaindustrial.registry.forge.ModCriteriaForge;
import dev.alaindustrial.registry.forge.ModDataComponentsForge;
import dev.alaindustrial.registry.forge.ModDataRegistriesForge;
import dev.alaindustrial.registry.forge.ModEffectsForge;
import dev.alaindustrial.registry.forge.ModEntitiesForge;
import dev.alaindustrial.registry.forge.ModFluidsForge;
import dev.alaindustrial.registry.forge.ModItemsForge;
import dev.alaindustrial.registry.forge.ModLootConditionsForge;
import dev.alaindustrial.registry.forge.ModLootModifiersForge;
import dev.alaindustrial.registry.forge.ModMenusForge;
import dev.alaindustrial.registry.forge.ModParticlesForge;
import dev.alaindustrial.registry.forge.ModProfessionsForge;
import dev.alaindustrial.registry.forge.ModRecipesForge;
import dev.alaindustrial.registry.forge.ModSoundsForge;
import dev.alaindustrial.registry.forge.ModWorldGenForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DataPackRegistryEvent;

/**
 * MinecraftForge entry point (MOD-962, slice 2.1: every registry of the shared manifest).
 *
 * <p><b>Order.</b> The same table of contents as {@code IndustrializationNeoForge}, and as load-bearing: install
 * the loader seams, put every {@code DeferredRegister} on the mod bus, bind the {@link ModContent} facade, check
 * nothing is left unbound, then the mod-bus events, then the server side ({@link ForgeServerWiring}: payloads,
 * player attachments, config, server hooks, the block-use chain, the command); on the physical client,
 * {@link IndustrializationForgeClient} (Forge's {@code @Mod} has no {@code dist}, so the client class is reached
 * behind a dist check). The fluid and item lookups arrive in slice 2.3; until then a block that needs an
 * uninstalled seam throws on use, which is the intended loud failure.
 *
 * <p><b>Forge differences</b> (sources of Forge 66.1.0): EventBus 7, so a mod-bus listener is
 * {@code X.getBus(busGroup).addListener(...)} and a global one {@code X.BUS.addListener(...)}; capabilities are
 * attached from outside through {@code AttachCapabilitiesEvent.BlockEntities}; the mixin configs are named by the
 * jar manifest, not by mods.toml.
 */
@Mod(Industrialization.MOD_ID)
public final class IndustrializationForge {

	public IndustrializationForge(FMLJavaModLoadingContext context) {
		BusGroup modBus = context.getModBusGroup();
		installLoaderSeams();
		registerDeferredRegisters(modBus);
		bindContentFacade();
		ModContent.verifyAllBound();
		registerModBusEvents(modBus);
		ForgeServerWiring.install();
		registerGameBusEvents();
		if (FMLEnvironment.dist == Dist.CLIENT) {
			IndustrializationForgeClient.init(context);
		}
		bootstrapGameTests(modBus);

		Industrialization.LOGGER.info("Industrialization (Forge) initialized.");
	}

	/**
	 * The energy seams (our transaction journal and the per-face capability lookup), the fluid and item bridges,
	 * the packet dispatcher, the machines' fake player, the place event and the bone-meal event.
	 */
	private static void installLoaderSeams() {
		EnergyTransactions.install(new ForgeEnergyTransactions());
		EnergyLookup.install(new ForgeEnergyLookup());
		ForgeBridges.install();
		NetworkDispatcher.install(new ForgeNetworkDispatcher());
		FakePlayers.install(new ForgeFakePlayers());
		PlaceEvents.install(new ForgePlaceEvents());
		BonemealEventsForge.install();
	}

	/**
	 * Every {@code DeferredRegister} on the mod bus, in NeoForge's order. The order of these calls is readability:
	 * Forge fires the vanilla registries in vanilla order (fluid before block before item), then the modded ones
	 * by name, whatever the order they were attached in.
	 */
	private static void registerDeferredRegisters(BusGroup modBus) {
		ModFluidsForge.register(modBus);
		ModBlocksForge.BLOCKS.register(modBus);
		ModEntitiesForge.register(modBus);
		ModItemsForge.ITEMS.register(modBus);
		ModBlockEntitiesForge.BLOCK_ENTITIES.register(modBus);
		ModMenusForge.MENUS.register(modBus);
		ModCreativeTabForge.TABS.register(modBus);
		ModSoundsForge.register(modBus);
		ModEffectsForge.register(modBus);
		ModParticlesForge.register(modBus);
		ModDataComponentsForge.DATA_COMPONENTS.register(modBus);
		ModRecipesForge.register(modBus);
		ModCriteriaForge.register(modBus);
		ModLootConditionsForge.register(modBus);
		ModProfessionsForge.register(modBus);
		// Before datapack load: the placed features and the oil lakes name these types.
		ModWorldGenForge.register(modBus);
	}

	/** Binds the registered holders into the loader-neutral {@link ModContent} facade, in NeoForge's order. */
	private static void bindContentFacade() {
		ModFluidsForge.init();
		ModBlocksForge.init();
		ModItemsForge.init();
		ModBlockEntitiesForge.init();
		ModMenusForge.init();
		ModDataComponentsForge.init();
		ModCreativeTabEventsForge.register();
	}

	/** Mod-bus listeners: the town's structure types, loot modifiers, datapack registries and the setup calls. */
	private static void registerModBusEvents(BusGroup modBus) {
		ModWorldGenForge.registerTownTypes(modBus);
		ModLootModifiersForge.register(modBus);
		DataPackRegistryEvent.NewRegistry.BUS.addListener(ModDataRegistriesForge::addAll);
		// The one-off setup calls every loader replays. DispenserBlock.registerBehavior writes an unsynchronised
		// map and Forge runs mod setup in parallel, hence enqueueWork (main thread).
		FMLCommonSetupEvent.getBus(modBus).addListener(event -> event.enqueueWork(() -> {
			for (CommonSetup.Step step : CommonSetup.STEPS) {
				step.run().run();
			}
		}));
	}

	/** Global-bus listeners: the energy, fluid and item capabilities of our block entities and item stacks. */
	private static void registerGameBusEvents() {
		ForgeBridges.registerCapabilityEvents();
	}

	/**
	 * The dev-only gametest lane ({@code ForgeGameTestBootstrap}) lives in the {@code gametest} source set, which dev
	 * runs load and the shipped jar does not contain, so it is reached by name: absent in production, a silent
	 * no-op; present but broken, a loud failure.
	 */
	private static void bootstrapGameTests(BusGroup modBus) {
		try {
			Class.forName("dev.alaindustrial.gametest.forge.ForgeGameTestBootstrap").getMethod("init", BusGroup.class)
					.invoke(null, modBus);
		} catch (ClassNotFoundException e) {
			// Shipped jar: the gametest source set is not packed.
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("ForgeGameTestBootstrap present but failed to initialise", e);
		}
	}
}
