package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import dev.alaindustrial.loot.LootInjections;
import dev.alaindustrial.loot.LootInjections.Injection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * NeoForge's global loot modifier files are written from the shared injection table
 * {@link LootInjections#INJECTIONS} (MOD-962, step 2c): Fabric replays the table in code, NeoForge reads
 * these files, and this test is what keeps the two the same declaration.
 *
 * <p>For every row of the table, the file
 * {@code neoforge/src/main/resources/data/alaindustrial/loot_modifiers/<id>.json} must hold, byte for byte,
 * what {@link #render} writes for it: a {@code neoforge:add_table} modifier whose {@code conditions} list holds
 * the target's {@code neoforge:loot_table_id}, followed by {@code alaindustrial:bonus_chest_enabled} for a gated
 * row (the 26.2 form; on 26.3 the same row is one {@code condition}, an {@code all_of} when gated). No other
 * loot modifier file of this mod may exist. The files are
 * read as the server loaded them, so a file the game does not see counts as missing. A modifier applies to
 * its table whatever the table's source, as every row does on every loader (see {@link LootInjections}).
 *
 * <p><b>Written only by an explicit command (ADR-032)</b>, never by the build, a hook or {@code regen.py}:
 * <pre>
 * DIR=&lt;repo&gt;/neoforge/src/main/resources/data/alaindustrial/loot_modifiers
 * JAVA_TOOL_OPTIONS="-Dalaindustrial.lootModifiers.writeTo=$DIR" \
 *   ./gradlew :neoforge:test --tests dev.alaindustrial.LootModifierTableTest
 * </pre>
 * The run writes one file per row (LF line endings) and then fails on purpose, so it can never pass for a
 * check. A file whose id left the table is named in that failure and deleted by hand.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class LootModifierTableTest {

	/** System property naming the DIRECTORY the explicit write command puts the files into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.lootModifiers.writeTo";

	/**
	 * @implements MOD-962-LOOT01 — every NeoForge loot modifier file of the mod is exactly what the shared
	 *     injection table writes, and the table writes every file there is
	 */
	@Test
	void everyLootModifierFileIsWrittenFromTheTable(MinecraftServer server) throws IOException {
		Map<String, String> expected = render(LootInjections.INJECTIONS);
		Map<String, Resource> loaded = lootModifierFiles(server);
		TreeSet<String> orphans = new TreeSet<>(loaded.keySet());
		orphans.removeAll(expected.keySet());
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			Path dir = Path.of(writeTo);
			Files.createDirectories(dir);
			for (Map.Entry<String, String> file : expected.entrySet()) {
				Files.writeString(dir.resolve(file.getKey() + ".json"), file.getValue(), StandardCharsets.UTF_8);
			}
			fail("loot modifiers rewritten in " + dir + " (" + expected.size() + " files)"
					+ (orphans.isEmpty() ? "" : "; no table row names " + orphans + " any more, delete them by hand")
					+ " - review the diff, then run again without -D" + WRITE_TO_PROPERTY);
		}
		TreeSet<String> missing = new TreeSet<>(expected.keySet());
		missing.removeAll(loaded.keySet());
		assertEquals("", (missing.isEmpty() ? "" : "no file for table rows " + missing + "; ")
						+ (orphans.isEmpty() ? "" : "files no table row names: " + orphans),
				"MOD-962: the loot modifier files and LootInjections name different injections. Write them"
						+ " with the command in this class's javadoc.");
		List<String> differing = new ArrayList<>();
		for (Map.Entry<String, String> file : expected.entrySet()) {
			if (!file.getValue().equals(NeoForgeBiomeModifierSnapshotTest.read(loaded.get(file.getKey())))) {
				differing.add(file.getKey() + ".json");
			}
		}
		assertEquals(List.of(), differing, "MOD-962: these loot_modifiers files are not what LootInjections"
				+ " writes. Write them with the command in this class's javadoc.");
	}

	/** The mod's {@code loot_modifiers/*.json} as the server loaded them, keyed by file stem. */
	static Map<String, Resource> lootModifierFiles(MinecraftServer server) {
		Map<String, Resource> files = new TreeMap<>();
		for (Map.Entry<Identifier, Resource> entry : server.getResourceManager()
				.listResources("loot_modifiers", id -> id.getPath().endsWith(".json")).entrySet()) {
			Identifier id = entry.getKey();
			if (Industrialization.MOD_ID.equals(id.getNamespace())) {
				String path = id.getPath();
				String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
				files.put(name, entry.getValue());
			}
		}
		return files;
	}

	/** One NeoForge {@code add_table} file per row, keyed by {@link Injection#id()}, in the order of the table. */
	static Map<String, String> render(List<Injection> injections) {
		Map<String, String> files = new LinkedHashMap<>();
		for (Injection injection : injections) {
			if (files.containsKey(injection.id())) {
				fail("two LootInjections rows share the id '" + injection.id() + "' - one file per row");
			}
			// This line (26.2): a NeoForge 26.2 modifier takes a LIST of conditions, each keyed "condition" (on
			// 26.3, one "condition" object keyed "type", an all_of for a gated row). This is the form
			// docs/tools/lines/data_line_convert.py writes from the 26.3 file, tab-indented like its source.
			String condition = "\t\t{\n"
					+ "\t\t\t\"condition\": \"neoforge:loot_table_id\",\n"
					+ "\t\t\t\"loot_table_id\": \"" + injection.target().identifier() + "\"\n"
					+ (injection.bonusChestGate()
							? "\t\t},\n"
									+ "\t\t{\n"
									+ "\t\t\t\"condition\": \"alaindustrial:bonus_chest_enabled\"\n"
							: "")
					+ "\t\t}\n";
			files.put(injection.id(), "{\n"
					+ "\t\"type\": \"neoforge:add_table\",\n"
					+ "\t\"conditions\": [\n"
					+ condition
					+ "\t],\n"
					+ "\t\"table\": \"" + injection.table().identifier() + "\"\n"
					+ "}\n");
		}
		return files;
	}
}
