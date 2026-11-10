package dev.alaindustrial.registry;

import com.mojang.serialization.MapCodec;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Central registration for worldgen injection. The configured/placed feature JSON is data-driven
 * ({@code data/alaindustrial/worldgen/...}); this class replays the shared worldgen type lists
 * ({@link ModWorldgenTypes}) and wires the placed features into biomes by replaying the shared table
 * {@link WorldgenInjections#INJECTIONS} (MOD-708).
 */
public final class ModWorldGen {
	private ModWorldGen() {
	}

	public static void init() {
		// MOD-962: the placement-modifier, feature, structure and piece types are the shared lists of
		// ModWorldgenTypes (the registration deadline and the MapCodec note live in its javadoc).
		for (ModWorldgenTypes.Entry<MapCodec<? extends PlacementModifier>> e : ModWorldgenTypes.PLACEMENT_MODIFIERS) {
			Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, e.id(), e.value().get());
		}
		for (ModWorldgenTypes.Entry<MapCodec<? extends Feature>> e : ModWorldgenTypes.FEATURE_TYPES) {
			Registry.register(BuiltInRegistries.FEATURE_TYPE, e.id(), e.value().get());
		}
		for (ModWorldgenTypes.Entry<StructureType<?>> e : ModWorldgenTypes.STRUCTURE_TYPES) {
			Registry.register(BuiltInRegistries.STRUCTURE_TYPE, e.id(), e.value().get());
		}
		for (ModWorldgenTypes.Entry<StructurePieceType> e : ModWorldgenTypes.STRUCTURE_PIECES) {
			Registry.register(BuiltInRegistries.STRUCTURE_PIECE, e.id(), e.value().get());
		}
		// MOD-708: which placed feature goes into which biomes at which step is the shared table
		// WorldgenInjections.INJECTIONS; NeoForge's biome modifier files are written from the same rows.
		for (WorldgenInjections.Injection injection : WorldgenInjections.INJECTIONS) {
			BiomeModifications.addFeature(fabricSelector(injection), injection.step(), injection.feature());
		}
	}

	/** The biomes of a row as Fabric has always picked them: by its tag, or by the overworld's biome source. */
	private static Predicate<BiomeSelectionContext> fabricSelector(WorldgenInjections.Injection injection) {
		return switch (injection.fabricSelector()) {
			case BIOME_TAG -> BiomeSelectors.tag(injection.biomes());
			case FOUND_IN_OVERWORLD -> BiomeSelectors.foundInOverworld();
		};
	}
}
