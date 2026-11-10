package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModPlayerAttachments;
import dev.alaindustrial.registry.ModPlayerAttachments.PlayerAttachmentDef;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jspecify.annotations.Nullable;

/**
 * Every player attachment of one server player on Forge (MOD-962, slice 2.5): ONE entity capability holding the
 * values of all {@link ModPlayerAttachments#PLAYER_ATTACHMENTS} entries, so a new entry needs no Forge change.
 *
 * <p><b>Save form.</b> Under {@code ForgeCaps → alaindustrial:player_attachments}, one key per entry id, each value
 * in that entry's {@code mapCodec} with registry-aware ops (the NeoForge form). The key is Forge's own; what carries
 * the data across loaders is the neutral copy common code writes beside it ({@code PlayerDataCarryOver}).
 *
 * <p><b>A read is a read</b> (MOD-483): {@link #get} returns {@code null} for an entry the player never had, and
 * {@link #serializeNBT} writes nothing for it. Nothing here installs a default.
 *
 * <p><b>Values are shared, not copied</b>, by {@link #copyFrom}: every attachment value today is an immutable
 * record. A mutable value would have to be round-tripped through its codec there.
 *
 * <p>The self {@link LazyOptional} is created once and never invalidated: the entity's own validity flag hides it
 * after removal, and {@code reviveCaps()} on a dead player (the death clone) must still reach it.
 *
 * <p>No Forge static is read here: the capability is handed in, so the class is testable without the Forge
 * runtime (a {@code CapabilityToken} only resolves inside it).
 */
@AutoRegisterCapability
public final class PlayerAttachmentContainer implements ICapabilitySerializable<CompoundTag> {

	private final @Nullable Capability<PlayerAttachmentContainer> capability;
	private final LazyOptional<PlayerAttachmentContainer> self = LazyOptional.of(() -> this);
	/** Entry id to value; an absent key means "the player has none". */
	private final Map<String, Object> values = new HashMap<>();

	public PlayerAttachmentContainer(@Nullable Capability<PlayerAttachmentContainer> capability) {
		this.capability = capability;
	}

	@Override
	public <C> LazyOptional<C> getCapability(Capability<C> cap, @Nullable Direction side) {
		return capability != null && cap == capability ? self.cast() : LazyOptional.empty();
	}

	/** The stored value, or {@code null} when the player has none. Never installs a default. */
	@SuppressWarnings("unchecked")
	public <T> @Nullable T get(PlayerAttachmentDef<T> def) {
		return (T) values.get(def.id());
	}

	public <T> void set(PlayerAttachmentDef<T> def, T value) {
		values.put(def.id(), Objects.requireNonNull(value, "value"));
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider registries) {
		RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		CompoundTag out = new CompoundTag();
		for (PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			write(def, ops, out);
		}
		return out;
	}

	private <T> void write(PlayerAttachmentDef<T> def, RegistryOps<Tag> ops, CompoundTag out) {
		T value = get(def);
		if (value == null) {
			return;
		}
		def.mapCodec().codec().encodeStart(ops, value)
				.resultOrPartial(error -> Industrialization.LOGGER.error("player attachment {} not saved: {}",
						def.id(), error))
				.ifPresent(tag -> out.put(def.id(), tag));
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider registries, CompoundTag in) {
		values.clear();
		RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		for (PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			read(def, ops, in);
		}
	}

	/** One broken entry is logged and skipped; the others still load. */
	private <T> void read(PlayerAttachmentDef<T> def, RegistryOps<Tag> ops, CompoundTag in) {
		Tag tag = in.get(def.id());
		if (tag == null) {
			return;
		}
		def.mapCodec().codec().parse(ops, tag)
				.resultOrPartial(error -> Industrialization.LOGGER.error("player attachment {} not loaded: {}",
						def.id(), error))
				.ifPresent(value -> values.put(def.id(), value));
	}

	/**
	 * {@code PlayerEvent.Clone}: takes over what the old player had, by the rule of the other two loaders — a
	 * return from the End copies everything, a death copies only the {@code copyOnDeath} entries.
	 */
	public void copyFrom(PlayerAttachmentContainer original, boolean wasDeath) {
		for (PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			Object value = original.values.get(def.id());
			if (value != null && shouldCopy(def, wasDeath)) {
				values.put(def.id(), value);
			}
		}
	}

	static boolean shouldCopy(PlayerAttachmentDef<?> def, boolean wasDeath) {
		return !wasDeath || def.copyOnDeath();
	}
}
