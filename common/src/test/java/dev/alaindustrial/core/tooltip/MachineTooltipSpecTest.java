package dev.alaindustrial.core.tooltip;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.alaindustrial.core.tooltip.MachineTooltipSpec.Line;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec.Tier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The four tooltip modes of an owner-declared description (MOD-716, ADR-040): [SHIFT] up/down x EU numbers
 * on/off, decided by each line's {@link MachineTooltipSpec.Shown} flag. These are the rules the four parallel
 * lists of the old {@code MachineTooltips} encoded by hand; the golden {@code MachineTooltipsGoldenTest} proves
 * the shipped descriptions reproduce them for every item, this pins the rules themselves.
 *
 * <p>Since MOD-958 (the owner's style "09b") the hint stays under [SHIFT] too, its key then white, the tier
 * always waits for [SHIFT], a key line waits for it, a warning never does, and the purpose line comes first.
 */
class MachineTooltipSpecTest {

	private static final String HOLD = MachineTooltipSpec.HOLD_SHIFT_KEY;

	private static List<String> keys(List<Line> lines) {
		List<String> keys = new ArrayList<>();
		for (Line line : lines) {
			keys.add(line.key());
		}
		return keys;
	}

	/** The standard machine: numbers before [SHIFT], tier and more numbers under it. */
	private static final MachineTooltipSpec MACHINE = MachineTooltipSpec.processing(() -> 2, () -> 150, () -> 800);

	private static final String DRAW = MachineTooltipSpec.STAT_DRAW;
	private static final String OPERATION = MachineTooltipSpec.STAT_OPERATION;
	private static final String OPEN = MachineTooltipSpec.KEY_OPEN_MACHINE;

	@Test
	void processingMachineInAllFourModes() {
		assertEquals(List.of(DRAW, OPERATION, HOLD), keys(MACHINE.lines(false, true)));
		assertEquals(List.of(DRAW, OPERATION, "tooltip.alaindustrial.tier_lv", MachineTooltipSpec.STAT_BUFFER,
				MachineTooltipSpec.STAT_PER_OPERATION, OPEN, HOLD), keys(MACHINE.lines(true, true)));
		assertEquals(List.of(HOLD), keys(MACHINE.lines(false, false)));
		assertEquals(List.of("tooltip.alaindustrial.tier_lv", OPEN, HOLD), keys(MACHINE.lines(true, false)));
	}

	@Test
	void theOperationIsInSecondsAndEveryFigureHasItsRoleAndUnit() {
		Line draw = MACHINE.basic().get(0);
		Line operation = MACHINE.basic().get(1);
		assertEquals(TooltipRole.ENERGY, draw.role());
		assertEquals(TooltipUnits.EU_PER_TICK, draw.unit());
		assertEquals(TooltipRole.TIME, operation.role());
		assertEquals(TooltipUnits.SECONDS, operation.unit());
		assertEquals(new TooltipUnits.Decimal("7", "5"), operation.argValues()[0], "150 ticks are 7.5 s");
		assertEquals(TooltipRole.KEY, MACHINE.detailed().get(2).role());
	}

	@Test
	void theHintKnowsWhetherShiftIsHeldAndAlwaysDetailedDropsIt() {
		List<Line> up = MACHINE.lines(false, true);
		List<Line> held = MACHINE.lines(true, true);
		assertArrayEquals(new Object[] {false}, up.get(up.size() - 1).argValues());
		assertArrayEquals(new Object[] {true}, held.get(held.size() - 1).argValues());
		assertEquals(TooltipRole.HINT, held.get(held.size() - 1).role());
		List<String> always = keys(MACHINE.lines(false, true, true));
		assertEquals(keys(held).subList(0, held.size() - 1), always, "always-detailed: everything, no hint");
	}

	@Test
	void aWarningNeverWaitsForShiftAndAKeyAlwaysDoes() {
		MachineTooltipSpec spec = new MachineTooltipSpec(null,
				List.of(MachineTooltipSpec.action("tooltip.alaindustrial.k")),
				List.of(new Line("tooltip.alaindustrial.w", List.of(), MachineTooltipSpec.Shown.ALWAYS, null,
						TooltipRole.WARNING, null)));
		assertEquals(List.of("tooltip.alaindustrial.w", HOLD), keys(spec.lines(false, true)),
				"a warning shows without [SHIFT], the key line waits for it");
		assertEquals(List.of("tooltip.alaindustrial.k", "tooltip.alaindustrial.w", HOLD), keys(spec.lines(true, true)));
		MachineTooltipSpec keysOnly = new MachineTooltipSpec(null,
				List.of(MachineTooltipSpec.action("tooltip.alaindustrial.k")), List.of());
		assertEquals(List.of("tooltip.alaindustrial.k"), keys(keysOnly.lines(false, true)),
				"a key line with nothing beside it shows without [SHIFT]");
	}

	@Test
	void theTierLineCarriesItsTierAndItsColour() {
		assertEquals(TooltipRole.TIER, Tier.HV.line().role());
		assertArrayEquals(new Object[] {Tier.HV}, Tier.HV.line().argValues());
		assertEquals(0xFF55FF, Tier.HV.rgb());
		assertEquals(0x55FF55, Tier.LV.rgb());
		assertEquals(Tier.MV.rgb(), Tier.LV.rgb());
	}

	@Test
	void perOperationEnergyIsDrawTimesDurationReadLive() {
		AtomicInteger draw = new AtomicInteger(2);
		MachineTooltipSpec spec = MachineTooltipSpec.processing(draw::get, () -> 150, () -> 800);
		Line perOp = spec.detailed().get(1);
		assertArrayEquals(new Object[] {300}, perOp.argValues());
		draw.set(3);
		assertArrayEquals(new Object[] {450}, perOp.argValues(), "the value must be read at draw time");
	}

	@Test
	void labelLinesShowWithNumbersAndOnlyUnderShiftWithout() {
		// A word line among the basic lines (the cable's safety), a word line under [SHIFT].
		MachineTooltipSpec box = new MachineTooltipSpec(null,
				List.of(MachineTooltipSpec.stat("capacity", () -> 40000),
						MachineTooltipSpec.text("tooltip.alaindustrial.word", TooltipRole.STATE_ON)),
				List.of(MachineTooltipSpec.text("tooltip.alaindustrial.battery_box_io", TooltipRole.INFO)));
		// The green word is a state (MOD-958): it stands above the figures.
		assertEquals(List.of("tooltip.alaindustrial.word", "tooltip.alaindustrial.capacity", HOLD),
				keys(box.lines(false, true)));
		assertEquals(List.of("tooltip.alaindustrial.word", "tooltip.alaindustrial.capacity",
				"tooltip.alaindustrial.battery_box_io", HOLD), keys(box.lines(true, true)));
		assertEquals(List.of(HOLD), keys(box.lines(false, false)));
		assertEquals(List.of("tooltip.alaindustrial.word", "tooltip.alaindustrial.battery_box_io", HOLD),
				keys(box.lines(true, false)));
	}

	@Test
	void theBatteryBoxTierWaitsForShift() {
		MachineTooltipSpec box = new MachineTooltipSpec(Tier.LV,
				List.of(MachineTooltipSpec.stat("capacity", () -> 40000)), List.of());
		assertEquals(List.of("tooltip.alaindustrial.capacity", HOLD), keys(box.lines(false, true)));
		assertEquals(List.of("tooltip.alaindustrial.capacity", "tooltip.alaindustrial.tier_lv", HOLD),
				keys(box.lines(true, true)));
	}

	@Test
	void alwaysLinesShowInEveryMode() {
		// The sprinkler shape: no tier, two sizes that are not energy figures.
		MachineTooltipSpec sprinkler = new MachineTooltipSpec(null,
				List.of(MachineTooltipSpec.plain("tank_mb", () -> 4000)),
				List.of(MachineTooltipSpec.plain("range", () -> 4)));
		assertEquals(List.of("tooltip.alaindustrial.tank_mb", HOLD), keys(sprinkler.lines(false, false)));
		assertEquals(List.of("tooltip.alaindustrial.tank_mb", "tooltip.alaindustrial.range", HOLD),
				keys(sprinkler.lines(true, false)));
		assertEquals(keys(sprinkler.lines(true, false)), keys(sprinkler.lines(true, true)));
	}

	@Test
	void noHintWhenShiftWouldShowNothingMore() {
		// The Advanced Industrial Workbench shape: no tier, only always-shown notes, nothing behind [SHIFT].
		MachineTooltipSpec bench = new MachineTooltipSpec(null,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.a", TooltipRole.INFO),
						MachineTooltipSpec.note("tooltip.alaindustrial.b", TooltipRole.INFO)),
				List.of());
		List<String> both = List.of("tooltip.alaindustrial.a", "tooltip.alaindustrial.b");
		assertEquals(both, keys(bench.lines(false, true)));
		assertEquals(both, keys(bench.lines(false, false)));
		assertEquals(both, keys(bench.lines(true, false)));
	}

	@Test
	void theHintAndTheTiersKeepTheirColours() {
		Line hold = MACHINE.lines(false, true).get(2);
		assertEquals(HOLD, hold.key());
		assertEquals(TooltipRole.HINT, hold.role());
		assertEquals(Tier.LV.rgb(), Tier.MV.rgb());
		assertEquals(0xFF55FF, Tier.HV.rgb());
		assertEquals("tooltip.alaindustrial.tier_hv", Tier.HV.line().key());
		assertEquals(TooltipRole.ENERGY, MachineTooltipSpec.stat("buffer", () -> 1).role());
		assertEquals(TooltipRole.INFO, MachineTooltipSpec.stat("not_styled", () -> 1).role());
	}

	@Test
	void labelledLineKeepsItsLabelAndItsStat() {
		Line mode = MachineTooltipSpec.labelled("gui.alaindustrial.incubator.mode.transform", "duration_ticks",
				() -> 300);
		assertEquals("gui.alaindustrial.incubator.mode.transform", mode.label());
		assertEquals("tooltip.alaindustrial.duration_ticks", mode.key());
		assertArrayEquals(new Object[] {new TooltipUnits.Decimal("15", null)}, mode.argValues(), "300 ticks are 15 s");
		assertEquals(TooltipRole.TIME, mode.role());
		assertEquals(MachineTooltipSpec.Shown.NUMBERS, mode.shown());
	}

	@Test
	void valueLinesKeepTheTypeOfTheirArgument() {
		assertEquals(Long.class, MachineTooltipSpec.statValue("buffer", () -> 12L).argValues()[0].getClass());
		assertEquals(String.class, MachineTooltipSpec.statValue("cable_loss", () -> "0.6").argValues()[0].getClass());
		assertEquals(Integer.class, MachineTooltipSpec.stat("buffer", () -> 12).argValues()[0].getClass());
	}

	@Test
	void listsAreCopiedNotShared() {
		List<Line> basic = new ArrayList<>(List.of(MachineTooltipSpec.stat("buffer", () -> 1)));
		MachineTooltipSpec spec = new MachineTooltipSpec(Tier.LV, basic, List.of());
		basic.clear();
		assertEquals(1, spec.basic().size());
		assertThrows(UnsupportedOperationException.class, () -> spec.basic().add(Tier.LV.line()));
	}
}
