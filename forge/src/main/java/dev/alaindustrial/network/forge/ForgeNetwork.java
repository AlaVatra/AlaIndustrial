package dev.alaindustrial.network.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.network.ModPayloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadFlow;
import net.minecraftforge.network.payload.PayloadProtocol;

/**
 * Forge payload registration (MOD-962, slice 2.4): ONE payload channel that replays the shared
 * {@link ModPayloads#PAYLOADS} list, the counterpart of {@code NeoForgeNetwork} and of
 * {@code IndustrializationFabric.registerNetworkPayloads}. Sending goes through the neutral
 * {@link dev.alaindustrial.network.NetworkDispatcher} ({@link ForgeNetworkDispatcher}).
 *
 * <p><b>When.</b> Forge has no registration event for payloads: the channel is built from the {@code @Mod}
 * constructor, which is before {@code NetworkRegistry} locks at the end of loading (it refuses any later
 * registration).
 *
 * <p><b>Threads.</b> Forge hands a custom payload to its channel on the netty thread, before vanilla's
 * {@code ensureRunningOnSameThread}; every entry is registered with {@code addMain}, which queues the handler on
 * the game thread and marks the packet handled (without that mark the client patch falls through to the vanilla
 * path). {@code ClientPayloadManifest.Dispatch} is a Fabric-only detail, ignored here as on NeoForge.
 *
 * <p><b>Dedicated server.</b> The client side is reached only from inside the clientbound handler lambdas
 * ({@link ForgeClientNetwork}), which a dedicated server never invokes, so no client class is linked there.
 *
 * <p><b>Forge-only payloads.</b> {@link ForgeAttachmentSyncPayload} carries the player attachments to their
 * owner: NeoForge syncs attachments itself and Fabric through its attachment API, so it is not in the shared
 * list.
 */
public final class ForgeNetwork {

	/** The one channel; null until {@link #register()} ran in the {@code @Mod} constructor. */
	private static Channel<CustomPacketPayload> channel;

	/** Every payload id this channel registered, in registration order (read by the dev probe). */
	private static final List<Identifier> REGISTERED_IDS = new ArrayList<>();

	private ForgeNetwork() {
	}

	/** The channel built by {@link #register()}. */
	public static Channel<CustomPacketPayload> channel() {
		Channel<CustomPacketPayload> built = channel;
		if (built == null) {
			throw new IllegalStateException("ForgeNetwork.register() has not run: the Forge entry point builds the "
					+ "payload channel in its constructor");
		}
		return built;
	}

	/** The ids registered on the channel: every {@code ModPayloads.PAYLOADS} entry, then the Forge-only ones. */
	public static List<Identifier> registeredIds() {
		return List.copyOf(REGISTERED_IDS);
	}

	/** Builds the channel. Called once from the {@code @Mod} constructor. */
	public static void register() {
		if (channel != null) {
			throw new IllegalStateException("ForgeNetwork.register() called twice");
		}
		PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> play =
				ChannelBuilder.named(Industrialization.id("main"))
						.networkProtocolVersion(Integer.parseInt(ModPayloads.PROTOCOL_VERSION))
						.payloadChannel()
						.play();
		PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> toClient = play.clientbound();
		PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> toServer = play.serverbound();

		ModPayloads.Registrar replayer = new ModPayloads.Registrar() {
			@Override
			public <T extends CustomPacketPayload> void clientbound(ModPayloads.PayloadDef<T> def) {
				toClient.addMain(def.type(), play(def.codec()),
						(payload, context) -> ForgeClientNetwork.receive(payload));
				REGISTERED_IDS.add(def.type().id());
			}

			@Override
			public <T extends CustomPacketPayload> void serverbound(ModPayloads.PayloadDef<T> def) {
				toServer.addMain(def.type(), play(def.codec()), (payload, context) -> {
					// Null only outside the play listener; NeoForge cannot get here without a player either.
					ServerPlayer sender = context.getSender();
					if (sender != null) {
						def.handle(payload, sender);
					}
				});
				REGISTERED_IDS.add(def.type().id());
			}
		};
		for (ModPayloads.PayloadDef<?> def : ModPayloads.PAYLOADS) {
			def.bindTo(replayer);
		}
		toClient.addMain(ForgeAttachmentSyncPayload.TYPE, ForgeAttachmentSyncPayload.CODEC,
				(payload, context) -> ForgeClientNetwork.receiveAttachment(payload));
		REGISTERED_IDS.add(ForgeAttachmentSyncPayload.TYPE.id());
		// Both flows were added on the same builder; build() closes it and registers the channel.
		channel = toClient.build();
	}

	/**
	 * {@code PayloadFlow.addMain} takes a {@code StreamCodec<RegistryFriendlyByteBuf, T>} exactly, while the shared
	 * definition carries {@code StreamCodec<? super RegistryFriendlyByteBuf, T>}. A stream codec only consumes its
	 * buffer, so narrowing the buffer type is sound.
	 */
	@SuppressWarnings("unchecked")
	private static <T> StreamCodec<RegistryFriendlyByteBuf, T> play(
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		return (StreamCodec<RegistryFriendlyByteBuf, T>) codec;
	}
}
