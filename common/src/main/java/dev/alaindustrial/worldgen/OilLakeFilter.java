package dev.alaindustrial.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Placement filter {@code alaindustrial:oil_lake_filter} (MOD-238 audit): rejects an oil lake whose
 * footprint would land inside a structure listed in {@code #alaindustrial:no_oil_lakes}.
 *
 * <p><b>Why this is needed.</b> Oil lakes generate at {@link
 * net.minecraft.world.level.levelgen.GenerationStep.Decoration#LAKES} (step index 1), long before
 * the structure steps — but the feature writes into a 3 × 3 chunk area, so a lake placed in the
 * chunk currently being decorated can overwrite a structure that a NEIGHBOUR chunk already
 * generated. The configured feature's {@code can_replace_with_air_or_fluid} predicate is no defence:
 * {@code #minecraft:features_cannot_replace} protects seven blocks (bedrock, spawner, chest, end
 * portal frame, reinforced deepslate, trial spawner, vault) — planks, beds and rails are not among
 * them. The underground band (Y −40…−8) overlaps Ancient City and mineshafts directly.
 *
 * <p><b>Why the lookup is hand-rolled (MOD-246).</b> The obvious call,
 * {@code StructureManager#getStructureWithPieceAt}, <b>crashes world generation</b>. It resolves a
 * position in two hops: read the probe's chunk for {@code getReferencesForStructure} — the chunk
 * coordinates where matching structures <em>start</em> — then walk into each of those chunks for the
 * {@code StructureStart} itself. Hop one is local; hop two goes wherever the structure began, which
 * for a mineshaft or an Ancient City is easily a dozen chunks away. {@code WorldGenRegion} allows
 * access only within {@code generatingStep.directDependencies().size()} chessboard distance and
 * throws {@code IllegalStateException: Requested chunk unavailable during world generation}
 * otherwise. The previous version of this class argued it was safe because every probe stays within
 * ±8 blocks of the origin; that is true of the probes and irrelevant to where the references lead.
 *
 * <p>So the two hops are done here, with {@link WorldGenRegion#hasChunk} in front of each one: a
 * chunk outside the region is answered "no protected structure here" rather than crashed on. That is
 * a deliberate soft failure — the filter can miss a structure whose start chunk is out of reach, and
 * a lake may clip it, which is the same outcome as not having the filter at all and strictly better
 * than a dead world.
 *
 * <p>{@code requireChunk = false} is <b>not</b> an alternative, and {@code hasChunk} alone is not the whole
 * answer (MOD-970). Verified by {@code javap} on 26.3 and 26.2: vanilla
 * {@code WorldGenRegion#getChunk(int, int, ChunkStatus, boolean)} never reads that parameter and either returns
 * the chunk or throws; it never returns {@code null}. {@code WorldGenRegion} is not final, so a region from
 * another mod can subclass it. The trace of GitHub issue 7 (Distant Horizons 3.3.4) shows a
 * {@code NullPointerException} on {@code "chunk"} inside {@code isProtected}; the conclusion drawn from it is that
 * the Distant Horizons region answers {@code null} for a chunk {@code hasChunk} accepted. The Distant Horizons
 * bytecode itself was not inspected, so this is an inference from the trace, not a verified fact. Every read goes
 * through {@link OilLakeStructureWalk#readable}: outside the region nothing is read, and {@code null} is the same
 * soft failure as above — "no protected structure here". An exception thrown from a foreign {@code getChunk}
 * itself is deliberately not caught. {@code hasChunk} stays the only non-throwing probe in vanilla — a plain
 * distance comparison.
 *
 * Asking for {@code STRUCTURE_STARTS} is legal at every allowed distance, because it is at or before
 * each status in the dependency array ({@code [CARVERS, CARVERS, STRUCTURE_STARTS × 7]} at
 * {@code FEATURES}), and starts carry their full piece list from that status onward.
 *
 * <p>The sampling geometry itself lives in the Minecraft-free {@link OilLakeSamples} so it can be
 * unit-tested at L1.
 */
public final class OilLakeFilter extends PlacementFilter {

	/** Registry id of this modifier type; the placed-feature JSON refers to it by this name. */
	public static final Identifier ID = Industrialization.id("oil_lake_filter");

	/**
	 * Structures an oil lake must never overwrite. Data-driven
	 * ({@code data/alaindustrial/tags/worldgen/structure/no_oil_lakes.json}) so a modpack can add
	 * its own structures — or empty the tag — without touching code.
	 */
	public static final TagKey<Structure> NO_OIL_LAKES =
			TagKey.create(Registries.STRUCTURE, Industrialization.id("no_oil_lakes"));

	/**
	 * Structures an oil feature must keep out of as a whole AREA, not piece by piece (MOD-830): the structure's
	 * own bounding box — its pieces and the terrain margin around them — rather than the boxes of the pieces.
	 * An ALA town ({@code #alaindustrial:ala_towns}) is ground between buildings, and its ground pieces follow
	 * the terrain, so their boxes say little about where their blocks lie; "no oil lake inside the town" is a
	 * question about the area. Data-driven like {@link #NO_OIL_LAKES}
	 * ({@code data/alaindustrial/tags/worldgen/structure/no_oil_lakes_in_area.json}).
	 *
	 * <p>Answered from the chunk's own structure references, never from the start: the game records a reference in
	 * every chunk the structure's box reaches ({@code ChunkGenerator.createReferences}, javap 26.3), so a reference
	 * here means the area reaches this chunk. That holds wherever the town began — its start chunk may lie outside
	 * the region the oil feature may read, and a town is wider than that region — at the price of a chunk's
	 * precision at the area's edge.
	 */
	public static final TagKey<Structure> NO_OIL_LAKES_IN_AREA =
			TagKey.create(Registries.STRUCTURE, Industrialization.id("no_oil_lakes_in_area"));

	/**
	 * The footprint this instance guards, in blocks around the placement origin (MOD-248). Until then
	 * the filter was stateless and hard-coded to the 16 × 8 × 16 box of {@code minecraft:lake}; there
	 * are now four oil features with four different extents, and a filter calibrated for a lake would
	 * wave a geyser's dome straight into an Ancient City.
	 */
	private final int horizontalRadius;
	private final int minYOffset;
	private final int maxYOffset;
	private final int yStep;

	public static final MapCodec<OilLakeFilter> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(1, 16).fieldOf("horizontal_radius")
					.forGetter(filter -> filter.horizontalRadius),
			Codec.intRange(-512, 0).fieldOf("min_y_offset")
					.forGetter(filter -> filter.minYOffset),
			Codec.intRange(0, 64).fieldOf("max_y_offset")
					.forGetter(filter -> filter.maxYOffset),
			Codec.intRange(1, 64).fieldOf("y_step")
					.forGetter(filter -> filter.yStep))
			.apply(instance, OilLakeFilter::new));

	/**
	 * The modifier type object. Created eagerly in common (it is a plain lambda over {@link #CODEC},
	 * not a registry read) and registered into {@code BuiltInRegistries.PLACEMENT_MODIFIER_TYPE} by
	 * each loader — Fabric eagerly, NeoForge through a {@code DeferredRegister}.
	 */
	public static final PlacementModifierType<OilLakeFilter> TYPE = () -> CODEC;

	private OilLakeFilter(int horizontalRadius, int minYOffset, int maxYOffset, int yStep) {
		this.horizontalRadius = horizontalRadius;
		this.minYOffset = minYOffset;
		this.maxYOffset = maxYOffset;
		this.yStep = yStep;
	}

	// WorldGenRegion#getLevel is soft-deprecated ("do not touch the server level from worldgen"). It is
	// used here for the one thing it is still the only route to — asking whether this world generates
	// structures at all — exactly as vanilla's own ChunkStatusTasks#generateFeatures does before calling
	// applyBiomeDecoration. Nothing else on the ServerLevel is read; the structure lookup itself never
	// leaves the region (see the class doc, MOD-246).
	@SuppressWarnings("deprecation")
	@Override
	protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
		WorldGenLevel level = context.getLevel();
		if (!(level instanceof WorldGenRegion region)) {
			// Not the chunk-decoration path (e.g. a /place command against a live level): there is no
			// region to scope the lookup to, so do not guess — let the placement through.
			return true;
		}
		if (!region.getLevel().structureManager().shouldGenerateStructures()) {
			return true;
		}
		List<Structure> protectedStructures = structuresOf(region, NO_OIL_LAKES);
		List<Structure> protectedAreas = structuresOf(region, NO_OIL_LAKES_IN_AREA);
		if (protectedStructures.isEmpty() && protectedAreas.isEmpty()) {
			// Empty (or absent) tags — a modpack opting out. Nothing to protect, nothing to look up.
			return true;
		}
		// One adapter per filter call, not per probe: the probe position is the only thing that changes.
		Probe probe = new Probe(region);
		return !OilLakeSamples.anyBlocked(origin.getX(), origin.getY(), origin.getZ(),
				horizontalRadius, minYOffset, maxYOffset, yStep,
				(x, y, z) -> probe.at(x, y, z).isProtected(protectedStructures, false)
						|| probe.isProtected(protectedAreas, true));
	}

	private static List<Structure> structuresOf(WorldGenRegion region, TagKey<Structure> tag) {
		List<Structure> structures = new ArrayList<>();
		region.registryAccess().lookupOrThrow(Registries.STRUCTURE).getTagOrEmpty(tag)
				.forEach(holder -> structures.add(holder.value()));
		return structures;
	}

	/**
	 * The region and the structure lookup of one filter call, adapted once for {@link OilLakeStructureWalk}; the
	 * probed position is the only state that moves between probes, so a probe allocates nothing here. Used from
	 * the one worldgen thread that runs the call, never shared.
	 *
	 * <p>Whether a protected structure — or, with {@code wholeArea}, its area at a chunk's precision (see
	 * {@link #NO_OIL_LAKES_IN_AREA}) — covers the probed position, asking only chunks this region is allowed to
	 * touch. Every chunk read goes through {@link OilLakeStructureWalk#readable}; see the class doc for why, and
	 * what the soft failure costs.
	 */
	private static final class Probe
			implements OilLakeStructureWalk.Region<ChunkAccess>, OilLakeStructureWalk.Lookup<ChunkAccess, Structure> {
		private final WorldGenRegion region;
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		Probe(WorldGenRegion region) {
			this.region = region;
		}

		Probe at(int x, int y, int z) {
			pos.set(x, y, z);
			return this;
		}

		boolean isProtected(List<Structure> structures, boolean wholeArea) {
			return OilLakeStructureWalk.isProtected(this, this, structures, wholeArea,
					SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
		}

		@Override
		public boolean hasChunk(int chunkX, int chunkZ) {
			return region.hasChunk(chunkX, chunkZ);
		}

		@Override
		public ChunkAccess chunkOrNull(int chunkX, int chunkZ) {
			return region.getChunk(chunkX, chunkZ, ChunkStatus.STRUCTURE_STARTS, false);
		}

		@Override
		public boolean anyReference(ChunkAccess chunk, Structure structure,
				OilLakeStructureWalk.StartVisitor visitor) {
			for (long reference : chunk.getReferencesForStructure(structure)) {
				// References are packed chunk coordinates; unpack without allocating a ChunkPos.
				if (visitor.visit(ChunkPos.getX(reference), ChunkPos.getZ(reference))) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean startCovers(ChunkAccess startChunk, Structure structure) {
			StructureStart start = startChunk.getStartForStructure(structure);
			return start != null && start.isValid() && coversPosition(start, pos);
		}
	}

	/**
	 * Piece-level containment, matching what {@code getStructureWithPieceAt} means: a structure's own
	 * bounding box is the union of its pieces and can swallow a lot of empty space (a village's box
	 * spans the whole settlement), so testing it instead would reject far more lakes than intended.
	 */
	private static boolean coversPosition(StructureStart start, BlockPos pos) {
		for (StructurePiece piece : start.getPieces()) {
			if (piece.getBoundingBox().isInside(pos)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public PlacementModifierType<?> type() {
		return TYPE;
	}
}
