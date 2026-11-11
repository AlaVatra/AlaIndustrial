package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.BlockCapabilityParityScenarios;
import dev.alaindustrial.gametest.CableEnergyScenarios;
import dev.alaindustrial.gametest.IndustrialistScenarios;
import dev.alaindustrial.gametest.ItemPipeScenarios;
import dev.alaindustrial.gametest.PayloadRegistrationScenarios;
import dev.alaindustrial.gametest.RosterEntry;
import dev.alaindustrial.gametest.RosterReplayScenarios;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The hand-wired tests of the Forge gametest lane (MOD-962 stage 3), line by line the twin of
 * {@code NeoForgeGameTests.register}: the same ids, ticks and bodies, with Forge's own bodies where the NeoForge
 * one names a NeoForge API, then the Forge-only checks ({@code forge_*}). Every common scenario that is wired
 * exactly once per lane runs from the roster ({@link ScenarioRosterForge}) instead.
 *
 * <p>The bodies go into {@code TEST_FUNCTION} from {@link RegisterEvent}; the instances into the roster pack
 * ({@link RosterPack}), from the same list.
 */
public final class ForgeGameTests {

	private static final Logger LOGGER = LoggerFactory.getLogger("alaindustrial/scenario-roster");

	private static @Nullable List<ForgeTestRegistrar.Test> tests;

	private ForgeGameTests() {}

	/** Wired from {@link ForgeGameTestBootstrap#init}. */
	static void init(BusGroup modBus) {
		RegisterEvent.getBus(modBus).addListener(event ->
				event.<Consumer<GameTestHelper>>register(Registries.TEST_FUNCTION, helper -> {
					for (ForgeTestRegistrar.Test test : tests()) {
						helper.register(test.id(), test.body());
					}
					LOGGER.info("[scenario roster] forge: {} hand-wired test functions registered", tests().size());
				}));
	}

	/** Writes the hand-wired instances into the roster pack. */
	static void registerInstances(RosterPackWriter writer) {
		for (ForgeTestRegistrar.Test test : tests()) {
			writer.functionTest(test.id(), test.data(), test.environment(), "ForgeGameTests");
		}
	}

	private static synchronized List<ForgeTestRegistrar.Test> tests() {
		if (tests == null) {
			ForgeTestRegistrar event = new ForgeTestRegistrar();
			register(event);
			tests = event.tests();
		}
		return tests;
	}

	private static void register(ForgeTestRegistrar event) {
		// MOD-062 Industrialist: the per-loader seams (POI state map, trade sets, village pool injection).
		registerTest(event, "industrialist_workbench_poi_mapping", 40, true,
				IndustrialistScenarios::workbenchStateMapsToPoi);

		// A vanilla furnace neighbour must not NPE during endpoint discovery (loader-capability null-safety).
		registerTest(event, "cable_vanilla_neighbor_no_npe", 100, true,
				CableEnergyScenarios::cableVanillaNeighborNoNpe);

		// Network split + rejoin (R-CON-04, R-CON-09): break stops delivery, replace resumes flow.
		registerTest(event, "network_split_rejoin_resumes_flow", 200, true,
				CableEnergyScenarios::networkSplitRejoinResumesFlow);

		// MOD-084: the Forge half of the cross-mod bridge, bodies on ForgeCapabilities.ENERGY of the stack.
		registerTest(event, "xmod_foreign_charger_fills_pouch", 40, true,
				ItemEnergyCapabilityScenariosForge::fun01ForeignChargerFillsPouch);
		registerTest(event, "xmod_foreign_machine_cannot_drain", 40, true,
				ItemEnergyCapabilityScenariosForge::neg01ForeignMachineCannotDrain);
		registerTest(event, "xmod_pack_charges_foreign_item", 40, true,
				ItemEnergyCapabilityScenariosForge::fun02PackChargesForeignItem);
		// MOD-372: every powered item of the mod reachable through the capability.
		registerTest(event, "xmod_every_powered_item_exposes_capability", 40, true,
				ItemEnergyCapabilityScenariosForge::reg01EveryPoweredItemExposesCapability);
		// MOD-906: reinforced bone meal against Forge's BonemealEvent (ALLOW spends one meal on Forge).
		registerTest(event, "reinforced_bone_meal_event_handled", 40, true,
				BonemealEventScenariosForge::handledByAnotherModSpendsNothing);
		registerTest(event, "reinforced_bone_meal_event_denied", 40, true,
				BonemealEventScenariosForge::deniedByAnotherModSpendsNothing);
		// MOD-871: a slingshot torch against Forge's BlockEvent.EntityPlaceEvent.
		registerTest(event, "slingshot_place_event_cancelled_drops_torch", 40, true,
				SlingshotPlaceEventScenariosForge::cancelledPlaceEventDropsTheTorch);

		// MOD-193: the furnace family through the sided item path (Forge: the BaseContainerBlockEntity mixin).
		registerTest(event, "item_pipe_vanilla_furnace_bottom_extract", 60, true,
				h -> ItemPipeScenarios.extractsFurnaceResultFromBottom(h, Blocks.FURNACE,
						"vanilla furnace bottom extract"));
		registerTest(event, "item_pipe_iron_furnace_bottom_extract", 60, true,
				h -> ItemPipeScenarios.extractsFurnaceResultFromBottom(h, ModContent.IRON_FURNACE.get(),
						"iron furnace bottom extract"));

		// MOD-433: the capability Forge hands out on a block-entity face is exactly the port the block entity
		// exposes there — asked through BlockEntity.getCapability, the way a foreign mod would.
		registerTest(event, "block_capabilities_match_ports", 40, true,
				helper -> BlockCapabilityParityScenarios.capabilitiesMatchPorts(helper, CAPABILITY_PROBES));

		// MOD-706: every payload of ModPayloads.PAYLOADS registered in its direction, asked of Forge's channel.
		registerTest(event, "payloads_registered_in_their_direction", 40, true,
				helper -> PayloadRegistrationScenarios.everyPayloadRegisteredInItsDirection(
						helper, ForgePayloadProbe.INSTANCE));

		// MOD-717: the roster floor — hand-wired, it must outlive the roster it watches.
		registerTest(event, "roster_is_replayed_on_this_lane", 20, true,
				helper -> RosterReplayScenarios.rosterIsReplayedOnThisLane(helper, RosterEntry.Lane.FORGE));

		// MOD-705: scenarios of this Minecraft line only (the class differs per line).
		LineOnlyRegistrations.register(event);

		// Forge-only checks (MOD-962; moved from the stage-2 probe): the bridges of slice 2.3,
		registerTest(event, "forge_capabilities_invalidated_on_removal", 40, true,
				ForgeBridgeScenarios::capabilitiesInvalidatedOnRemoval);
		registerTest(event, "forge_furnace_faces_through_hopper_path", 40, true,
				ForgeBridgeScenarios::furnaceFacesThroughHopperPath);
		registerTest(event, "forge_chest_pairs_join_and_split", 40, true,
				ForgeBridgeScenarios::chestPairsJoinAndSplit);
		registerTest(event, "forge_tank_snapshot_and_lying_foreign_tank", 40, true,
				ForgeBridgeScenarios::tankSnapshotAndLyingForeignTank);
		registerTest(event, "forge_provider_found_through_own_capability", 40, true,
				ForgeBridgeScenarios::providerFoundThroughOwnCapability);
		registerTest(event, "forge_item_fluid_bridge", 40, true,
				ForgeBridgeScenarios::itemFluidBridge);
		// the server wiring of slices 2.4 and 2.5,
		registerTest(event, "forge_config_file_and_command_exist", 40, true,
				ForgeWiringScenarios::configFileAndCommandExist);
		// posts a level unload for the overworld (every per-level cache is swept): alone in its environment.
		registerInEnvironment(event, "forge_fake_player_contract", 40, true, ALONE_ENVIRONMENT,
				ForgeWiringScenarios::fakePlayerContract);
		registerTest(event, "forge_attachments_survive_save_and_clone", 40, true,
				ForgeWiringScenarios::attachmentsSurviveSaveAndClone);
		registerTest(event, "forge_place_event_refuses_and_restores", 40, true,
				ForgeWiringScenarios::placeEventRefusesAndRestores);
		registerTest(event, "forge_bonemeal_gate_contract", 40, true,
				ForgeWiringScenarios::bonemealGateContract);
		registerTest(event, "forge_use_block_hook_cancels_with_result", 40, true,
				ForgeWiringScenarios::useBlockHookCancelsWithResult);
		registerTest(event, "forge_payload_channel_single_instance", 40, true,
				ForgeWiringScenarios::payloadChannelSingleInstance);
		// and what Forge itself loads.
		registerTest(event, "forge_datapack_loaded", 40, true,
				ForgeLoaderScenarios::datapackLoaded);
		registerTest(event, "forge_uncovered_registries_hold_ours", 40, true,
				ForgeLoaderScenarios::uncoveredRegistriesHoldOurs);
		registerTest(event, "forge_mixin_audit_passes", 40, true,
				ForgeLoaderScenarios::mixinAuditPasses);
		registerTest(event, "forge_fluid_types_follow_the_manifest", 40, true,
				ForgeLoaderScenarios::fluidTypesFollowTheManifest);
		registerTest(event, "forge_biome_modifiers_place_features", 40, true,
				ForgeLoaderScenarios::biomeModifiersPlaceFeatures);
		// loader parity (MOD-962): crops follow the game's soil tags, which Forge's own patches dropped.
		registerTest(event, "forge_crops_take_tagged_soil", 40, true,
				CropSoilScenariosForge::cropsTakeTaggedSoil);
		registerTest(event, "forge_crop_soil_bonus_follows_vanilla_tag", 40, true,
				CropSoilScenariosForge::soilBonusFollowsVanillaTag);
		// MOD-962: a foreign charger cannot swap a full crystal blank on Forge; the holder's inventory tick does.
		registerTest(event, "forge_foreign_charged_blank_finishes_in_inventory", 40, true,
				ItemEnergyCapabilityScenariosForge::crystalBlankFilledByForeignChargerFinishesInInventory);
	}

	/** An environment no other test names: a test in it runs in a batch of its own. */
	private static final Identifier ALONE_ENVIRONMENT = Industrialization.id("forge_alone");

	/** The three Forge probes for {@link BlockCapabilityParityScenarios} (MOD-433). */
	private static final BlockCapabilityParityScenarios.Probes CAPABILITY_PROBES =
			new BlockCapabilityParityScenarios.Probes(
					(level, pos, side) -> present(level.getBlockEntity(pos), ForgeCapabilities.ENERGY, side),
					(level, pos, side) -> present(level.getBlockEntity(pos), ForgeCapabilities.FLUID_HANDLER, side),
					(level, pos, side) -> present(level.getBlockEntity(pos), ForgeCapabilities.ITEM_HANDLER, side));

	private static boolean present(@Nullable BlockEntity be, Capability<?> capability,
			net.minecraft.core.@Nullable Direction side) {
		return be != null && be.getCapability(capability, side).isPresent();
	}

	/**
	 * Register one hand-wired test under the alaindustrial namespace, on the rig, in the lane's default
	 * environment. The first parameter is named {@code event} as on NeoForge, so one reader parses both lanes.
	 */
	static void registerTest(ForgeTestRegistrar event, String name, int maxTicks, boolean required,
			Consumer<GameTestHelper> body) {
		event.add(name, maxTicks, required, ScenarioRosterForge.DEFAULT_ENVIRONMENT, body);
	}

	/** {@link #registerTest} in an environment of its own choosing (the pack writes it, empty). */
	static void registerInEnvironment(ForgeTestRegistrar event, String name, int maxTicks, boolean required,
			Identifier environment, Consumer<GameTestHelper> body) {
		event.add(name, maxTicks, required, environment, body);
	}
}
