package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModWorldgenTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge registration of the mod's worldgen extension points (MOD-238 audit, extended in
 * MOD-248). Replays the shared lists of {@link ModWorldgenTypes} (MOD-962), as Fabric's
 * {@code ModWorldGen} does eagerly.
 *
 * <p>Only the registry <em>types</em> live here: the configured/placed features themselves are
 * data-driven and shared, and biome injection on NeoForge is a datapack
 * {@code neoforge:add_features} biome modifier, not code.
 *
 * <p>{@code BuiltInRegistries.PLACEMENT_MODIFIER_TYPE} and {@code BuiltInRegistries.FEATURE} are
 * frozen before mod construction on NeoForge, so these must be {@link DeferredRegister}s — and they
 * must fire before datapack load, or the JSON referring to {@code alaindustrial:oil_lake_filter} /
 * {@code alaindustrial:oil_lake} / {@code alaindustrial:oil_geyser} fails to parse.
 */
public final class ModWorldGenNeoForge {

	public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIER_TYPES =
			DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<Feature<?>> FEATURES =
			DeferredRegister.create(Registries.FEATURE, Industrialization.MOD_ID);

	public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
			DeferredRegister.create(Registries.STRUCTURE_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
			DeferredRegister.create(Registries.STRUCTURE_PIECE, Industrialization.MOD_ID);

	static {
		// MOD-962: the entries are the shared lists of ModWorldgenTypes; each loader only replays them.
		for (ModWorldgenTypes.Entry<PlacementModifierType<?>> e : ModWorldgenTypes.PLACEMENT_MODIFIERS) {
			PLACEMENT_MODIFIER_TYPES.register(e.id().getPath(), e.value());
		}
		for (ModWorldgenTypes.Entry<Feature<?>> e : ModWorldgenTypes.FEATURE_TYPES) {
			FEATURES.register(e.id().getPath(), e.value());
		}
		for (ModWorldgenTypes.Entry<StructureType<?>> e : ModWorldgenTypes.STRUCTURE_TYPES) {
			STRUCTURE_TYPES.register(e.id().getPath(), e.value());
		}
		for (ModWorldgenTypes.Entry<StructurePieceType> e : ModWorldgenTypes.STRUCTURE_PIECES) {
			STRUCTURE_PIECES.register(e.id().getPath(), e.value());
		}
	}

	/** The two ALA town registers on the mod bus (MOD-830). */
	public static void registerTownTypes(net.neoforged.bus.api.IEventBus modBus) {
		STRUCTURE_TYPES.register(modBus);
		STRUCTURE_PIECES.register(modBus);
	}

	private ModWorldGenNeoForge() {
	}
}
