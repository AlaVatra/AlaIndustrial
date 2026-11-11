package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.attachment.PlayerAttachmentAccessor;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.forge.ForgeAttachmentSyncPayload;
import dev.alaindustrial.registry.ModPlayerAttachments;
import dev.alaindustrial.registry.ModPlayerAttachments.PlayerAttachmentDef;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;

/**
 * Player attachments on Forge (MOD-962, slice 2.5), the counterpart of {@code ModAttachmentsNeoForge}: every
 * {@link ModPlayerAttachments#PLAYER_ATTACHMENTS} entry lives in the one {@link PlayerAttachmentContainer}
 * capability of a {@link ServerPlayer}, its store is bound to that container, and its value reaches the owner's
 * client through {@link ForgeAttachmentSyncPayload}.
 *
 * <p><b>Death and the End.</b> {@code PlayerEvent.Clone} runs after the old player was removed, which invalidated
 * its capabilities; they are revived for the copy and invalidated again after it, or the old player's providers
 * would stay reachable.
 *
 * <p><b>Sync.</b> The owner gets every synced entry at login and at respawn (a death without {@code copyOnDeath}
 * resets the server value) and an entry each time it is set. The client store belongs to the session, so a
 * dimension change needs nothing. At login the empty value is sent too: NeoForge sends only existing data, this
 * makes the client state explicit, and the effect is the same.
 */
public final class ForgePlayerAttachments {

	public static final Capability<PlayerAttachmentContainer> CAPABILITY =
			CapabilityManager.get(new CapabilityToken<>() {
			});

	private static final Identifier KEY = Industrialization.id("player_attachments");

	private ForgePlayerAttachments() {
	}

	/** Called once from the {@code @Mod} constructor, before any player exists. */
	public static void init() {
		for (PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			bindStore(def);
		}
		AttachCapabilitiesEvent.Entities.BUS.addListener(ForgePlayerAttachments::attach);
		PlayerEvent.Clone.BUS.addListener(ForgePlayerAttachments::onClone);
		PlayerEvent.PlayerLoggedInEvent.BUS.addListener(
				(PlayerEvent.PlayerLoggedInEvent event) -> syncAll(event.getEntity()));
		PlayerEvent.PlayerRespawnEvent.BUS.addListener(
				(PlayerEvent.PlayerRespawnEvent event) -> syncAll(event.getEntity()));
	}

	/** Fired from the Entity constructor: no field of the player may be touched here. */
	private static void attach(AttachCapabilitiesEvent.Entities event) {
		if (event.getObject() instanceof ServerPlayer) {
			event.addCapability(KEY, new PlayerAttachmentContainer(CAPABILITY));
		}
	}

	/** The player's container, or null when the player has none (not a server player, or removed). */
	public static @Nullable PlayerAttachmentContainer container(Player player) {
		return player.getCapability(CAPABILITY).resolve().orElse(null);
	}

	private static void onClone(PlayerEvent.Clone event) {
		copy(event.getOriginal(), event.getEntity(), event.isWasDeath());
	}

	/** The clone step, public for the dev probe: revive the removed original, copy, invalidate it again. */
	public static void copy(Player original, Player fresh, boolean wasDeath) {
		if (!(original instanceof ServerPlayer old) || !(fresh instanceof ServerPlayer now)) {
			return;
		}
		old.reviveCaps();
		try {
			PlayerAttachmentContainer from = container(old);
			PlayerAttachmentContainer to = container(now);
			if (from != null && to != null) {
				to.copyFrom(from, wasDeath);
			}
		} finally {
			old.invalidateCaps();
		}
	}

	private static <T> void bindStore(PlayerAttachmentDef<T> def) {
		def.bind(new PlayerAttachmentAccessor<>() {
			@Override
			public T get(ServerPlayer player) {
				// A read is a read (MOD-483): nothing stored, nothing installed, nothing sent.
				PlayerAttachmentContainer container = container(player);
				T stored = container == null ? null : container.get(def);
				return stored != null ? stored : def.empty();
			}

			@Override
			public void set(ServerPlayer player, T value) {
				PlayerAttachmentContainer container = container(player);
				if (container == null) {
					Industrialization.LOGGER.warn("player attachment {} not written: {} has no container", def.id(),
							player.getScoreboardName());
					return;
				}
				container.set(def, value);
				if (def.syncToOwner()) {
					sync(player, def, value);
				}
			}
		});
	}

	private static void syncAll(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		for (PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			if (def.syncToOwner()) {
				syncCurrent(serverPlayer, def);
			}
		}
	}

	private static <T> void syncCurrent(ServerPlayer player, PlayerAttachmentDef<T> def) {
		PlayerAttachmentContainer container = container(player);
		T value = container == null ? null : container.get(def);
		sync(player, def, value != null ? value : def.empty());
	}

	private static <T> void sync(ServerPlayer player, PlayerAttachmentDef<T> def, T value) {
		NetworkDispatcher dispatcher = NetworkDispatcher.get();
		// MOD-513: a gametest mock player and a fake player have no channel to send on.
		if (dispatcher.canSendToPlayer(player, ForgeAttachmentSyncPayload.TYPE)) {
			dispatcher.sendToPlayer(player, new ForgeAttachmentSyncPayload(def, value));
		}
	}
}
