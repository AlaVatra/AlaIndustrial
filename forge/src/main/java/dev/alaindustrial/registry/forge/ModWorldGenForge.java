package dev.alaindustrial.registry.forge;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModWorldgenTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Forge registration of the mod's worldgen extension points (MOD-962), the counterpart of
 * {@code ModWorldGenNeoForge}: it replays the four shared lists of {@link ModWorldgenTypes} (placement
 * modifiers, feature types, structure types, structure pieces).
 *
 * <p>Only the registry <em>types</em> live here; the configured/placed features are data-driven and shared.
 * What is registered is the {@link MapCodec}, not an instance (MOD-226, 26.3). The two-phase shape of
 * NeoForge is kept: {@link #register} attaches the placement-modifier and feature registers early, and
 * {@link #registerTownTypes} attaches the ALA town structure and piece registers later, in the mod-bus
 * event pass. All four fire before the datapack loads, which is the deadline the JSON needs.
 */
public final class ModWorldGenForge {

	public static final DeferredRegister<MapCodec<? extends PlacementModifier>> PLACEMENT_MODIFIER_TYPES =
			DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES =
			DeferredRegister.create(Registries.FEATURE_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
			DeferredRegister.create(Registries.STRUCTURE_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
			DeferredRegister.create(Registries.STRUCTURE_PIECE, Industrialization.MOD_ID);

	static {
		// The entries are the shared lists of ModWorldgenTypes; each loader only replays them.
		for (ModWorldgenTypes.Entry<MapCodec<? extends PlacementModifier>> e : ModWorldgenTypes.PLACEMENT_MODIFIERS) {
			PLACEMENT_MODIFIER_TYPES.register(e.id().getPath(), e.value());
		}
		for (ModWorldgenTypes.Entry<MapCodec<? extends Feature>> e : ModWorldgenTypes.FEATURE_TYPES) {
			FEATURE_TYPES.register(e.id().getPath(), e.value());
		}
		for (ModWorldgenTypes.Entry<StructureType<?>> e : ModWorldgenTypes.STRUCTURE_TYPES) {
			STRUCTURE_TYPES.register(e.id().getPath(), e.value());
		}
		for (ModWorldgenTypes.Entry<StructurePieceType> e : ModWorldgenTypes.STRUCTURE_PIECES) {
			STRUCTURE_PIECES.register(e.id().getPath(), e.value());
		}
	}

	/** The early pass: the placement-modifier and feature registers on the mod bus. */
	public static void register(BusGroup modBusGroup) {
		PLACEMENT_MODIFIER_TYPES.register(modBusGroup);
		FEATURE_TYPES.register(modBusGroup);
	}

	/** The two ALA town registers on the mod bus (MOD-830). */
	public static void registerTownTypes(BusGroup modBusGroup) {
		STRUCTURE_TYPES.register(modBusGroup);
		STRUCTURE_PIECES.register(modBusGroup);
	}

	private ModWorldGenForge() {
	}
}
