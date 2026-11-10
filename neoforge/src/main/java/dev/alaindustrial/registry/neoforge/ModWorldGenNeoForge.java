package dev.alaindustrial.registry.neoforge;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModWorldgenTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
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
 * <p>{@code BuiltInRegistries.PLACEMENT_MODIFIER_TYPE} and {@code BuiltInRegistries.FEATURE_TYPE} are
 * frozen before mod construction on NeoForge, so these must be {@link DeferredRegister}s — and they
 * must fire before datapack load, or the JSON referring to {@code alaindustrial:oil_lake_filter} /
 * {@code alaindustrial:oil_lake} / {@code alaindustrial:oil_geyser} fails to parse.
 *
 * <p><b>MOD-226 (26.3): what is registered is the {@link MapCodec}, not an instance.</b> A feature is
 * now its own configuration — a record decoded from {@code data/<ns>/worldgen/feature/} — so the type
 * registry moved from {@code Registries.FEATURE} (which in 26.3 holds the decoded instances) to the
 * new {@code Registries.FEATURE_TYPE}, and both it and {@code PLACEMENT_MODIFIER_TYPE} are declared
 * {@code Registry<MapCodec<? extends X>>}. Verified against the 26.3 sources, not from memory:
 * {@code Feature.DIRECT_CODEC} dispatches on {@code BuiltInRegistries.FEATURE_TYPE} and
 * {@code PlacementModifier.CODEC} on {@code BuiltInRegistries.PLACEMENT_MODIFIER_TYPE}. The registry
 * ids the JSON names are unchanged.
 */
public final class ModWorldGenNeoForge {

	public static final DeferredRegister<MapCodec<? extends PlacementModifier>> PLACEMENT_MODIFIER_TYPES =
			DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES =
			DeferredRegister.create(Registries.FEATURE_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
			DeferredRegister.create(Registries.STRUCTURE_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
			DeferredRegister.create(Registries.STRUCTURE_PIECE, Industrialization.MOD_ID);

	static {
		// MOD-962: the entries are the shared lists of ModWorldgenTypes; each loader only replays them.
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

	/** The two ALA town registers on the mod bus (MOD-830). */
	public static void registerTownTypes(net.neoforged.bus.api.IEventBus modBus) {
		STRUCTURE_TYPES.register(modBus);
		STRUCTURE_PIECES.register(modBus);
	}

	private ModWorldGenNeoForge() {
	}
}
