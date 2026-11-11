package dev.alaindustrial.network.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModPlayerAttachments;
import dev.alaindustrial.registry.ModPlayerAttachments.PlayerAttachmentDef;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

/**
 * Forge-only clientbound payload (MOD-962, slice 2.5): one player attachment's current value, sent to its owner.
 * NeoForge's attachment {@code sync(...)} and Fabric's attachment API do this for the other two loaders, so the
 * payload is registered by {@link ForgeNetwork} beside the shared list, not in it.
 *
 * <p>Wire form: the definition's id, then the value in the definition's own {@code streamCodec}.
 */
public record ForgeAttachmentSyncPayload(PlayerAttachmentDef<?> def, Object value) implements CustomPacketPayload {

	public static final Type<ForgeAttachmentSyncPayload> TYPE =
			new Type<>(Industrialization.id("forge_attachment_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ForgeAttachmentSyncPayload> CODEC =
			StreamCodec.of(ForgeAttachmentSyncPayload::write, ForgeAttachmentSyncPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, ForgeAttachmentSyncPayload payload) {
		buf.writeUtf(payload.def.id());
		encode(buf, payload.def, payload.value);
	}

	/** The value was put in by {@code ForgePlayerAttachments} from the same definition, so the cast holds. */
	@SuppressWarnings("unchecked")
	private static <T> void encode(RegistryFriendlyByteBuf buf, PlayerAttachmentDef<T> def, Object value) {
		def.streamCodec().encode(buf, (T) value);
	}

	private static ForgeAttachmentSyncPayload read(RegistryFriendlyByteBuf buf) {
		String id = buf.readUtf();
		PlayerAttachmentDef<?> def = byId(id);
		if (def == null) {
			throw new DecoderException("unknown player attachment " + id);
		}
		return new ForgeAttachmentSyncPayload(def, def.streamCodec().decode(buf));
	}

	static @Nullable PlayerAttachmentDef<?> byId(String id) {
		for (PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			if (def.id().equals(id)) {
				return def;
			}
		}
		return null;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
