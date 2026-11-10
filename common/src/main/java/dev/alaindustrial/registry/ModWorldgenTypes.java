package dev.alaindustrial.registry;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.worldgen.AbandonedLabFeature;
import dev.alaindustrial.worldgen.OilGeyserFeature;
import dev.alaindustrial.worldgen.OilLakeFeature;
import dev.alaindustrial.worldgen.OilLakeFilter;
import dev.alaindustrial.worldgen.town.AlaTownTypes;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Every worldgen <em>type</em> the mod registers, declared once for both loaders (MOD-962, the MOD-555
 * pattern): the placement-modifier type, the feature types, the ALA town's structure type and its piece type.
 * Each loader replays the four lists below in a single loop — Fabric eagerly through {@code Registry.register}
 * in {@code ModWorldGen}, NeoForge through {@code DeferredRegister.register} in {@code ModWorldGenNeoForge}.
 * Before this, the registrations were written by hand once per loader; a third loader would have needed a third
 * hand copy, and a type added on one loader and forgotten on the other fails the datapack parse there.
 *
 * <p>The configured/placed features themselves are data-driven ({@code data/alaindustrial/worldgen/...}) and
 * biome injection is {@link WorldgenInjections}; only the registry types live here. The values are suppliers
 * because NeoForge registers lazily.
 *
 * <p><b>Registration deadline.</b> All four registries must be filled before the datapack loads, because the
 * JSON names the ids and an unknown {@code "type"} (or an unknown placement modifier) is a parse error:
 * {@code alaindustrial:oil_lake_filter} keeps oil features out of villages, mineshafts and Ancient Cities
 * (MOD-238 audit); the three feature types are the oil lake and geyser (MOD-248) and the lore lab (MOD-513);
 * the town's structure type is the {@code "type"} of a town structure file and the lab piece's type is written
 * into saved chunks (MOD-830), so both must also exist before the first saved town is read. On NeoForge the
 * built-in registries are frozen before mod construction, so the placement-modifier and feature registers are
 * attached to the mod bus in the early registration pass and the two structure registers later, in the
 * mod-bus event pass ({@code registerTownTypes}); that order is kept.
 *
 * <p><b>MOD-226 (26.3): what goes into the placement-modifier and feature registries is the {@link MapCodec},
 * not an instance.</b> {@code PLACEMENT_MODIFIER_TYPE} and the {@code FEATURE_TYPE} registry are
 * {@code Registry<MapCodec<? extends X>>}: a feature is now its own configuration, a record decoded from
 * {@code data/<ns>/worldgen/feature/}, and {@code BuiltInRegistries.FEATURE} holds those decoded instances
 * rather than the types. Verified against the 26.3 sources: {@code Feature.DIRECT_CODEC} dispatches on
 * {@code BuiltInRegistries.FEATURE_TYPE} and {@code PlacementModifier.CODEC} on
 * {@code BuiltInRegistries.PLACEMENT_MODIFIER_TYPE}.
 */
public final class ModWorldgenTypes {

	/**
	 * One registry entry.
	 *
	 * @param id    registry id ({@code alaindustrial:<path>}); NeoForge registers its path under the mod id
	 * @param value the registered object, a supplier because NeoForge registers lazily
	 * @param <T>   the registry's element type
	 */
	public record Entry<T>(Identifier id, Supplier<T> value) {
	}

	/** {@code PLACEMENT_MODIFIER_TYPE}: {@code oil_lake_filter}, keeps oil features out of protected structures. */
	public static final List<Entry<MapCodec<? extends PlacementModifier>>> PLACEMENT_MODIFIERS = List.of(
			new Entry<>(OilLakeFilter.ID, () -> OilLakeFilter.CODEC));

	/** {@code FEATURE_TYPE}: {@code oil_lake}, {@code oil_geyser}, {@code abandoned_lab}. */
	public static final List<Entry<MapCodec<? extends Feature>>> FEATURE_TYPES = List.of(
			new Entry<>(OilLakeFeature.ID, () -> OilLakeFeature.CODEC),
			new Entry<>(OilGeyserFeature.ID, () -> OilGeyserFeature.CODEC),
			new Entry<>(AbandonedLabFeature.ID, () -> AbandonedLabFeature.CODEC));

	/** {@code STRUCTURE_TYPE}: {@code ala_town}, what decodes a town's structure file (MOD-830). */
	public static final List<Entry<StructureType<?>>> STRUCTURE_TYPES = List.of(
			new Entry<>(AlaTownTypes.STRUCTURE_TYPE_ID, () -> AlaTownTypes.STRUCTURE_TYPE));

	/** {@code STRUCTURE_PIECE}: {@code ala_town_lab}, what reads a saved lab piece back (MOD-830). */
	public static final List<Entry<StructurePieceType>> STRUCTURE_PIECES = List.of(
			new Entry<>(AlaTownTypes.LAB_PIECE_ID, () -> AlaTownTypes.LAB_PIECE));

	private ModWorldgenTypes() {
	}
}
