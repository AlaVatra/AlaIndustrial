package dev.alaindustrial.client.forge;

import dev.alaindustrial.registry.ModPlayerAttachments.PlayerAttachmentDef;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's copy of the local player's attachments on Forge (MOD-962, slice 2.5), filled by
 * {@code ForgeAttachmentSyncPayload}. NeoForge reads the synced copy off the {@code LocalPlayer} entity; here the
 * store belongs to the session, so a dimension change (same server player, new {@code LocalPlayer}) keeps it, and
 * {@link #clear()} on logout keeps one world's value from showing in the next before its login sync.
 *
 * <p>Holds no client type, so it is loadable on either side. Written on the client game thread only (the payload
 * is registered with {@code addMain}); read by the client caches from the render and tick threads, hence a
 * concurrent map.
 */
public final class ForgeClientAttachments {

	private static final Map<String, Object> VALUES = new ConcurrentHashMap<>();

	private ForgeClientAttachments() {
	}

	/** Stores what the server sent for one attachment. */
	public static void put(PlayerAttachmentDef<?> def, Object value) {
		VALUES.put(def.id(), value);
	}

	/** The last synced value, or the definition's empty value when nothing arrived yet. */
	@SuppressWarnings("unchecked")
	public static <T> T current(PlayerAttachmentDef<T> def) {
		Object value = VALUES.get(def.id());
		return value != null ? (T) value : def.empty();
	}

	/** Forgets everything; called when the client leaves a world. */
	public static void clear() {
		VALUES.clear();
	}
}
