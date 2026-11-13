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
 * L1: the thermoelectric generator's screen keeps every line inside its frame in every language of the mod, and its
 * parts do not run into each other (MOD-811). The owner's playtest found the Russian "no pair" status running out
 * of the frame through the right border; this measures the title, the status row and the row rates with the vanilla
 * default font's own advances ({@code font-advances.json}, {@code python tools/gen_gui_font_advances.py}) and fits
 * them with the {@link TextFit} rule the screen's status rows follow (one line, scaled down to 0.7 at most).
 *
 * @implements MOD-811-SCREEN-FITS
 */
class ThermoelectricGeneratorLayoutTest {

	private static final String TABLE = "dev/alaindustrial/client/font-advances.json";
	private static final String EN_US = "assets/alaindustrial/lang/en_us.json";
	/** The widest rate a block can show: three axes of lava against blue ice under a large multiplier. */
	private static final String WIDE_RATE = "99,75";
	/** The inner edge of the frame's bevel: text may run up to here and no further. */
	private static final int FRAME_INNER = 4;
	/** The energy bar's well ends here (EnergyBarSpec.LEFT_WINDMILL, 10 px wide at x=17, with its bevel). */
	private static final int ENERGY_BAR_RIGHT = 29;
	/** The player inventory's first well (ThermoelectricGeneratorMenu: slots at y=96, wells one pixel above). */
	private static final int INVENTORY_TOP = 95;
	private static final int LINE = 9;

	private static final Map<Integer, Integer> ADVANCES = loadAdvances();

	@Test
	void everyLineOfEveryLanguageFitsTheFrame() throws IOException, URISyntaxException {
		List<String> failures = new ArrayList<>();
		List<Path> files = langFiles();
		assertTrue(files.size() >= 20, "expected every language file of the mod, found " + files.size());
		int statusBand = ThermoelectricGeneratorLayout.STATUS_RIGHT - ThermoelectricGeneratorLayout.STATUS_LEFT;
		int rateBand = ThermoelectricGeneratorLayout.RATE_RIGHT - ThermoelectricGeneratorLayout.RATE_LEFT;
		for (Path file : files) {
			Map<String, String> lang =
					SettlementCoreLayoutTest.flatObject(Files.readString(file, StandardCharsets.UTF_8));
			String code = file.getFileName().toString();
			for (String key : ThermoelectricGeneratorLayout.STATUS_KEYS) {
				check(failures, code, key, text(lang, key, WIDE_RATE), statusBand);
			}
			check(failures, code, ThermoelectricGeneratorLayout.RATE_KEY,
					text(lang, ThermoelectricGeneratorLayout.RATE_KEY, WIDE_RATE), rateBand);
			String title = text(lang, "block.alaindustrial.thermoelectric_generator");
			int titleRoom = ThermoelectricGeneratorLayout.IMAGE_WIDTH - ThermoelectricGeneratorLayout.TITLE_X
					- FRAME_INNER;
			if (width(title) > titleRoom) {
				failures.add(code + " title: " + width(title) + " px of " + titleRoom);
			}
		}
		assertEquals(List.of(), failures, "lines that would cross the thermoelectric generator's frame");
	}

	/** Left to right: the bar, a mark, a well, the thermocouple bar, a well, a mark, the rate; top to bottom: rows. */
	@Test
	void thePartsDoNotRunIntoEachOther() {
		int cell = ThermoelectricGeneratorLayout.CELL;
		assertTrue(ENERGY_BAR_RIGHT <= ThermoelectricGeneratorLayout.ICON_LEFT_X, "the left mark covers the bar");
		assertTrue(ThermoelectricGeneratorLayout.ICON_LEFT_X + ThermoelectricGeneratorLayout.ICON
				< ThermoelectricGeneratorLayout.CELL_LEFT_X, "the left mark runs into its well");
		assertTrue(ThermoelectricGeneratorLayout.CELL_LEFT_X + cell <= ThermoelectricGeneratorLayout.BAR_X,
				"the thermocouple bar starts inside the left well");
		assertTrue(ThermoelectricGeneratorLayout.BAR_X + ThermoelectricGeneratorLayout.STRIP_W + 2
				<= ThermoelectricGeneratorLayout.CELL_RIGHT_X, "the thermocouple bar runs into the right well");
		assertTrue(ThermoelectricGeneratorLayout.CELL_RIGHT_X + cell < ThermoelectricGeneratorLayout.ICON_RIGHT_X,
				"the right mark runs into its well");
		assertTrue(ThermoelectricGeneratorLayout.ICON_RIGHT_X + ThermoelectricGeneratorLayout.ICON
				< ThermoelectricGeneratorLayout.RATE_LEFT, "the rate starts on the right mark");
		assertTrue(ThermoelectricGeneratorLayout.RATE_RIGHT <= ThermoelectricGeneratorLayout.IMAGE_WIDTH
				- FRAME_INNER, "the rate runs past the frame");
		assertTrue(ENERGY_BAR_RIGHT <= ThermoelectricGeneratorLayout.STATUS_LEFT
				&& ThermoelectricGeneratorLayout.STATUS_RIGHT <= ThermoelectricGeneratorLayout.IMAGE_WIDTH
						- FRAME_INNER, "the status band runs over the bar or past the frame");
		int[] rows = ThermoelectricGeneratorLayout.ROW_Y;
		assertTrue(rows[0] >= 6 + LINE, "the first row runs into the title");
		for (int i = 1; i < rows.length; i++) {
			assertTrue(rows[i - 1] + cell <= rows[i], "row " + i + " overlaps the row above it");
		}
		assertTrue(rows[rows.length - 1] + cell <= ThermoelectricGeneratorLayout.STATUS_Y,
				"the status row runs into the last row");
		assertTrue(ThermoelectricGeneratorLayout.STATUS_Y + LINE <= ThermoelectricGeneratorLayout.INVENTORY_LABEL_Y,
				"the status row runs into the inventory label");
		assertTrue(ThermoelectricGeneratorLayout.INVENTORY_LABEL_Y + LINE <= INVENTORY_TOP,
				"the inventory label runs into the inventory");
		assertTrue(ThermoelectricGeneratorLayout.FLAME_U >= 186 && ThermoelectricGeneratorLayout.FLAKE_U >= 186
				&& ThermoelectricGeneratorLayout.STRIP_HOT_LEFT_U >= 186
				&& ThermoelectricGeneratorLayout.STRIP_HOT_RIGHT_U >= 186,
				"a sprite overlaps the energy fill of the service area (x 176..185)");
	}

	private static void check(List<String> failures, String code, String key, String text, int band) {
		TextFit.Fit fit = TextFit.fit(text, band, 1, ThermoelectricGeneratorLayoutTest::width);
		if (!fit.fits()) {
			failures.add(code + " " + key + ": " + width(text) + " px in a band of " + band);
		}
	}

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

	/** The translation with every {@code %s} filled by {@code arg}. */
	private static String text(Map<String, String> lang, String key, String... arg) {
		assertTrue(lang.containsKey(key), "missing key " + key);
		String pattern = lang.get(key);
		return arg.length == 0 ? pattern : pattern.replace("%s", arg[0]);
	}

	private static List<Path> langFiles() throws IOException, URISyntaxException {
		Path dir = Path.of(ThermoelectricGeneratorLayoutTest.class.getClassLoader().getResource(EN_US).toURI())
				.getParent();
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		}
	}

	private static Map<Integer, Integer> loadAdvances() {
		try (InputStream stream = ThermoelectricGeneratorLayoutTest.class.getClassLoader().getResourceAsStream(TABLE)) {
			Map<Integer, Integer> table = new HashMap<>();
			SettlementCoreLayoutTest.flatObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
					.forEach((k, v) -> table.put(Integer.parseInt(k, 16), Integer.parseInt(v)));
			return table;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
