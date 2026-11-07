package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.tooltip.MachineTooltips;
import dev.alaindustrial.client.tooltip.TooltipRender;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.TooltipUnits;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-022 — headless coverage for the loader-neutral hover-tooltip content ({@link MachineTooltips}). The
 * append logic is a pure function of an {@link ItemStack} + a {@code shiftDown} boolean (no client-only
 * classes — that is why it lives in {@code common}), so it is unit-testable without a render surface: this
 * asserts the CONTENT (which translation keys appear) for machines, the Network Analyzer and non-machine
 * items, in both shift states. Guards the tooltip provider both loaders now register (Fabric
 * {@code ItemTooltipCallback}, NeoForge {@code ItemTooltipEvent}) — the NeoForge side was silently missing
 * before the review.
 *
 * <p>Boots the ephemeral server so {@code ModContent} item/block handles resolve.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class MachineTooltipsTest {

	private static List<String> tooltipKeys(ItemStack stack, boolean shiftDown) {
		List<Component> lines = new ArrayList<>();
		MachineTooltips.append(stack, lines, shiftDown);
		List<String> keys = new ArrayList<>();
		for (Component line : lines) {
			// A style-09b line starts with its icon; its key is the first translatable part (MOD-958).
			String key = TooltipRender.lineKey(line);
			keys.add(key != null ? key : line.getString());
		}
		return keys;
	}

	private static final String HOLD = MachineTooltipSpec.HOLD_SHIFT_KEY;

	/** The keys without the purpose line, which the hook adds from the lang file (MOD-958), not the description. */
	private static List<String> withoutPurpose(List<String> keys) {
		return keys.stream().filter(key -> !key.endsWith(".purpose")).toList();
	}

	/** A machine block item shows its stat lines + the "hold shift" hint when shift is up. */
	@Test
	void macerator_basicTooltip_showsStatsAndShiftHint(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.MACERATOR.get()), false);
		assertFalse(keys.isEmpty(), "macerator tooltip produced no lines (NeoForge tooltip provider missing?)");
		assertTrue(keys.contains(MachineTooltipSpec.STAT_DRAW), "basic tooltip missing draw line");
		assertEquals("tooltip.alaindustrial.macerator.purpose", keys.get(0), "the purpose line comes first (MOD-958)");
		assertTrue(keys.contains("tooltip.alaindustrial.hold_shift"), "basic tooltip missing hold-shift hint");
	}

	/** Holding shift adds the detailed lines (tier, per-op energy); the hint stays, its key white (MOD-958). */
	@Test
	void macerator_detailedTooltip_onShift(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.MACERATOR.get()), true);
		assertTrue(keys.contains("tooltip.alaindustrial.tier_lv"), "detailed tooltip missing tier line");
		assertTrue(keys.contains(MachineTooltipSpec.STAT_PER_OPERATION), "detailed tooltip missing per-operation line");
		assertEquals(HOLD, keys.get(keys.size() - 1), "the hint stays last under shift (MOD-958)");
	}

	/** The Network Analyzer tool shows its usage line (and the shift hint when no scan is stored). */
	@Test
	void networkAnalyzer_showsUsageLine(MinecraftServer server) {
		// MOD-958: the right-clicks are a key line, which waits for [SHIFT] beside the mode line.
		List<String> keys = tooltipKeys(new ItemStack(ModContent.NETWORK_ANALYZER.get()), true);
		// A long line is wrapped into literal pieces (MOD-958): found by its key or by its text.
		assertTrue(keys.contains("tooltip.alaindustrial.network_analyzer.usage")
				|| keys.stream().anyMatch(key -> key.contains("Right-click a cable")),
				"analyzer tooltip missing usage line");
	}

	/** A vanilla non-machine item gets no Ala Industrial lines. */
	@Test
	void nonMachineItem_getsNoLines(MinecraftServer server) {
		assertTrue(tooltipKeys(new ItemStack(Items.DIRT), false).isEmpty(),
				"a non-machine item must not receive machine tooltip lines");
	}

	/**
	 * MOD-432 — the Garden Drone Station passes the machine gate: its basic branch (per-action cost) is
	 * reachable, not dead code behind {@code isMachineBlock()}. Was red on HEAD: the block was absent from
	 * the gate, so the tooltip was empty although the branch and its 20 translations existed.
	 */
	@Test
	void gardenDroneStation_basicTooltip_showsActionCostAndShiftHint(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.GARDEN_DRONE_STATION.get()), false);
		assertTrue(keys.contains("tooltip.alaindustrial.garden_drone_action_cost"),
				"garden drone station basic tooltip missing action-cost line (block not admitted by isMachineBlock?)");
		assertTrue(keys.contains("tooltip.alaindustrial.hold_shift"), "basic tooltip missing hold-shift hint");
	}

	/** MOD-432 — under shift the drone station shows tier, range and buffer instead of the hint. */
	@Test
	void gardenDroneStation_detailedTooltip_onShift(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.GARDEN_DRONE_STATION.get()), true);
		assertTrue(keys.contains("tooltip.alaindustrial.tier_lv"), "detailed tooltip missing tier line");
		assertTrue(keys.contains("tooltip.alaindustrial.garden_drone_range"), "detailed tooltip missing range line");
		assertTrue(keys.contains("tooltip.alaindustrial.capacity"), "detailed tooltip missing capacity line");
		assertTrue(keys.contains(HOLD), "the hint stays under shift, its key white (MOD-958)");
	}

	/**
	 * MOD-432 — the Teleporter passes the machine gate too: under shift its tooltip shows HV tier, buffer
	 * and I/O rule, and was dead on HEAD. (Since MOD-693 the buffer line comes from the basic branch.)
	 */
	@Test
	void teleporter_detailedTooltip_onShift(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.TELEPORTER.get()), true);
		assertTrue(keys.contains("tooltip.alaindustrial.tier_hv"), "teleporter tooltip missing HV tier line");
		assertTrue(keys.contains("tooltip.alaindustrial.buffer"), "teleporter tooltip missing buffer line");
		assertTrue(keys.contains("tooltip.alaindustrial.teleporter_io"),
				"teleporter tooltip missing I/O line (block not admitted by isMachineBlock?)");
		assertTrue(keys.contains(HOLD), "the hint stays under shift, its key white (MOD-958)");
	}

	/**
	 * MOD-432 — with EU numbers hidden ({@code showEuNumbers=false}) a gated block must still say something
	 * under shift: the drone station falls into the tier-only group of {@code addNonNumericTooltip()}, so the
	 * shift-up hint never points at an empty detailed tooltip.
	 */
	@Test
	void gardenDroneStation_nonNumericTooltip_showsTier(MinecraftServer server) {
		boolean previous = AlaClientConfig.showEuNumbers;
		AlaClientConfig.showEuNumbers = false;
		try {
			List<String> keys = tooltipKeys(new ItemStack(ModContent.GARDEN_DRONE_STATION.get()), true);
			assertTrue(keys.contains("tooltip.alaindustrial.tier_lv"),
					"non-numeric tooltip missing tier line (block absent from addNonNumericTooltip?)");
			assertTrue(keys.contains(HOLD), "the hint stays under shift, its key white (MOD-958)");
		} finally {
			AlaClientConfig.showEuNumbers = previous;
		}
	}

	/**
	 * Every block {@code MachineTooltips.isMachineBlock} admits, one handle per gated class (all eight
	 * cable grades, since each names its own tier). Kept by hand on purpose: the tests below first prove
	 * each entry really passes the gate (its shift-up tooltip is the hold-shift hint), so a stale entry
	 * fails loudly instead of being skipped.
	 */
	private static final List<Supplier<Block>> MACHINE_BLOCKS = List.of(
			ModContent.SOLAR_PANEL, ModContent.DAYLIGHT_SOLAR_PANEL, ModContent.MOONLIT_SOLAR_PANEL,
			ModContent.GENERATOR, ModContent.GEOTHERMAL_GENERATOR, ModContent.MACERATOR,
			ModContent.ELECTRIC_FURNACE, ModContent.COMPRESSOR, ModContent.RECYCLER,
			ModContent.COMPONENT_REPAIR_BENCH, ModContent.SAWMILL, ModContent.EXTRACTOR, ModContent.INCUBATOR,
			ModContent.PUMP, ModContent.POLYMERIZER, ModContent.GALVANIC_BATH, ModContent.FERMENTER,
			ModContent.VULCANIZER, ModContent.THERMAL_CENTRIFUGE, ModContent.ELECTRIC_HEATER,
			ModContent.GARDEN_DRONE_STATION, ModContent.TELEPORTER, ModContent.BATTERY_BOX,
			ModContent.COPPER_CABLE, ModContent.TIN_CABLE, ModContent.GOLD_CABLE, ModContent.ELECTRUM_CABLE,
			ModContent.INSULATED_COPPER_CABLE, ModContent.INSULATED_TIN_CABLE, ModContent.INSULATED_GOLD_CABLE,
			ModContent.INSULATED_ELECTRUM_CABLE);

	private static boolean hasTierLine(List<String> keys) {
		return keys.contains("tooltip.alaindustrial.tier_lv")
				|| keys.contains("tooltip.alaindustrial.tier_mv")
				|| keys.contains("tooltip.alaindustrial.tier_hv");
	}

	/**
	 * MOD-693 — the recycler's shift tooltip printed {@code energy_input} and {@code duration_ticks} twice:
	 * its detailed branch repeated the basic one instead of adding the tier line every other machine
	 * adds. Red before the fix (each key counted 2, no tier line).
	 */
	@Test
	void recycler_detailed_hasNoDuplicateLines(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.RECYCLER.get()), true);
		assertEquals(1, Collections.frequency(keys, "tooltip.alaindustrial.energy_input"),
				"recycler shift tooltip must print energy_input once: " + keys);
		assertEquals(1, Collections.frequency(keys, "tooltip.alaindustrial.duration_ticks"),
				"recycler shift tooltip must print duration_ticks once: " + keys);
		assertTrue(keys.contains("tooltip.alaindustrial.tier_lv"), "recycler shift tooltip missing tier line: " + keys);
	}

	/**
	 * MOD-693 — with EU numbers hidden, every gated machine must say something under shift: at least its
	 * tier. Red before the fix on the seven machines admitted by {@code isMachineBlock} but absent from
	 * {@code addNonNumericTooltip} (sawmill, polymerizer, galvanic bath, fermenter, vulcanizer, thermal
	 * centrifuge, electric heater): shift-up promised details, shift-down printed nothing.
	 */
	@Test
	void nonNumeric_shift_showsTier(MinecraftServer server) {
		boolean previous = AlaClientConfig.showEuNumbers;
		AlaClientConfig.showEuNumbers = false;
		try {
			List<String> failures = new ArrayList<>();
			for (Supplier<Block> handle : MACHINE_BLOCKS) {
				Block block = handle.get();
				ItemStack stack = new ItemStack(block);
				List<String> up = tooltipKeys(stack, false);
				// Only the hint and, since MOD-958, a purpose line: no figure shows with EU numbers off.
				assertEquals(HOLD, up.get(up.size() - 1),
						block + " is not a gated machine block (shift-up tooltip does not end with the hint)");
				assertTrue(up.subList(0, up.size() - 1).stream().allMatch(key -> key.endsWith(".purpose")),
						block + " shows more than its purpose with EU numbers off: " + up);
				List<String> down = tooltipKeys(stack, true);
				if (down.isEmpty() || !hasTierLine(down)) {
					failures.add(block + " -> " + down);
				}
			}
			assertTrue(failures.isEmpty(), "non-numeric shift tooltip without a tier line: " + failures);
		} finally {
			AlaClientConfig.showEuNumbers = previous;
		}
	}

	/**
	 * MOD-693 (owner decision 10) — the alloy smelter gets a machine tooltip through its declared
	 * {@link MachineTooltipSpec} (a {@code BlockTooltipCatalog} row until MOD-716 moved it to the block):
	 * draw and duration without shift, tier, buffer and per-operation energy
	 * under it, and the tier alone with EU numbers hidden. Replaces the characterisation test that pinned
	 * "no tooltip" while the decision was open.
	 */
	@Test
	void alloySmelter_hasTooltip(MinecraftServer server) {
		ItemStack stack = new ItemStack(ModContent.ALLOY_SMELTER.get());
		String purpose = "tooltip.alaindustrial.alloy_smelter.purpose";
		assertEquals(List.of(purpose, MachineTooltipSpec.STAT_DRAW, MachineTooltipSpec.STAT_OPERATION, HOLD),
				tooltipKeys(stack, false));
		assertEquals(List.of(purpose, MachineTooltipSpec.STAT_DRAW, MachineTooltipSpec.STAT_OPERATION,
				"tooltip.alaindustrial.tier_lv", MachineTooltipSpec.STAT_BUFFER, MachineTooltipSpec.STAT_PER_OPERATION,
				MachineTooltipSpec.KEY_OPEN_MACHINE, HOLD), tooltipKeys(stack, true));
		MachineTooltipSpec entry = spec(ModContent.ALLOY_SMELTER.get());
		assertEquals(MachineRates.euPerTick(Config.alloySmelterEuPerTick, Config.globalMachineSpeedMultiplier),
				entry.basic().get(0).argValues()[0],
				"alloy smelter draw must quote its own draw, not the machine standard");
		assertEquals(TooltipUnits.seconds(MachineRates.duration(Config.alloySmelterDuration,
				Config.globalMachineSpeedMultiplier)), entry.basic().get(1).argValues()[0],
				"alloy smelter operation must quote its own duration, in seconds");
		boolean previous = AlaClientConfig.showEuNumbers;
		AlaClientConfig.showEuNumbers = false;
		try {
			assertEquals(List.of(purpose, "tooltip.alaindustrial.tier_lv", MachineTooltipSpec.KEY_OPEN_MACHINE, HOLD),
					tooltipKeys(stack, true));
		} finally {
			AlaClientConfig.showEuNumbers = previous;
		}
	}

	/**
	 * MOD-693 (owner decision 11) — the twelve blocks that shipped a plain {@code BlockItem} with no tooltip
	 * now declare theirs ({@link HasMachineTooltip}; a {@code BlockTooltipCatalog} row each until MOD-716).
	 * For every one: the tooltip is
	 * not empty, shift-up is the row's basic lines plus the hold-shift hint, shift-down is the basic lines,
	 * the tier line (when the row has one) and the detailed lines — exactly what the catalog declares.
	 */
	private static final List<Supplier<Block>> CATALOG_BLOCKS = List.of(
			ModContent.CESU, ModContent.CANNING_MACHINE, ModContent.ASSEMBLER, ModContent.DISTILLATION_COLUMN,
			ModContent.CHARGE_PAD, ModContent.ENERGY_CONDENSER, ModContent.WIND_MILL, ModContent.WATER_MILL,
			ModContent.LIGHTNING_ROD_GENERATOR, ModContent.RADIANT_SOLAR_PANEL, ModContent.SPRINKLER,
			ModContent.MOB_REPELLER, ModContent.ALLOY_SMELTER);

	private static List<String> keysOf(List<MachineTooltipSpec.Line> stats) {
		List<String> keys = new ArrayList<>();
		for (MachineTooltipSpec.Line stat : stats) {
			keys.add(stat.key());
		}
		return keys;
	}

	/** The block's own tooltip description (MOD-716), or a failure naming a block that declares none. */
	private static MachineTooltipSpec spec(Block block) {
		assertTrue(block instanceof HasMachineTooltip, block + " declares no tooltip");
		return ((HasMachineTooltip) block).machineTooltip();
	}

	@Test
	void catalogBlocks_tooltipsMatchTheCatalog(MinecraftServer server) {
		// MOD-716: the rows moved onto the blocks, so there is no catalog left to count; the thirteen stay
		// listed here, and MachineTooltipsGoldenTest pins every item of the mod.
		assertEquals(13, CATALOG_BLOCKS.size());
		for (Supplier<Block> handle : CATALOG_BLOCKS) {
			Block block = handle.get();
			MachineTooltipSpec entry = spec(block);
			assertNotNull(entry, block + " has no catalog row");
			ItemStack stack = new ItemStack(block);

			List<String> up = new ArrayList<>(keysOf(entry.basic()));
			up.add(HOLD);
			assertEquals(up, withoutPurpose(tooltipKeys(stack, false)), block + " shift-up tooltip");
			assertTrue(up.size() > 1, block + " shift-up tooltip is only the hold-shift hint");

			List<String> down = new ArrayList<>(keysOf(entry.basic()));
			if (entry.tier() != null) {
				down.add(entry.tier().line().key());
			}
			down.addAll(keysOf(entry.detailed()));
			down.add(HOLD); // MOD-958: the hint stays under shift, its key white
			assertEquals(down, withoutPurpose(tooltipKeys(stack, true)), block + " shift-down tooltip");
			assertFalse(down.isEmpty(), block + " shift-down tooltip is empty");
		}
	}

	/** MOD-693 — with EU numbers hidden, every catalog block still says something under shift. */
	@Test
	void catalogBlocks_nonNumeric_shiftIsNotEmpty(MinecraftServer server) {
		boolean previous = AlaClientConfig.showEuNumbers;
		AlaClientConfig.showEuNumbers = false;
		try {
			for (Supplier<Block> handle : CATALOG_BLOCKS) {
				List<String> down = tooltipKeys(new ItemStack(handle.get()), true);
				assertFalse(down.isEmpty(), handle.get() + " non-numeric shift tooltip is empty");
				assertFalse(down.contains("tooltip.alaindustrial.energy_input")
					|| down.contains(MachineTooltipSpec.STAT_DRAW),
						handle.get() + " shows an EU figure with showEuNumbers off: " + down);
			}
		} finally {
			AlaClientConfig.showEuNumbers = previous;
		}
	}

	/**
	 * MOD-693 — the teleporter had no basic branch, so shift-up showed nothing but the hold-shift hint.
	 * Its buffer line now sits in the basic tooltip (shift-down still shows buffer, HV tier and I/O rule —
	 * see {@link #teleporter_detailedTooltip_onShift}). Red before the fix.
	 */
	@Test
	void teleporter_basicTooltip_showsBufferAndShiftHint(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.TELEPORTER.get()), false);
		assertTrue(keys.contains("tooltip.alaindustrial.buffer"), "teleporter basic tooltip missing buffer line: " + keys);
		assertTrue(keys.contains("tooltip.alaindustrial.hold_shift"), "basic tooltip missing hold-shift hint");
		assertTrue(keys.size() > 1, "teleporter basic tooltip must be more than the hold-shift hint: " + keys);
	}
}
