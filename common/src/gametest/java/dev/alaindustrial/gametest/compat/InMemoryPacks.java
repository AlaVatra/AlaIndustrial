package dev.alaindustrial.gametest.compat;

import java.util.stream.Stream;
import net.minecraft.server.packs.AbstractPackMetadataResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;

/**
 * Version facade (ADR-036) for a data pack a game-test lane builds in memory (the Forge roster pack, ADR-053): the
 * base class such a pack extends and the {@link Pack.ResourcesSupplier} that hands it to {@code Pack}. The names
 * and signatures are the same on both lines; the bodies are this line's.
 *
 * <p><b>This twin: Minecraft 26.3</b>, where a pack's metadata half is its own {@link PackMetadataResources}: the
 * base is {@link AbstractPackMetadataResources} (it reads {@code pack.mcmeta} sections through
 * {@code getRootResource}), and the supplier answers {@code openMetadata} with the pack and {@code openResources}
 * with a stream of it. 26.2 had one {@code PackResources} for both halves, the base {@code AbstractPackResources}
 * and a supplier of {@code openPrimary}/{@code openFull}; javap of both {@code minecraft-merged.jar}s.
 *
 * <p>It lives in {@code dev.alaindustrial.gametest.compat}: the gametest source set is its own JPMS module
 * on NeoForge (ADR-036, "Boundaries").
 */
public final class InMemoryPacks {

	private InMemoryPacks() {}

	/** The base of an in-memory pack: implements the metadata half; the subclass serves the files. */
	public abstract static class Base extends AbstractPackMetadataResources implements PackResources {

		protected Base(PackLocationInfo location) {
			super(location);
		}
	}

	/** A supplier that answers every open of the pack with {@code resources} itself. */
	public static Pack.ResourcesSupplier supplier(PackResources resources) {
		return new Pack.ResourcesSupplier() {
			@Override
			public PackMetadataResources openMetadata(PackLocationInfo location) {
				return resources;
			}

			@Override
			public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata metadata) {
				return Stream.of(resources);
			}
		};
	}
}
