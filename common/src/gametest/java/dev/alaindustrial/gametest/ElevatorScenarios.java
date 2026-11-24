package dev.alaindustrial.gametest;

import dev.alaindustrial.block.ElevatorShaftBlock;
import dev.alaindustrial.block.ElevatorStationBlock;
import dev.alaindustrial.block.ElevatorWinchBlock;
import dev.alaindustrial.block.entity.ElevatorWinchBlockEntity;
import dev.alaindustrial.block.entity.elevator.ElevatorCarry;
import dev.alaindustrial.block.entity.elevator.ElevatorRescue;
import dev.alaindustrial.block.entity.elevator.ElevatorShaft;
import dev.alaindustrial.core.elevator.ElevatorFare;
import dev.alaindustrial.core.elevator.ElevatorTrip;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Loader-neutral gametests of the elevator (MOD-769, suite TC-ELV-001), the glass shaft of variant A: the cabin
 * carries what stands in it between the stations, the trip is paid up front or refused, the station doors follow the
 * cabin, a broken shaft sets the catches, a gap in the tube is found and named, and a reload or a hanging rider does
 * not break the shaft.
 *
 * <p><b>The rigs.</b> A winch at (2, 7, 3) closes a tube at x 2, z 3, inside the 8-block rig. The three-floor rig is
 * three stations, at y 1..2, 3..4 and 5..6, so the floors are at y 1, 3 and 5; the frame rig is two stations at
 * y 1..2 and 5..6 with shaft frames at y 3 and 4 between them. The stations face north. The winch is real and ticks
 * with the world: a trip is measured on the game clock, which a direct {@code serverTick} loop would not move.
 */
public final class ElevatorScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ElevatorScenarios::riderIsCarriedUpAndPaysTheFare, "elevator_rider_carried_up")
						.ticks(80),
				RosterEntry.of(ElevatorScenarios::descentCarriesWithoutFallDamage, "elevator_descent_no_fall_damage")
						.ticks(140),
				RosterEntry.of(ElevatorScenarios::refusesWithoutEnoughEnergy, "elevator_refuses_without_energy")
						.ticks(20),
				RosterEntry.of(ElevatorScenarios::doorsOpenOnlyAtTheCabinsFloor, "elevator_doors_follow_the_cabin")
						.ticks(80),
				RosterEntry.of(ElevatorScenarios::brokenShaftSetsTheCatches, "elevator_broken_shaft_sets_catches")
						.ticks(120),
				RosterEntry.of(ElevatorScenarios::gapInTheShaftIsNamed, "elevator_gap_in_the_shaft")
						.ticks(80),
				RosterEntry.of(ElevatorScenarios::assemblyIsReadByTheWinch, "elevator_assembly_read_by_winch")
						.ticks(120),
				RosterEntry.of(ElevatorScenarios::framesJoinAndStationIsOneBlock, "elevator_frames_join_station_whole")
						.ticks(20),
				RosterEntry.of(ElevatorScenarios::tripSurvivesASaveAndLoad, "elevator_trip_survives_reload")
						.ticks(40),
				RosterEntry.of(ElevatorScenarios::hangingEntityIsSetOnTheCabin, "elevator_rescue_sets_down")
						.ticks(20));

		private Roster() {}
	}

	private ElevatorScenarios() {
	}

	private static final BlockPos WINCH = new BlockPos(2, 7, 3);
	private static final int[] FLOORS = {1, 3, 5};
	/** The frame rig: stations at these floors, frames in the two cells between them. */
	private static final int[] FRAME_RIG_FLOORS = {1, 5};
	/** A full buffer: more than any trip of the rig costs. */
	private static final long FULL = 10_000L;
	/** Ticks after building before the first call: the winch has read its shaft and the entities are seen. */
	private static final int SETTLE = 3;
	/** A standing winch reads its shaft every two seconds: wait one reading and a little. */
	private static final int READING = 45;

	private static BlockPos cell(int y) {
		return new BlockPos(WINCH.getX(), y, WINCH.getZ());
	}

	private static void station(GameTestHelper helper, int floor) {
		BlockState lower = ModContent.ELEVATOR_STATION.get().defaultBlockState()
				.setValue(ElevatorStationBlock.FACING, Direction.NORTH);
		helper.setBlock(cell(floor), lower);
		helper.setBlock(cell(floor + 1), lower.setValue(ElevatorStationBlock.HALF, DoubleBlockHalf.UPPER));
	}

	private static void frame(GameTestHelper helper, int y) {
		helper.setBlock(cell(y), ModContent.ELEVATOR_SHAFT.get().defaultBlockState()
				.setValue(ElevatorShaftBlock.UP, true).setValue(ElevatorShaftBlock.DOWN, true));
	}

	private static ElevatorWinchBlockEntity winch(GameTestHelper helper, long charge) {
		helper.setBlock(WINCH, ModContent.ELEVATOR_WINCH.get().defaultBlockState()
				.setValue(ElevatorWinchBlock.FACING, Direction.NORTH));
		ElevatorWinchBlockEntity winch = helper.getBlockEntity(WINCH, ElevatorWinchBlockEntity.class);
		winch.getEnergyStorage().setAmountUntracked(charge);
		return winch;
	}

	/** The three-floor rig: three stations stacked under the winch. */
	private static ElevatorWinchBlockEntity build(GameTestHelper helper, long charge) {
		for (int floor : FLOORS) {
			station(helper, floor);
		}
		return winch(helper, charge);
	}

	/** The frame rig: two stations with two shaft frames between them. */
	private static ElevatorWinchBlockEntity buildWithFrames(GameTestHelper helper) {
		for (int floor : FRAME_RIG_FLOORS) {
			station(helper, floor);
		}
		frame(helper, 3);
		frame(helper, 4);
		return winch(helper, FULL);
	}

	private static int abs(GameTestHelper helper, int relativeY) {
		return helper.absolutePos(new BlockPos(0, relativeY, 0)).getY();
	}

	private static ArmorStand standIn(GameTestHelper helper, int floorY) {
		return helper.spawn(EntityTypes.ARMOR_STAND, Vec3.atBottomCenterOf(cell(floorY)));
	}

	private static String key(Component notice) {
		return notice != null && notice.getContents() instanceof TranslatableContents t ? t.getKey() : "<none>";
	}

	private static Object arg(Component notice, int index) {
		return ((TranslatableContents) notice.getContents()).getArgs()[index];
	}

	private static boolean assembled(GameTestHelper helper) {
		return helper.getBlockState(WINCH).getValue(ElevatorWinchBlock.ASSEMBLED);
	}

	/**
	 * @implements TC-ELV-001-FUN01 — an armor stand in the cabin rides from floor 1 to floor 5: every tick of the
	 *     ride it stands on the cabin's floor, it ends on the floor of floor 5 with no fall distance, and the trip
	 *     cost the fare of four blocks up with one passenger, taken when the cabin set off.
	 */
	public static void riderIsCarriedUpAndPaysTheFare(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = build(helper, FULL);
		ArmorStand stand = standIn(helper, FLOORS[0]);
		int top = abs(helper, FLOORS[2]);
		long fare = ElevatorFare.fare(ElevatorWinchBlockEntity.tariff(), 4, true, 1);
		helper.runAfterDelay(SETTLE, () -> {
			helper.assertTrue(winch.standsAt(abs(helper, FLOORS[0])), "a new winch puts the cabin on its lowest floor");
			winch.request(top, null);
			helper.assertTrue(winch.trip() != null, "a paid call sets the cabin off: " + key(winch.lastNotice()));
			helper.assertTrue(winch.getEnergyStorage().getAmount() == FULL - fare,
					"the whole trip is paid at the start: " + fare + " EU, left "
							+ winch.getEnergyStorage().getAmount());
		});
		helper.onEachTick(() -> {
			ElevatorTrip trip = winch.trip();
			if (trip != null) {
				double floor = winch.cabinHeight(helper.getLevel().getGameTime());
				double off = stand.getY() - (floor + ElevatorCarry.FLOOR_TOP);
				helper.assertTrue(Math.abs(off) < 0.35D, "the rider must stand on the moving floor, off by " + off);
			}
		});
		helper.runAfterDelay(SETTLE + 45, () -> {
			helper.assertTrue(winch.trip() == null && winch.standsAt(top),
					"the cabin stands at floor 3 (y " + top + ")");
			double off = stand.getY() - (top + ElevatorCarry.FLOOR_TOP);
			helper.assertTrue(Math.abs(off) < 0.05D, "the rider arrived on the top floor, off by " + off);
			helper.assertTrue(stand.fallDistance == 0.0D, "no fall distance after the ride: " + stand.fallDistance);
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN02 — down from floor 5 to floor 1 the rider keeps standing on the floor: no fall
	 *     distance builds up and it takes no damage on arrival; the descent costs the launch and the brake.
	 */
	public static void descentCarriesWithoutFallDamage(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = build(helper, FULL);
		int top = abs(helper, FLOORS[2]);
		int bottom = abs(helper, FLOORS[0]);
		ArmorStand[] stand = new ArmorStand[1];
		float[] health = new float[1];
		long[] before = new long[1];
		helper.runAfterDelay(SETTLE, () -> winch.request(top, null));
		helper.runAfterDelay(SETTLE + 45, () -> {
			helper.assertTrue(winch.standsAt(top), "the empty cabin went up first");
			stand[0] = standIn(helper, FLOORS[2]);
			health[0] = stand[0].getHealth();
		});
		helper.runAfterDelay(SETTLE + 50, () -> {
			before[0] = winch.getEnergyStorage().getAmount();
			winch.request(bottom, null);
			long paid = before[0] - winch.getEnergyStorage().getAmount();
			long fare = ElevatorFare.fare(ElevatorWinchBlockEntity.tariff(), 4, false, 1);
			helper.assertTrue(paid == fare, "the descent costs the launch and the brake: " + fare + ", paid " + paid);
		});
		helper.onEachTick(() -> {
			if (stand[0] != null && winch.trip() != null) {
				helper.assertTrue(stand[0].fallDistance < 0.5D, "fall distance builds up: " + stand[0].fallDistance);
			}
		});
		helper.runAfterDelay(SETTLE + 100, () -> {
			helper.assertTrue(winch.standsAt(bottom), "the cabin came down to floor 1");
			double off = stand[0].getY() - (bottom + ElevatorCarry.FLOOR_TOP);
			helper.assertTrue(Math.abs(off) < 0.05D, "the rider came down with the cabin, off by " + off);
			helper.assertTrue(stand[0].getHealth() == health[0], "the ride down must not hurt");
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN03 — with less EU than the trip costs the call is refused: the cabin stays, the
	 *     buffer is untouched and the notice names the price and what the winch holds.
	 */
	public static void refusesWithoutEnoughEnergy(GameTestHelper helper) {
		long fare = ElevatorFare.fare(ElevatorWinchBlockEntity.tariff(), 4, true, 0);
		long charge = fare - 1;
		ElevatorWinchBlockEntity winch = build(helper, charge);
		helper.runAfterDelay(SETTLE, () -> {
			winch.request(abs(helper, FLOORS[2]), null);
			helper.assertTrue(winch.trip() == null, "a refused call leaves the cabin standing");
			helper.assertTrue(winch.getEnergyStorage().getAmount() == charge, "a refused call takes nothing");
			helper.assertTrue("message.alaindustrial.elevator.no_energy".equals(key(winch.lastNotice())),
					"the refusal says why: " + key(winch.lastNotice()));
			helper.assertTrue(Long.valueOf(fare).equals(arg(winch.lastNotice(), 0))
					&& Long.valueOf(charge).equals(arg(winch.lastNotice(), 1)),
					"the refusal names the price " + fare + " and the charge " + charge);
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN04 — the station of the cabin's floor is open (green lamp, the doorway free to walk
	 *     through) and every other station shut (red, the front a wall); on the way every station is shut; on arrival
	 *     the station of the new floor opens and the old one stays shut.
	 */
	public static void doorsOpenOnlyAtTheCabinsFloor(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = build(helper, FULL);
		int top = abs(helper, FLOORS[2]);
		helper.runAfterDelay(SETTLE, () -> {
			assertStations(helper, FLOORS[0]);
			winch.request(top, null);
		});
		helper.runAfterDelay(SETTLE + 2, () -> assertStations(helper, Integer.MIN_VALUE));
		helper.runAfterDelay(SETTLE + 45, () -> {
			assertStations(helper, FLOORS[2]);
			helper.succeed();
		});
	}

	/** Every station of the three-floor rig: open at {@code openFloor} (relative), shut elsewhere. */
	private static void assertStations(GameTestHelper helper, int openFloor) {
		for (int floor : FLOORS) {
			boolean open = floor == openFloor;
			for (int up = 0; up < 2; up++) {
				BlockState state = helper.getBlockState(cell(floor + up));
				helper.assertTrue(state.getValue(ElevatorStationBlock.OPEN) == open,
						"the station at floor y " + floor + "+" + up + " should be " + (open ? "open" : "shut"));
			}
			// The doorway: the middle of the front edge collides while shut and is free while open (over the floor
			// plate an open station lends the standing cabin).
			BlockPos lower = helper.absolutePos(cell(floor));
			BlockState state = helper.getLevel().getBlockState(lower);
			boolean blocks = !state.getCollisionShape(helper.getLevel(), lower, CollisionContext.empty())
					.toAabbs().stream().noneMatch(box -> box.minZ < 0.1D && box.minX <= 0.5D && box.maxX >= 0.5D
							&& box.maxY > ElevatorCarry.FLOOR_TOP + 1.0E-6D);
			helper.assertTrue(blocks != open, "the doorway at floor y " + floor + " should be "
					+ (open ? "free" : "walled"));
		}
	}

	/**
	 * @implements TC-ELV-001-FUN05 — the station of floor 1 broken under the rising cabin cuts the tube: the catches
	 *     lower the cabin, slowly and for free, to the nearest floor below whose station and tube are whole (floor 2,
	 *     at y 3), not the one it was going to.
	 */
	public static void brokenShaftSetsTheCatches(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = build(helper, FULL);
		int middle = abs(helper, FLOORS[1]);
		int top = abs(helper, FLOORS[2]);
		boolean[] broken = new boolean[1];
		helper.runAfterDelay(SETTLE, () -> winch.request(top, null));
		helper.onEachTick(() -> {
			if (!broken[0] && winch.trip() != null
					&& winch.cabinHeight(helper.getLevel().getGameTime()) > middle + 0.6D) {
				broken[0] = true;
				helper.setBlock(cell(FLOORS[0] + 1), Blocks.AIR.defaultBlockState());
			}
		});
		helper.runAfterDelay(SETTLE + 90, () -> {
			helper.assertTrue(broken[0], "the rig broke the station mid-trip");
			helper.assertTrue(helper.getBlockState(cell(FLOORS[0])).isAir(),
					"the station's lower half went with its upper half");
			helper.assertTrue(winch.standsAt(middle), "the catches lowered the cabin to floor 2 (y " + middle
					+ "), it stands at " + winch.cabinHeight(helper.getLevel().getGameTime()));
			helper.assertTrue("message.alaindustrial.elevator.caught".equals(key(winch.lastNotice())),
					"the riders are told the catches engaged: " + key(winch.lastNotice()));
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN06 — a frame taken out of the tube between two floors is a gap: the winch reads the
	 *     shaft as broken at that height, its lamp turns red, a call is refused naming the height, and the frame put
	 *     back makes the shaft whole again.
	 */
	public static void gapInTheShaftIsNamed(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = buildWithFrames(helper);
		int gap = abs(helper, 3);
		helper.runAfterDelay(SETTLE, () -> {
			helper.assertTrue(winch.shaft().valid() && assembled(helper), "the frame rig is a whole shaft");
			helper.setBlock(cell(3), Blocks.AIR.defaultBlockState());
			winch.request(abs(helper, FRAME_RIG_FLOORS[1]), null);
			helper.assertTrue(winch.trip() == null, "a call over the gap is refused");
			helper.assertTrue("message.alaindustrial.elevator.problem.gap".equals(key(winch.lastNotice()))
					&& Integer.valueOf(gap).equals(arg(winch.lastNotice(), 0)),
					"the refusal names the gap at y " + gap + ": " + key(winch.lastNotice()));
		});
		helper.runAfterDelay(SETTLE + 4, () -> {
			helper.assertTrue(winch.shaft().problem() == ElevatorShaft.Problem.GAP && winch.shaft().gap() == gap,
					"the winch reads a gap at y " + gap + ": " + winch.shaft().problem() + " at "
							+ winch.shaft().gap());
			helper.assertTrue(!assembled(helper), "the winch's lamp is red over a broken shaft");
			frame(helper, 3);
		});
		helper.runAfterDelay(SETTLE + 4 + READING, () -> {
			helper.assertTrue(winch.shaft().valid() && assembled(helper), "the frame put back makes the shaft whole");
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN07 — what the winch says while the shaft is built: a winch on nothing has no shaft,
	 *     a tube with one station has one floor, two stations make it ready; the lamp is green only then.
	 */
	public static void assemblyIsReadByTheWinch(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = winch(helper, FULL);
		helper.runAfterDelay(SETTLE, () -> {
			helper.assertTrue(winch.shaft().problem() == ElevatorShaft.Problem.NO_COLUMN && !assembled(helper),
					"a winch on nothing reads no shaft: " + winch.shaft().problem());
			station(helper, 5);
			frame(helper, 4);
			frame(helper, 3);
			frame(helper, 2);
		});
		helper.runAfterDelay(SETTLE + READING, () -> {
			helper.assertTrue(winch.shaft().problem() == ElevatorShaft.Problem.ONE_FLOOR && !assembled(helper),
					"one station is one floor: " + winch.shaft().problem());
			helper.setBlock(cell(2), Blocks.AIR.defaultBlockState());
			station(helper, 1);
		});
		helper.runAfterDelay(SETTLE + 2 * READING, () -> {
			helper.assertTrue(winch.shaft().valid() && assembled(helper), "two stations make the shaft ready");
			helper.assertTrue(winch.floors().equals(List.of(abs(helper, 1), abs(helper, 5))),
					"the floors are the stations' lower halves: " + winch.floors());
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN08 — frames join: a frame placed on another tells the one below that the tube goes on
	 *     (no ring between them); a station is one thing: its upper half broken takes the lower half with it.
	 */
	public static void framesJoinAndStationIsOneBlock(GameTestHelper helper) {
		BlockState lone = ModContent.ELEVATOR_SHAFT.get().defaultBlockState();
		helper.setBlock(cell(1), lone);
		helper.assertTrue(!helper.getBlockState(cell(1)).getValue(ElevatorShaftBlock.UP),
				"a lone frame shows its top ring");
		helper.setBlock(cell(2), lone);
		helper.assertTrue(helper.getBlockState(cell(1)).getValue(ElevatorShaftBlock.UP),
				"a frame on top joins the one below: no ring between them");
		station(helper, 4);
		helper.setBlock(cell(5), Blocks.AIR.defaultBlockState());
		helper.assertTrue(helper.getBlockState(cell(4)).isAir(), "breaking the upper half breaks the station");
		helper.succeed();
	}

	/**
	 * @implements TC-ELV-001-FUN09 — a winch saved mid-trip and loaded again (a chunk unload) keeps the trip, and its
	 *     cabin is at the destination once the trip's time has passed.
	 */
	public static void tripSurvivesASaveAndLoad(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = build(helper, FULL);
		int top = abs(helper, FLOORS[2]);
		helper.runAfterDelay(SETTLE, () -> winch.request(top, null));
		helper.runAfterDelay(SETTLE + 10, () -> {
			ElevatorTrip trip = winch.trip();
			helper.assertTrue(trip != null, "the cabin is on its way when the chunk unloads");
			HolderLookup.Provider registries = helper.getLevel().registryAccess();
			CompoundTag tag = winch.saveCustomOnly(registries);
			BlockPos at = helper.absolutePos(WINCH);
			ElevatorWinchBlockEntity loaded = new ElevatorWinchBlockEntity(at, helper.getLevel().getBlockState(at));
			loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
			helper.assertTrue(trip.equals(loaded.trip()), "the trip survives the save: " + loaded.trip());
			helper.assertTrue(loaded.floors().equals(winch.floors()), "the floors survive the save");
			double later = helper.getLevel().getGameTime() + 1000.0D;
			helper.assertTrue(Math.abs(loaded.cabinHeight(later) - top) < 1.0E-6D,
					"after the unload the cabin is at the destination");
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN10 — something left hanging in the shaft (saved mid-ride, loaded later) is set down
	 *     on the floor of the standing cabin.
	 */
	public static void hangingEntityIsSetOnTheCabin(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = build(helper, FULL);
		int bottom = abs(helper, FLOORS[0]);
		ArmorStand[] hanging = new ArmorStand[1];
		// Spawned after the winch's own load-time sweep, so only the call below can set it down.
		helper.runAfterDelay(SETTLE, () -> {
			hanging[0] = helper.spawn(EntityTypes.ARMOR_STAND, Vec3.atBottomCenterOf(cell(4)).add(0.0D, 0.5D, 0.0D));
			hanging[0].setNoGravity(true);
		});
		helper.runAfterDelay(SETTLE + 3, () -> {
			helper.assertTrue(!hanging[0].onGround() && hanging[0].getY() > bottom + 2.0D,
					"the stand still hangs in the shaft's air");
			ElevatorRescue.rescueAll(helper.getLevel(), winch);
			double off = hanging[0].getY() - (bottom + ElevatorCarry.FLOOR_TOP);
			helper.assertTrue(Math.abs(off) < 1.0E-6D, "the hanging stand is set on the cabin, off by " + off);
			helper.succeed();
		});
	}
}
