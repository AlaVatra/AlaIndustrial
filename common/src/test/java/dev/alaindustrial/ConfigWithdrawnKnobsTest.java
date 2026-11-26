package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.environment.GeneratorConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * MOD-1031 withdrew the Mirror Concentrator and its three knobs ({@code radiantEuPerTick},
 * {@code radiantAssembledEuPerTick}, {@code radiantBuffer}). An operator's file saved before that still carries
 * them, edited or not: it must load as before, keep every other edit, and lose only the three dead keys when the
 * file is rewritten in canonical form (ADR-033: no schema rung, a key this build does not declare is not read).
 */
class ConfigWithdrawnKnobsTest {

	private static final String[] WITHDRAWN = {"radiantEuPerTick", "radiantAssembledEuPerTick", "radiantBuffer"};

	@TempDir
	Path dir;

	private Path file;
	private Supplier<Path> savedPath;

	@BeforeEach
	void bindConfigPath() {
		savedPath = Config.configPath;
		file = dir.resolve("alaindustrial.json");
		Config.configPath = () -> file;
	}

	@AfterEach
	void restore() {
		Config.configPath = savedPath;
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(dir.resolve("restore.json")));
	}

	@Test
	void aFileWithTheWithdrawnKnobsLoadsAndDropsThem() throws IOException {
		String edited = ConfigText.withValue(ConfigText.golden(), "generators", "solarEuPerTick", "3");
		// The three keys as a 0.1.240 file holds them, at the head of the generators section.
		String section = "  \"generators\": {\n";
		int at = edited.indexOf(section);
		assertTrue(at >= 0, "the golden file has no generators section");
		String old = edited.substring(0, at + section.length())
				+ "    \"_comment_radiantEuPerTick\": \"Mirror Concentrator output in EU/t during the day.\",\n"
				+ "    \"radiantEuPerTick\": 20,\n"
				+ "    \"radiantAssembledEuPerTick\": 24,\n"
				+ "    \"radiantBuffer\": 99999,\n"
				+ edited.substring(at + section.length());
		Files.writeString(file, old, StandardCharsets.UTF_8);

		assertEquals(Config.LoadResult.LOADED, Config.reload(), "an old file with the concentrator's knobs must load");
		assertEquals(3, GeneratorConfig.solarEuPerTick, "the operator's other edit is kept");
		assertEquals(16_000, GeneratorConfig.reinforcedSolarBuffer, "the old buffer knob does not leak into T3");

		String rewritten = ConfigText.read(file);
		for (String key : WITHDRAWN) {
			assertFalse(rewritten.contains("\"" + key + "\""), key + " is still written back");
		}
		assertNull(ConfigText.firstDifference(edited, rewritten),
				"the rewritten file is the canonical one with the operator's edit, nothing else");
	}
}
