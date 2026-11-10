package dev.alaindustrial.attachment;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModPlayerAttachments;
import dev.alaindustrial.registry.ModPlayerAttachments.PlayerAttachmentDef;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A player's mod data that survives a change of loader (MOD-962). Each loader saves the
 * {@link ModPlayerAttachments#PLAYER_ATTACHMENTS} entries under a key of its own, which no other loader reads, and the
 * first save after a move drops the foreign key (a player save starts from an empty tag). So every save also writes a
 * loader-neutral copy, {@value #KEY}: one child per entry id, in the entry's {@code codec} — the same tag every loader
 * stores inside its own key.
 *
 * <p><b>Load.</b> The copy is read into a pending value per entry; the loader's own store is filled from it when the
 * player joins ({@link #applyOnJoin}, a {@code ServerHookRoster.PLAYER_JOIN} entry). At load time the player has no
 * connection yet, and a store write syncs to the owner. An entry the copy lacks (a file no build with this class
 * wrote) is read from the loaders' own keys instead, which rescues a world moved before its first save here.
 *
 * <p><b>Precedence, per entry.</b>
 * <ol>
 *   <li>The neutral copy wins over everything, the loader's own key included. A save by a build with this class
 *       writes both from the same store, and a save by any other build writes no copy at all (a player save starts
 *       from an empty tag, {@code PlayerDataStorage.save}), so a copy that is present is never older than the own key
 *       beside it. The own key is the one that can be older — a world played on A, then on B, then back on A, with
 *       A's key still in the file — and winning there would roll the player's progress back.</li>
 *   <li>Without the copy, the loader's own key, as the loader loaded it: a rescued value is applied only while the
 *       store still holds the entry's empty value.</li>
 *   <li>Then the other loaders' keys, in the order of {@link #LOADER_KEYS}.</li>
 * </ol>
 *
 * <p>A save between load and join writes the value the join would apply, so the copy cannot fall back to the stale
 * own key in that window.
 */
public final class PlayerDataCarryOver {

	/** The loader-neutral copy in the player's saved data. */
	public static final String KEY = Industrialization.MOD_ID + ":player_data";

	/** How each loader nests an entry, outermost key first; the entry is then stored in its {@code codec}. */
	private static final String[][] LOADER_KEYS = {
			// fabric-data-attachment-api-v1 2.2.30 (AttachmentSerializingImpl): a map of attachment id to value.
			{"fabric:attachments"},
			// NeoForge 26.3.0.99 (AttachmentHolder.ATTACHMENTS_NBT_KEY): attachment id to the MapCodec's fields.
			{"neoforge:attachments"},
			// Forge 66.1.0 (Entity "ForgeCaps", CapabilityDispatcher): provider id, then the bare entry id.
			{"ForgeCaps", Industrialization.MOD_ID + ":player_attachments"},
	};

	private record Pending(Object value, boolean fromNeutralCopy) {
	}

	/**
	 * Where a player keeps what was loaded and not yet applied: a field the common mixin adds to
	 * {@code ServerPlayer}, so it lives and dies with that one object (an entity's {@code equals} is its id, which a
	 * respawned copy takes over). The value is this class's own map; nothing else reads it.
	 */
	public interface PendingHolder {
		@Nullable Object alaindustrial$pendingPlayerData();

		void alaindustrial$pendingPlayerData(@Nullable Object pending);
	}

	private PlayerDataCarryOver() {
	}

	/** The end of {@code ServerPlayer.addAdditionalSaveData}: writes the neutral copy. */
	public static void write(ServerPlayer player, ValueOutput output) {
		ValueOutput copy = output.child(KEY);
		for (PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			writeEntry(player, def, copy);
		}
	}

	private static <T> void writeEntry(ServerPlayer player, PlayerAttachmentDef<T> def, ValueOutput copy) {
		T value = resolve(player, def);
		if (value != null) {
			copy.store(def.id(), def.codec(), value);
		}
	}

	/** The end of {@code ServerPlayer.readAdditionalSaveData}: remembers what {@link #applyOnJoin} will apply. */
	public static void read(ServerPlayer player, ValueInput input) {
		Optional<ValueInput> copy = input.child(KEY);
		Map<String, Pending> pending = new HashMap<>();
		for (PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			readEntry(def, input, copy, pending);
		}
		holder(player).alaindustrial$pendingPlayerData(pending.isEmpty() ? null : pending);
	}

	private static <T> void readEntry(PlayerAttachmentDef<T> def, ValueInput input, Optional<ValueInput> copy,
			Map<String, Pending> pending) {
		Optional<T> neutral = copy.flatMap(c -> c.read(def.id(), def.codec()));
		if (neutral.isPresent()) {
			pending.put(def.id(), new Pending(neutral.get(), true));
			return;
		}
		for (String[] path : LOADER_KEYS) {
			Optional<T> value = under(input, path).flatMap(c -> c.read(entryKey(def, path), def.codec()));
			if (value.isPresent()) {
				pending.put(def.id(), new Pending(value.get(), false));
				return;
			}
		}
	}

	/** The entry's key inside a loader's compound: Forge's container uses the bare id, the others the full one. */
	private static String entryKey(PlayerAttachmentDef<?> def, String[] path) {
		return path.length > 1 ? def.id() : Industrialization.MOD_ID + ":" + def.id();
	}

	private static Optional<ValueInput> under(ValueInput input, String[] path) {
		Optional<ValueInput> at = Optional.of(input);
		for (String key : path) {
			at = at.flatMap(c -> c.child(key));
		}
		return at;
	}

	/** A {@code ServerHookRoster.PLAYER_JOIN} entry: fills the loader's store from what {@link #read} found. */
	public static void applyOnJoin(ServerPlayer player) {
		if (pending(player) == null) {
			return;
		}
		for (PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			applyEntry(player, def);
		}
		holder(player).alaindustrial$pendingPlayerData(null);
	}

	private static <T> void applyEntry(ServerPlayer player, PlayerAttachmentDef<T> def) {
		PlayerAttachmentAccessor<T> accessor = def.accessor();
		T value = resolve(player, def);
		if (accessor != null && value != null && !value.equals(accessor.get(player))) {
			accessor.set(player, value);
		}
	}

	/** The value the player has once the pending one is applied by the precedence above; null when unbound. */
	@SuppressWarnings("unchecked")
	private static <T> @Nullable T resolve(ServerPlayer player, PlayerAttachmentDef<T> def) {
		PlayerAttachmentAccessor<T> accessor = def.accessor();
		if (accessor == null) {
			return null;
		}
		T stored = accessor.get(player);
		Map<String, Pending> pending = pending(player);
		Pending entry = pending == null ? null : pending.get(def.id());
		if (entry == null) {
			return stored;
		}
		if (entry.fromNeutralCopy() || stored.equals(def.empty())) {
			return (T) entry.value();
		}
		return stored;
	}

	private static PendingHolder holder(ServerPlayer player) {
		return (PendingHolder) player;
	}

	@SuppressWarnings("unchecked")
	private static @Nullable Map<String, Pending> pending(ServerPlayer player) {
		return (Map<String, Pending>) holder(player).alaindustrial$pendingPlayerData();
	}
}
