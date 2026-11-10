package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import dev.alaindustrial.compat.MobSpawns;
import dev.alaindustrial.registry.ModMobs;
import dev.alaindustrial.registry.WorldgenInjections;
import dev.alaindustrial.registry.WorldgenInjections.Injection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * NeoForge's biome modifier files are written from the shared injection table
 * {@link WorldgenInjections#INJECTIONS} (MOD-708, batch 9): Fabric replays the table in code, NeoForge reads
 * these files, and this test is what keeps the two the same declaration.
 *
 * <p>For every modifier name of the table, the file
 * {@code neoforge/src/main/resources/data/alaindustrial/neoforge/biome_modifier/<name>.json} must hold, byte
 * for byte, what {@link #render} writes for its rows, and every mob of {@link ModMobs#MOBS} with a natural spawn
 * has its {@code <mob>_spawns.json} as {@link #renderSpawns} writes it; no other biome modifier file of this mod may
 * exist. The files are read as the server loaded them, so a file the game does not see counts as missing.
 *
 * <p><b>Written only by an explicit command (ADR-032)</b>, never by the build, a hook or {@code regen.py}:
 * <pre>
 * DIR=&lt;repo&gt;/neoforge/src/main/resources/data/alaindustrial/neoforge/biome_modifier
 * JAVA_TOOL_OPTIONS="-Dalaindustrial.biomeModifiers.writeTo=$DIR" \
 *   ./gradlew :neoforge:test --tests dev.alaindustrial.BiomeModifierTableTest
 * </pre>
 * The run writes one file per modifier name (LF line endings) and then fails on purpose, so it can never pass
 * for a check. A file whose name left the table is named in that failure and deleted by hand.
 *
 * <p><b>The Forge loader reads the same table (MOD-962)</b>: {@value #FORGE_DIR} under the repository root holds
 * one {@code forge:add_features} / {@code forge:add_spawns} file per name, rendered by the same two methods. The
 * same command rewrites them, and the same test compares them byte for byte with the files on disk (the NeoForge
 * server does not load Forge's resources). A tree without a {@code forge/} directory (the {@code mc/26.2} line)
 * skips that half.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class BiomeModifierTableTest {

	/** System property naming the DIRECTORY the explicit write command puts the files into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.biomeModifiers.writeTo";

	/** The Forge loader's biome modifier directory, relative to the repository root. */
	static final String FORGE_DIR = "forge/src/main/resources/data/alaindustrial/forge/biome_modifier";

	/**
	 * @implements MOD-708-WGN03 — every NeoForge biome modifier file of the mod is exactly what the shared
	 *     injection table writes, and the table writes every file there is
	 */
	@Test
	void everyBiomeModifierFileIsWrittenFromTheTable(MinecraftServer server) throws IOException {
		Map<String, String> expected = render(WorldgenInjections.INJECTIONS, "neoforge");
		expected.putAll(renderSpawns(ModMobs.MOBS, "neoforge"));
		Path forgeRoot = repositoryRoot().resolve("forge");
		Map<String, String> forgeExpected = render(WorldgenInjections.INJECTIONS, "forge");
		forgeExpected.putAll(renderSpawns(ModMobs.MOBS, "forge"));
		Map<String, Resource> loaded = NeoForgeBiomeModifierSnapshotTest.biomeModifierFiles(server);
		TreeSet<String> orphans = new TreeSet<>(loaded.keySet());
		orphans.removeAll(expected.keySet());
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			writeAll(Path.of(writeTo), expected);
			if (Files.isDirectory(forgeRoot)) {
				writeAll(repositoryRoot().resolve(FORGE_DIR), forgeExpected);
			}
			fail("biome modifiers rewritten in " + writeTo + " (" + expected.size() + " files)"
					+ (orphans.isEmpty() ? "" : "; no table row names " + orphans + " any more, delete them by hand")
					+ " - review the diff, then run again without -D" + WRITE_TO_PROPERTY);
		}
		TreeSet<String> missing = new TreeSet<>(expected.keySet());
		missing.removeAll(loaded.keySet());
		assertEquals("", (missing.isEmpty() ? "" : "no file for table modifiers " + missing + "; ")
						+ (orphans.isEmpty() ? "" : "files no table row names: " + orphans),
				"MOD-708: the biome modifier files and WorldgenInjections name different modifiers. Write them"
						+ " with the command in this class's javadoc.");
		List<String> differing = new ArrayList<>();
		for (Map.Entry<String, String> file : expected.entrySet()) {
			if (!file.getValue().equals(NeoForgeBiomeModifierSnapshotTest.read(loaded.get(file.getKey())))) {
				differing.add(file.getKey() + ".json");
			}
		}
		assertEquals(List.of(), differing, "MOD-708: these neoforge/biome_modifier files are not what"
				+ " WorldgenInjections writes. Write them with the command in this class's javadoc.");
		if (Files.isDirectory(forgeRoot)) {
			assertEquals(List.of(), forgeDrift(repositoryRoot().resolve(FORGE_DIR), forgeExpected), "MOD-962: the"
					+ " Forge biome modifier files are not what WorldgenInjections writes (missing, differing or"
					+ " orphaned). Write them with the command in this class's javadoc.");
		}
	}

	/** Every Forge file that is missing, differs from the table or names no table row, in name order. */
	private static List<String> forgeDrift(Path dir, Map<String, String> expected) throws IOException {
		TreeSet<String> onDisk = new TreeSet<>();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".json"))
						.forEach(n -> onDisk.add(n.substring(0, n.length() - ".json".length())));
			}
		}
		List<String> drift = new ArrayList<>();
		for (Map.Entry<String, String> file : expected.entrySet()) {
			if (!onDisk.contains(file.getKey())) {
				drift.add(file.getKey() + ".json (missing)");
			} else if (!file.getValue().equals(Files.readString(dir.resolve(file.getKey() + ".json")))) {
				drift.add(file.getKey() + ".json (differs)");
			}
		}
		onDisk.removeAll(expected.keySet());
		onDisk.forEach(orphan -> drift.add(orphan + ".json (no table row)"));
		return drift;
	}

	private static void writeAll(Path dir, Map<String, String> files) throws IOException {
		Files.createDirectories(dir);
		for (Map.Entry<String, String> file : files.entrySet()) {
			Files.writeString(dir.resolve(file.getKey() + ".json"), file.getValue(), StandardCharsets.UTF_8);
		}
	}

	/** The nearest directory above the working directory that holds {@code settings.gradle}. */
	private static Path repositoryRoot() {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			if (Files.isRegularFile(dir.resolve("settings.gradle"))) {
				return dir;
			}
		}
		throw new IllegalStateException("no settings.gradle above " + Path.of("").toAbsolutePath());
	}

	/**
	 * One NeoForge {@code add_spawns} file per mob of {@link ModMobs#MOBS} (MOD-767), named
	 * {@code <mob>_spawns}: the Fabric side replays the same {@link ModMobs.NaturalSpawn} in code. The spawner
	 * entry's shape is the line's own ({@link MobSpawns#spawnerJson}). A mob that never appears on its own (the shift
	 * worker, MOD-832) has no file. Forge's {@code spawners} field takes only a list, so its file wraps the entry in
	 * one; NeoForge's also accepts the single object.
	 */
	static Map<String, String> renderSpawns(List<ModMobs.MobDef<?>> mobs, String loader) {
		Map<String, String> files = new LinkedHashMap<>();
		for (ModMobs.MobDef<?> mob : mobs) {
			ModMobs.NaturalSpawn spawn = mob.naturalSpawn();
			if (spawn == null) {
				continue;
			}
			String spawner = MobSpawns.spawnerJson(mob.key(), spawn.weight(), spawn.minGroup(), spawn.maxGroup());
			files.put(mob.id() + "_spawns", "{\n"
					+ "\t\"type\": \"" + loader + ":add_spawns\",\n"
					+ "\t\"biomes\": \"#" + spawn.biomes().location() + "\",\n"
					+ "\t\"spawners\": " + ("forge".equals(loader)
							? "[\n\t\t" + spawner.replace("\n", "\n\t") + "\n\t]" : spawner) + "\n"
					+ "}\n");
		}
		return files;
	}

	/** One {@code <loader>:add_features} file per modifier name, keyed by name, in the order of the table. */
	static Map<String, String> render(List<Injection> injections, String loader) {
		Map<String, List<Injection>> groups = new LinkedHashMap<>();
		for (Injection injection : injections) {
			groups.computeIfAbsent(injection.neoForgeModifier(), name -> new ArrayList<>()).add(injection);
		}
		Map<String, String> files = new LinkedHashMap<>();
		for (Map.Entry<String, List<Injection>> group : groups.entrySet()) {
			Injection first = group.getValue().get(0);
			StringBuilder features = new StringBuilder();
			for (Injection injection : group.getValue()) {
				if (injection.step() != first.step() || !injection.biomes().equals(first.biomes())) {
					fail("table rows of NeoForge modifier '" + group.getKey() + "' disagree: "
							+ first.feature().identifier() + " runs at " + first.step() + " in #"
							+ first.biomes().location() + ", " + injection.feature().identifier() + " at "
							+ injection.step() + " in #" + injection.biomes().location()
							+ " - one file has one step and one biome selector");
				}
				features.append(features.isEmpty() ? "" : ",\n")
						.append("\t\t\"").append(injection.feature().identifier()).append('"');
			}
			files.put(group.getKey(), "{\n"
					+ "\t\"type\": \"" + loader + ":add_features\",\n"
					+ "\t\"biomes\": \"#" + first.biomes().location() + "\",\n"
					+ "\t\"features\": [\n" + features + "\n\t],\n"
					+ "\t\"step\": \"" + first.step().getSerializedName() + "\"\n"
					+ "}\n");
		}
		return files;
	}
}
