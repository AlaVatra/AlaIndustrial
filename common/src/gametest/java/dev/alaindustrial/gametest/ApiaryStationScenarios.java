package dev.alaindustrial.gametest;

import dev.alaindustrial.block.ApiaryDroneStationBlock;
import dev.alaindustrial.block.HiveAttachmentBlock;
import dev.alaindustrial.block.entity.ApiaryDroneStationBlockEntity;
import dev.alaindustrial.block.entity.ApiaryStationStatus;
import dev.alaindustrial.block.entity.machine.DroneBay;
import dev.alaindustrial.core.apiary.ApiaryConfig;
import dev.alaindustrial.core.apiary.ApiaryRules;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.item.apiary.ApiaryMapItem;
import dev.alaindustrial.registry.ModContent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The apiary station, slice 2 (MOD-923): the pair of blocks and its ports, the drain of a full hive without touching
 * the bees, which jars are served, four drones at four hives, the EU paid only for a docking that took honey, the comb
 * chance, the stops that lose nothing, and breaking either half.
 *
 * <p>The station is driven through {@link AlaGameTestHelper#drive}, each scenario in one synchronous body, so the
 * knobs it pins go through {@link ConfigOverrides#sync()} and nothing waits on real ticks. Every scenario asserts both
 * sides of its rule, so it can fail.
 */
final class ApiaryStationScenarios {

	private ApiaryStationScenarios() {}

	public static final class Roster {
		private Roster() {}

		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ApiaryStationScenarios::pairAndPorts, "apiary_station_pair_and_ports").ticks(20),
				RosterEntry.of(ApiaryStationScenarios::drainsAFullHiveLeavingTheBees, "apiary_station_drains_full_hive")
						.ticks(20),
				RosterEntry.of(ApiaryStationScenarios::servesOnlyJarsItMay, "apiary_station_serves_only_jars_it_may")
						.ticks(20),
				RosterEntry.of(ApiaryStationScenarios::fourDronesFourHivesOneDroneAHive,
						"apiary_station_four_drones_four_hives").ticks(20),
				RosterEntry.of(ApiaryStationScenarios::paysOnlyForADockingThatTookHoney,
						"apiary_station_pays_only_for_honey").ticks(20),
				RosterEntry.of(ApiaryStationScenarios::combChanceZeroAndOne, "apiary_station_comb_chance").ticks(20),
				RosterEntry.of(ApiaryStationScenarios::fullTankOrCombsStopTheFlights, "apiary_station_full_stops")
						.ticks(20),
				RosterEntry.of(ApiaryStationScenarios::breakingEitherHalf, "apiary_station_breaking_halves").ticks(20));
	}

	/** The station's casing; its hive stands on top. */
	private static final BlockPos STATION = new BlockPos(3, 1, 3);
	/** A hive four blocks east of the station, its entrance north, the jar on its top. */
	private static final BlockPos HIVE = new BlockPos(7, 1, 3);

	/** Ticks enough for every drone sent within a few blocks to fly out, dock, fly home and pour. */
	private static int roundTicks() {
		return 2 * ApiaryRules.legTicks(8.0) + ApiaryConfig.apiaryDockTicks + ApiaryConfig.apiaryPourTicks + 12;
	}

	private static ApiaryDroneStationBlockEntity station(GameTestHelper helper, int drones, boolean hive) {
		helper.setBlock(STATION, ModContent.APIARY_DRONE_STATION.get().defaultBlockState()
				.setValue(ApiaryDroneStationBlock.FACING, Direction.NORTH));
		if (hive) {
			helper.setBlock(STATION.above(), ModContent.APIARY_DRONE_HIVE.get());
		}
		ApiaryDroneStationBlockEntity be = helper.getBlockEntity(STATION, ApiaryDroneStationBlockEntity.class);
		for (int i = 0; i < drones; i++) {
			be.setItem(ApiaryDroneStationBlockEntity.BAY_SLOT_START + i, new ItemStack(ModContent.APIARY_DRONE.get()));
		}
		be.getEnergyStorage().setAmountUntracked(ApiaryConfig.apiaryStationBuffer);
		return be;
	}

	/** A hive at {@code hive} with {@code honey}, its entrance north, a jar on its top; returns the jar's cell. */
	private static BlockPos hiveWithJar(GameTestHelper helper, BlockPos hive, int honey) {
		helper.setBlock(hive, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.FACING, Direction.NORTH)
				.setValue(BeehiveBlock.HONEY_LEVEL, honey));
		helper.setBlock(hive.above(), ModContent.HIVE_ATTACHMENT.get().defaultBlockState()
				.setValue(HiveAttachmentBlock.FACING, Direction.UP).setValue(HiveAttachmentBlock.HONEY_LEVEL, honey));
		return hive.above();
	}

	/** Puts a map with {@code jars} (relative cells) into the station. */
	private static void map(GameTestHelper helper, ApiaryDroneStationBlockEntity be, BlockPos... jars) {
		ItemStack map = new ItemStack(ModContent.APIARY_MAP.get());
		for (BlockPos jar : jars) {
			ApiaryMapItem.edit(map, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(jar)), false);
		}
		be.setItem(ApiaryDroneStationBlockEntity.MAP_SLOT, map);
	}

	private static int honeyLevel(GameTestHelper helper, BlockPos hive) {
		return helper.getBlockState(hive).getValue(BeehiveBlock.HONEY_LEVEL);
	}

	private static int busyBays(ApiaryDroneStationBlockEntity be) {
		int busy = 0;
		for (int i = 0; i < ApiaryRules.BAYS; i++) {
			busy += be.bay(i).busy() ? 1 : 0;
		}
		return busy;
	}

	private static long euOf(ApiaryDroneStationBlockEntity be) {
		return be.getEnergyStorage().getAmount();
	}

	/** The price of a flight from the station to {@code jar}, as the station reckons it. */
	private static long price(GameTestHelper helper, BlockPos jar) {
		double distance = Math.sqrt(helper.absolutePos(jar).distSqr(helper.absolutePos(STATION)));
		return ApiaryRules.launchCost(ApiaryConfig.apiaryDroneLaunchEu, ApiaryConfig.apiaryDroneEuPerBlock, distance);
	}

	/**
	 * The casing alone says «no hive» and sends nothing; with the hive on top it works. Every face of the casing takes
	 * EU, gives honey and gives combs to an item pipe; the hive has no block entity, so nothing connects to it.
	 */
	public static void pairAndPorts(GameTestHelper helper) {
		ApiaryDroneStationBlockEntity be = station(helper, 1, false);
		BlockPos jar = hiveWithJar(helper, HIVE, 5);
		map(helper, be, jar);
		AlaGameTestHelper.drive(be, helper, 5);
		if (be.status() != ApiaryStationStatus.NO_HIVE || busyBays(be) != 0) {
			helper.fail("the casing without its hive said " + be.status() + " with " + busyBays(be) + " drones out");
		}
		helper.setBlock(STATION.above(), ModContent.APIARY_DRONE_HIVE.get());
		be.wake();
		AlaGameTestHelper.drive(be, helper, 2);
		if (busyBays(be) != 1) {
			helper.fail("with the hive on top no drone flew to the full hive (" + be.status() + ")");
		}
		BlockState casing = helper.getBlockState(STATION);
		for (Direction face : Direction.values()) {
			if (be.energyRoleForFace(face) != EnergyRole.IN
					|| !((ApiaryDroneStationBlock) casing.getBlock()).isCableConnectable(casing, face)) {
				helper.fail("the casing's " + face + " face takes no cable");
			}
			if (be.fluidPort(face) == null || !be.fluidPort(face).supportsExtraction()) {
				helper.fail("the casing's " + face + " face gives no honey");
			}
			boolean combs = false;
			for (int slot : be.getSlotsForFace(face)) {
				combs |= slot == ApiaryDroneStationBlockEntity.COMB_SLOT;
			}
			if (!combs || !be.canTakeItemThroughFace(ApiaryDroneStationBlockEntity.COMB_SLOT,
					new ItemStack(Items.HONEYCOMB), face)) {
				helper.fail("an item pipe cannot take combs through the casing's " + face + " face");
			}
			if (be.canPlaceItemThroughFace(ApiaryDroneStationBlockEntity.BAY_SLOT_START,
					new ItemStack(ModContent.APIARY_DRONE.get()), face)) {
				helper.fail("a pipe may put a drone in through the " + face + " face");
			}
		}
		if (helper.getLevel().getBlockEntity(helper.absolutePos(STATION.above())) != null) {
			helper.fail("the hive half has a block entity, so something could connect to it");
		}
		helper.succeed();
	}

	/**
	 * A full hive with three bees inside is drained to 0 by one drone: the bees stay three, no bee comes out, the tank
	 * gets one hive's honey and the station pays exactly that flight's price.
	 */
	public static void drainsAFullHiveLeavingTheBees(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("apiaryCombChance", 0.0);
			ApiaryDroneStationBlockEntity be = station(helper, 1, true);
			BlockPos jar = hiveWithJar(helper, HIVE, 5);
			BeehiveBlockEntity hive = helper.getBlockEntity(HIVE, BeehiveBlockEntity.class);
			for (int i = 0; i < 3; i++) {
				hive.storeBee(BeehiveBlockEntity.Occupant.create(0));
			}
			map(helper, be, jar);
			long before = euOf(be);
			AlaGameTestHelper.drive(be, helper, roundTicks());
			if (honeyLevel(helper, HIVE) != 0) {
				helper.fail("the hive still holds honey level " + honeyLevel(helper, HIVE));
			}
			if (hive.getOccupantCount() != 3) {
				helper.fail("the hive holds " + hive.getOccupantCount() + " bees, not the three it had");
			}
			AABB around = new AABB(helper.absolutePos(HIVE)).inflate(8.0);
			if (!helper.getLevel().getEntitiesOfClass(Bee.class, around).isEmpty()) {
				helper.fail("a bee came out of the hive the drone drained");
			}
			if (be.honeyTank.amount != ApiaryConfig.apiaryHoneyPerHive) {
				helper.fail("the tank holds " + be.honeyTank.amount + " mB, expected "
						+ ApiaryConfig.apiaryHoneyPerHive);
			}
			if (before - euOf(be) != price(helper, jar)) {
				helper.fail("the round cost " + (before - euOf(be)) + " EU, expected " + price(helper, jar));
			}
			if (busyBays(be) != 0 || !helper.getBlockState(jar).is(ModContent.HIVE_ATTACHMENT.get())
					|| helper.getBlockState(jar).getValue(HiveAttachmentBlock.DRAINING)) {
				helper.fail("after the round the drone is not home or the jar still drains");
			}
			helper.succeed();
		}
	}

	/**
	 * Only a jar on the map, in this dimension, within range and in a loaded chunk is served: the same full hive is
	 * left alone when its jar is too far, unloaded, in another dimension or crossed out, and drained when it is not.
	 */
	public static void servesOnlyJarsItMay(GameTestHelper helper) {
		ApiaryDroneStationBlockEntity be = station(helper, 1, true);
		BlockPos jar = hiveWithJar(helper, HIVE, 5);
		Level level = helper.getLevel();
		// too far: a map row past the range (nothing needs to stand there; the distance refuses it first)
		BlockPos far = STATION.east(ApiaryConfig.apiaryStationRange + 2);
		map(helper, be, far);
		AlaGameTestHelper.drive(be, helper, 3);
		expectNoFlight(helper, be, "a jar past the range");
		// another dimension: the jar's own coordinates, written in the Nether
		ItemStack nether = new ItemStack(ModContent.APIARY_MAP.get());
		ApiaryMapItem.edit(nether, GlobalPos.of(Level.NETHER, helper.absolutePos(jar)), false);
		be.setItem(ApiaryDroneStationBlockEntity.MAP_SLOT, nether);
		be.wake();
		AlaGameTestHelper.drive(be, helper, 3);
		expectNoFlight(helper, be, "a jar written in another dimension");
		// crossed out: the map lists nothing
		ItemStack crossed = new ItemStack(ModContent.APIARY_MAP.get());
		GlobalPos here = GlobalPos.of(level.dimension(), helper.absolutePos(jar));
		ApiaryMapItem.edit(crossed, here, false);
		ApiaryMapItem.edit(crossed, here, true);
		be.setItem(ApiaryDroneStationBlockEntity.MAP_SLOT, crossed);
		be.wake();
		AlaGameTestHelper.drive(be, helper, 3);
		expectNoFlight(helper, be, "a crossed-out jar");
		// unloaded: the station asks its probe and is told the jar's chunk is not loaded
		map(helper, be, jar);
		BlockPos absJar = helper.absolutePos(jar);
		be.useLoadedProbe(pos -> !pos.equals(absJar));
		be.wake();
		AlaGameTestHelper.drive(be, helper, 3);
		expectNoFlight(helper, be, "a jar in an unloaded chunk");
		// the same jar, loaded: a drone flies
		be.useLoadedProbe(null);
		be.wake();
		AlaGameTestHelper.drive(be, helper, 3);
		if (busyBays(be) != 1) {
			helper.fail("the served jar got no drone (" + be.status() + ")");
		}
		helper.succeed();
	}

	private static void expectNoFlight(GameTestHelper helper, ApiaryDroneStationBlockEntity be, String what) {
		if (busyBays(be) != 0) {
			helper.fail("a drone flew to " + what);
		}
		if (honeyLevel(helper, HIVE) != 5 || euOf(be) != ApiaryConfig.apiaryStationBuffer) {
			helper.fail(what + " was drained or cost EU");
		}
	}

	/**
	 * Four full hives and four drones: four drones fly at once, each to its own hive. With three drones only three fly;
	 * one full hive gets one drone however many are free.
	 */
	public static void fourDronesFourHivesOneDroneAHive(GameTestHelper helper) {
		BlockPos[] hives = {new BlockPos(0, 1, 0), new BlockPos(6, 1, 0), new BlockPos(0, 1, 6), new BlockPos(6, 1, 6)};
		BlockPos[] jars = new BlockPos[hives.length];
		ApiaryDroneStationBlockEntity be = station(helper, 4, true);
		for (int i = 0; i < hives.length; i++) {
			jars[i] = hiveWithJar(helper, hives[i], 5);
		}
		map(helper, be, jars);
		AlaGameTestHelper.drive(be, helper, 2);
		Set<BlockPos> targets = new HashSet<>();
		for (int i = 0; i < ApiaryRules.BAYS; i++) {
			DroneBay bay = be.bay(i);
			if (bay.busy()) {
				targets.add(bay.jar());
			}
		}
		if (busyBays(be) != 4 || targets.size() != 4) {
			helper.fail(busyBays(be) + " drones flew to " + targets.size() + " different hives, expected 4 and 4");
		}
		// three drones: the empty bay never flies
		be.setItem(ApiaryDroneStationBlockEntity.BAY_SLOT_START + 2, ItemStack.EMPTY);
		AlaGameTestHelper.drive(be, helper, 1);
		if (be.bay(2).busy()) {
			helper.fail("the bay whose drone was taken out still flies");
		}
		// The hive the taken drone was flying to is still full, so the first drone home serves it in a second round.
		AlaGameTestHelper.drive(be, helper, 2 * roundTicks());
		if (busyBays(be) != 0) {
			helper.fail(busyBays(be) + " drones still out after two rounds");
		}
		for (int i = 0; i < hives.length; i++) {
			hiveWithJar(helper, hives[i], 5);
		}
		be.wake();
		AlaGameTestHelper.drive(be, helper, 2);
		if (busyBays(be) != 3 || be.bay(2).busy()) {
			helper.fail("with three drones " + busyBays(be) + " flew, bay 2 busy: " + be.bay(2).busy());
		}
		// Four full hives, three drones: the fourth hive is served by the first drone home, in a second round.
		AlaGameTestHelper.drive(be, helper, 2 * roundTicks());
		if (busyBays(be) != 0) {
			helper.fail(busyBays(be) + " drones still out after the three-drone rounds");
		}
		// one full hive, three free drones: one flies, and the next looks send no second one
		for (int i = 1; i < hives.length; i++) {
			hiveWithJar(helper, hives[i], 2);
		}
		hiveWithJar(helper, hives[0], 5);
		be.wake();
		AlaGameTestHelper.drive(be, helper, ApiaryConfig.apiaryScanIntervalTicks * 3);
		if (busyBays(be) != 1) {
			helper.fail(busyBays(be) + " drones went to the one full hive");
		}
		helper.succeed();
	}

	/**
	 * With no full hive the station spends nothing however long it runs; a drone that finds its hive no longer full
	 * when it docks comes home empty and costs nothing; one that takes honey costs its flight.
	 */
	public static void paysOnlyForADockingThatTookHoney(GameTestHelper helper) {
		ApiaryDroneStationBlockEntity be = station(helper, 1, true);
		BlockPos jar = hiveWithJar(helper, HIVE, 4);
		map(helper, be, jar);
		AlaGameTestHelper.drive(be, helper, 200);
		if (euOf(be) != ApiaryConfig.apiaryStationBuffer || busyBays(be) != 0) {
			helper.fail("without a full hive the station spent " + (ApiaryConfig.apiaryStationBuffer - euOf(be))
					+ " EU");
		}
		hiveWithJar(helper, HIVE, 5);
		be.wake();
		AlaGameTestHelper.drive(be, helper, 2);
		// another station (or the player with shears) takes the honey while the drone is on its way
		helper.setBlock(HIVE, helper.getBlockState(HIVE).setValue(BeehiveBlock.HONEY_LEVEL, 0));
		AlaGameTestHelper.drive(be, helper, roundTicks());
		if (euOf(be) != ApiaryConfig.apiaryStationBuffer || be.honeyTank.amount != 0) {
			helper.fail("a docking at an emptied hive cost " + (ApiaryConfig.apiaryStationBuffer - euOf(be))
					+ " EU and gave " + be.honeyTank.amount + " mB");
		}
		hiveWithJar(helper, HIVE, 5);
		be.wake();
		AlaGameTestHelper.drive(be, helper, roundTicks());
		if (ApiaryConfig.apiaryStationBuffer - euOf(be) != price(helper, jar)
				|| be.honeyTank.amount != ApiaryConfig.apiaryHoneyPerHive) {
			helper.fail("a docking that took honey cost " + (ApiaryConfig.apiaryStationBuffer - euOf(be)) + " EU");
		}
		helper.succeed();
	}

	/** At chance 0 no comb ever comes; at chance 1 every full hive gives one. */
	public static void combChanceZeroAndOne(GameTestHelper helper) {
		ApiaryDroneStationBlockEntity be = station(helper, 1, true);
		BlockPos jar = hiveWithJar(helper, HIVE, 5);
		map(helper, be, jar);
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("apiaryCombChance", 0.0);
			AlaGameTestHelper.drive(be, helper, roundTicks());
			if (!be.getItem(ApiaryDroneStationBlockEntity.COMB_SLOT).isEmpty()) {
				helper.fail("a comb came at chance 0");
			}
			o.set("apiaryCombChance", 1.0);
			hiveWithJar(helper, HIVE, 5);
			be.wake();
			AlaGameTestHelper.drive(be, helper, roundTicks());
			ItemStack combs = be.getItem(ApiaryDroneStationBlockEntity.COMB_SLOT);
			if (!combs.is(Items.HONEYCOMB) || combs.getCount() != 1) {
				helper.fail("at chance 1 the comb slot holds " + combs);
			}
		}
		helper.succeed();
	}

	/**
	 * A tank with no room for one more hive, or a comb slot with no room for one more comb, sends no drone: the hive
	 * keeps its honey, the tank and the combs keep theirs, the status says why. Room again, and the drone flies.
	 */
	public static void fullTankOrCombsStopTheFlights(GameTestHelper helper) {
		ApiaryDroneStationBlockEntity be = station(helper, 1, true);
		BlockPos jar = hiveWithJar(helper, HIVE, 5);
		map(helper, be, jar);
		be.honeyTank.fluid = FluidHolder.of(ModContent.HONEY.get());
		be.honeyTank.amount = ApiaryDroneStationBlockEntity.TANK_CAPACITY - ApiaryConfig.apiaryHoneyPerHive + 1;
		AlaGameTestHelper.drive(be, helper, 3);
		if (busyBays(be) != 0 || be.status() != ApiaryStationStatus.TANK_FULL || honeyLevel(helper, HIVE) != 5) {
			helper.fail("a nearly full tank still sent a drone (" + be.status() + ")");
		}
		be.honeyTank.amount = 0;
		be.setItem(ApiaryDroneStationBlockEntity.COMB_SLOT, new ItemStack(Items.HONEYCOMB, 64));
		be.wake();
		AlaGameTestHelper.drive(be, helper, 3);
		if (busyBays(be) != 0 || be.status() != ApiaryStationStatus.OUTPUT_FULL
				|| be.getItem(ApiaryDroneStationBlockEntity.COMB_SLOT).getCount() != 64) {
			helper.fail("a full comb slot still sent a drone (" + be.status() + ")");
		}
		be.setItem(ApiaryDroneStationBlockEntity.COMB_SLOT, ItemStack.EMPTY);
		be.wake();
		AlaGameTestHelper.drive(be, helper, 3);
		if (busyBays(be) != 1) {
			helper.fail("with room again no drone flew (" + be.status() + ")");
		}
		helper.succeed();
	}

	/**
	 * Taking the hive off the casing brings a drone home at once with its honey poured into the tank, and the station
	 * stands with «no hive», slots intact; breaking the casing drops its slots and leaves the hive.
	 */
	public static void breakingEitherHalf(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("apiaryCombChance", 0.0);
			ApiaryDroneStationBlockEntity be = station(helper, 2, true);
			BlockPos jar = hiveWithJar(helper, HIVE, 5);
			map(helper, be, jar);
			// out, docked and on the way home with the honey
			int toReturn = ApiaryRules.legTicks(4.2) + 2 + ApiaryConfig.apiaryDockTicks + 10;
			AlaGameTestHelper.drive(be, helper, toReturn);
			DroneBay bay = be.bay(0).busy() ? be.bay(0) : be.bay(1);
			if (bay.phase() != DroneBay.Phase.RETURNING || bay.honey() <= 0) {
				helper.fail("the drone is not flying home with honey but " + bay.phase());
			}
			helper.setBlock(STATION.above(), Blocks.AIR);
			AlaGameTestHelper.drive(be, helper, 1);
			if (busyBays(be) != 0 || be.status() != ApiaryStationStatus.NO_HIVE
					|| be.honeyTank.amount != ApiaryConfig.apiaryHoneyPerHive) {
				helper.fail("without the hive: " + busyBays(be) + " drones out, " + be.status() + ", "
						+ be.honeyTank.amount + " mB");
			}
			if (be.getItem(ApiaryDroneStationBlockEntity.MAP_SLOT).isEmpty()
					|| be.getItem(ApiaryDroneStationBlockEntity.BAY_SLOT_START).isEmpty()) {
				helper.fail("taking the hive off lost the map or a drone");
			}
			helper.setBlock(STATION.above(), ModContent.APIARY_DRONE_HIVE.get());
			helper.getLevel().destroyBlock(helper.absolutePos(STATION), true);
			helper.assertItemEntityPresent(ModContent.APIARY_DRONE.get(), STATION, 2.0);
			helper.assertItemEntityPresent(ModContent.APIARY_MAP.get(), STATION, 2.0);
			if (!helper.getBlockState(STATION.above()).is(ModContent.APIARY_DRONE_HIVE.get())) {
				helper.fail("breaking the casing took the hive with it");
			}
			helper.killAllEntities();
		}
		helper.succeed();
	}
}
