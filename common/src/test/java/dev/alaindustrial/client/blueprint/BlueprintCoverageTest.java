package dev.alaindustrial.client.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.gametest.CreativeTabSnapshotView;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The blueprint coverage guard (MOD-935): every item the player can hold is the subject of some blueprint, or
 * its row in the shared exemption list ({@code player_coverage_exemptions.json}, MOD-963) names the
 * {@code blueprint} surface with a reason.
 *
 * <p><b>What "the player can hold" means here.</b> The reviewed creative-tab reference
 * ({@link CreativeTabSnapshotView}) is what both loaders' tabs show, and {@code CreativeTabCoverageScenarios}
 * holds every item to "shown in a tab, or flagged {@code hiddenFromPlayers}". So the tab's mod ids are exactly
 * the visible items, read on the MC-free L1 lane as an object rather than by parsing the content declarations.
 *
 * <p>An exemption fails the guard when it has no reason, when the player cannot hold the item, and when the
 * item has a blueprint after all; each rule is proven able to fail by a planted violator below.
 */
class BlueprintCoverageTest {

	private static final String SURFACE = "blueprint";
	private static final String NS = "alaindustrial:";
	private static final Pattern TAB_ID = Pattern.compile("(?<![\\w:])alaindustrial:([a-z0-9_]+)");
	private static final Pattern ROW = Pattern.compile("\"([a-z0-9_]+)\"\\s*:\\s*\\{([^{}]*)}");
	private static final Pattern SURFACES = Pattern.compile("\"surfaces\"\\s*:\\s*\\[([^\\]]*)]");
	private static final Pattern REASON = Pattern.compile("\"reason\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
	private static final Pattern WORD = Pattern.compile("\"([^\"]*)\"");

	/** One row of the shared list: the surfaces it is left off and why. */
	record Exemption(List<String> surfaces, String reason) {
	}

	@Test
	void everyVisibleItemHasABlueprintOrAReasonedExemption() throws IOException {
		assertTrue(CreativeTabSnapshotView.captured(), "the creative-tab reference was never captured");
		List<String> findings = findings(visibleItems(), subjects(), exemptions());
		assertTrue(findings.isEmpty(), String.join("\n", findings));
	}

	@Test
	void theVisibleListIsNotEmpty() {
		assertTrue(visibleItems().size() > 300, "the tab reference parsed to " + visibleItems().size()
				+ " ids: the format changed, not the content");
	}

	@Test
	void aPlantedGapIsReported() {
		List<String> found = findings(Set.of(NS + "a", NS + "b"), Set.of(NS + "a"), Map.of());
		assertEquals(1, found.size(), found.toString());
		assertTrue(found.get(0).contains(NS + "b"), found.toString());
	}

	@Test
	void aReasonedExemptionCoversTheGap() {
		Map<String, Exemption> rows = Map.of("b", new Exemption(List.of(SURFACE), "MOD-0: why"));
		assertTrue(findings(Set.of(NS + "a", NS + "b"), Set.of(NS + "a"), rows).isEmpty());
	}

	@Test
	void anExemptionOfAnotherSurfaceDoesNotCoverTheGap() {
		Map<String, Exemption> rows = Map.of("b", new Exemption(List.of("book"), "MOD-0: why"));
		assertEquals(1, findings(Set.of(NS + "a", NS + "b"), Set.of(NS + "a"), rows).size());
	}

	@Test
	void anExemptionWithNoReasonFails() {
		Map<String, Exemption> rows = Map.of("b", new Exemption(List.of(SURFACE), " "));
		List<String> found = findings(Set.of(NS + "a", NS + "b"), Set.of(NS + "a"), rows);
		assertTrue(found.stream().anyMatch(f -> f.contains("no reason")), found.toString());
	}

	@Test
	void anExemptionOfAnItemWithABlueprintFails() {
		Map<String, Exemption> rows = Map.of("a", new Exemption(List.of(SURFACE), "MOD-0: why"));
		List<String> found = findings(Set.of(NS + "a"), Set.of(NS + "a"), rows);
		assertTrue(found.stream().anyMatch(f -> f.contains("has a blueprint")), found.toString());
	}

	@Test
	void anExemptionOfAnItemThePlayerCannotHoldFails() {
		Map<String, Exemption> rows = Map.of("ghost", new Exemption(List.of(SURFACE), "MOD-0: why"));
		List<String> found = findings(Set.of(NS + "a"), Set.of(NS + "a"), rows);
		assertTrue(found.stream().anyMatch(f -> f.contains("cannot hold")), found.toString());
	}

	@Test
	void theSharedListParsesAndHasRows() throws IOException {
		assertTrue(exemptions().size() >= 5, "the shared exemption list parsed to " + exemptions().size() + " rows");
	}

	/** Every reason the guard is red, one line each; empty when the coverage holds. */
	static List<String> findings(Set<String> visible, Set<String> subjects, Map<String, Exemption> rows) {
		List<String> out = new ArrayList<>();
		Set<String> exempt = new TreeSet<>();
		for (Map.Entry<String, Exemption> row : new java.util.TreeMap<>(rows).entrySet()) {
			if (!row.getValue().surfaces().contains(SURFACE)) {
				continue;
			}
			String id = NS + row.getKey();
			String where = "exemption " + row.getKey() + " (" + SURFACE + "): ";
			if (row.getValue().reason().isBlank()) {
				out.add(where + "no reason; an exemption says why the item has no blueprint");
				continue;
			}
			if (!visible.contains(id)) {
				out.add(where + "the player cannot hold " + id + " (not in a creative tab); remove the row");
				continue;
			}
			if (subjects.contains(id)) {
				out.add(where + id + " has a blueprint after all; remove `" + SURFACE + "` from the row");
				continue;
			}
			exempt.add(id);
		}
		for (String id : new TreeSet<>(visible)) {
			if (!subjects.contains(id) && !exempt.contains(id)) {
				out.add(id + ": no blueprint lists it as a subject. Draw it into the sheet of its system, or add a row "
						+ "with surface `" + SURFACE + "` and a reason to player_coverage_exemptions.json");
			}
		}
		return out;
	}

	/** The mod's ids in the reviewed creative-tab reference: the items the player can hold. */
	static Set<String> visibleItems() {
		Set<String> ids = new TreeSet<>();
		for (String line : CreativeTabSnapshotView.lines()) {
			// "<tab> <id>" or "<tab> after <anchor>: <ids>": the anchor is placed already, not by this line.
			String placed = line.contains(" after ") ? line.substring(line.indexOf(": ", line.indexOf(" after ")) + 1)
					: line;
			Matcher m = TAB_ID.matcher(placed);
			while (m.find()) {
				ids.add(NS + m.group(1));
			}
		}
		return ids;
	}

	static Set<String> subjects() {
		Set<String> out = new TreeSet<>();
		for (BlueprintDef def : BlueprintCatalog.ALL) {
			out.addAll(def.subjects());
		}
		return out;
	}

	static Map<String, Exemption> exemptions() throws IOException {
		String text;
		try (InputStream in = BlueprintCoverageTest.class.getClassLoader()
				.getResourceAsStream("dev/alaindustrial/player_coverage_exemptions.json")) {
			if (in == null) {
				throw new IOException("player_coverage_exemptions.json is not on the test classpath");
			}
			text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		int items = text.indexOf("\"items\"");
		if (items < 0) {
			throw new IOException("player_coverage_exemptions.json: no `items` object");
		}
		Map<String, Exemption> rows = new LinkedHashMap<>();
		Matcher row = ROW.matcher(text.substring(items + "\"items\"".length()));
		while (row.find()) {
			String body = row.group(2);
			List<String> surfaces = new ArrayList<>();
			Matcher list = SURFACES.matcher(body);
			if (list.find()) {
				Matcher word = WORD.matcher(list.group(1));
				while (word.find()) {
					surfaces.add(word.group(1));
				}
			}
			Matcher reason = REASON.matcher(body);
			rows.put(row.group(1), new Exemption(surfaces, reason.find() ? reason.group(1) : ""));
		}
		return rows;
	}
}
