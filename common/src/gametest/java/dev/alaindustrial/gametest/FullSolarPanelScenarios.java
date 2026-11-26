package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;
import static dev.alaindustrial.gametest.SolarPanelRig.POS;
import static dev.alaindustrial.gametest.SolarPanelRig.setClearDay;
import static dev.alaindustrial.gametest.SolarPanelRig.setRaining;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BlockEntityDataMigrations;
import dev.alaindustrial.block.entity.BronzeLunarPanelBlockEntity;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.ReinforcedSolarPanelBlockEntity;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.environment.SolarCurve;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The full-block third rungs of both solar branches (MOD-1031): the Reinforced Solar Panel follows the height of
 * the sun, the Bronze Lunar Panel the phase of the moon; both grow from their T2 panel with a resonance chip — the
 * moonlit one with its own lunar evolution chip too (MOD-1033) — and the moonlit panel's old saves move their upgrade
 * chips to make room for its new chip slot.
 *
 * <p>World time is global to a batch, so each body stages the clock and reads the panel in the same method, like
 * {@link SolarPanelScenarios}. Expected figures are computed from the knobs through {@link SolarCurve} and the
 * global multiplier, and each one is also pinned to the owner's number at the default config, so a flat curve or
 * a wrong constant cannot pass.
 */
public final class FullSolarPanelScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FullSolarPanelScenarios::reinforcedFollowsTheSun, "mod1031_reinforced_follows_the_sun")
						.ticks(40).sky(),
				RosterEntry.of(FullSolarPanelScenarios::reinforcedWeather, "mod1031_reinforced_rain_zero_snow_fifth")
						.ticks(40).sky(),
				RosterEntry.of(FullSolarPanelScenarios::bronzeFollowsTheMoon, "mod1031_bronze_follows_the_moon")
						.ticks(40).sky(),
				RosterEntry.of(FullSolarPanelScenarios::bronzeWeather, "mod1031_bronze_rain_half_snow_half")
						.ticks(40).sky(),
				RosterEntry.of(FullSolarPanelScenarios::daylightEvolvesIntoReinforced,
								"mod1031_daylight_evolves_into_reinforced")
						.ticks(40).sky(),
				RosterEntry.of(FullSolarPanelScenarios::moonlitTakesResonanceChipAndEvolves,
								"mod1031_moonlit_evolves_into_bronze")
						.ticks(40).sky(),
				RosterEntry.of(FullSolarPanelScenarios::moonlitGrowsWithItsLunarChip,
								"mod1033_moonlit_grows_with_its_lunar_chip")
						.ticks(40).sky(),
				RosterEntry.of(FullSolarPanelScenarios::moonlitOldSaveMovesItsChips,
								"mod1031_moonlit_old_save_moves_its_chips")
						.ticks(20));

		private Roster() {}
	}

	private FullSolarPanelScenarios() {}

	/** Clock ticks of a full moon night (day 0) and of a new moon night (day 4), both at midnight. */
	private static final int FULL_MOON_MIDNIGHT = 18_000;
	private static final int NEW_MOON_MIDNIGHT = 4 * 24_000 + 18_000;

	/** What the buffer gains a tick for a mechanical rate, the way the generator base applies the multiplier. */
	private static int effective(int mechanical) {
		return mechanical > 0 ? Math.max(1, Math.round(mechanical * Config.globalEuRateMultiplier)) : 0;
	}

	private static void setClock(GameTestHelper helper, int ticks) {
		var level = helper.getLevel();
		var server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set " + ticks);
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f);
		level.updateSkyBrightness();
	}

	private static <T> T be(GameTestHelper helper, Class<T> type) {
		var be = helper.getLevel().getBlockEntity(helper.absolutePos(POS));
		if (!type.isInstance(be)) {
			helper.fail("expected a " + type.getSimpleName() + " at " + POS + ", found " + be);
		}
		return type.cast(be);
	}

	private static int rateAt(GameTestHelper helper, ReinforcedSolarPanelBlockEntity panel) {
		drive(panel, helper, 1);
		return panel.getDataAccess().get(2);
	}

	/**
	 * The day branch's curve: the whole peak at noon, less in the morning and evening, nothing at night — and the
	 * numbers the owner signed off at the default config (12 at noon, 8 an hour after sunrise).
	 *
	 * @implements MOD-1031-T3-DAY — output follows the height of the sun
	 */
	public static void reinforcedFollowsTheSun(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.REINFORCED_SOLAR_PANEL.get());
		ReinforcedSolarPanelBlockEntity panel = be(helper, ReinforcedSolarPanelBlockEntity.class);
		int peak = GeneratorConfig.reinforcedSolarPeakEuPerTick;
		double edge = GeneratorConfig.reinforcedSolarEdgeFactor;

		setClock(helper, 6000);
		int noon = rateAt(helper, panel);
		helper.assertTrue(panel.getDataAccess().get(3) == ReinforcedSolarPanelBlockEntity.MODE_DAY,
				"at noon expected MODE_DAY, got " + panel.getDataAccess().get(3));
		helper.assertTrue(noon == effective(SolarCurve.scaled(peak, SolarCurve.dayShare(6000, edge))),
				"noon rate " + noon);

		setClock(helper, 1000);
		int morning = rateAt(helper, panel);
		helper.assertTrue(morning == effective(SolarCurve.scaled(peak, SolarCurve.dayShare(1000, edge))),
				"morning rate " + morning);
		helper.assertTrue(morning < noon, "the morning (" + morning + ") must make less than noon (" + noon + ")");

		setClock(helper, 11_000);
		int evening = rateAt(helper, panel);
		helper.assertTrue(evening == morning, "the curve is symmetric about noon: evening " + evening
				+ ", morning " + morning);

		setClock(helper, FULL_MOON_MIDNIGHT);
		helper.assertTrue(rateAt(helper, panel) == 0, "the day panel made EU at midnight");

		if (peak == 12 && edge == 0.5 && Config.globalEuRateMultiplier == 1.0f) {
			helper.assertTrue(noon == 12 && morning == 8, "owner's figures: 12 at noon, 8 at 07:00; got " + noon
					+ " and " + morning);
		}
		helper.succeed();
	}

	/**
	 * Rain stops the day panel; a snow layer keeps a fifth of the current figure, at least 1.
	 *
	 * @implements MOD-1031-T3-DAY-WEATHER — rain and thunder 0, snow 20 %
	 */
	public static void reinforcedWeather(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.REINFORCED_SOLAR_PANEL.get());
		ReinforcedSolarPanelBlockEntity panel = be(helper, ReinforcedSolarPanelBlockEntity.class);
		setClock(helper, 6000);
		int noon = SolarCurve.scaled(GeneratorConfig.reinforcedSolarPeakEuPerTick,
				SolarCurve.dayShare(6000, GeneratorConfig.reinforcedSolarEdgeFactor));

		helper.setBlock(POS.above(), Blocks.SNOW);
		int snow = rateAt(helper, panel);
		helper.assertTrue(panel.getDataAccess().get(3) == ReinforcedSolarPanelBlockEntity.MODE_DAY_SNOW,
				"a snow layer must flag MODE_DAY_SNOW, got " + panel.getDataAccess().get(3));
		helper.assertTrue(snow == effective(SolarCurve.snowOutput(noon, GeneratorConfig.solarSnowFactor)),
				"snow rate " + snow);
		helper.assertTrue(snow > 0 && snow < effective(noon), "snow must dim, not stop: " + snow);
		helper.setBlock(POS.above(), Blocks.AIR);

		setRaining(helper, false);
		// The sky cache re-samples on its own cadence; drive past it so the rain is seen.
		drive(panel, helper, GeneratorConfig.solarSkySampleTicks + 1);
		int rain = panel.getDataAccess().get(2);
		helper.assertTrue(rain == 0, "rain must stop the day panel, got " + rain + " EU/t");
		helper.assertTrue(panel.getDataAccess().get(3) == ReinforcedSolarPanelBlockEntity.MODE_DAY_WEATHER,
				"rain must flag MODE_DAY_WEATHER, got " + panel.getDataAccess().get(3));
		setClearDay(helper);
		helper.succeed();
	}

	/**
	 * The night branch's curve: the full moon gives the whole peak, the new moon {@code newMoon} of it — read from
	 * the game's own moon brightness, which the rig checks it really changed.
	 *
	 * @implements MOD-1031-T3-NIGHT — output follows the phase of the moon
	 */
	public static void bronzeFollowsTheMoon(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.BRONZE_LUNAR_PANEL.get());
		BronzeLunarPanelBlockEntity panel = be(helper, BronzeLunarPanelBlockEntity.class);
		var level = helper.getLevel();
		BlockPos abs = helper.absolutePos(POS);
		int peak = GeneratorConfig.bronzeLunarPeakEuPerTick;
		double newMoon = GeneratorConfig.bronzeLunarNewMoonFactor;

		setClock(helper, FULL_MOON_MIDNIGHT);
		float fullBrightness = level.getMoonBrightness(abs);
		drive(panel, helper, 1);
		int full = panel.getDataAccess().get(2);

		setClock(helper, NEW_MOON_MIDNIGHT);
		float newBrightness = level.getMoonBrightness(abs);
		drive(panel, helper, 1);
		int fresh = panel.getDataAccess().get(2);

		if (fullBrightness <= newBrightness) {
			helper.fail("rig: the clock did not change the moon (brightness " + fullBrightness + " at day 0, "
					+ newBrightness + " at day 4)");
			return;
		}
		helper.assertTrue(panel.getDataAccess().get(3) == BronzeLunarPanelBlockEntity.MODE_NIGHT,
				"at night expected MODE_NIGHT, got " + panel.getDataAccess().get(3));
		helper.assertTrue(full == effective(SolarCurve.scaled(peak, SolarCurve.moonShare(fullBrightness, newMoon))),
				"full moon rate " + full + " at brightness " + fullBrightness);
		helper.assertTrue(fresh == effective(SolarCurve.scaled(peak, SolarCurve.moonShare(newBrightness, newMoon))),
				"new moon rate " + fresh + " at brightness " + newBrightness);
		helper.assertTrue(fresh < full, "the new moon (" + fresh + ") must make less than the full moon ("
				+ full + ")");

		setClock(helper, 6000);
		drive(panel, helper, 1);
		helper.assertTrue(panel.getDataAccess().get(2) == 0, "the night panel made EU at noon");

		if (peak == 10 && newMoon == 0.5 && Config.globalEuRateMultiplier == 1.0f
				&& fullBrightness == 1.0f && newBrightness == 0.0f) {
			helper.assertTrue(full == 10 && fresh == 5, "owner's figures: 10 at the full moon, 5 at the new moon; got "
					+ full + " and " + fresh);
		}
		helper.succeed();
	}

	/**
	 * Rain and thunder halve the night panel, never below the floor; a snow layer halves it too.
	 *
	 * @implements MOD-1031-T3-NIGHT-WEATHER — rain half (at least 2), snow 50 %
	 */
	public static void bronzeWeather(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.BRONZE_LUNAR_PANEL.get());
		BronzeLunarPanelBlockEntity panel = be(helper, BronzeLunarPanelBlockEntity.class);
		var level = helper.getLevel();
		BlockPos abs = helper.absolutePos(POS);
		setClock(helper, FULL_MOON_MIDNIGHT);
		int clear = SolarCurve.scaled(GeneratorConfig.bronzeLunarPeakEuPerTick,
				SolarCurve.moonShare(level.getMoonBrightness(abs), GeneratorConfig.bronzeLunarNewMoonFactor));

		helper.setBlock(POS.above(), Blocks.SNOW);
		drive(panel, helper, 1);
		int snow = panel.getDataAccess().get(2);
		helper.assertTrue(panel.getDataAccess().get(3) == BronzeLunarPanelBlockEntity.MODE_NIGHT_SNOW,
				"a snow layer must flag MODE_NIGHT_SNOW, got " + panel.getDataAccess().get(3));
		helper.assertTrue(snow == effective(SolarCurve.snowOutput(clear, GeneratorConfig.bronzeLunarSnowFactor)),
				"snow rate " + snow + " from " + clear);
		helper.setBlock(POS.above(), Blocks.AIR);

		setRaining(helper, true);
		drive(panel, helper, GeneratorConfig.solarSkySampleTicks + 1);
		int rain = panel.getDataAccess().get(2);
		int expected = SolarCurve.weatherOutput(clear, GeneratorConfig.bronzeLunarWeatherFactor,
				GeneratorConfig.bronzeLunarWeatherMinEuPerTick);
		helper.assertTrue(panel.getDataAccess().get(3) == BronzeLunarPanelBlockEntity.MODE_NIGHT_WEATHER,
				"a storm must flag MODE_NIGHT_WEATHER, got " + panel.getDataAccess().get(3));
		helper.assertTrue(rain == effective(expected), "storm rate " + rain + ", expected " + effective(expected));
		helper.assertTrue(rain > 0, "the night branch must keep working in a storm");
		helper.assertTrue(SolarCurve.weatherOutput(5, 0.5, 2) == 3 && SolarCurve.weatherOutput(3, 0.5, 2) == 2,
				"the rain floor: half, at least 2");
		setClearDay(helper);
		helper.succeed();
	}

	/**
	 * The daylight panel with a resonance chip grows into the Reinforced Solar Panel — no longer the withdrawn
	 * Mirror Concentrator — with its charge, owner and upgrade chips (each in the same arm of the new panel), the
	 * resonance chip used up and nothing dropped on the ground.
	 *
	 * @implements MOD-1031-EVOLVE-DAY — T2 day + resonance chip → T3 day
	 */
	public static void daylightEvolvesIntoReinforced(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		setClearDay(helper);
		DaylightSolarPanelBlockEntity panel = be(helper, DaylightSolarPanelBlockEntity.class);
		UUID owner = UUID.randomUUID();
		panel.setOwner(owner, "solar_owner");
		panel.setItem(DaylightSolarPanelBlockEntity.CHIP_SLOT, new ItemStack(ModContent.RESONANCE_CHIP.get()));
		panel.getEnergyStorage().setAmountUntracked(5_000);
		fitUpgrades(panel);
		panel.setEvolveProgressTicks(GeneratorConfig.solarEvolveTicks - 1);
		drive(panel, helper, 2);
		expectGrown(helper, ModContent.REINFORCED_SOLAR_PANEL.get(), ReinforcedSolarPanelBlockEntity.class, owner,
				5_000L, ModContent.RESONANCE_CHIP.get());
		helper.succeed();
	}

	/**
	 * The moonlit panel takes the resonance chip (one at a time), banks night time with it and grows into the Bronze
	 * Lunar Panel with its charge, owner and upgrade chips. Since MOD-1033 it takes the lunar evolution chip as well
	 * (its own scenario below), and still nothing else: a day evolution chip is refused.
	 *
	 * @implements MOD-1031-EVOLVE-NIGHT — T2 night + resonance chip → T3 night
	 */
	public static void moonlitTakesResonanceChipAndEvolves(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		MoonlitSolarPanelBlockEntity panel = be(helper, MoonlitSolarPanelBlockEntity.class);
		ItemStack resonance = new ItemStack(ModContent.RESONANCE_CHIP.get());
		helper.assertTrue(
				panel.canPlaceItemThroughFace(MoonlitSolarPanelBlockEntity.CHIP_SLOT, resonance, Direction.UP),
				"the moonlit panel refused a resonance chip in its empty slot");
		helper.assertTrue(!panel.canPlaceItemThroughFace(MoonlitSolarPanelBlockEntity.CHIP_SLOT,
				new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get()), Direction.UP),
				"the moonlit panel took the solar evolution chip, which belongs to the day branch");

		setClock(helper, FULL_MOON_MIDNIGHT);
		UUID owner = UUID.randomUUID();
		panel.setOwner(owner, "lunar_owner");
		panel.setItem(MoonlitSolarPanelBlockEntity.CHIP_SLOT, resonance.copy());
		helper.assertTrue(!panel.canPlaceItemThroughFace(MoonlitSolarPanelBlockEntity.CHIP_SLOT, resonance,
				Direction.UP), "automation stacked a second chip into an occupied slot");
		panel.getEnergyStorage().setAmountUntracked(4_000);
		drive(panel, helper, 1);
		helper.assertTrue(panel.getEvolveProgressTicks() == 1, "one night tick with the chip banked "
				+ panel.getEvolveProgressTicks() + " ticks");
		fitUpgrades(panel);
		panel.setEvolveProgressTicks(GeneratorConfig.solarEvolveTicks - 1);
		drive(panel, helper, 2);
		expectGrown(helper, ModContent.BRONZE_LUNAR_PANEL.get(), BronzeLunarPanelBlockEntity.class, owner, 4_000L,
				ModContent.RESONANCE_CHIP.get());
		setClearDay(helper);
		helper.succeed();
	}

	/**
	 * The owner's check of MOD-1031: the lunar evolution chip that grew the moonlit panel is refused by its chip slot,
	 * so the night branch cannot climb. Now the slot of the open menu takes it — the path a player uses — the panel
	 * banks night time with it and grows into the Bronze Lunar Panel with its charge, owner and upgrade chips, the
	 * chip used up and nothing dropped.
	 *
	 * @implements MOD-1033-EVOLVE-NIGHT-LUNAR — T2 night + lunar evolution chip → T3 night
	 */
	public static void moonlitGrowsWithItsLunarChip(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		MoonlitSolarPanelBlockEntity panel = be(helper, MoonlitSolarPanelBlockEntity.class);
		ItemStack lunar = new ItemStack(ModContent.ALIGNMENT_CHIP_NIGHT.get());
		var player = AlaGameTestHelper.mockPlayerInLevel(helper);
		var menu = new MoonlitSolarPanelMenu(0, player.getInventory(), panel,
				ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(POS)));
		helper.assertTrue(menu.machineSlot(MoonlitSolarPanelBlockEntity.CHIP_SLOT).mayPlace(lunar),
				"the chip slot of the moonlit panel's menu refused the lunar evolution chip");

		setClock(helper, FULL_MOON_MIDNIGHT);
		UUID owner = UUID.randomUUID();
		panel.setOwner(owner, "lunar_owner");
		panel.setItem(MoonlitSolarPanelBlockEntity.CHIP_SLOT, lunar.copy());
		panel.getEnergyStorage().setAmountUntracked(4_000);
		drive(panel, helper, 1);
		helper.assertTrue(panel.getEvolveProgressTicks() == 1, "one night tick with the lunar chip banked "
				+ panel.getEvolveProgressTicks() + " ticks");
		fitUpgrades(panel);
		panel.setEvolveProgressTicks(GeneratorConfig.solarEvolveTicks - 1);
		drive(panel, helper, 2);
		expectGrown(helper, ModContent.BRONZE_LUNAR_PANEL.get(), BronzeLunarPanelBlockEntity.class, owner, 4_000L,
				ModContent.ALIGNMENT_CHIP_NIGHT.get());
		setClearDay(helper);
		helper.succeed();
	}

	/** A mute chip in upgrade arm 0 and a statistics chip in arm 2: the T2 panel's arms start after its chip slot. */
	private static void fitUpgrades(dev.alaindustrial.block.entity.MachineBlockEntity panel) {
		panel.setItem(panel.upgradeSlotStart(), new ItemStack(ModContent.MUTE_CHIP.get()));
		panel.setItem(panel.upgradeSlotStart() + 2, new ItemStack(ModContent.STATS_CHIP.get()));
	}

	private static <T extends dev.alaindustrial.block.entity.MachineBlockEntity> void expectGrown(GameTestHelper helper,
			Block block, Class<T> type, UUID owner, long minCharge, Item chip) {
		Block now = helper.getLevel().getBlockState(helper.absolutePos(POS)).getBlock();
		helper.assertTrue(now == block, "the panel grew into " + now + ", expected " + block);
		T grown = be(helper, type);
		helper.assertTrue(owner.equals(grown.getOwner()), "the owner did not carry over: " + grown.getOwner());
		helper.assertTrue(grown.getEnergyStorage().getAmount() >= minCharge,
				"the stored energy did not carry over: " + grown.getEnergyStorage().getAmount());
		for (int slot = 0; slot < grown.getContainerSize(); slot++) {
			helper.assertTrue(!grown.getItem(slot).is(chip),
					"the chip was not used up: it sits in slot " + slot);
		}
		helper.assertTrue(grown.getUpgradeStack(0).is(ModContent.MUTE_CHIP.get()),
				"upgrade arm 0 did not carry the mute chip: " + grown.getUpgradeStack(0));
		helper.assertTrue(grown.getUpgradeStack(2).is(ModContent.STATS_CHIP.get()),
				"upgrade arm 2 did not carry the statistics chip: " + grown.getUpgradeStack(2));
		var dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(helper.absolutePos(POS)).inflate(3));
		helper.assertTrue(dropped.isEmpty(), "the evolution dropped " + dropped.size() + " stacks on the ground: "
				+ dropped.stream().map(e -> e.getItem().toString()).toList());
	}

	/**
	 * A moonlit panel saved before MOD-1031 had its upgrade arms at 0..3; the chip slot took index 0, so the
	 * ladder's rung moves the run one slot up. A re-save is the new layout and moves nothing.
	 *
	 * @implements MOD-1031-MIGRATION — moonlit save v3 → v4
	 */
	public static void moonlitOldSaveMovesItsChips(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		helper.assertTrue(BlockEntityDataMigrations.DATA_VERSION >= 4, "the moonlit chip slot is format version 4, the"
				+ " ladder reads " + BlockEntityDataMigrations.DATA_VERSION);
		String items = "Items:[{Slot:0b,id:\"alaindustrial:mute_chip\",count:1},"
				+ "{Slot:2b,id:\"alaindustrial:stats_chip\",count:1}]";
		for (String version : List.of("AlaDataVersion:3,", "")) {
			MoonlitSolarPanelBlockEntity old = load(registries, "{" + version + "Energy:1234L," + items + "}");
			String what = version.isEmpty() ? "a save with no version" : "a version 3 save";
			expect(helper, what, old, null, MoonlitSolarPanelBlockEntity.CHIP_SLOT);
			expect(helper, what, old, ModContent.MUTE_CHIP.get(), 1);
			expect(helper, what, old, ModContent.STATS_CHIP.get(), 3);
			helper.assertTrue(old.getEnergyStorage().getAmount() == 1234L, what + ": charge "
					+ old.getEnergyStorage().getAmount());
			CompoundTag resaved = old.saveCustomOnly(registries);
			MoonlitSolarPanelBlockEntity again = load(registries, resaved);
			expect(helper, what + " re-saved", again, ModContent.MUTE_CHIP.get(), 1);
			expect(helper, what + " re-saved", again, ModContent.STATS_CHIP.get(), 3);
		}
		// A resonance chip in slot 0 is a new-layout save an older jar wrote back: nothing moves.
		MoonlitSolarPanelBlockEntity roundTrip = load(registries,
				"{Items:[{Slot:0b,id:\"alaindustrial:resonance_chip\",count:1},"
						+ "{Slot:1b,id:\"alaindustrial:mute_chip\",count:1}]}");
		expect(helper, "an older jar's write-back", roundTrip, ModContent.RESONANCE_CHIP.get(), 0);
		expect(helper, "an older jar's write-back", roundTrip, ModContent.MUTE_CHIP.get(), 1);
		// The same for the lunar evolution chip the slot takes since MOD-1033.
		MoonlitSolarPanelBlockEntity lunarTrip = load(registries,
				"{Items:[{Slot:0b,id:\"alaindustrial:alignment_chip_night\",count:1},"
						+ "{Slot:1b,id:\"alaindustrial:mute_chip\",count:1}]}");
		expect(helper, "an older jar's write-back with a lunar chip", lunarTrip, ModContent.ALIGNMENT_CHIP_NIGHT.get(),
				0);
		expect(helper, "an older jar's write-back with a lunar chip", lunarTrip, ModContent.MUTE_CHIP.get(), 1);
		helper.succeed();
	}

	private static MoonlitSolarPanelBlockEntity load(RegistryAccess registries, String snbt) {
		return load(registries, SaveFormatTestSupport.parse(snbt));
	}

	private static MoonlitSolarPanelBlockEntity load(RegistryAccess registries, CompoundTag tag) {
		MoonlitSolarPanelBlockEntity be = SaveFormatTestSupport.fresh(MoonlitSolarPanelBlockEntity::new,
				ModContent.MOONLIT_SOLAR_PANEL.get());
		return SaveFormatTestSupport.load(be, registries, tag);
	}

	/** Slot {@code slot} holds one {@code item}; a {@code null} item means the slot is empty. */
	private static void expect(GameTestHelper helper, String what, MoonlitSolarPanelBlockEntity be, Item item,
			int slot) {
		ItemStack stack = be.getItem(slot);
		boolean ok = item == null ? stack.isEmpty() : stack.is(item);
		helper.assertTrue(ok, what + ": slot " + slot + " should hold " + (item == null ? "nothing" : item)
				+ ", holds " + stack);
	}
}
