package dev.alaindustrial.gametest.compat;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/**
 * Version facade (ADR-036) naming the registry that holds worldgen feature TYPES (the {@code "type"} a feature
 * JSON names, e.g. {@code alaindustrial:oil_lake}), for a scenario that counts the mod's entries in it. The
 * signature of {@link #registry} is the same on both lines; the body is this line's.
 *
 * <p><b>This twin: Minecraft 26.3</b>, where the types are {@code MapCodec}s in {@code Registries.FEATURE_TYPE} and
 * {@code Registries.FEATURE} holds the decoded features (26.2 kept the types, {@code Feature} instances, in
 * {@code Registries.FEATURE}; javap of both {@code minecraft-merged.jar}s).
 *
 * <p>It lives in {@code dev.alaindustrial.gametest.compat}: the gametest source set is its own JPMS module
 * on NeoForge (ADR-036, "Boundaries").
 */
public final class FeatureTypes {

	private FeatureTypes() {}

	/** The key of the feature-type registry of this line. */
	public static ResourceKey<? extends Registry<?>> registry() {
		return Registries.FEATURE_TYPE;
	}
}
