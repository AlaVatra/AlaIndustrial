package dev.alaindustrial.worldgen;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for the chunk walk of the oil structure filter (MOD-970): a region that answers {@code null} for a chunk
 * it said it had (Distant Horizons) must read as "no protected structure here", never as an exception.
 *
 * @implements TC-OIL-002-UNIT05 — oil structure filter chunk walk is null-safe
 */
class OilLakeStructureWalkTest {

	/** A chunk of the fake world: the starts it knows (structure -> covers the probe) and its references. */
	private record Chunk(Map<String, Boolean> starts, Map<String, List<int[]>> references) {
	}

	/** Region over a map of chunks; a key present with a null value is "hasChunk yes, getChunk null". */
	private static final class FakeRegion implements OilLakeStructureWalk.Region<Chunk> {
		final Map<Long, Chunk> chunks = new HashMap<>();
		final Set<Long> inReach = new HashSet<>();
		int reads;

		static long key(int x, int z) {
			return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
		}

		FakeRegion put(int x, int z, Chunk chunk) {
			chunks.put(key(x, z), chunk);
			inReach.add(key(x, z));
			return this;
		}

		@Override
		public boolean hasChunk(int x, int z) {
			return inReach.contains(key(x, z));
		}

		@Override
		public Chunk chunkOrNull(int x, int z) {
			reads++;
			return chunks.get(key(x, z));
		}
	}

	private static final OilLakeStructureWalk.Lookup<Chunk, String> LOOKUP = new OilLakeStructureWalk.Lookup<>() {
		@Override
		public boolean anyReference(Chunk chunk, String structure, OilLakeStructureWalk.StartVisitor visitor) {
			for (int[] ref : chunk.references().getOrDefault(structure, List.of())) {
				if (visitor.visit(ref[0], ref[1])) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean startCovers(Chunk startChunk, String structure) {
			return startChunk.starts().getOrDefault(structure, false);
		}
	};

	private static Chunk probeChunk(int[]... refs) {
		return new Chunk(Map.of(), Map.of("town", List.of(refs)));
	}

	private static Chunk startChunk(boolean covers) {
		return new Chunk(Map.of("town", covers), Map.of());
	}

	private static boolean walk(FakeRegion region, boolean wholeArea) {
		return OilLakeStructureWalk.isProtected(region, LOOKUP, List.of("town"), wholeArea, 0, 0);
	}

	@Test
	void nullCentreChunkIsNotProtectedInBothModes() {
		FakeRegion region = new FakeRegion();
		region.chunks.put(FakeRegion.key(0, 0), null);
		region.inReach.add(FakeRegion.key(0, 0));
		assertDoesNotThrow(() -> walk(region, false));
		assertFalse(walk(region, false));
		assertFalse(walk(region, true));
	}

	@Test
	void nullStartChunkIsSkippedAndTheNextReferenceStillCounts() {
		FakeRegion region = new FakeRegion().put(0, 0, probeChunk(new int[] { 5, 5 }, new int[] { 6, 6 }));
		region.chunks.put(FakeRegion.key(5, 5), null);
		region.inReach.add(FakeRegion.key(5, 5));
		region.put(6, 6, startChunk(true));
		assertTrue(walk(region, false));
	}

	@Test
	void nullStartChunkAloneIsNotProtected() {
		FakeRegion region = new FakeRegion().put(0, 0, probeChunk(new int[] { 5, 5 }));
		region.chunks.put(FakeRegion.key(5, 5), null);
		region.inReach.add(FakeRegion.key(5, 5));
		assertFalse(walk(region, false));
	}

	@Test
	void cleanTwinAnswersFromTheStart() {
		FakeRegion covering = new FakeRegion().put(0, 0, probeChunk(new int[] { 5, 5 })).put(5, 5, startChunk(true));
		FakeRegion missing = new FakeRegion().put(0, 0, probeChunk(new int[] { 5, 5 })).put(5, 5, startChunk(false));
		assertTrue(walk(covering, false));
		assertFalse(walk(missing, false));
	}

	@Test
	void chunkOutsideTheRegionIsNeverRead() {
		FakeRegion none = new FakeRegion();
		assertFalse(walk(none, false));
		assertEquals(0, none.reads);
		FakeRegion farStart = new FakeRegion().put(0, 0, probeChunk(new int[] { 9, 9 }));
		farStart.chunks.put(FakeRegion.key(9, 9), startChunk(true));
		assertFalse(walk(farStart, false));
		assertEquals(1, farStart.reads, "only the probe chunk; the start chunk is out of reach");
	}

	@Test
	void wholeAreaAnswersFromReferencesWithoutReadingTheStart() {
		FakeRegion region = new FakeRegion().put(0, 0, probeChunk(new int[] { 5, 5 }));
		assertTrue(walk(region, true));
		assertEquals(1, region.reads);
		FakeRegion empty = new FakeRegion().put(0, 0, probeChunk());
		assertFalse(walk(empty, true));
	}
}
