package dev.alaindustrial;

import dev.alaindustrial.command.AlaCommandCommon;
import dev.alaindustrial.lifecycle.ServerHookRoster;
import dev.alaindustrial.network.forge.ForgeNetwork;
import dev.alaindustrial.registry.forge.ForgePlayerAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Everything of the Forge server side beyond the registries and the loader seams (MOD-962, slices 2.4 and 2.5),
 * called once from the {@code IndustrializationForge} constructor: the payload channel, the player attachments,
 * the config, every {@link ServerHookRoster} entry on its Forge event, the {@code /reload} config parity, the
 * early block-use chain and the {@code /ala} command. The NeoForge twins are {@code NeoForgeNetwork},
 * {@code ModAttachmentsNeoForge}, {@code NeoForgeConfigLoader} and {@code IndustrializationNeoForge}'s
 * {@code registerServerHooks} / {@code registerGameBusEvents}.
 *
 * <p><b>EventBus 7.</b> Every listener is on the global per-event bus ({@code X.BUS}); a cancellable bus takes a
 * {@code Consumer}, a {@code Predicate} (true cancels) or a monitor, so each lambda names its parameter type, or
 * javac cannot pick between the first two.
 */
final class ForgeServerWiring {

	private ForgeServerWiring() {
	}

	/** In the order of NeoForge's constructor: payloads, attachments, config, server hooks, game-bus events. */
	static void install() {
		// Not an event on Forge: the channel is built now, before NetworkRegistry locks at the end of loading.
		ForgeNetwork.register();
		ForgePlayerAttachments.init();
		ForgeConfigLoader.register();
		registerServerHooks();
		registerGameBusEvents();
	}

	/** Each {@link ServerHookRoster} entry point on the Forge event that fires where NeoForge's does. */
	private static void registerServerHooks() {
		ServerAboutToStartEvent.BUS.addListener((ServerAboutToStartEvent event) ->
				ServerHookRoster.onServerStarting(event.getServer()));
		// Fired by ForgeEventFactory.onPostServerTick at the very end of MinecraftServer.tickServer.
		TickEvent.ServerTickEvent.Post.BUS.addListener((TickEvent.ServerTickEvent.Post event) ->
				ServerHookRoster.onServerTick(event.server()));
		// LivingDamageEvent fires after armour and absorption, before the health changes. A monitor sees the final
		// amount after every other mod's edit and whether one cancelled it; Forge's form of NeoForge's
		// LivingDamageEvent.Post with getHealthDamage() > 0 (both fire for a killing blow too).
		LivingDamageEvent.BUS.addListener((LivingDamageEvent event, boolean cancelled) -> {
			if (reportsHurt(cancelled, event.getAmount()) && event.getEntity() instanceof ServerPlayer player) {
				ServerHookRoster.onPlayerHurt(player);
			}
		});
		LivingDeathEvent.BUS.addListener((LivingDeathEvent event) ->
				ServerHookRoster.onLivingDeath(event.getEntity(), event.getSource()));
		// PlayerList.remove posts this first, before saving and removing: the player's state is still valid.
		PlayerEvent.PlayerLoggedOutEvent.BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				ServerHookRoster.onPlayerLeave(player);
			}
		});
		PlayerEvent.PlayerLoggedInEvent.BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				ServerHookRoster.onPlayerJoin(player);
			}
		});
		EntityJoinLevelEvent.BUS.addListener((EntityJoinLevelEvent event) ->
				ServerHookRoster.onEntityJoin(event.getEntity(), event.getLevel()));
		LevelEvent.Unload.BUS.addListener((LevelEvent.Unload event) -> {
			if (event.getLevel() instanceof ServerLevel level) {
				ServerHookRoster.onLevelUnload(level);
			}
		});
		ServerStoppingEvent.BUS.addListener((ServerStoppingEvent event) ->
				ServerHookRoster.onServerStopping(event.getServer()));
		ServerStoppedEvent.BUS.addListener((ServerStoppedEvent event) ->
				ServerHookRoster.onServerStopped(event.getServer()));
	}

	/** The damage monitor's rule: a hit no listener cancelled that still deals damage. */
	static boolean reportsHurt(boolean cancelled, float amount) {
		return !cancelled && amount > 0.0f;
	}

	/** Config reload parity, the early block-use chain and the command, as in NeoForge's registerGameBusEvents. */
	private static void registerGameBusEvents() {
		// Fires mid-login with the joining player and on /reload with none; only the second re-reads the config
		// (MOD-100, MOD-695). Recipe sync for a client recipe viewer is not wired: no viewer has a Forge 26.x build
		// (MOD-962 task log).
		OnDatapackSyncEvent.BUS.addListener((OnDatapackSyncEvent event) -> {
			if (event.getPlayer() == null) {
				ForgeConfigLoader.reload(ServerLifecycleHooks.getCurrentServer());
			}
		});
		// MOD-706: bucket deposit, oil lighting, cable dye. RightClickBlock fires on both sides before vanilla's use;
		// a non-PASS answer cancels it and both Forge patches return the cancellation result, so the first non-PASS
		// listener wins, as on NeoForge.
		PlayerInteractEvent.RightClickBlock.BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
			InteractionResult result = ServerHookRoster.onUseBlock(
					event.getLevel(), event.getEntity(), event.getHand(), event.getHitVec());
			if (result == InteractionResult.PASS) {
				return false;
			}
			event.setCancellationResult(result);
			return true;
		});
		// /ala for everyone; the hidden /ala demo subtree only outside production (MOD-058).
		RegisterCommandsEvent.BUS.addListener((RegisterCommandsEvent event) ->
				AlaCommandCommon.register(event.getDispatcher(), !FMLEnvironment.production));
	}
}
