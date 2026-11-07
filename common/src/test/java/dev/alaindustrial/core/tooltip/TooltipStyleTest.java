package dev.alaindustrial.core.tooltip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.tooltip.TooltipLayout.Hint;
import dev.alaindustrial.core.tooltip.TooltipLayout.Placed;
import dev.alaindustrial.core.tooltip.TooltipLayout.Slot;
import dev.alaindustrial.core.tooltip.TooltipUnits.Decimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The owner's tooltip style "09b" (MOD-958) as data: the role table, the number formats and the layout rule the
 * blocks and the items share. The rendering itself (a role to a chat colour) is pinned by the golden
 * {@code MachineTooltipsGoldenTest}.
 */
class TooltipStyleTest {

	// --- roles ---------------------------------------------------------------------------------------

	@Test
	void theRoleTableIsTheOwnersTable() {
		assertEquals(0xAAAAAA, TooltipRole.PURPOSE.rgb());
		assertEquals(0xFF8A1E, TooltipRole.ENERGY.rgb());
		assertEquals(0xBFEBFF, TooltipRole.TIME.rgb());
		assertEquals(0x55FF55, TooltipRole.STATE_ON.rgb());
		assertEquals(0xAAAAAA, TooltipRole.STATE_OFF.rgb());
		assertEquals(0x57C7FF, TooltipRole.FLUID.rgb());
		assertEquals(0x00AAAA, TooltipRole.MODULE.rgb());
		assertEquals(0xFFC15A, TooltipRole.KEY.rgb());
		assertEquals(0x55FFFF, TooltipRole.RANGE.rgb());
		assertEquals(0x55FFFF, TooltipRole.SPEED.rgb());
		assertEquals(0xFF5555, TooltipRole.WARNING.rgb());
		assertEquals(0xFFFF55, TooltipRole.NOTE.rgb());
		assertEquals("ℹ", TooltipRole.PURPOSE.glyph());
		assertEquals("✘", TooltipRole.STATE_OFF.glyph(), "U+2716 is not in the bitmap font");
		assertEquals("🌊", TooltipRole.FLUID.glyph());
		assertNull(TooltipRole.HINT.glyph());
	}

	@Test
	void everyIconIsOneCodePointInTheBitmapFontsRange() {
		for (TooltipRole role : TooltipRole.values()) {
			String glyph = role.glyph();
			if (glyph != null) {
				assertEquals(1, glyph.codePointCount(0, glyph.length()), role + " icon is one glyph");
			}
		}
	}

	@Test
	void figuresAreStatsAndKeysWaitForShift() {
		assertEquals(Slot.STAT, TooltipRole.ENERGY.defaultSlot());
		assertEquals(Slot.STAT, TooltipRole.FLUID.defaultSlot());
		assertEquals(Slot.STATE, TooltipRole.STATE_OFF.defaultSlot());
		assertTrue(TooltipRole.KEY.defaultSlot().underShift());
		assertTrue(TooltipRole.TIER.defaultSlot().underShift());
		assertFalse(TooltipRole.WARNING.defaultSlot().underShift());
	}

	// --- units ---------------------------------------------------------------------------------------

	@Test
	void thousandsAreSplitBySpaces() {
		assertEquals("0", TooltipUnits.grouped(0));
		assertEquals("999", TooltipUnits.grouped(999));
		assertEquals("3 200", TooltipUnits.grouped(3200));
		assertEquals("1 000 000", TooltipUnits.grouped(1_000_000));
		assertEquals("-5 000", TooltipUnits.grouped(-5000));
	}

	@Test
	void ticksAreSecondsWithAtMostTwoDigits() {
		assertEquals(new Decimal("7", "5"), TooltipUnits.seconds(150));
		assertEquals(new Decimal("2", null), TooltipUnits.seconds(40));
		assertEquals(new Decimal("0", "65"), TooltipUnits.seconds(13));
		assertEquals(new Decimal("0", "05"), TooltipUnits.seconds(1));
		assertEquals(new Decimal("1 200", null), TooltipUnits.seconds(24_000));
		assertEquals("7.5", TooltipUnits.seconds(150).toString());
	}

	@Test
	void factorsRoundToTwoDigits() {
		assertEquals(new Decimal("0", "8"), TooltipUnits.factor(0.8f));
		assertEquals(new Decimal("0", "64"), TooltipUnits.factor(Math.pow(0.8f, 2)));
		assertEquals(new Decimal("2", null), TooltipUnits.factor(2.0));
		assertEquals(new Decimal("8", null), TooltipUnits.factor(Math.pow(2.0f, 3)));
	}

	// --- layout --------------------------------------------------------------------------------------

	private static List<Placed<String>> magnet() {
		return List.of(new Placed<>(Slot.KEY, "key"), new Placed<>(Slot.DETAIL, "range"),
				new Placed<>(Slot.STAT, "charge"), new Placed<>(Slot.TIER, "tier"),
				new Placed<>(Slot.STATE, "on"), new Placed<>(Slot.PURPOSE, "purpose"),
				new Placed<>(Slot.MODULE, "filter"), new Placed<>(Slot.WARNING, "hot"));
	}

	@Test
	void linesFollowTheSlotOrderAndShiftLinesWait() {
		assertEquals(List.of("purpose", "on", "charge", "hot"), TooltipLayout.arrange(magnet(), false));
		assertEquals(List.of("purpose", "on", "charge", "tier", "range", "filter", "key", "hot"),
				TooltipLayout.arrange(magnet(), true));
	}

	@Test
	void keysShowWithoutShiftWhenTheyAreAllThereIsBesideThePurpose() {
		List<Placed<String>> wrench = List.of(new Placed<>(Slot.PURPOSE, "purpose"), new Placed<>(Slot.KEY, "key"));
		assertEquals(List.of("purpose", "key"), TooltipLayout.arrange(wrench, false));
		List<Placed<String>> withNote = List.of(new Placed<>(Slot.KEY, "key"), new Placed<>(Slot.NOTE, "note"));
		assertEquals(List.of("note"), TooltipLayout.arrange(withNote, false));
	}

	@Test
	void theHintShowsOnlyWhenShiftAddsSomething() {
		assertEquals(Hint.KEY_UP, TooltipLayout.hint(4, 8, false, false));
		assertEquals(Hint.KEY_HELD, TooltipLayout.hint(4, 8, true, false));
		assertEquals(Hint.NONE, TooltipLayout.hint(4, 4, false, false));
		assertEquals(Hint.NONE, TooltipLayout.hint(4, 4, true, false));
		assertEquals(Hint.NONE, TooltipLayout.hint(4, 8, false, true), "always-detailed shows no hint");
	}
}
