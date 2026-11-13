package dev.alaindustrial.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * L1: nothing of the to-do clipboard's window is cut in any language of the mod (MOD-905). The owner's playtest
 * found the buttons' words cut in Russian; the buttons are icons now, and every line the window
 * still writes is measured here with the vanilla default font's own advances ({@code font-advances.json}, written
 * by {@code python tools/gen_gui_font_advances.py}) against the box {@link TodoWindowLayout} gives it.
 */
class TodoWindowLayoutTest {

	private static final String TABLE = "dev/alaindustrial/client/font-advances.json";
	private static final String EN_US = "assets/alaindustrial/lang/en_us.json";
	private static final String TITLE = "block.alaindustrial.todo_clipboard";
	private static final Map<Integer, Integer> ADVANCES = loadAdvances();

	@Test
	void everyLineOfEveryLanguageFitsItsBox() throws IOException, URISyntaxException {
		List<String> failures = new ArrayList<>();
		List<Path> files = langFiles();
		assertTrue(files.size() >= 20, "expected every language file of the mod, found " + files.size());
		int progress = width("10/10");
		for (Path file : files) {
			Map<String, String> lang = SettlementCoreLayoutTest.flatObject(Files.readString(file,
					StandardCharsets.UTF_8));
			String code = file.getFileName().toString();
			oneLine(failures, code, TITLE, text(lang, TITLE), TodoWindowLayout.titleWidth());
			for (String key : TodoWindowLayout.ROW_LINES) {
				oneLine(failures, code, key, text(lang, key, "10"), TodoWindowLayout.ROW_TEXT_W);
			}
			for (String key : TodoWindowLayout.SUBTITLE_LINES) {
				// The owner's name is cut with an ellipsis by the screen; its label and three letters must show.
				oneLine(failures, code, key, text(lang, key, "Abc…"), TodoWindowLayout.subtitleWidth(progress));
			}
			for (String key : TodoWindowLayout.FIELD_HINTS) {
				oneLine(failures, code, key, text(lang, key), TodoWindowLayout.FIELD_W);
			}
			for (String key : TodoWindowLayout.RIGHTS_LINES) {
				TextFit.Fit rights = TextFit.fit(text(lang, key), TodoWindowLayout.RIGHTS_W,
						TodoWindowLayout.RIGHTS_MAX_LINES, TodoWindowLayoutTest::width);
				if (!rights.fits()) {
					failures.add(code + " " + key + ": " + rights.lines().size() + " lines at " + rights.scale());
				}
			}
			String hint = text(lang, TodoWindowLayout.EMPTY_HINT);
			TextFit.Fit fit = TextFit.fit(hint, TodoWindowLayout.HINT_W, TodoWindowLayout.HINT_MAX_LINES,
					TodoWindowLayoutTest::width);
			if (!fit.fits()) {
				failures.add(code + " " + TodoWindowLayout.EMPTY_HINT + ": " + fit.lines().size() + " lines at "
						+ fit.scale());
			}
		}
		assertEquals(List.of(), failures, "lines the clipboard's window would cut");
	}

	/** The rows, the hint, the control row and the inventory label stack without touching. */
	@Test
	void thePartsOfTheWindowDoNotOverlap() {
		assertTrue(TodoWindowLayout.SUBTITLE_Y + 9 <= TodoWindowLayout.LIST_Y - 1, "subtitle runs into the list");
		assertTrue(TodoWindowLayout.HINT_Y + TodoWindowLayout.HINT_MAX_LINES * TodoWindowLayout.HINT_LINE
				<= TodoWindowLayout.LIST_BOTTOM + 1, "the empty list's hint runs out of the sheet");
		assertTrue(TodoWindowLayout.LIST_BOTTOM + 1 < TodoWindowLayout.CONTROLS_Y - 1,
				"the list runs into the buttons");
		assertTrue(TodoWindowLayout.CONTROLS_Y + TodoWindowLayout.BUTTON + 1 <= TodoWindowLayout.INV_Y - 11,
				"the buttons run into the inventory label");
		assertTrue(TodoWindowLayout.ACCESS_BUTTON_X + TodoWindowLayout.BUTTON + 1
				<= TodoWindowLayout.LIST_X + TodoWindowLayout.LIST_W + 1, "the last button leaves the frame's column");
		assertTrue(TodoWindowLayout.FIELD_WELL_X + TodoWindowLayout.FIELD_WELL_W + 1 < TodoWindowLayout.FIELD_BUTTON_X,
				"the field runs into its button");
		assertTrue(TodoWindowLayout.REMOVE_X + TodoWindowLayout.CROSS <= TodoWindowLayout.LIST_X
				+ TodoWindowLayout.LIST_W - 1, "the remove cross leaves the sheet");
		assertEquals(TodoWindowLayout.ICON, TodoWindowLayout.BUTTON - 4, "an icon sits two pixels inside its button");
	}

	private static void oneLine(List<String> failures, String code, String key, String text, int box) {
		int w = width(text);
		if (w > box) {
			failures.add(code + " " + key + ": " + w + " px in a " + box + " px box (\"" + text + "\")");
		}
	}

	/** The string's width in the default font, as {@code Font.width} adds it up. */
	private static int width(String text) {
		int sum = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			Integer advance = ADVANCES.get(cp);
			if (advance == null) {
				throw new AssertionError(String.format("U+%04X is not in %s: run python tools/gen_gui_font_advances.py",
						cp, TABLE));
			}
			sum += advance;
			i += Character.charCount(cp);
		}
		return sum;
	}

	/** The translation of {@code key} with each plain {@code %s} filled by {@code arg}. */
	private static String text(Map<String, String> lang, String key, String... arg) {
		assertTrue(lang.containsKey(key), "missing key " + key);
		String pattern = lang.get(key);
		return arg.length == 0 ? pattern : pattern.replace("%s", arg[0]);
	}

	private static List<Path> langFiles() throws IOException, URISyntaxException {
		Path dir = Path.of(TodoWindowLayoutTest.class.getClassLoader().getResource(EN_US).toURI()).getParent();
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		}
	}

	private static Map<Integer, Integer> loadAdvances() {
		try (InputStream stream = TodoWindowLayoutTest.class.getClassLoader().getResourceAsStream(TABLE)) {
			Map<Integer, Integer> table = new HashMap<>();
			SettlementCoreLayoutTest.flatObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
					.forEach((k, v) -> table.put(Integer.parseInt(k, 16), Integer.parseInt(v)));
			return table;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
