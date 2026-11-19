package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.machine.MachineChannels;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.environment.DrawMeter;
import dev.alaindustrial.core.environment.FuelRuntime;
import dev.alaindustrial.core.environment.FuelTally;
import dev.alaindustrial.core.environment.GeneratorConfig;
import net.minecraft.server.level.ServerPlayer;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.core.energy.NetworkManager;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.alaindustrial.menu.GeneratorMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.GameType;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

/**
 * L2 functional suite for the fuel generator — automates the parts of {@code TC-GEN-001}
 * (docs/testing/blocks/generators/generator.md) that need a live {@link net.minecraft.server.level.ServerLevel}.
 * Migrated/expanded from the legacy {@code IndustrializationSelfTest} fuel-generator checks.
 *
 * <p>Drives {@code serverTick} directly (deterministic, no waiting) like the legacy self-test, then
 * {@code helper.succeed()}. Each method = exactly one case, traced via {@code @implements}.
 *
 * <p>Numbers come from {@link Config} (canon), never hard-coded.
 */
public final class GeneratorScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(GeneratorScenarios::tcGen001Fun01_coalRaisesBuffer, "tc_gen001_fun01_coal_raises_buffer")
						.fabricId("GeneratorGameTest", "tcGen001Fun01_coalRaisesBuffer").ticks(20, 40),
				RosterEntry.of(GeneratorScenarios::tcGen001Per01_stateSurvivesNbtRoundTrip,
								"tc_gen001_per01_state_survives_nbt_round_trip")
						.fabricId("GeneratorGameTest", "tcGen001Per01_stateSurvivesNbtRoundTrip").ticks(20, 40),
				RosterEntry.of(GeneratorScenarios::tcGen001Neg04_lavaBucketRejected,
								"tc_gen001_neg04_lava_bucket_rejected")
						.fabricId("GeneratorGameTest", "tcGen001Neg04_lavaBucketRejected").ticks(20, 40),
				RosterEntry.of(GeneratorScenarios::tcGen001Neg04b_menuSlotRejectsLavaBucket,
								"tc_gen001_neg04b_menu_slot_rejects_lava_bucket")
						.fabricId("GeneratorGameTest", "tcGen001Neg04b_menuSlotRejectsLavaBucket").ticks(20, 40),
				RosterEntry.of(GeneratorScenarios::tcGen001Fun03_coalBlockBurnsLonger,
								"tc_gen001_fun03_coal_block_burns_longer")
						.fabricId("GeneratorGameTest", "tcGen001Fun03_coalBlockBurnsLonger").ticks(20, 40),
				RosterEntry.of(GeneratorScenarios::tcGen001Neg02_nonFuelProducesNoEu,
								"tc_gen001_neg02_non_fuel_produces_no_eu")
						.fabricId("GeneratorGameTest", "tcGen001Neg02_nonFuelProducesNoEu").ticks(20, 40),
				RosterEntry.of(GeneratorScenarios::tcGen001Neg02b_fuelSlotRejectsNonFuel,
								"tc_gen001_neg02b_fuel_slot_rejects_non_fuel")
						.fabricId("GeneratorGameTest", "tcGen001Neg02b_fuelSlotRejectsNonFuel").ticks(20, 40),
				RosterEntry.of(GeneratorScenarios::tcGen001Per02_breakDropsFuelNoDupe,
								"tc_gen001_per02_break_drops_fuel_no_dupe")
						.fabricId("GeneratorGameTest", "tcGen001Per02_breakDropsFuelNoDupe").ticks(20, 40),
				RosterEntry.of(GeneratorScenarios::tcGen001Con01_pairwiseNeighbours,
								"tc_gen001_con01_pairwise_neighbours")
						.fabricId("GeneratorGameTest", "tcGen001Con01_pairwiseNeighbours").ticks(20, 40),
				RosterEntry.of(GeneratorScenarios::tcGen001Phy02_hitboxIsFullCube,
								"tc_gen001_phy02_hitbox_is_full_cube")
						.fabricId("GeneratorGameTest", "tcGen001Phy02_hitboxIsFullCube").ticks(20, 40),
				RosterEntry.of(GeneratorScenarios::tcGen001Prf02_packetCappedAtLv,
								"tc_gen001_prf02_packet_capped_at_lv")
						.fabricId("GeneratorGameTest", "tcGen001Prf02_packetCappedAtLv").ticks(20, 40),
				RosterEntry.of(GeneratorScenarios::tcGen001Sta01_litStateTracksBurning,
								"tc_gen001_sta01_lit_state_tracks_burning")
						.fabricId("GeneratorGameTest", "tcGen001Sta01_litStateTracksBurning").ticks(20, 40),
				RosterEntry.of(GeneratorScenarios::tcGen001Fun02_bufferCapsAtMax, "tc_gen001_fun02_buffer_caps_at_max")
						.fabricId("GeneratorGameTest", "tcGen001Fun02_bufferCapsAtMax").ticks(20, 100),
				RosterEntry.of(GeneratorScenarios::tcGen001Fun04_pushesToAdjacentConsumer,
								"tc_gen001_fun04_pushes_to_adjacent_consumer")
						.fabricId("GeneratorGameTest", "tcGen001Fun04_pushesToAdjacentConsumer").ticks(20, 100),
				RosterEntry.of(GeneratorScenarios::tcGen001Neg01_rejectsExternalEu,
								"tc_gen001_neg01_rejects_external_eu")
						.fabricId("GeneratorGameTest", "tcGen001Neg01_rejectsExternalEu").ticks(20, 100),
				RosterEntry.of(GeneratorScenarios::tcGen001Neg03_fullBufferPausesBurn,
								"tc_gen001_neg03_full_buffer_pauses_burn")
						.fabricId("GeneratorGameTest", "tcGen001Neg03_fullBufferPausesBurn").ticks(20, 100),
				RosterEntry.of(GeneratorScenarios::lightLoadBurnsOnlyWhatTheLineDraws,
								"mod986_light_load_burns_only_what_the_line_draws")
						.ticks(20, 100),
				RosterEntry.of(GeneratorScenarios::tcGen001Neg05_fullAdjacentConsumerDoesNotDrainGenerator,
								"tc_gen001_neg05_full_adjacent_consumer_does_not_drain_generator")
						.fabricId("GeneratorGameTest", "tcGen001Neg05_fullAdjacentConsumerDoesNotDrainGenerator")
						.ticks(20, 100),
				RosterEntry.of(GeneratorScenarios::tcGen001Prf01_ratePerTickMatchesConfig,
								"tc_gen001_prf01_rate_per_tick_matches_config")
						.fabricId("GeneratorGameTest", "tcGen001Prf01_ratePerTickMatchesConfig").ticks(20, 100),
				RosterEntry.of(GeneratorScenarios::comparatorReadsFuelSlot, "mod991_comparator_reads_fuel_slot")
						.ticks(60, 60),
				RosterEntry.of(GeneratorScenarios::fuelRuntimeFollowsTheChipAndTheDraw,
								"mod990_fuel_runtime_follows_the_chip_and_the_draw")
						.ticks(20, 60),
				RosterEntry.of(GeneratorScenarios::fuelBurntCountsByKindOnlyWithTheChip,
								"mod992_fuel_burnt_counts_by_kind_only_with_the_chip")
						.ticks(20, 60));

		private Roster() {}
	}

	private GeneratorScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/** Place a generator at {@link #POS} and return its block entity (absolute pos for serverTick). */
	private static GeneratorBlockEntity placeGenerator(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, POS, ModContent.GENERATOR.get(), GeneratorBlockEntity.class);
	}

	/**
	 * @implements TC-GEN-001-FUN01 — coal in the fuel slot burns and fills the EU buffer.
	 * @covers R-GUI-02
	 */
	public static void tcGen001Fun01_coalRaisesBuffer(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 10);
		if (gen.getEnergyStorage().getAmount() <= 0) {
			helper.fail("buffer did not grow from burning coal");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-FUN02 — buffer caps at generatorBuffer and never exceeds it (BVA).
	 * @covers R-NRG-01
	 */
	public static void tcGen001Fun02_bufferCapsAtMax(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		long cap = gen.getEnergyStorage().getCapacity();
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		// BVA: one burn tick short. A gap smaller than one tick's output holds the burn instead (MOD-986).
		gen.getEnergyStorage().setAmountUntracked(cap - GeneratorBlockEntity.effectiveEuPerTick());
		drive(gen, helper, 5);
		long amount = gen.getEnergyStorage().getAmount();
		if (amount != cap) {
			helper.fail("expected buffer to settle at cap " + cap + " but was " + amount);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-NEG01 — generator never accepts external EU (it is a producer only).
	 * @covers R-NRG-03
	 */
	public static void tcGen001Neg01_rejectsExternalEu(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		if (gen.getEnergyStorage().supportsInsertion()) {
			helper.fail("generator storage must not support insertion (maxInsert=0)");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-NEG03 — a full buffer pauses the burn so fuel is not wasted.
	 * @covers R-NRG-11
	 */
	public static void tcGen001Neg03_fullBufferPausesBurn(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 1); // start a burn so burnTime > 0
		gen.getEnergyStorage().setAmountUntracked(gen.getEnergyStorage().getCapacity()); // force buffer full
		drive(gen, helper, 1);
		int burn1 = gen.getDataAccess().get(2); // index 2 = progress = burnTime
		drive(gen, helper, 1);
		int burn2 = gen.getDataAccess().get(2);
		if (!(burn1 > 0 && burn1 == burn2)) {
			helper.fail("full buffer must freeze burn: burn1=" + burn1 + " burn2=" + burn2);
		}
		helper.succeed();
	}

	/**
	 * A light load burns only the fuel it draws (MOD-986). A buffer with less room than one burn tick makes holds
	 * the burn, and a line drawing a few EU a tick from a nearly full buffer costs burn ticks worth what it drew,
	 * not one burn tick per game tick with the rest thrown away.
	 *
	 * @covers R-NRG-11
	 */
	public static void lightLoadBurnsOnlyWhatTheLineDraws(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		int euPerTick = GeneratorBlockEntity.effectiveEuPerTick();
		long cap = gen.getEnergyStorage().getCapacity();
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 1); // start a burn so burnTime > 0

		// Room for less than one burn tick: the burn holds.
		gen.getEnergyStorage().setAmountUntracked(cap - (euPerTick - 1));
		int held1 = gen.getDataAccess().get(2); // index 2 = progress = burnTime
		drive(gen, helper, 3);
		int held2 = gen.getDataAccess().get(2);
		helper.assertTrue(held1 > 0 && held1 == held2, "a gap smaller than one burn tick must hold the burn: "
				+ held1 + " -> " + held2);

		// A light draw from a nearly full buffer: what burns matches what the line took, within one burn tick.
		long lightDraw = Math.max(1, euPerTick / 3);
		int ticks = 60;
		int before = gen.getDataAccess().get(2);
		drawEachTick(gen, helper, lightDraw, ticks);
		int after = gen.getDataAccess().get(2);
		long burnt = (long) (before - after) * euPerTick;
		long drawn = lightDraw * ticks;
		helper.assertTrue(after > 0, "the coal ran out mid-test, the rig is wrong: burnTime " + after);
		helper.assertTrue(burnt <= drawn + euPerTick, "a light load wasted fuel: burnt " + burnt + " EU of fuel for "
				+ drawn + " EU drawn");
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-PER01 — buffer + remaining burn survive an NBT save/load round-trip.
	 * @covers R-PER-01
	 */
	public static void tcGen001Per01_stateSurvivesNbtRoundTrip(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 5);
		long energy0 = gen.getEnergyStorage().getAmount();
		int burn0 = gen.getDataAccess().get(2);

		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = gen.saveCustomOnly(registries);
		GeneratorBlockEntity restored = new GeneratorBlockEntity(gen.getBlockPos(),
				helper.getLevel().getBlockState(gen.getBlockPos()));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.getEnergyStorage().getAmount() != energy0 || restored.getDataAccess().get(2) != burn0) {
			helper.fail("NBT round-trip lost state: energy " + energy0 + "->"
					+ restored.getEnergyStorage().getAmount() + " burn " + burn0 + "->"
					+ restored.getDataAccess().get(2));
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-NEG04 — the fuel slot rejects a lava bucket; lava is the geothermal
	 *     generator's domain (burning it here would out-produce geothermal ~10×). Decision 2026-06-30.
	 */
	public static void tcGen001Neg04_lavaBucketRejected(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		if (gen.canPlaceItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.LAVA_BUCKET))) {
			helper.fail("fuel slot accepts a lava bucket — lava belongs to the geothermal generator");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-NEG04b — GeneratorMenu#Slot.mayPlace rejects a lava bucket at the GUI
	 *     level (client-side fast path that avoids optimistic placement → server-correction flicker).
	 *     Complements NEG04 which tests the hopper path (canPlaceItem).
	 */
	public static void tcGen001Neg04b_menuSlotRejectsLavaBucket(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		var menu = new GeneratorMenu(0, player.getInventory(), gen, ContainerLevelAccess.NULL);
		if (menu.slots.get(0).mayPlace(new ItemStack(Items.LAVA_BUCKET))) {
			helper.fail("GeneratorMenu fuel slot accepted a lava bucket — mayPlace must return false");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-FUN03 — a coal block burns far longer than a single coal (the generator
	 *     inherits vanilla fuel burn values; ~10× in vanilla).
	 */
	public static void tcGen001Fun03_coalBlockBurnsLonger(GameTestHelper helper) {
		GeneratorBlockEntity g1 = placeGenerator(helper);
		g1.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL));
		drive(g1, helper, 1);
		int coalDur = g1.getDataAccess().get(3); // index 3 = maxProgress = burnDuration

		BlockPos pos2 = new BlockPos(3, 2, 1);
		helper.setBlock(pos2, ModContent.GENERATOR.get());
		GeneratorBlockEntity g2 = helper.getBlockEntity(pos2, GeneratorBlockEntity.class);
		g2.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL_BLOCK));
		drive(g2, helper, 1);
		int blockDur = g2.getDataAccess().get(3);

		if (!(coalDur > 0 && blockDur > coalDur)) {
			helper.fail("coal block must burn longer than coal: coal=" + coalDur + " block=" + blockDur);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-FUN04 — generator pushes EU into a directly-adjacent consumer (no cable).
	 * @covers R-NRG-03, R-CON-11
	 */
	public static void tcGen001Fun04_pushesToAdjacentConsumer(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		BlockPos sink = new BlockPos(2, 2, 1); // east of POS, orthogonally adjacent
		// BatteryBox input face = FACING (MOD-006); the generator sits on its WEST side, so face WEST.
		helper.setBlock(sink, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity battery_box = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 20);
		if (battery_box == null || battery_box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent battery_box received no EU from the generator");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-NEG05 — a generator whose only adjacent neighbour is FULL must not lose EU
	 *     (MOD-022 Phase-2 EnergyMover guard). The cable-less push runs through
	 *     {@code DirectAdjacencyDistributor.distribute -> EnergyMover.probe/commit}: {@code probe} dry-runs the transfer, so
	 *     against a full neighbour it moves 0 and nothing is extracted. The naive "extract, then refund the
	 *     surplus via insert" shape would instead pull from the generator and fail to refund it — a
	 *     generator publishes {@code maxInsert == 0}, so the rate-capped refund returns 0 and the extracted
	 *     EU is destroyed. Pre-charges the generator to half and fills the neighbour, then asserts the
	 *     generator's buffer is byte-for-byte unchanged after driving. @covers R-NRG-03
	 */
	public static void tcGen001Neg05_fullAdjacentConsumerDoesNotDrainGenerator(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		BlockPos sink = new BlockPos(2, 2, 1); // east of POS, orthogonally adjacent
		helper.setBlock(sink, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity box = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		if (box == null) {
			helper.fail("battery_box missing after placement");
		}
		// Generator pre-charged to half, NO fuel: produce() == 0, so its buffer can only change via the
		// push path — any drop is a leak, not production noise. It publishes maxInsert == 0.
		long genStart = gen.getEnergyStorage().getCapacity() / 2;
		gen.getEnergyStorage().setAmountUntracked(genStart);
		gen.setChanged();
		// Neighbour full: a correct push moves 0 and extracts nothing from the generator.
		box.getEnergyStorage().setAmountUntracked(box.getEnergyStorage().getCapacity());
		box.setChanged();

		drive(gen, helper, 20);

		long genEnd = gen.getEnergyStorage().getAmount();
		if (genEnd != genStart) {
			helper.fail("generator lost " + (genStart - genEnd) + " EU pushing into a FULL neighbour — "
					+ "EnergyMover refund leak (extracted EU not restored to the maxInsert==0 generator)");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-NEG02 — a non-fuel item (cobblestone) yields no EU. Note: it IS placeable
	 *     in the fuel slot (canPlaceItem only checks the slot index), it simply never burns.
	 * @covers R-GUI-02
	 */
	public static void tcGen001Neg02_nonFuelProducesNoEu(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COBBLESTONE, 64));
		drive(gen, helper, 20);
		if (gen.getEnergyStorage().getAmount() != 0) {
			helper.fail("cobblestone is not fuel; buffer must stay 0 but was " + gen.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-PRF01 — generation rate equals {@code GeneratorConfig.fuelEuPerTick} (canon 8 EU/t).
	 * @covers R-NRG-04
	 */
	public static void tcGen001Prf01_ratePerTickMatchesConfig(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 1);              // tick 1 starts the burn
		gen.getEnergyStorage().setAmountUntracked(0);  // measure one clean tick from empty
		drive(gen, helper, 1);
		long made = gen.getEnergyStorage().getAmount();
		if (made != GeneratorConfig.fuelEuPerTick) {
			helper.fail("EU/t expected " + GeneratorConfig.fuelEuPerTick + " but measured " + made);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-NEG02b — fuel slot rejects non-fuel (canPlaceItem validates fuel, R-GUI-02).
	 */
	public static void tcGen001Neg02b_fuelSlotRejectsNonFuel(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		if (gen.canPlaceItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COBBLESTONE))) {
			helper.fail("fuel slot accepts non-fuel cobblestone — canPlaceItem should validate fuel");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-PER02 — breaking a generator drops its fuel exactly once (no dupe/loss).
	 * @covers R-BRK-06, R-PER-04
	 */
	public static void tcGen001Per02_breakDropsFuelNoDupe(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		int placed = 5;
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, placed));
		BlockPos abs = gen.getBlockPos();
		Containers.dropContents(helper.getLevel(), abs, gen);
		AABB box = new AABB(abs.getX() - 2, abs.getY() - 2, abs.getZ() - 2,
				abs.getX() + 3, abs.getY() + 3, abs.getZ() + 3);
		int dropped = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (e.getItem().is(Items.COAL)) {
				dropped += e.getItem().getCount();
			}
		}
		if (dropped != placed) {
			helper.fail("expected " + placed + " coal dropped, got " + dropped);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-GEN-001-CON01 — pairwise neighbour connectivity: the generator delivers EU to
	 *     compatible neighbours (a directly-adjacent consumer and a directly-adjacent storage) and an
	 *     AIR face yields nothing (no block entity, no crash). A copper cable on a third face exercises
	 *     the network-transport path side-by-side without delivering to a bare cable (no consumer past it).
	 * @covers R-CON-01
	 */
	public static void tcGen001Con01_pairwiseNeighbours(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);

		// Macerator EAST (consumer: IN on every face). BatteryBox SOUTH with default FACING=NORTH — its input
		// face (MOD-006: input on FACING) is NORTH, which touches the generator on its −z side.
		// Copper cable WEST (a network segment, not itself a delivery target). UP face left as AIR.
		BlockPos maceratorPos = POS.relative(Direction.EAST);
		BlockPos batteryBoxPos = POS.relative(Direction.SOUTH);
		BlockPos cablePos = POS.relative(Direction.WEST);
		BlockPos airPos = POS.relative(Direction.UP);

		helper.setBlock(maceratorPos, ModContent.MACERATOR.get());
		helper.setBlock(batteryBoxPos, ModContent.BATTERY_BOX.get()); // default FACING = NORTH → input toward the generator
		helper.setBlock(cablePos, ModContent.COPPER_CABLE.get());

		MaceratorBlockEntity macerator = helper.getBlockEntity(maceratorPos, MaceratorBlockEntity.class);
		BatteryBoxBlockEntity battery_box = helper.getBlockEntity(batteryBoxPos, BatteryBoxBlockEntity.class);
		CableBlockEntity cable = helper.getBlockEntity(cablePos, CableBlockEntity.class);
		if (macerator == null || battery_box == null || cable == null) {
			helper.fail("neighbour block entities missing after placement");
		}

		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		// Pre-charge the buffer: at 8 EU/t the first neighbour in Direction order would drain it before
		// the others get any (direct distribute has no fair split — that's the network's job, R-NRG-08).
		gen.getEnergyStorage().setAmountUntracked(gen.getEnergyStorage().getCapacity());

		// First serverTick on the cable registers it with the NetworkManager (level + neighbours loaded).
		cable.serverTick(helper.getLevel(), cablePos, helper.getLevel().getBlockState(cablePos));

		// Drive the generator (its serverTick directly pushes to the cable-less adjacent consumer/storage)
		// and run the network transport pass alongside it. The cabled path moves nothing here (the cable
		// has a producer but no consumer endpoint, so its network stays asleep); this just proves the two
		// delivery paths coexist without crashing.
		for (int i = 0; i < 20; i++) {
			drive(gen, helper, 1);
			NetworkManager.tickAll(helper.getLevel());
		}

		if (macerator.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent consumer (macerator) received no EU from the generator");
		}
		if (battery_box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent storage (battery_box) received no EU from the generator");
		}
		// The AIR face has no block entity — nothing was delivered there and nothing crashed.
		// Null-safe world lookup: helper.getBlockEntity asserts presence and would throw on an air face.
		if (helper.getLevel().getBlockEntity(helper.absolutePos(airPos)) != null) {
			helper.fail("AIR face unexpectedly has a block entity at " + airPos);
		}
		helper.succeed();
	}


	/**
	 * @implements TC-GEN-001-PHY02 — the generator's collision shape is a full cube (16³); it is a
	 *     solid occluder, not a partial-shape block.
	 * @covers R-PHY-02
	 */
	public static void tcGen001Phy02_hitboxIsFullCube(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		BlockPos abs = gen.getBlockPos();
		BlockState state = helper.getLevel().getBlockState(abs);

		// Direct convenience query: collision shape equals a full block.
		if (!state.isCollisionShapeFullBlock(helper.getLevel(), abs)) {
			helper.fail("generator collision shape is not a full block");
		}

		// Cross-check via the raw VoxelShape so a regression to a partial shape is caught either way.
		VoxelShape collision = state.getCollisionShape(helper.getLevel(), abs);
		if (!Block.isShapeFullBlock(collision)) {
			helper.fail("generator collision VoxelShape is not a full 16³ cube");
		}
		helper.succeed();
	}


	/**
	 * @implements TC-GEN-001-PRF02 — one transfer into a single consumer is capped at the LV per-tick
	 *     transfer limit ({@code EnergyTier.LV.maxVoltage()} = 32 EU). The generator buffer is filled to
	 *     capacity and a fresh BatteryBox (buffer 0) sits on a face it accepts input on; one serverTick must
	 *     move more than 0 but no more than 32 EU.
	 * @covers R-NRG-04
	 */
	public static void tcGen001Prf02_packetCappedAtLv(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.getEnergyStorage().setAmountUntracked(gen.getEnergyStorage().getCapacity()); // buffer near/at full

		BlockPos sink = POS.east(); // (2,2,1): generator is on its WEST side → face WEST so input meets it (MOD-006)
		helper.setBlock(sink, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity battery_box = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		if (battery_box == null) {
			helper.fail("battery_box block entity missing after placement");
		}
		battery_box.getEnergyStorage().setAmountUntracked(0);

		drive(gen, helper, 1); // single tick -> DirectAdjacencyDistributor.distribute, capped at srcTier.maxVoltage()=32

		long lvCap = dev.alaindustrial.core.energy.EnergyTier.LV.maxVoltage();
		long gained = battery_box.getEnergyStorage().getAmount();
		if (!(gained > 0 && gained <= lvCap)) {
			helper.fail("LV transfer must be in (0," + lvCap + "] EU per tick but moved " + gained);
		}
		helper.succeed();
	}


	/**
	 * @implements TC-GEN-001-STA01 — the block's {@code lit} blockstate tracks whether it is burning.
	 *     {@code updateLit(made > 0)} runs every {@code serverTick}
	 *     ({@code AbstractGeneratorBlockEntity#serverTick}), writing the property back to the world via
	 *     {@code level.setBlock}, so LIT is read from the live level, not a cached state.
	 * @covers R-VIS-01
	 */
	public static void tcGen001Sta01_litStateTracksBurning(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		BlockPos abs = gen.getBlockPos();

		// Burning: coal in the fuel slot lights the block.
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		drive(gen, helper, 3);
		if (!helper.getLevel().getBlockState(abs).getValue(BlockStateProperties.LIT)) {
			helper.fail("generator must be LIT while burning fuel");
		}

		// Not burning: empty the fuel slot and fill the buffer so a burn cannot start (produce() needs
		// room). updateLit(false) on the next tick must clear LIT.
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, ItemStack.EMPTY);
		gen.getEnergyStorage().setAmountUntracked(gen.getEnergyStorage().getCapacity());
		drive(gen, helper, 3);
		if (helper.getLevel().getBlockState(abs).getValue(BlockStateProperties.LIT)) {
			helper.fail("generator must not be LIT once the burn ends");
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-991 - a real comparator behind the generator reads the fuel slot by the vanilla container
	 *     rule (empty 0, half a stack 8, a full stack 15), and the reading drops to 7 when the generator burns
	 *     one item of the half stack. The comparator only changes when the generator tells its neighbours, so
	 *     this also proves the notification on a slot write and on burning.
	 */
	public static void comparatorReadsFuelSlot(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		// A full buffer pauses burning (TC-GEN-001-NEG03), so the slot holds still until the buffer is emptied.
		gen.getEnergyStorage().setAmountUntracked(gen.getEnergyStorage().getCapacity());
		BlockPos comparatorPos = POS.south();
		helper.setBlock(comparatorPos.below(), Blocks.STONE.defaultBlockState());
		// A comparator reads the block its FACING points at: north of it is the generator.
		helper.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState()
				.setValue(ComparatorBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(4, () -> {
			expectComparator(helper, comparatorPos, 0, "an empty fuel slot");
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			helper.runAfterDelay(4, () -> {
				expectComparator(helper, comparatorPos, 15, "a full stack of coal");
				gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 32));
				helper.runAfterDelay(4, () -> {
					expectComparator(helper, comparatorPos, 8, "half a stack of coal");
					gen.getEnergyStorage().setAmountUntracked(0); // room again: the next tick burns one coal
					helper.runAfterDelay(6, () -> {
						int left = gen.getItem(GeneratorBlockEntity.FUEL_SLOT).getCount();
						helper.assertTrue(left == 31, "the generator must have burnt exactly one coal, has " + left);
						expectComparator(helper, comparatorPos, 7, "31 coal after one was burnt");
						helper.succeed();
					});
				});
			});
		});
	}

	private static void expectComparator(GameTestHelper helper, BlockPos comparatorPos, int expected, String what) {
		ComparatorBlockEntity comparator = helper.getBlockEntity(comparatorPos, ComparatorBlockEntity.class);
		int read = comparator.getOutputSignal();
		helper.assertTrue(read == expected, "comparator behind " + what + " must read " + expected + ", reads " + read);
	}

	/**
	 * MOD-990: the fuel span and the load are worked out only with the statistics chip, and then they follow the
	 * line's real draw. Without the chip the generator keeps nothing and the menu's snapshot carries no fuel line
	 * (the menu sends no snapshot at all then: {@code MachineMenu.broadcastChanges}). With the chip, a line drawing
	 * 2 EU/t reads "2 of 8 EU/t" and the span is every EU still to come over 2 EU/t, worked out here from the
	 * vanilla burn time of coal, not from the class under test; a line that stops drawing reads "waiting".
	 *
	 * @implements MOD-990-RUNTIME — span and load from the real draw, only with the statistics chip
	 * @covers MOD-990
	 */
	public static void fuelRuntimeFollowsTheChipAndTheDraw(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 16));
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		GeneratorMenu menu = new GeneratorMenu(1, player.getInventory(), gen,
				ContainerLevelAccess.create(helper.getLevel(), gen.getBlockPos()));
		long now = helper.getLevel().getGameTime();

		// No chip: a line draws, and nothing is measured or worked out.
		drawEachTick(gen, helper, 2L, 100);
		helper.assertTrue(!gen.hasStatsChip(), "the rig needs a generator without the statistics chip");
		helper.assertTrue(gen.fuelReadout() == null, "no chip, yet the generator worked out " + gen.fuelReadout());
		helper.assertTrue(menu.statsSnapshot(gen, now).fuel().isEmpty(), "no chip, yet the snapshot has a fuel line");

		// The chip: 2 EU/t drawn, from a window that starts when the chip went in.
		gen.setItem(gen.upgradeSlotStart() + 2, new ItemStack(ModContent.STATS_CHIP.get()));
		drawEachTick(gen, helper, 2L, 200);
		FuelRuntime.Readout fuel = menu.statsSnapshot(gen, now + 1).fuel().orElse(null);
		helper.assertTrue(fuel != null, "a chipped generator's snapshot carries no fuel line");
		int euPerTick = Math.max(1, Math.round(GeneratorConfig.fuelEuPerTick * Config.globalEuRateMultiplier));
		helper.assertTrue(fuel.euPerTick() == euPerTick, "full output " + fuel.euPerTick() + ", expected " + euPerTick);
		helper.assertTrue(fuel.drawTicks() > 0 && fuel.drawEu() == 2L * fuel.drawTicks(),
				"a 2 EU/t line reads " + fuel.drawEu() + " EU over " + fuel.drawTicks() + " ticks");
		long burnLeft = gen.getDataAccess().get(MachineChannels.PROGRESS.ordinal());
		long coalTicks = 1600L; // vanilla burn time of coal; the rig's generator has no owner, so no Steady Hands
		long fuelEu = (burnLeft + coalTicks * gen.getItem(GeneratorBlockEntity.FUEL_SLOT).getCount()) * euPerTick;
		long expected = (fuelEu + gen.getEnergyStorage().getAmount()) / 2L;
		helper.assertTrue(Math.abs(fuel.ticksLeft() - expected) <= 1L,
				"at 2 EU/t the fuel lasts " + expected + " ticks, the readout says " + fuel.ticksLeft());

		// The line stops drawing: once the window has rolled past the old load, the fuel waits.
		drawEachTick(gen, helper, 0L, DrawMeter.SAMPLE_EVERY * DrawMeter.SAMPLES + DrawMeter.SAMPLE_EVERY);
		FuelRuntime.Readout idle = gen.fuelReadout();
		helper.assertTrue(idle != null && idle.ticksLeft() == FuelRuntime.WAITING,
				"a generator nothing draws from reads " + idle + ", not waiting");
		helper.succeed();
	}

	/**
	 * MOD-992: the statistics chip counts the fuel burnt by kind, and only the chip. Without it a burnt stick
	 * leaves no tally, no save key and nothing in the snapshot. With it, one coal and two oak planks read as
	 * such, and the EU the panel shows as generated is every burn tick at the full output, worked out here from
	 * the vanilla burn times, with a stretch of full-buffer pause in the middle that must not open a gap. The
	 * tally survives a save and load, and a save from before the task loads with an empty tally.
	 *
	 * @implements MOD-992-TALLY — burnt fuel by kind, only with the statistics chip, kept across a reload
	 * @covers MOD-992
	 */
	public static void fuelBurntCountsByKindOnlyWithTheChip(GameTestHelper helper) {
		GeneratorBlockEntity gen = placeGenerator(helper);
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		GeneratorMenu menu = new GeneratorMenu(1, player.getInventory(), gen,
				ContainerLevelAccess.create(helper.getLevel(), gen.getBlockPos()));
		var registries = helper.getLevel().registryAccess();
		long now = helper.getLevel().getGameTime();
		int euPerTick = Math.max(1, Math.round(GeneratorConfig.fuelEuPerTick * Config.globalEuRateMultiplier));

		// No chip: a stick burns out (100 ticks) and nothing is counted, saved or put in the snapshot.
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.STICK));
		drawEachTick(gen, helper, euPerTick, 110);
		helper.assertTrue(!gen.hasStatsChip(), "the rig needs a generator without the statistics chip");
		helper.assertTrue(gen.getItem(GeneratorBlockEntity.FUEL_SLOT).isEmpty(), "the stick was not burnt");
		helper.assertTrue(gen.fuelBurntTally().isEmpty(), "no chip, yet the generator counted "
				+ gen.fuelBurntTally().counts());
		helper.assertTrue(!gen.saveCustomOnly(registries).contains("StatsFuelBurnt"),
				"no chip, yet the save carries a burnt-fuel key");
		helper.assertTrue(menu.statsSnapshot(gen, now).fuelBurnt().isEmpty(),
				"no chip, yet the snapshot carries burnt fuel");

		// The chip: one coal, then two oak planks; the line draws the full output, stops long enough for the
		// buffer to fill and the burn to pause, then draws again until the second plank is burning.
		gen.setItem(gen.upgradeSlotStart() + 2, new ItemStack(ModContent.STATS_CHIP.get()));
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL));
		drawEachTick(gen, helper, euPerTick, 1);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.OAK_PLANKS, 2));
		drawEachTick(gen, helper, euPerTick, 600);
		drawEachTick(gen, helper, 0L, 600);
		helper.assertTrue(gen.getEnergyStorage().getCapacity() - gen.getEnergyStorage().getAmount()
						< GeneratorBlockEntity.effectiveEuPerTick(),
				"the pause needs a buffer with no room for a burn tick, the rig has "
						+ gen.getEnergyStorage().getAmount());
		drawEachTick(gen, helper, euPerTick, 1000);
		helper.assertTrue(gen.getItem(GeneratorBlockEntity.FUEL_SLOT).isEmpty(), "the second plank was not taken");

		Map<String, Long> expected = Map.of("minecraft:coal", 1L, "minecraft:oak_planks", 2L);
		helper.assertTrue(gen.fuelBurntTally().counts().equals(expected) && gen.fuelBurntTally().other() == 0L,
				"burnt by kind " + gen.fuelBurntTally().counts() + ", expected " + expected);
		FuelTally.Top top = menu.statsSnapshot(gen, now + 1).fuelBurnt().orElse(null);
		FuelTally.Top expectedTop = new FuelTally.Top(List.of(new FuelTally.Entry("minecraft:oak_planks", 2L),
				new FuelTally.Entry("minecraft:coal", 1L)), 0L);
		helper.assertTrue(expectedTop.equals(top), "the snapshot's burnt fuel reads " + top);

		// Generated EU = every burn tick at the full output: coal 1600 and plank 300 (vanilla), the second plank
		// as far as it has burnt. The pause burnt nothing and generated nothing, so the two agree.
		long plankLeft = gen.getDataAccess().get(MachineChannels.PROGRESS.ordinal());
		long burnTicks = 1600L + 300L + (300L - plankLeft);
		long generated = gen.getEnergyStorage().getTotalEnergyGenerated();
		helper.assertTrue(generated == burnTicks * euPerTick,
				"generated " + generated + " EU, the fuel burnt " + burnTicks + " ticks at " + euPerTick + " EU/t");
		helper.assertTrue(menu.statsSnapshot(gen, now + 2).energyGenerated() == generated,
				"the snapshot's generated EU differs from the counter");

		// Save and load: the tally comes back; a save without the key (before MOD-992) starts from zero.
		CompoundTag tag = gen.saveCustomOnly(registries);
		helper.assertTrue(tag.contains("StatsFuelBurnt"), "a chipped generator that burnt fuel saves no tally");
		GeneratorBlockEntity restored = new GeneratorBlockEntity(gen.getBlockPos(),
				helper.getLevel().getBlockState(gen.getBlockPos()));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		helper.assertTrue(restored.fuelBurntTally().counts().equals(expected),
				"after a reload the tally reads " + restored.fuelBurntTally().counts());
		helper.assertTrue(restored.getEnergyStorage().getTotalEnergyGenerated() == generated,
				"after a reload the generated EU reads " + restored.getEnergyStorage().getTotalEnergyGenerated());
		CompoundTag old = tag.copy();
		old.remove("StatsFuelBurnt");
		GeneratorBlockEntity fromOld = new GeneratorBlockEntity(gen.getBlockPos(),
				helper.getLevel().getBlockState(gen.getBlockPos()));
		fromOld.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, old));
		helper.assertTrue(fromOld.fuelBurntTally().isEmpty(), "an old save loads with a tally "
				+ fromOld.fuelBurntTally().counts());
		helper.succeed();
	}

	/** Tick the generator {@code ticks} times, drawing {@code eu} out of it after each tick as a line would. */
	private static void drawEachTick(GeneratorBlockEntity gen, GameTestHelper helper, long eu, int ticks) {
		for (int i = 0; i < ticks; i++) {
			drive(gen, helper, 1);
			if (eu > 0L) {
				EnergyTransactions.get().runCommitting(txn -> gen.getEnergyStorage().extract(eu, txn));
			}
		}
	}
}
