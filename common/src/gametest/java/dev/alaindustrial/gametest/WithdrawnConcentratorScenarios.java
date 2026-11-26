package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

import dev.alaindustrial.block.ConcentratorPart;
import dev.alaindustrial.block.ConcentratorSectionBlock;
import dev.alaindustrial.block.RadiantSolarPanelBlock;
import dev.alaindustrial.block.WithdrawnSolarBlocks;
import dev.alaindustrial.block.entity.RadiantSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.ReinforcedSolarPanelBlockEntity;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.item.WithdrawnBlockItem;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The withdrawn Mirror Concentrator and Concentrator Section (MOD-1031) in a world saved before the withdrawal:
 * the concentrator turns into the Reinforced Solar Panel with its charge, owner and upgrade chips; every section of
 * an assembled structure leaves its cell empty and drops one machine casing, so the panel sees the sky and works; a
 * loose section turns into the casing block on a random tick; and both items turn in a player's inventory and place
 * their successor. Nothing is lost.
 *
 * <p>An old chunk is staged by setting blocks with {@link #AS_LOADED}: no neighbour updates and no placement hook,
 * which is how a chunk read from disk arrives — so the conversion has to come from the paths a loaded world
 * really runs, not from the placement this rig would otherwise trigger.
 */
public final class WithdrawnConcentratorScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(WithdrawnConcentratorScenarios::concentratorKeepsChargeOwnerAndChips,
								"mod1031_concentrator_turns_into_reinforced_panel")
						.ticks(20),
				RosterEntry.of(WithdrawnConcentratorScenarios::assembledStructureFreesThePanel,
								"mod1031_assembled_concentrator_frees_the_panel")
						.ticks(60).sky(),
				RosterEntry.of(WithdrawnConcentratorScenarios::orphanedSectionReturnsACasing,
								"mod1031_orphaned_section_returns_a_casing")
						.ticks(20),
				RosterEntry.of(WithdrawnConcentratorScenarios::looseSectionTurnsIntoCasing,
								"mod1031_loose_section_turns_into_casing")
						.ticks(40),
				RosterEntry.of(WithdrawnConcentratorScenarios::itemsTurnAndPlaceTheirSuccessors,
								"mod1031_withdrawn_items_turn_in_inventory")
						.ticks(20));

		private Roster() {}
	}

	private WithdrawnConcentratorScenarios() {}

	private static final BlockPos CORE = new BlockPos(1, 2, 1);
	/** A chunk read from disk: no shape updates to neighbours, no {@code onPlace}. */
	private static final int AS_LOADED = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

	private static void setAsLoaded(GameTestHelper helper, BlockPos rel, BlockState state) {
		helper.getLevel().setBlock(helper.absolutePos(rel), state, AS_LOADED);
	}

	private static Block blockAt(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getBlockState(helper.absolutePos(rel)).getBlock();
	}

	/**
	 * @implements MOD-1031-LEGACY-CORE — the concentrator becomes the T3 day panel with charge, owner and chips
	 */
	public static void concentratorKeepsChargeOwnerAndChips(GameTestHelper helper) {
		setAsLoaded(helper, CORE, ModContent.RADIANT_SOLAR_PANEL.get().defaultBlockState());
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CORE)) instanceof RadiantSolarPanelBlockEntity old)) {
			helper.fail("rig: no concentrator block entity at the core");
			return;
		}
		UUID owner = UUID.randomUUID();
		old.setOwner(owner, "concentrator_owner");
		old.getEnergyStorage().setAmountUntracked(15_999L);
		old.setItem(0, new ItemStack(ModContent.MUTE_CHIP.get()));
		old.setItem(2, new ItemStack(ModContent.STATS_CHIP.get()));
		drive(old, helper, 1);

		helper.assertTrue(blockAt(helper, CORE) == ModContent.REINFORCED_SOLAR_PANEL.get(),
				"the concentrator turned into " + blockAt(helper, CORE));
		ReinforcedSolarPanelBlockEntity panel = helper.getBlockEntity(CORE, ReinforcedSolarPanelBlockEntity.class);
		helper.assertTrue(panel.getEnergyStorage().getAmount() == 15_999L,
				"the charge did not carry over: " + panel.getEnergyStorage().getAmount());
		helper.assertTrue(owner.equals(panel.getOwner()), "the owner did not carry over: " + panel.getOwner());
		helper.assertTrue(panel.getItem(0).is(ModContent.MUTE_CHIP.get()), "slot 0: " + panel.getItem(0));
		helper.assertTrue(panel.getItem(2).is(ModContent.STATS_CHIP.get()), "slot 2: " + panel.getItem(2));
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
				new AABB(helper.absolutePos(CORE)).inflate(3)).isEmpty(),
				"the conversion dropped items on the ground");
		helper.succeed();
	}

	/** The seven sections of an assembled concentrator around {@link #CORE}, facing north, with their saved parts. */
	private static final List<BlockPos> CELLS = List.of(CORE.east(), CORE.south(), CORE.east().south(),
			CORE.above(), CORE.above().east(), CORE.above().south(), CORE.above().east().south());
	private static final List<ConcentratorPart> PARTS = List.of(ConcentratorPart.RIGHT, ConcentratorPart.BACK,
			ConcentratorPart.BACK_RIGHT, ConcentratorPart.TOP, ConcentratorPart.TOP_RIGHT,
			ConcentratorPart.TOP_BACK, ConcentratorPart.TOP_BACK_RIGHT);

	/** Machine casings lying as items within {@code radius} of {@code rel}, counted by stack size. */
	private static int casingItemsNear(GameTestHelper helper, BlockPos rel, int radius) {
		int count = 0;
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
				new AABB(helper.absolutePos(rel)).inflate(radius))) {
			if (item.getItem().is(ModContent.MACHINE_CASING_ITEM.get())) {
				count += item.getItem().getCount();
			}
		}
		return count;
	}

	/**
	 * An assembled concentrator in an old chunk: the core turns into the Reinforced Solar Panel on its first tick,
	 * and the chain of neighbour updates empties all seven section cells, each dropping exactly one machine casing
	 * (the core cell is the panel, not a section). With the top section gone, the panel works at noon under a
	 * clear sky. Before the MOD-1031 review the sections turned into casing blocks, and the one over the core
	 * shaded the panel to zero.
	 *
	 * @implements MOD-1031-LEGACY-STRUCTURE — an assembled concentrator becomes a working panel and seven casings
	 */
	public static void assembledStructureFreesThePanel(GameTestHelper helper) {
		setAsLoaded(helper, CORE, ModContent.RADIANT_SOLAR_PANEL.get().defaultBlockState()
				.setValue(RadiantSolarPanelBlock.ASSEMBLED, Boolean.TRUE)
				.setValue(RadiantSolarPanelBlock.FACING, Direction.NORTH));
		for (int i = 0; i < CELLS.size(); i++) {
			setAsLoaded(helper, CELLS.get(i), ModContent.CONCENTRATOR_SECTION.get().defaultBlockState()
					.setValue(ConcentratorSectionBlock.PART, PARTS.get(i))
					.setValue(ConcentratorSectionBlock.FACING, Direction.NORTH));
		}
		for (BlockPos cell : CELLS) {
			helper.assertTrue(blockAt(helper, cell) == ModContent.CONCENTRATOR_SECTION.get(),
					"rig: the staging already converted " + cell);
		}
		RadiantSolarPanelBlockEntity old = helper.getBlockEntity(CORE, RadiantSolarPanelBlockEntity.class);
		drive(old, helper, 1);
		helper.assertTrue(blockAt(helper, CORE) == ModContent.REINFORCED_SOLAR_PANEL.get(),
				"the core turned into " + blockAt(helper, CORE));
		helper.succeedWhen(() -> {
			for (BlockPos cell : CELLS) {
				helper.assertTrue(blockAt(helper, cell) == Blocks.AIR,
						"the section at " + cell + " is " + blockAt(helper, cell) + ", not an empty cell");
			}
			int casings = casingItemsNear(helper, CORE, 4);
			helper.assertTrue(casings == CELLS.size(), "the seven sections dropped " + casings + " machine casings");

			SolarPanelRig.setClearDay(helper);
			ReinforcedSolarPanelBlockEntity panel = helper.getBlockEntity(CORE, ReinforcedSolarPanelBlockEntity.class);
			// The panel ticked while the top section still stood: drive past the sky cache's cadence so it looks again.
			drive(panel, helper, GeneratorConfig.solarSkySampleTicks + 1);
			int rate = panel.getDataAccess().get(2);
			helper.assertTrue(panel.getDataAccess().get(3) == ReinforcedSolarPanelBlockEntity.MODE_DAY,
					"the converted panel is not in MODE_DAY by day under a clear sky: mode "
							+ panel.getDataAccess().get(3) + ", " + rate + " EU/t");
			helper.assertTrue(rate > 0, "the converted panel makes " + rate + " EU/t by day under a clear sky");
		});
	}

	/**
	 * A section of an assembled structure whose core sat in a chunk that was not loaded when the core turned, so
	 * that no neighbour update ever reaches it, is still refunded by its random tick: the cell empties and one
	 * casing drops, instead of a casing block left floating where the structure stood. A second conversion of the
	 * same cell pays nothing more.
	 *
	 * @implements MOD-1031-LEGACY-ORPHAN — a section cut off from its core still returns its casing
	 */
	public static void orphanedSectionReturnsACasing(GameTestHelper helper) {
		BlockPos cell = CORE.above();
		BlockState section = ModContent.CONCENTRATOR_SECTION.get().defaultBlockState()
				.setValue(ConcentratorSectionBlock.PART, ConcentratorPart.TOP)
				.setValue(ConcentratorSectionBlock.FACING, Direction.NORTH);
		setAsLoaded(helper, cell, section);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(cell);
		section.randomTick(level, abs, level.getRandom());
		helper.assertTrue(blockAt(helper, cell) == Blocks.AIR,
				"a random tick left the orphaned section as " + blockAt(helper, cell));
		int first = casingItemsNear(helper, cell, 2);
		helper.assertTrue(first == 1, "the orphaned section dropped " + first + " casings");
		WithdrawnSolarBlocks.convertSection(level, abs);
		int second = casingItemsNear(helper, cell, 2);
		helper.assertTrue(second == 1, "a second conversion of the same cell paid again: " + second + " casings");
		helper.succeed();
	}

	/**
	 * @implements MOD-1031-LEGACY-SECTION — a loose section turns on a random tick, a placed one on its next tick
	 */
	public static void looseSectionTurnsIntoCasing(GameTestHelper helper) {
		BlockPos loose = new BlockPos(1, 2, 1);
		BlockState section = ModContent.CONCENTRATOR_SECTION.get().defaultBlockState();
		setAsLoaded(helper, loose, section);
		ServerLevel level = helper.getLevel();
		section.randomTick(level, helper.absolutePos(loose), level.getRandom());
		helper.assertTrue(blockAt(helper, loose) == ModContent.MACHINE_CASING.get(),
				"a random tick left the loose section as " + blockAt(helper, loose));
		helper.assertTrue(section.isRandomlyTicking(), "the section does not take random ticks: a loose one in an"
				+ " old world would never turn");

		BlockPos placed = new BlockPos(3, 2, 1);
		helper.setBlock(placed, section);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(blockAt(helper, placed) == ModContent.MACHINE_CASING.get(),
					"a section set by a command is still " + blockAt(helper, placed));
			helper.succeed();
		});
	}

	/**
	 * @implements MOD-1031-LEGACY-ITEMS — both items turn in a player's inventory and place their successors
	 */
	public static void itemsTurnAndPlaceTheirSuccessors(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		ItemStack concentrator = new ItemStack(ModContent.RADIANT_SOLAR_PANEL_ITEM.get(), 2);
		ItemStack sections = new ItemStack(ModContent.CONCENTRATOR_SECTION_ITEM.get(), 7);
		player.getInventory().setItem(3, concentrator);
		player.getInventory().setItem(4, sections);
		concentrator.getItem().inventoryTick(concentrator, helper.getLevel(), player, null);
		sections.getItem().inventoryTick(sections, helper.getLevel(), player, null);
		ItemStack three = player.getInventory().getItem(3);
		ItemStack four = player.getInventory().getItem(4);
		helper.assertTrue(three.is(ModContent.REINFORCED_SOLAR_PANEL_ITEM.get()) && three.getCount() == 2,
				"the concentrator item turned into " + three);
		helper.assertTrue(four.is(ModContent.MACHINE_CASING_ITEM.get()) && four.getCount() == 7,
				"the section item turned into " + four);
		helper.assertTrue(concentrator.getItem() instanceof WithdrawnBlockItem,
				"the concentrator item is not a withdrawn item");

		BlockState placedCore = ModContent.RADIANT_SOLAR_PANEL.get().getStateForPlacement(null);
		BlockState placedSection = ModContent.CONCENTRATOR_SECTION.get().getStateForPlacement(null);
		helper.assertTrue(placedCore != null && placedCore.is(ModContent.REINFORCED_SOLAR_PANEL.get()),
				"placing the concentrator item puts down " + placedCore);
		helper.assertTrue(placedSection != null && placedSection.is(ModContent.MACHINE_CASING.get()),
				"placing the section item puts down " + placedSection);

		List<ItemStack> coreDrops = Block.getDrops(ModContent.RADIANT_SOLAR_PANEL.get().defaultBlockState(),
				helper.getLevel(), helper.absolutePos(CORE), null);
		List<ItemStack> sectionDrops = Block.getDrops(ModContent.CONCENTRATOR_SECTION.get().defaultBlockState(),
				helper.getLevel(), helper.absolutePos(CORE), null);
		helper.assertTrue(coreDrops.size() == 1 && coreDrops.get(0).is(ModContent.REINFORCED_SOLAR_PANEL_ITEM.get()),
				"the concentrator drops " + coreDrops);
		helper.assertTrue(sectionDrops.size() == 1 && sectionDrops.get(0).is(ModContent.MACHINE_CASING_ITEM.get()),
				"the section drops " + sectionDrops);
		helper.succeed();
	}
}
