package dev.alaindustrial.gametest.compat;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/**
 * Version facade (ADR-036) naming the registry that holds worldgen feature TYPES (the {@code "type"} a feature
 * JSON names, e.g. {@code alaindustrial:oil_lake}), for a scenario that counts the mod's entries in it. The
 * signature of {@link #registry} is the same on both lines; the body is this line's.
 *
 * <p><b>This twin: Minecraft 26.2</b>, where a feature type is a {@code Feature} instance and the registry is
 * {@code Registries.FEATURE} (26.3 moved the types to {@code Registries.FEATURE_TYPE}, of {@code MapCodec}s, and
 * gave {@code FEATURE} to the decoded features; javap of both {@code minecraft-merged.jar}s).
 *
 * <p>It lives in {@code dev.alaindustrial.gametest.compat}: the gametest source set is its own JPMS module
 * on NeoForge (ADR-036, "Boundaries").
 */
public final class FeatureTypes {

	private FeatureTypes() {}

	/** The key of the feature-type registry of this line. */
	public static ResourceKey<? extends Registry<?>> registry() {
		return Registries.FEATURE;
	}
}
