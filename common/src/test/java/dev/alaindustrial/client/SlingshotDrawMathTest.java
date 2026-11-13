package dev.alaindustrial.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Pins the slingshot's first-person draw (MOD-961): the pouch arm shows only on the other hand while a slingshot is
 * drawn, and the arm's pull frames switch where the item model's frames switch. The zoom is the bow's,
 * {@link BowDrawMathTest}.
 */
class SlingshotDrawMathTest {

	private static final String ITEM_MODEL = "assets/alaindustrial/items/slingshot.json";

	@Test
	void pouchArmOnlyOnTheOtherHandWhileDrawingASlingshot() {
		assertTrue(SlingshotDrawMath.showsPouchArm(true, true, 71_990, false), "the free hand while drawing");
		assertFalse(SlingshotDrawMath.showsPouchArm(true, true, 71_990, true), "the hand holding the slingshot");
		assertFalse(SlingshotDrawMath.showsPouchArm(false, true, 71_990, false), "at rest");
		assertFalse(SlingshotDrawMath.showsPouchArm(true, false, 71_990, false), "a bow or another used item");
		assertFalse(SlingshotDrawMath.showsPouchArm(true, true, 0, false), "use time run out");
	}

	@Test
	void pullFramesSwitchWhereTheItemModelSwitches() throws Exception {
		String json;
		try (InputStream in = SlingshotDrawMathTest.class.getClassLoader().getResourceAsStream(ITEM_MODEL)) {
			assertNotNull(in, ITEM_MODEL + " must be on the test runtime classpath");
			json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		Matcher scale = Pattern.compile("\"scale\"\\s*:\\s*([0-9.]+)").matcher(json);
		assertTrue(scale.find(), "the pull frames are a range_dispatch with a scale");
		double perTick = Double.parseDouble(scale.group(1));
		List<Integer> ticks = new ArrayList<>();
		Matcher threshold = Pattern.compile("\"threshold\"\\s*:\\s*([0-9.]+)").matcher(json);
		while (threshold.find()) {
			ticks.add((int) Math.round(Double.parseDouble(threshold.group(1)) / perTick));
		}
		assertEquals(List.of(SlingshotDrawMath.FRAME_1_TICKS, SlingshotDrawMath.FRAME_2_TICKS), ticks,
				"the arm's frame ticks must equal the item model's thresholds / scale");

		assertEquals(0, SlingshotDrawMath.pullFrame(0));
		assertEquals(0, SlingshotDrawMath.pullFrame(SlingshotDrawMath.FRAME_1_TICKS - 1));
		assertEquals(1, SlingshotDrawMath.pullFrame(SlingshotDrawMath.FRAME_1_TICKS));
		assertEquals(1, SlingshotDrawMath.pullFrame(SlingshotDrawMath.FRAME_2_TICKS - 1));
		assertEquals(2, SlingshotDrawMath.pullFrame(SlingshotDrawMath.FRAME_2_TICKS));
		assertEquals(2, SlingshotDrawMath.pullFrame(72_000));
		for (int t = 1; t <= 30; t++) {
			assertTrue(SlingshotDrawMath.pouchBack(t) >= SlingshotDrawMath.pouchBack(t - 1),
					"the pouch hand only comes back while drawing, tick " + t);
		}
	}
}
