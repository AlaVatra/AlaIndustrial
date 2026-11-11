package dev.alaindustrial;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.ClientContentManifest;
import dev.alaindustrial.client.ClientDisconnectReset;
import dev.alaindustrial.client.ClientSetup;
import dev.alaindustrial.client.JetpackFlight;
import dev.alaindustrial.client.ModKeyMappings;
import dev.alaindustrial.client.dashboard.InventoryProfileButton;
import dev.alaindustrial.client.forge.ForgePiezoPlateModels;
import dev.alaindustrial.client.forge.ForgeRootSoilModels;
import dev.alaindustrial.client.render.CableGhostGizmos;
import dev.alaindustrial.client.screen.AlaConfigScreen;
import dev.alaindustrial.client.screen.MenuScreenManifest;
import dev.alaindustrial.client.tooltip.MachineTooltips;
import dev.alaindustrial.client.tooltip.PouchClientTooltip;
import dev.alaindustrial.item.energy.PouchTooltip;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * MinecraftForge client entry point (MOD-962, slice 2.2): replays every shared client list, the twin of
 * {@code IndustrializationNeoForgeClient} and of the Fabric {@code IndustrializationClient}.
 *
 * <p>Forge's {@code @Mod} has no {@code dist}, so {@link IndustrializationForge} calls {@link #init} behind a
 * {@code FMLEnvironment.dist == Dist.CLIENT} guard; a dedicated server never loads this class. Every listener is
 * registered from the mod constructor, before any of its events fires (Forge 66.1.0: the mod is constructed in
 * {@code ClientModLoader.begin}, ahead of the particle, GUI, key-mapping and renderer events of {@code Minecraft}'s
 * constructor and of the first resource reload).
 *
 * <p><b>Order.</b> NeoForge's table of contents: client config, the setup steps, content listeners, client hooks,
 * input and HUD. Two places differ from NeoForge, both because of Forge:
 * <ul>
 *   <li>{@link ClientSetup#STEPS} run as {@code FMLConstructModEvent} work ({@code enqueueWork}: the main thread,
 *       after every mod is constructed, inside {@code ClientModLoader.begin}, so before the first resource reload),
 *       not in {@code FMLClientSetupEvent}: Forge fires that event from inside the first resource reload, in
 *       parallel with the item-model parsing the two item-model steps must precede. Not in the constructor either:
 *       Forge constructs mods on parallel worker threads, and the steps write vanilla id mappers. No step reads a
 *       registry.</li>
 *   <li>The menu screens bind in {@code FMLClientSetupEvent} (Forge has no screen-registration event) through
 *       vanilla's {@code MenuScreens.register}, which Forge's access transformer makes public; on the main thread,
 *       because the screen map is a plain {@code HashMap}.</li>
 * </ul>
 * World overlays, block outlines and the block-entity culling boxes have no Forge event; the client mixins of
 * {@code alaindustrial.forge-client.mixins.json} hook them. Payload handlers and player-attachment client caches
 * arrive with slices 2.4 and 2.5.
 */
final class IndustrializationForgeClient {

	private IndustrializationForgeClient() {
	}

	static void init(FMLJavaModLoadingContext context) {
		BusGroup modBus = context.getModBusGroup();
		initClientConfig(context);
		// Forge constructs mods in parallel (CONSTRUCT is a ParallelTransition): the steps write vanilla id mappers,
		// so they run as construct-event work, on the main thread, before ClientModLoader.begin returns and the
		// first resource reload is created.
		FMLConstructModEvent.getBus(modBus).addListener(event -> event.enqueueWork(() -> {
			for (ClientSetup.Step step : ClientSetup.STEPS) {
				step.run().run();
			}
		}));
		registerContentListeners(modBus);
		registerClientHooks();
		registerInputAndHud();
		// Slices 2.4/2.5: bind each player attachment's client cache to its synced copy, clear it on logout.
		dev.alaindustrial.network.forge.ForgeClientNetwork.install();

		Industrialization.LOGGER.info("Industrialization (Forge client) initialized.");
	}

	/** The client config and the screen the mod list opens for it. */
	private static void initClientConfig(FMLJavaModLoadingContext context) {
		AlaClientConfig.init(FMLPaths.CONFIGDIR.get());
		context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory(parent -> new AlaConfigScreen(parent)));
	}

	/**
	 * Menu screens, the root and plate models, fluid models and particles, block tints, the pouch tooltip,
	 * block-entity and entity renderers and model layers.
	 */
	private static void registerContentListeners(BusGroup modBus) {
		FMLClientSetupEvent.getBus(modBus).addListener(event ->
				event.enqueueWork(IndustrializationForgeClient::registerMenuScreens));
		ForgeRootSoilModels.init();
		ModelEvent.ModifyBakingResult.BUS.addListener(ForgeRootSoilModels::onBake);
		ModelEvent.ModifyBakingResult.BUS.addListener(ForgePiezoPlateModels::onBake);
		ModelEvent.BakeFluidModels.BUS.addListener(IndustrializationForgeClient::bakeFluidModels);
		RegisterParticleProvidersEvent.BUS.addListener(IndustrializationForgeClient::registerParticles);
		RegisterColorHandlersEvent.Block.BUS.addListener(event -> {
			for (ClientContentManifest.BlockTintDef def : ClientContentManifest.BLOCK_TINTS) {
				event.register(def.sources(), def.block().get());
			}
		});
		RegisterClientTooltipComponentFactoriesEvent.BUS.addListener(event ->
				event.register(PouchTooltip.class, PouchClientTooltip::new));
		EntityRenderersEvent.RegisterRenderers.BUS.addListener(IndustrializationForgeClient::registerRenderers);
		EntityRenderersEvent.RegisterLayerDefinitions.BUS.addListener(event -> {
			for (ClientContentManifest.ModelLayerDef def : ClientContentManifest.MODEL_LAYERS) {
				event.registerLayerDefinition(def.location(), def.definition());
			}
		});
	}

	/** Binds each menu type to its screen from the shared manifest, as the Fabric client does. */
	private static void registerMenuScreens() {
		MenuScreenManifest.ScreenRegistrar registrar = new MenuScreenManifest.ScreenRegistrar() {
			@Override
			public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(
					MenuType<M> menuType, MenuScreenManifest.ScreenFactory<M, U> screen) {
				MenuScreens.register(menuType, screen::create);
			}
		};
		for (MenuScreenManifest.ScreenDef<?, ?> def : MenuScreenManifest.SCREENS) {
			def.bindTo(registrar);
		}
	}

	/**
	 * The fluid models. Forge's event takes a baked model, so each shared unbaked model is baked with the event's
	 * material baker; one model serves the still and the flowing fluid.
	 */
	private static void bakeFluidModels(ModelEvent.BakeFluidModels event) {
		for (ClientContentManifest.FluidModelDef def : ClientContentManifest.FLUID_MODELS) {
			FluidModel baked = def.model().bake(event.materials(), () -> Industrialization.MOD_ID + ":" + def.still());
			event.register(def.stillFluid(), baked);
			Fluid flowing = def.flowingFluid();
			if (flowing != null) {
				event.register(flowing, baked);
			}
		}
	}

	private static void registerParticles(RegisterParticleProvidersEvent event) {
		ClientContentManifest.ParticleRegistrar registrar = new ClientContentManifest.ParticleRegistrar() {
			@Override
			public <T extends ParticleOptions> void register(ParticleType<T> type,
					Function<SpriteSet, ParticleProvider<T>> factory) {
				event.registerSpriteSet(type, factory::apply);
			}
		};
		for (ClientContentManifest.ParticleProviderDef<?> def : ClientContentManifest.PARTICLE_PROVIDERS) {
			def.bindTo(registrar);
		}
	}

	/** Block-entity renderers, then the mobs and the other entities, from the shared lists. */
	private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		ClientContentManifest.RendererRegistrar registrar = new ClientContentManifest.RendererRegistrar() {
			@Override
			public <T extends BlockEntity, S extends BlockEntityRenderState> void register(BlockEntityType<T> type,
					BlockEntityRendererProvider<T, S> provider) {
				event.<T, S>registerBlockEntityRenderer(type, provider);
			}
		};
		for (ClientContentManifest.BlockEntityRendererDef<?, ?> def : ClientContentManifest.BLOCK_ENTITY_RENDERERS) {
			def.bindTo(registrar);
		}
		ClientContentManifest.EntityRendererRegistrar entities = new ClientContentManifest.EntityRendererRegistrar() {
			@Override
			public <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
				event.registerEntityRenderer(type, provider);
			}
		};
		for (ClientContentManifest.MobRendererDef<?> def : ClientContentManifest.MOB_RENDERERS) {
			def.bindTo(entities);
		}
		for (ClientContentManifest.EntityRendererDef<?> def : ClientContentManifest.ENTITY_RENDERERS) {
			def.bindTo(entities);
		}
	}

	/** Machine tooltips and the cable ghost; overlays and outlines come from the client mixins. */
	private static void registerClientHooks() {
		ItemTooltipEvent.BUS.addListener(event -> MachineTooltips.append(event.getItemStack(), event.getToolTip(),
				Minecraft.getInstance().hasShiftDown()));
		TickEvent.ClientTickEvent.Post.BUS.addListener(event -> CableGhostGizmos.tick());
	}

	/**
	 * Key mappings, picture-in-picture renderers and HUD layers (shared lists), the client-tick input steps, the
	 * profile button and the world-leave reset. Only the mappings are registered, not their category: the vanilla
	 * {@code KeyMapping.Category.register} in {@link ModKeyMappings} already lists it, and Forge has no category
	 * registration of its own.
	 */
	private static void registerInputAndHud() {
		RegisterKeyMappingsEvent.BUS.addListener(event -> {
			for (KeyMapping mapping : ClientContentManifest.KEY_MAPPINGS) {
				event.register(mapping);
			}
		});
		RegisterPictureInPictureRendererEvent.BUS.addListener(event -> {
			for (ClientContentManifest.PipRendererDef<?> def : ClientContentManifest.PICTURE_IN_PICTURE_RENDERERS) {
				event.register(def.factory().get());
			}
		});
		AddGuiOverlayLayersEvent.BUS.addListener(IndustrializationForgeClient::addHudLayers);
		TickEvent.ClientTickEvent.Post.BUS.addListener(event -> ModKeyMappings.handleInput());
		TickEvent.ClientTickEvent.Post.BUS.addListener(event -> JetpackFlight.clientTick());
		// The survival inventory only: the creative inventory is another screen class.
		ScreenEvent.Init.Post.BUS.addListener(event -> {
			if (event.getScreen() instanceof InventoryScreen) {
				event.addListener(InventoryProfileButton.install(event.getScreen()));
			}
		});
		ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> ClientDisconnectReset.run());
	}

	/**
	 * The HUD layers, in list order, which is drawing order. Forge sends the event once, for the root stack;
	 * {@code add(root, name, layer)} appends to the end of the root, so a layer draws over everything vanilla
	 * draws, the spot of NeoForge's {@code registerAboveAll}.
	 */
	private static void addHudLayers(AddGuiOverlayLayersEvent event) {
		ForgeLayeredDraw draw = event.getLayeredDraw();
		if (!ForgeLayeredDraw.VANILLA_ROOT.equals(draw.getName())) {
			return;
		}
		for (ClientContentManifest.HudLayerDef def : ClientContentManifest.HUD_LAYERS) {
			switch (def.placement()) {
				case LAST -> draw.add(ForgeLayeredDraw.VANILLA_ROOT, Industrialization.id(def.id()),
						def.renderer()::render);
			}
		}
	}
}
