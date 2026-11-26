package dev.alaindustrial.core.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.core.fluid.FluidHostRedirect;
import dev.alaindustrial.core.item.ItemHostRedirect;
import dev.alaindustrial.registry.BlockCapabilityRoster;
import dev.alaindustrial.registry.ContentManifest;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jspecify.annotations.Nullable;

/**
 * Attaches {@link BlockCapabilityProviderForge} to our block entities, driven by {@link BlockCapabilityRoster} —
 * the same three lists Fabric and NeoForge replay (energy hosts minus {@code NO_ENERGY_CAPABILITY}, fluid hosts,
 * containers) — never by {@code instanceof}, so the roster's exclusions hold on Forge too.
 */
public final class BlockCapabilitiesForge {

	/**
	 * Which capabilities one block-entity type publishes; {@code chest} selects the joined double-chest view;
	 * {@code lender}: the block entity of a block that lends another block's inventory and tank ({@link
	 * ItemHostRedirect}, {@link FluidHostRedirect} — the quarry's socket, MOD-1025), which answers with the lent ones.
	 */
	record Flags(boolean energy, boolean fluid, boolean item, boolean chest, boolean lender) {
		/** A lender's block entity: nothing of its own, the lent ports only. */
		static final Flags LENDER = new Flags(false, false, false, false, true);
	}

	private static volatile Map<BlockEntityType<?>, Flags> byType;

	private BlockCapabilitiesForge() {
	}

	/** {@code AttachCapabilitiesEvent.BlockEntities}: runs inside the block entity's constructor, see the provider. */
	static void attach(AttachCapabilitiesEvent.BlockEntities event) {
		BlockEntity be = event.getObject();
		Map<BlockEntityType<?>, Flags> byOurTypes = flags();
		Flags flags = byOurTypes == null ? null : byOurTypes.get(be.getType());
		if (flags == null && byOurTypes != null && lends(be.getBlockState())) {
			// MOD-1025: Fabric and NeoForge lend these as a block capability; Forge has only the block entity.
			flags = Flags.LENDER;
		}
		if (flags == null) {
			return;
		}
		BlockCapabilityProviderForge provider = new BlockCapabilityProviderForge(be, flags);
		event.addCapability(Industrialization.id("transfer"), provider);
		event.addListener(provider::invalidateAll); // setRemoved and chunk unload
	}

	/** A block of ours that lends another block's inventory or tank ({@code BlockCapabilityRoster.itemLenders}). */
	private static boolean lends(BlockState state) {
		return state.getBlock() instanceof ItemHostRedirect || state.getBlock() instanceof FluidHostRedirect;
	}

	/** True for a block entity of ours whose item capability is ours to answer (the item mixin asks). */
	public static boolean isOurItemContainer(BlockEntityType<?> type) {
		Map<BlockEntityType<?>, Flags> byOurTypes = flags();
		Flags flags = byOurTypes == null ? null : byOurTypes.get(type);
		return flags != null && flags.item();
	}

	/**
	 * Built on first use once our block-entity types are registered; null, and not cached, before that. Another mod
	 * may build a block entity of its own while registries are still filling (the attach event fires in every
	 * block entity's constructor); none of ours can exist yet, so it simply gets no capability from us instead of a
	 * crash naming our class.
	 */
	private static @Nullable Map<BlockEntityType<?>, Flags> flags() {
		Map<BlockEntityType<?>, Flags> map = byType;
		if (map == null) {
			synchronized (BlockCapabilitiesForge.class) {
				map = byType;
				if (map == null) {
					map = build();
					byType = map;
				}
			}
		}
		return map;
	}

	private static @Nullable Map<BlockEntityType<?>, Flags> build() {
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			if (!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(Industrialization.id(def.id()))) {
				return null;
			}
		}
		List<ContentManifest.BlockEntityDef<?>> energy = BlockCapabilityRoster.energyHosts();
		List<ContentManifest.BlockEntityDef<?>> fluid = BlockCapabilityRoster.fluidHosts();
		List<ContentManifest.BlockEntityDef<?>> item = BlockCapabilityRoster.itemContainers();
		Map<BlockEntityType<?>, Flags> map = new IdentityHashMap<>();
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			Flags flags = new Flags(energy.contains(def), fluid.contains(def), item.contains(def),
					AbstractChestBlockEntity.class.isAssignableFrom(def.type()), false);
			if (flags.energy() || flags.fluid() || flags.item()) {
				map.put(def.registeredType(), flags);
			}
		}
		return map;
	}
}
