package dev.alaindustrial.gametest.forge;

import com.mojang.authlib.GameProfile;
import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.Invulnerability;
import dev.alaindustrial.core.forge.ForgeFakePlayer;
import dev.alaindustrial.core.forge.ForgeFakePlayers;
import dev.alaindustrial.core.world.FakePlayers;
import dev.alaindustrial.core.world.PlaceEvents;
import dev.alaindustrial.item.misc.ReinforcedBonemeal;
import dev.alaindustrial.network.ConfigSyncPayload;
import dev.alaindustrial.network.ModPayloads;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.forge.ForgeAttachmentSyncPayload;
import dev.alaindustrial.network.forge.ForgeNetwork;
import dev.alaindustrial.registry.ModPlayerAttachments;
import dev.alaindustrial.registry.forge.ForgePlayerAttachments;
import dev.alaindustrial.registry.forge.PlayerAttachmentContainer;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillSlot;
import dev.alaindustrial.skill.SkillStore;
import dev.alaindustrial.stats.PlayerModStats;
import dev.alaindustrial.stats.PlayerStatsStore;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.listener.EventListener;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkInstance;
import net.minecraftforge.network.NetworkRegistry;

/**
 * Forge-only gametests of the Forge server wiring (MOD-962; slices 2.4 and 2.5, moved from the stage-2 probe in
 * stage 3): the payload channel holds every shared payload on one channel instance, the config file and the command
 * exist, the fake player's contract, attachments through the entity's own save and the death clone, a cancelled place
 * event, the bone-meal gate, and the block-use hook reaching the roster. Hurt, death, join and leave need a real
 * player: dev client.
 *
 * <p>Each check works in its test's rig and leaves it as it found it (temporary listeners removed, the default game
 * mode restored). The fake-player check posts a level unload for the overworld, which sweeps every per-level cache of
 * the mod, so it runs in an environment of its own ({@link ForgeGameTests}): no other test shares its batch.
 */
final class ForgeWiringScenarios {

	private ForgeWiringScenarios() {
	}

	private static BlockPos cell(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(1, 2, 1));
	}

	static void payloadChannelSingleInstance(GameTestHelper helper) {
		ForgeChecks.run(helper, "payload channel", ForgeWiringScenarios::checkChannel);
	}

	static void configFileAndCommandExist(GameTestHelper helper) {
		ForgeChecks.run(helper, "config and command", () -> checkConfigAndCommand(helper.getLevel().getServer()));
	}

	static void fakePlayerContract(GameTestHelper helper) {
		ForgeChecks.run(helper, "fake players", () -> checkFakePlayers(helper.getLevel().getServer(),
				helper.getLevel()));
	}

	static void attachmentsSurviveSaveAndClone(GameTestHelper helper) {
		ForgeChecks.run(helper, "player attachments", () -> checkAttachments(helper.getLevel()));
	}

	static void placeEventRefusesAndRestores(GameTestHelper helper) {
		ForgeChecks.run(helper, "place events", () -> checkPlaceEvents(helper.getLevel(), cell(helper)));
	}

	static void bonemealGateContract(GameTestHelper helper) {
		ForgeChecks.run(helper, "bone-meal gate", () -> checkBonemeal(helper.getLevel(), cell(helper)));
	}

	static void useBlockHookCancelsWithResult(GameTestHelper helper) {
		ForgeChecks.run(helper, "block-use hook", () -> checkUseBlockHook(helper.getLevel(), cell(helper)));
	}

	/** Every shared payload plus the Forge-only sync, each registered once, all on the one channel. */
	private static List<String> checkChannel() {
		List<String> failures = new ArrayList<>();
		List<Identifier> expected = new ArrayList<>();
		for (ModPayloads.PayloadDef<?> def : ModPayloads.PAYLOADS) {
			expected.add(def.type().id());
		}
		expected.add(ForgeAttachmentSyncPayload.TYPE.id());
		List<Identifier> registered = ForgeNetwork.registeredIds();
		Set<NetworkInstance> instances = new HashSet<>();
		List<Identifier> unknownToForge = new ArrayList<>();
		for (Identifier id : registered) {
			NetworkInstance instance = NetworkRegistry.findTarget(id);
			if (instance == null) {
				unknownToForge.add(id);
			} else {
				instances.add(instance);
			}
		}
		ForgeChecks.log("channel: {} payload ids registered ({} shared + 1 Forge-only), {} channel instance(s)",
				registered.size(), ModPayloads.PAYLOADS.size(), instances.size());
		if (!registered.equals(expected)) {
			failures.add("payload channel ids differ from ModPayloads.PAYLOADS + forge_attachment_sync: " + registered);
		}
		if (!unknownToForge.isEmpty() || instances.size() != 1) {
			failures.add("payload ids not all on one Forge channel: unknown " + unknownToForge + ", instances "
					+ instances.size());
		}
		return failures;
	}

	private static List<String> checkConfigAndCommand(MinecraftServer server) {
		List<String> failures = new ArrayList<>();
		boolean configBound = FMLPaths.CONFIGDIR.get().resolve("alaindustrial.json").equals(Config.configPath.get());
		boolean configWritten = Files.isRegularFile(Config.configPath.get());
		boolean command = server.getCommands().getDispatcher().getRoot().getChild("ala") != null;
		ForgeChecks.log("config bound to the Forge config dir: {}, file written: {}; /ala registered: {}",
				configBound, configWritten, command);
		if (!configBound || !configWritten || !command) {
			failures.add("config/command wiring: bound=" + configBound + ", written=" + configWritten + ", /ala="
					+ command);
		}
		return failures;
	}

	/** Cached per level and profile, survival even in a creative world, invulnerable, cannot be sent to. */
	private static List<String> checkFakePlayers(MinecraftServer server, ServerLevel level) {
		List<String> failures = new ArrayList<>();
		if (!(FakePlayers.get() instanceof ForgeFakePlayers)) {
			failures.add("FakePlayers seam is " + FakePlayers.get().getClass().getName());
			return failures;
		}
		GameProfile profile = new GameProfile(UUID.fromString("00000000-0000-4000-8000-00000000a1a1"), "AlaProbe");
		ServerPlayer first = FakePlayers.get().get(level, profile);
		boolean cached = FakePlayers.get().get(level, profile) == first;
		boolean invulnerable = Invulnerability.isPermanent(first);
		// Its own throw-away advancements: no listener, so a trigger it fires credits nobody (NeoForge parity).
		boolean deafAdvancements = first.getAdvancements()
				.getTriggerMapForType(CriteriaTriggers.INVENTORY_CHANGED) == null;
		boolean noChannel;
		try {
			noChannel = !NetworkDispatcher.get().canSendToPlayer(first, ConfigSyncPayload.TYPE);
		} catch (RuntimeException e) {
			failures.add("canSendToPlayer threw on a fake player: " + e);
			noChannel = false;
		}

		GameType before = server.getWorldData().getGameType();
		GameType mode;
		server.getWorldData().setGameType(GameType.CREATIVE);
		try {
			mode = new ForgeFakePlayer(level, new GameProfile(UUID.randomUUID(), "AlaProbeCreative"))
					.gameMode.getGameModeForPlayer();
		} finally {
			server.getWorldData().setGameType(before);
		}

		// LEVEL_UNLOAD through Forge's event: the roster sweeps LevelStateRegistry, which holds the cache.
		LevelEvent.Unload.BUS.post(new LevelEvent.Unload(level));
		boolean freshAfterUnload = FakePlayers.get().get(level, profile) != first;

		ForgeChecks.log("fake player: cached {}, invulnerable {}, no channel {}, mode in a creative world {}, "
				+ "new after level unload {}, advancements without listeners {}", cached, invulnerable, noChannel,
				mode, freshAfterUnload, deafAdvancements);
		if (!cached || !invulnerable || !noChannel || mode != GameType.SURVIVAL || !freshAfterUnload
				|| !deafAdvancements) {
			failures.add("fake player contract: cached=" + cached + ", invulnerable=" + invulnerable + ", noChannel="
					+ noChannel + ", mode=" + mode + ", freshAfterLevelUnload=" + freshAfterUnload
					+ ", advancementsWithoutListeners=" + deafAdvancements);
		}
		return failures;
	}

	/**
	 * A value set through the common store survives the entity's own save and load, and a death clone takes over
	 * the copyOnDeath entries from the removed original, which is invalid again afterwards.
	 */
	private static List<String> checkAttachments(ServerLevel level) {
		List<String> failures = new ArrayList<>();
		ForgeFakePlayer owner = new ForgeFakePlayer(level, new GameProfile(UUID.randomUUID(), "AlaProbeSave"));
		PlayerAttachmentContainer fresh = ForgePlayerAttachments.container(owner);
		boolean hasContainer = fresh != null;
		boolean readIsRead = hasContainer && PlayerStatsStore.get(owner).equals(PlayerModStats.EMPTY)
				&& fresh.get(ModPlayerAttachments.PLAYER_STATS) == null;
		PlayerModStats stats = new PlayerModStats(900L, 300L, 2,
				Map.of(Industrialization.id("solar_panel"), 900L), 20L, 0L);
		PlayerSkills skills = PlayerSkills.EMPTY.with(SkillBranch.ENERGY, SkillSlot.IN);
		PlayerStatsStore.set(owner, stats);
		SkillStore.set(owner, skills);

		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		owner.saveWithoutId(out);
		ForgeFakePlayer loaded = new ForgeFakePlayer(level, new GameProfile(UUID.randomUUID(), "AlaProbeLoad"));
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
		boolean saved = stats.equals(PlayerStatsStore.get(loaded)) && skills.equals(SkillStore.get(loaded));

		owner.remove(Entity.RemovalReason.DISCARDED);
		boolean invalidAfterRemove = ForgePlayerAttachments.container(owner) == null;
		ForgeFakePlayer respawned = new ForgeFakePlayer(level, owner.getGameProfile());
		ForgePlayerAttachments.copy(owner, respawned, true);
		PlayerAttachmentContainer after = ForgePlayerAttachments.container(respawned);
		boolean copied = after != null;
		for (ModPlayerAttachments.PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			Object original = def == ModPlayerAttachments.PLAYER_STATS ? stats
					: def == ModPlayerAttachments.PLAYER_SKILLS ? skills : null;
			Object expected = def.copyOnDeath() ? original : null;
			Object actual = after == null ? null : after.get(def);
			copied &= expected == null ? actual == null : expected.equals(actual);
		}
		boolean invalidAgain = ForgePlayerAttachments.container(owner) == null;

		ForgeChecks.log("attachments: container {}, read is a read {}, save/load {}, removed invalid {}, "
				+ "death clone copied {}, original invalid again {}", hasContainer, readIsRead, saved,
				invalidAfterRemove, copied, invalidAgain);
		if (!hasContainer || !readIsRead || !saved || !invalidAfterRemove || !copied || !invalidAgain) {
			failures.add("player attachments: container=" + hasContainer + ", readIsRead=" + readIsRead + ", saved="
					+ saved + ", invalidAfterRemove=" + invalidAfterRemove + ", copied=" + copied + ", invalidAgain="
					+ invalidAgain);
		}
		return failures;
	}

	/** A cancelled EntityPlaceEvent puts the cell back; without a listener the block stays. */
	private static List<String> checkPlaceEvents(ServerLevel level, BlockPos cell) {
		List<String> failures = new ArrayList<>();
		level.getChunk(cell);
		BlockState previous = level.getBlockState(cell);
		ServerPlayer placer = FakePlayers.get().get(level, new GameProfile(UUID.randomUUID(), "AlaProbePlace"));
		EventListener refuse = BlockEvent.EntityPlaceEvent.BUS.addListener(
				(BlockEvent.EntityPlaceEvent event) -> true);
		boolean refused;
		BlockState afterRefusal;
		try {
			refused = !PlaceEvents.get().place(level, cell, Direction.UP, placer,
					() -> level.setBlock(cell, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL));
			afterRefusal = level.getBlockState(cell);
		} finally {
			BlockEvent.EntityPlaceEvent.BUS.removeListener(refuse);
		}
		boolean allowed = PlaceEvents.get().place(level, cell, Direction.UP, placer,
				() -> level.setBlock(cell, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL));
		boolean stayed = level.getBlockState(cell).is(Blocks.STONE);
		level.setBlock(cell, previous, Block.UPDATE_ALL);

		boolean restored = afterRefusal.equals(previous);
		ForgeChecks.log("place event: refused {}, cell restored {}, allowed {}, block stayed {}", refused,
				restored, allowed, stayed);
		if (!refused || !restored || !allowed || !stayed) {
			failures.add("place events: refused=" + refused + ", restored=" + restored + ", allowed=" + allowed
					+ ", stayed=" + stayed);
		}
		return failures;
	}

	/** The installed gate: cancelled is DENIED, ALLOW is HANDLED and Forge spent one meal, no player PROCEEDs. */
	private static List<String> checkBonemeal(ServerLevel level, BlockPos cell) {
		List<String> failures = new ArrayList<>();
		ReinforcedBonemeal.LoaderGate gate = ReinforcedBonemeal.LOADER_GATE;
		if (gate == null) {
			failures.add("ReinforcedBonemeal.LOADER_GATE is not installed");
			return failures;
		}
		ServerPlayer player = FakePlayers.get().get(level, new GameProfile(UUID.randomUUID(), "AlaProbeMeal"));
		BlockState state = Blocks.GRASS_BLOCK.defaultBlockState();
		ItemStack stack = new ItemStack(Items.BONE_MEAL, 4);

		ReinforcedBonemeal.Verdict plain = gate.check(player, level, cell, state, stack);
		ReinforcedBonemeal.Verdict denied;
		EventListener cancel = BonemealEvent.BUS.addListener((BonemealEvent event) -> true);
		try {
			denied = gate.check(player, level, cell, state, stack);
		} finally {
			BonemealEvent.BUS.removeListener(cancel);
		}
		ReinforcedBonemeal.Verdict handled;
		EventListener allow = BonemealEvent.BUS.addListener((BonemealEvent event) -> {
			event.setResult(Result.ALLOW);
		});
		try {
			handled = gate.check(player, level, cell, state, stack);
		} finally {
			BonemealEvent.BUS.removeListener(allow);
		}
		int left = stack.getCount();
		ReinforcedBonemeal.Verdict noPlayer = gate.check(null, level, cell, state, stack);

		ForgeChecks.log("bone meal: plain {}, cancelled {}, allowed {} (meals left {} of 4), no player {}",
				plain, denied, handled, left, noPlayer);
		if (plain != ReinforcedBonemeal.Verdict.PROCEED || denied != ReinforcedBonemeal.Verdict.DENIED
				|| handled != ReinforcedBonemeal.Verdict.HANDLED || left != 3
				|| noPlayer != ReinforcedBonemeal.Verdict.PROCEED) {
			failures.add("bone-meal gate: plain=" + plain + ", cancelled=" + denied + ", allowed=" + handled
					+ ", mealsLeft=" + left + ", noPlayer=" + noPlayer);
		}
		return failures;
	}

	/**
	 * USE_BLOCK through the RightClickBlock event: a dye on a bare copper cable is taken by the roster's cable-dye
	 * hook (SUCCESS, with the "bare cables take no dye" message), so our listener cancels the event with it.
	 */
	private static List<String> checkUseBlockHook(ServerLevel level, BlockPos cell) {
		List<String> failures = new ArrayList<>();
		level.getChunk(cell);
		BlockState previous = level.getBlockState(cell);
		Block cable = BuiltInRegistries.BLOCK.getValue(Industrialization.id("copper_cable"));
		ServerPlayer player = FakePlayers.get().get(level, new GameProfile(UUID.randomUUID(), "AlaProbeDye"));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.red()));
		boolean cancelled;
		InteractionResult result;
		try {
			level.setBlock(cell, cable.defaultBlockState(), Block.UPDATE_ALL);
			PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player,
					InteractionHand.MAIN_HAND, cell,
					new BlockHitResult(Vec3.atCenterOf(cell), Direction.UP, cell, false));
			cancelled = PlayerInteractEvent.RightClickBlock.BUS.post(event);
			result = event.getCancellationResult();
		} finally {
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			level.setBlock(cell, previous, Block.UPDATE_ALL);
		}
		ForgeChecks.log("block-use hook: event cancelled {}, result {}", cancelled, result);
		if (!cancelled || !(result instanceof InteractionResult.Success)) {
			failures.add("USE_BLOCK hook: cancelled=" + cancelled + ", result=" + result);
		}
		return failures;
	}
}
