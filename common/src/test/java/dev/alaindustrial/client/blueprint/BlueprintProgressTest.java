package dev.alaindustrial.client.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The opened-blueprints record outlives the session (MOD-933): what one launch marks, the next launch reads back,
 * and a missing or damaged file means "nothing opened yet" instead of an error.
 */
class BlueprintProgressTest {

	@Test
	void aSheetOpenedInOneLaunchIsStillOpenedInTheNext(@TempDir Path dir) throws Exception {
		BlueprintProgress.init(dir);
		assertFalse(BlueprintProgress.isViewed("energy_basics"));
		BlueprintProgress.markViewed("energy_basics");
		BlueprintProgress.markViewed("elevator");

		BlueprintProgress.init(dir);
		assertTrue(BlueprintProgress.isViewed("energy_basics"), "the record was not read back after a restart");
		assertTrue(BlueprintProgress.isViewed("elevator"));
		assertFalse(BlueprintProgress.isViewed("escalator"));
		assertEquals("elevator\nenergy_basics\n",
				Files.readString(dir.resolve(BlueprintProgress.FILE_NAME), StandardCharsets.UTF_8),
				"sorted ids, one per line, LF endings");
	}

	@Test
	void aMissingFileMeansNothingOpened(@TempDir Path dir) {
		assertEquals(Set.of(), BlueprintProgress.read(dir.resolve("absent.txt")));
	}

	@Test
	void blankLinesAndCarriageReturnsAreIgnored(@TempDir Path dir) throws Exception {
		Path file = dir.resolve(BlueprintProgress.FILE_NAME);
		Files.writeString(file, "honey\r\n\r\n  \nslingshot\r\n", StandardCharsets.UTF_8);
		assertEquals(Set.of("honey", "slingshot"), BlueprintProgress.read(file));
	}

	@Test
	void aDirectoryInPlaceOfTheFileDoesNotStopTheGame(@TempDir Path dir) throws Exception {
		Files.createDirectory(dir.resolve(BlueprintProgress.FILE_NAME));
		BlueprintProgress.init(dir);
		BlueprintProgress.markViewed("honey");
		assertTrue(BlueprintProgress.isViewed("honey"), "the session still remembers what it cannot save");
	}
}
