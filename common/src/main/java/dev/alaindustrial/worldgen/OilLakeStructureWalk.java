package dev.alaindustrial.worldgen;

/**
 * The Minecraft-free core of the oil filter's structure lookup (MOD-970): the two chunk hops of
 * {@code OilLakeFilter#isProtected}, with every decision about a chunk that is not there made in ONE place so it
 * can be unit-tested at L1 with a planted {@code null} and a clean twin.
 *
 * <p>A chunk is read only through {@link #readable}: outside the region nothing is read at all, and a region that
 * answers {@code null} for a chunk it claimed to have (a region subclass from another mod, see the filter's class
 * doc) is the same soft failure — "no protected structure here". The Minecraft side only adapts types: it supplies
 * the {@link Region} and the {@link Lookup}.
 *
 * @param <C> the chunk type of the caller
 * @param <S> the structure type of the caller
 */
final class OilLakeStructureWalk {

	private OilLakeStructureWalk() {
	}

	/** The part of a world-generation region the walk may ask: whether a chunk is in reach, and the chunk. */
	interface Region<C> {
		boolean hasChunk(int chunkX, int chunkZ);

		/**
		 * The chunk at the coordinates, or null. The precondition "{@link #hasChunk} said yes" is not enforced by
		 * this interface: {@link OilLakeStructureWalk#readable} guarantees it by never calling this otherwise, so
		 * an implementation must not be called directly.
		 */
		C chunkOrNull(int chunkX, int chunkZ);
	}

	/** Receives the chunk coordinates a structure reference points at; returns true to stop the walk. */
	interface StartVisitor {
		boolean visit(int startChunkX, int startChunkZ);
	}

	/** What the walk asks a chunk about a structure. */
	interface Lookup<C, S> {
		/** Offers every structure reference of the chunk to the visitor; true as soon as the visitor said true. */
		boolean anyReference(C chunk, S structure, StartVisitor visitor);

		/** Whether the structure start recorded in this chunk covers the position being probed. */
		boolean startCovers(C startChunk, S structure);
	}

	/**
	 * The only way a chunk is read: {@code null} when the region has no such chunk (no read is attempted) or when
	 * the region answered {@code null} anyway.
	 */
	static <C> C readable(Region<C> region, int chunkX, int chunkZ) {
		if (!region.hasChunk(chunkX, chunkZ)) {
			return null;
		}
		return region.chunkOrNull(chunkX, chunkZ);
	}

	/**
	 * Whether a protected structure — or, with {@code wholeArea}, its area at a chunk's precision — covers the
	 * position in chunk {@code (chunkX, chunkZ)}. An unreadable probe chunk answers false; an unreadable start
	 * chunk is skipped and the walk goes on with the next reference.
	 */
	static <C, S> boolean isProtected(Region<C> region, Lookup<C, S> lookup, Iterable<S> structures,
			boolean wholeArea, int chunkX, int chunkZ) {
		C chunk = readable(region, chunkX, chunkZ);
		if (chunk == null) {
			return false;
		}
		for (S structure : structures) {
			boolean hit = wholeArea
					? lookup.anyReference(chunk, structure, (x, z) -> true)
					: lookup.anyReference(chunk, structure, (x, z) -> {
						C start = readable(region, x, z);
						return start != null && lookup.startCovers(start, structure);
					});
			if (hit) {
				return true;
			}
		}
		return false;
	}
}
