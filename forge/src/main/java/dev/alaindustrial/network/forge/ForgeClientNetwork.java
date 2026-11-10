package dev.alaindustrial.network.forge;

import dev.alaindustrial.client.ClientPayloadManifest;
import dev.alaindustrial.client.forge.ForgeClientAttachments;
import dev.alaindustrial.registry.ModPlayerAttachments;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

/**
 * The client half of the Forge networking (MOD-962, slices 2.4 and 2.5).
 *
 * <p>{@link #receive} and {@link #receiveAttachment} are what {@link ForgeNetwork}'s clientbound handlers call; a
 * dedicated server never invokes those handlers, so the client classes named here are never linked there.
 *
 * <p>{@link #install()} is for the Forge client entry point (slice 2.2, {@code IndustrializationForgeClient}),
 * the counterpart of {@code IndustrializationNeoForgeClient}'s attachment loop: it binds each player attachment's
 * client cache to the synced copy and clears that copy when the client leaves a world. Until the client entry
 * point calls it, the client caches keep their empty values.
 */
public final class ForgeClientNetwork {

	private ForgeClientNetwork() {
	}

	/** Client side of every shared clientbound payload. */
	static void receive(CustomPacketPayload payload) {
		ClientPayloadManifest.receive(payload);
	}

	/** Client side of the Forge-only attachment sync. */
	static void receiveAttachment(ForgeAttachmentSyncPayload payload) {
		ForgeClientAttachments.put(payload.def(), payload.value());
	}

	/** Call once from the Forge client entry point. */
	public static void install() {
		for (ModPlayerAttachments.PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			bindClientCache(def);
		}
		ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(
				(ClientPlayerNetworkEvent.LoggingOut event) -> ForgeClientAttachments.clear());
	}

	private static <T> void bindClientCache(ModPlayerAttachments.PlayerAttachmentDef<T> def) {
		def.bindClientCache().accept(() -> ForgeClientAttachments.current(def));
	}
}
