package dev.alaindustrial.client.blueprint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Which blueprints this player has opened (MOD-797): drives the "NEW" stamp in the tooltip.
 *
 * <p>Since MOD-933 the set outlives the game session: it is kept on the client, one blueprint id per line in
 * {@value #FILE_NAME} next to the client config, so the stamp no longer comes back on every launch. It is the
 * player's own reading record on this computer, the same in every world; a record kept by the server (which an
 * advancement for reading every sheet would need) is a separate decision of MOD-933. A missing or unreadable
 * file means "nothing opened yet" and never stops the game.
 */
public final class BlueprintProgress {

	/** The file of opened blueprint ids, beside {@code alaindustrial-client.json}. */
	public static final String FILE_NAME = "alaindustrial-blueprints-viewed.txt";

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial");
	private static final Set<String> VIEWED = new TreeSet<>();
	private static @Nullable Path file;

	private BlueprintProgress() {
	}

	/** Reads the record from {@code configDir}; from now on every newly opened sheet is written there. */
	public static void init(Path configDir) {
		file = configDir.resolve(FILE_NAME);
		VIEWED.clear();
		VIEWED.addAll(read(file));
	}

	/** Record that the blueprint {@code id} was opened, and save the record when it is new. */
	public static void markViewed(String id) {
		if (VIEWED.add(id) && file != null) {
			write(file, VIEWED);
		}
	}

	/** Whether the blueprint {@code id} has been opened. */
	public static boolean isViewed(String id) {
		return VIEWED.contains(id);
	}

	/** The ids in {@code path}, one per line; blank lines skipped; an absent or unreadable file is empty. */
	static Set<String> read(Path path) {
		Set<String> ids = new TreeSet<>();
		if (!Files.isRegularFile(path)) {
			return ids;
		}
		try {
			for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
				if (!line.isBlank()) {
					ids.add(line.strip());
				}
			}
		} catch (IOException | RuntimeException e) {
			LOG.warn("[blueprint] could not read {}: {}; every sheet counts as new", path, e.toString());
		}
		return ids;
	}

	/** Writes {@code ids} sorted, one per line with LF endings (ADR-009), through a temporary file. */
	static void write(Path path, Set<String> ids) {
		try {
			Files.createDirectories(path.toAbsolutePath().getParent());
			List<String> lines = new ArrayList<>(new TreeSet<>(ids));
			Path temp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(temp, lines.isEmpty() ? "" : String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | RuntimeException e) {
			LOG.warn("[blueprint] could not save {}: {}", path, e.toString());
		}
	}
}
