package dev.alaindustrial.gametest;

import dev.alaindustrial.block.ElevatorStationBlock;
import dev.alaindustrial.block.ElevatorWinchBlock;
import dev.alaindustrial.block.entity.ElevatorWinchBlockEntity;
import dev.alaindustrial.block.entity.elevator.ElevatorCarry;
import dev.alaindustrial.core.elevator.ElevatorClearance;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Loader-neutral gametests of a PLAYER riding the elevator (MOD-769, suite TC-ELV-001, the owner's round 2 of
 * 2026-11-24): walking in through the open station off the middle line, sneaking in on an upper floor, riding from the
 * doorway's recess, jumping as the cabin sets off, and a client that hears of the trip late; and (round 3) the
 * rider's eyes inside the cabin as it is drawn, every frame of a ride up and down.
 *
 * <p><b>The client is simulated.</b> A player's position belongs to their client: the server's carry never moves a
 * player, the client's winch ticker does. A gametest has no client, so each test tick here plays the client's part
 * for the mock player in the client's order: the player's own move with vanilla collision against the real blocks
 * ({@code Entity.move}), gravity after it, the server's check of the position the client sends (it sends the
 * player back when the new box collides with anything the old one did not), then the winch's carry for the local player
 * ({@link ElevatorCarry#move} with the cabin the winch reports, {@link ElevatorWinchBlockEntity#cabin}). The server
 * winch runs as usual beside it and only spares the player the fall and the flying kick.
 *
 * <p>The rig is {@link ElevatorScenarios}'s three-floor rig: winch at (2, 7, 3), stations facing north at floors 1, 3
 * and 5 in the column x 2, z 3; a landing is stone in front of a station (z 1..2), its top at the floor's height.
 */
public final class ElevatorRiderScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ElevatorRiderScenarios::playerWalksInOffTheMiddleAndOut,
						"elevator_player_walks_in_and_out").ticks(80),
				RosterEntry.of(ElevatorRiderScenarios::sneakingPlayerWalksInUpstairsAndRidesDown,
						"elevator_sneaking_player_rides_down").ticks(120),
				RosterEntry.of(ElevatorRiderScenarios::riderInTheDoorwayRidesUp, "elevator_rider_in_doorway_rides_up")
						.ticks(80),
				RosterEntry.of(ElevatorRiderScenarios::jumperAndEdgeRiderStayWithTheCabin,
						"elevator_jumper_and_edge_rider_ride").ticks(140),
				RosterEntry.of(ElevatorRiderScenarios::lateClientIsStillCarried, "elevator_late_client_carried")
						.ticks(80),
				RosterEntry.of(ElevatorRiderScenarios::doorwayFitsEveryPlayerPose, "elevator_doorway_fits_player")
						.ticks(20),
				RosterEntry.of(ElevatorRiderScenarios::riderLooksOutFromInsideTheDrawnCabin,
						"elevator_rider_eyes_stay_in_cabin").ticks(180));

		private Roster() {}
	}

	private ElevatorRiderScenarios() {
	}

	private static final BlockPos WINCH = new BlockPos(2, 7, 3);
	private static final int[] FLOORS = {1, 3, 5};
	private static final long FULL = 10_000L;
	private static final int SETTLE = 3;
	/** A 4-block trip takes 30 ticks, a 2-block one 20: wait that and a little. */
	private static final int RIDE = 40;
	/** Walking speed of a player, blocks per tick, and vanilla's ground friction after a move. */
	private static final double WALK = 0.2D;
	private static final double GROUND_FRICTION = 0.546D;
	private static final double GRAVITY = 0.08D;
	/** A player who stands on the cabin's floor: feet this close to the floor's top. */
	private static final double ON_FLOOR = 0.05D;
	/** The cabin's inside height, and the near clip plane: an eye closer to the roof than this sees through it. */
	private static final double CABIN_ROOF = 2.0D;
	private static final double NEAR_CLIP = 0.05D;
	/** The frames drawn between two client ticks, by their partial tick. */
	private static final float[] FRAMES = {0.0F, 0.25F, 0.5F, 0.75F};
	/** A top speed well over the default 0.2 blocks per tick. */
	private static final double FAST = 0.5D;

	private static BlockPos cell(int y) {
		return new BlockPos(WINCH.getX(), y, WINCH.getZ());
	}

	private static ElevatorWinchBlockEntity build(GameTestHelper helper) {
		for (int floor : FLOORS) {
			BlockState lower = ModContent.ELEVATOR_STATION.get().defaultBlockState()
					.setValue(ElevatorStationBlock.FACING, Direction.NORTH);
			helper.setBlock(cell(floor), lower);
			helper.setBlock(cell(floor + 1), lower.setValue(ElevatorStationBlock.HALF, DoubleBlockHalf.UPPER));
		}
		helper.setBlock(WINCH, ModContent.ELEVATOR_WINCH.get().defaultBlockState()
				.setValue(ElevatorWinchBlock.FACING, Direction.NORTH));
		ElevatorWinchBlockEntity winch = helper.getBlockEntity(WINCH, ElevatorWinchBlockEntity.class);
		winch.getEnergyStorage().setAmountUntracked(FULL);
		return winch;
	}

	/** Stone in front of the station of {@code floor}, its top at the floor's height; air above it. */
	private static void landing(GameTestHelper helper, int floor) {
		for (int x = 1; x <= 3; x++) {
			for (int z = 0; z <= 2; z++) {
				helper.setBlock(new BlockPos(x, floor - 1, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, floor, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, floor + 1, z), Blocks.AIR);
			}
		}
	}

	private static int abs(GameTestHelper helper, int relativeY) {
		return helper.absolutePos(new BlockPos(0, relativeY, 0)).getY();
	}

	/** The middle of the station column, absolute. */
	private static Vec3 middle(GameTestHelper helper, int floor) {
		return Vec3.atBottomCenterOf(helper.absolutePos(cell(floor)));
	}

	/** A survival player standing at {@code offset} from the middle of the station cell of {@code floor}. */
	private static ServerPlayer playerAt(GameTestHelper helper, int floor, double dx, double dy, double dz) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		Vec3 at = middle(helper, floor).add(dx, dy, dz);
		player.setPos(at.x, at.y, at.z);
		player.setDeltaMovement(Vec3.ZERO);
		return player;
	}

	/**
	 * One client tick of the local player: the old position kept for the frames (vanilla's {@code setOldPosAndRot},
	 * which a client entity's tick starts with), walk {@code (wx, wz)} blocks this tick (zero to stand), vanilla's move
	 * with collision, gravity and ground friction after it; then the position packet the client sends, which the
	 * server takes or answers by sending the player back to where it last accepted them ({@link #serverTakes}); then
	 * the winch's client carry for the local player. {@code server[0]} is the last position the server accepted.
	 */
	private static void clientTick(Player player, double wx, double wz, ElevatorCarry.Cabin seen, Vec3[] server) {
		player.setOldPosAndRot();
		Vec3 motion = player.getDeltaMovement();
		boolean walking = wx != 0.0D || wz != 0.0D;
		player.setDeltaMovement(walking ? wx : motion.x, motion.y, walking ? wz : motion.z);
		player.move(MoverType.SELF, player.getDeltaMovement());
		Vec3 after = player.getDeltaMovement();
		player.setDeltaMovement(after.x * GROUND_FRICTION, (after.y - GRAVITY) * 0.98D, after.z * GROUND_FRICTION);
		if (server[0] == null || serverTakes(player, server[0])) {
			server[0] = player.position();
		} else {
			player.setPos(server[0]);
			player.setDeltaMovement(Vec3.ZERO);
		}
		if (seen != null) {
			ElevatorCarry.move(player, seen);
		}
	}

	/**
	 * Whether the server takes the client's move from {@code from} to where the player is now: vanilla's
	 * {@code ServerGamePacketListenerImpl.isEntityCollidingWithAnythingNew}, which sends the player back when the new
	 * box collides with any shape the old box did not. Vertical moves never count as "moved wrongly" (the server
	 * ignores y there), so for a ride this is the rule that decides.
	 */
	private static boolean serverTakes(Player player, Vec3 from) {
		AABB oldBox = player.getDimensions(player.getPose()).makeBoundingBox(from);
		VoxelShape oldShape = Shapes.create(oldBox.deflate(1.0E-5D));
		for (VoxelShape shape : player.level().getPreMoveCollisions(player, player.getBoundingBox().deflate(1.0E-5D),
				oldBox.getBottomCenter())) {
			if (!Shapes.joinIsNotEmpty(shape, oldShape, BooleanOp.AND)) {
				return false;
			}
		}
		return true;
	}

	private static ElevatorCarry.Cabin cabinNow(GameTestHelper helper, ElevatorWinchBlockEntity winch) {
		return winch.cabin(helper.getLevel(), helper.getLevel().getGameTime());
	}

	private static void assertOnFloor(GameTestHelper helper, Player player, int floor, String when) {
		double off = player.getY() - (abs(helper, floor) + ElevatorCarry.FLOOR_TOP);
		helper.assertTrue(Math.abs(off) < ON_FLOOR, when + ": the player should stand on the cabin floor at y "
				+ abs(helper, floor) + ", off by " + off + " (at " + player.position() + ")");
		helper.assertTrue(ElevatorCarry.inColumn(player, helper.absolutePos(cell(floor))),
				when + ": the player should be inside the tube, at " + player.position());
		helper.assertTrue(player.fallDistance < 0.5D, when + ": no fall builds up, got " + player.fallDistance);
	}

	/**
	 * @implements TC-ELV-001-FUN12 — a player 0.14 block off the doorway's middle line walks straight at the open
	 *     station of floor 1 from 1.2 blocks out: the doorway lines them up, they reach the middle of the cabin on its
	 *     floor plate, and walk straight back out onto the landing (no jump, no strafe).
	 */
	public static void playerWalksInOffTheMiddleAndOut(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = build(helper);
		landing(helper, FLOORS[0]);
		Player[] player = new Player[1];
		Vec3[] server = new Vec3[1];
		int[] phase = new int[1];
		helper.runAfterDelay(SETTLE, () -> player[0] = playerAt(helper, FLOORS[0], 0.14D, 0.0D, -1.7D));
		helper.onEachTick(() -> {
			Player p = player[0];
			if (p == null || phase[0] > 1) {
				return;
			}
			Vec3 mid = middle(helper, FLOORS[0]);
			clientTick(p, 0.0D, phase[0] == 0 ? WALK : -WALK, cabinNow(helper, winch), server);
			if (phase[0] == 0 && p.getZ() >= mid.z) {
				assertOnFloor(helper, p, FLOORS[0], "walked in");
				phase[0] = 1;
			} else if (phase[0] == 1 && p.getZ() <= mid.z - 1.2D) {
				double off = p.getY() - abs(helper, FLOORS[0]);
				helper.assertTrue(Math.abs(off) < 1.0E-3D, "walked out onto the landing, off by " + off);
				phase[0] = 2;
			}
		});
		helper.runAfterDelay(SETTLE + 40, () -> {
			helper.assertTrue(phase[0] >= 1, "the player could not walk into the cabin, stuck at "
					+ player[0].position() + " (middle " + middle(helper, FLOORS[0]) + ")");
			helper.assertTrue(phase[0] == 2, "the player could not walk out of the cabin, stuck at "
					+ player[0].position());
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN13 — on floor 3 (the shaft is hollow under the station) a sneaking player walks into
	 *     the standing cabin: sneaking does not stop them at the landing's edge, because the open station lends the
	 *     cabin a floor plate; then they ride down to floor 1 still sneaking and stand on its floor.
	 */
	public static void sneakingPlayerWalksInUpstairsAndRidesDown(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = build(helper);
		landing(helper, FLOORS[2]);
		int bottom = abs(helper, FLOORS[0]);
		Player[] player = new Player[1];
		Vec3[] server = new Vec3[1];
		boolean[] in = new boolean[1];
		helper.runAfterDelay(SETTLE, () -> winch.request(abs(helper, FLOORS[2]), null));
		helper.runAfterDelay(SETTLE + RIDE, () -> {
			helper.assertTrue(winch.standsAt(abs(helper, FLOORS[2])), "the empty cabin went up to floor 3 first");
			player[0] = playerAt(helper, FLOORS[2], 0.0D, 0.0D, -1.3D);
			player[0].setShiftKeyDown(true);
			player[0].setPose(Pose.CROUCHING);
		});
		helper.onEachTick(() -> {
			Player p = player[0];
			if (p == null) {
				return;
			}
			boolean walk = !in[0];
			clientTick(p, 0.0D, walk ? WALK / 3.0D : 0.0D, cabinNow(helper, winch), server);
			if (!in[0] && p.getZ() >= middle(helper, FLOORS[2]).z) {
				assertOnFloor(helper, p, FLOORS[2], "sneaked in");
				in[0] = true;
				p.setDeltaMovement(Vec3.ZERO);
				winch.request(bottom, null);
			}
		});
		helper.runAfterDelay(SETTLE + RIDE + 30 + RIDE, () -> {
			helper.assertTrue(in[0], "the sneaking player stopped at the landing's edge, at " + player[0].position());
			helper.assertTrue(winch.standsAt(bottom), "the cabin came down to floor 1");
			assertOnFloor(helper, player[0], FLOORS[0], "after the ride down");
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN14 — a player who stopped in the doorway's recess (centre just inside the cell, the
	 *     box sticking out of the tube) when the doors shut rides up to floor 5 with the cabin: the shut doors and the
	 *     walls above do not hold them back.
	 */
	public static void riderInTheDoorwayRidesUp(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = build(helper);
		landing(helper, FLOORS[0]);
		int top = abs(helper, FLOORS[2]);
		Player[] player = new Player[1];
		Vec3[] server = new Vec3[1];
		helper.runAfterDelay(SETTLE, () -> {
			player[0] = playerAt(helper, FLOORS[0], 0.0D, ElevatorCarry.FLOOR_TOP, -0.45D);
			AABB box = player[0].getBoundingBox();
			double front = helper.absolutePos(cell(FLOORS[0])).getZ();
			helper.assertTrue(box.minZ < front && box.maxZ > front, "the rig puts the player across the threshold");
			winch.request(top, null);
		});
		helper.onEachTick(() -> {
			if (player[0] != null) {
				clientTick(player[0], 0.0D, 0.0D, cabinNow(helper, winch), server);
			}
		});
		helper.runAfterDelay(SETTLE + RIDE, () -> {
			helper.assertTrue(winch.standsAt(top), "the cabin went up to floor 5");
			assertOnFloor(helper, player[0], FLOORS[2], "after the ride up from the doorway");
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN15 — a player at the side edge of the cabin jumps on the tick the cabin sets off up:
	 *     they land back on the risen floor and arrive at floor 5; then, at the other edge, they ride down to floor 1.
	 */
	public static void jumperAndEdgeRiderStayWithTheCabin(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = build(helper);
		int top = abs(helper, FLOORS[2]);
		int bottom = abs(helper, FLOORS[0]);
		double edge = ElevatorClearance.slack(EntityTypes.PLAYER.getWidth());
		Player[] player = new Player[1];
		Vec3[] server = new Vec3[1];
		helper.runAfterDelay(SETTLE, () -> {
			player[0] = playerAt(helper, FLOORS[0], edge, ElevatorCarry.FLOOR_TOP, 0.0D);
			winch.request(top, null);
			player[0].setDeltaMovement(0.0D, 0.42D, 0.0D);
		});
		helper.onEachTick(() -> {
			if (player[0] != null) {
				clientTick(player[0], 0.0D, 0.0D, cabinNow(helper, winch), server);
			}
		});
		helper.runAfterDelay(SETTLE + RIDE, () -> {
			helper.assertTrue(winch.standsAt(top), "the cabin went up to floor 5");
			assertOnFloor(helper, player[0], FLOORS[2], "after jumping as the cabin set off");
			Vec3 mid = middle(helper, FLOORS[2]);
			player[0].setPos(mid.x - edge, player[0].getY(), mid.z + edge);
			server[0] = player[0].position();
			winch.request(bottom, null);
		});
		helper.runAfterDelay(SETTLE + 2 * RIDE + 5, () -> {
			helper.assertTrue(winch.standsAt(bottom), "the cabin came down to floor 1");
			assertOnFloor(helper, player[0], FLOORS[0], "after the ride down at the edge");
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN16 — a client that hears of the trip 8 ticks late (the doors already shut, the cabin
	 *     already 0.6 block up on the server's clock) still finds its player inside the cabin and carries them to
	 *     floor 5.
	 */
	public static void lateClientIsStillCarried(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = build(helper);
		int top = abs(helper, FLOORS[2]);
		Player[] player = new Player[1];
		Vec3[] server = new Vec3[1];
		long[] sent = new long[1];
		helper.runAfterDelay(SETTLE, () -> {
			player[0] = playerAt(helper, FLOORS[0], 0.0D, ElevatorCarry.FLOOR_TOP, 0.0D);
			winch.request(top, null);
			sent[0] = helper.getLevel().getGameTime();
		});
		helper.onEachTick(() -> {
			if (player[0] == null) {
				return;
			}
			boolean late = helper.getLevel().getGameTime() < sent[0] + 8;
			ElevatorCarry.Cabin stale = new ElevatorCarry.Cabin(helper.absolutePos(cell(FLOORS[0])),
					abs(helper, FLOORS[0]), 0.0D, null);
			clientTick(player[0], 0.0D, 0.0D, late ? stale : cabinNow(helper, winch), server);
		});
		helper.runAfterDelay(SETTLE + RIDE, () -> {
			helper.assertTrue(winch.standsAt(top), "the cabin went up to floor 5");
			assertOnFloor(helper, player[0], FLOORS[2], "after a trip heard 8 ticks late");
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN17 — the open station's real collision lets a player through in every pose: the
	 *     tube is wider than a player with room to spare, and nothing of the station is in the way of a player body on
	 *     the floor plate, centred on the doorway, from half out of it to the back wall, up to the station's top.
	 */
	public static void doorwayFitsEveryPlayerPose(GameTestHelper helper) {
		build(helper);
		helper.runAfterDelay(SETTLE, () -> {
			BlockPos lower = helper.absolutePos(cell(FLOORS[0]));
			double width = EntityTypes.PLAYER.getWidth();
			helper.assertTrue(ElevatorClearance.fits(width) && ElevatorClearance.slack(width) >= 0.05D,
					"the tube is " + ElevatorClearance.OPENING + " wide for a player of " + width);
			Player probe = AlaGameTestHelper.detachedSurvivalPlayer(helper);
			Vec3 mid = Vec3.atBottomCenterOf(lower);
			double half = width / 2.0D;
			for (Pose pose : new Pose[] {Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING}) {
				double height = probe.getDimensions(pose).height();
				// From half out of the doorway to the back wall's reach: the walk in, every step of it.
				double deepest = 0.5D - ElevatorClearance.WALL - half - ElevatorClearance.SAFETY;
				for (double depth : new double[] {-0.5D, -0.375D, -0.25D, -0.125D, 0.0D, deepest}) {
					AABB body = new AABB(mid.x - half, lower.getY() + ElevatorCarry.FLOOR_TOP, mid.z + depth - half,
							mid.x + half, lower.getY() + ElevatorCarry.FLOOR_TOP + height, mid.z + depth + half);
					helper.assertTrue(helper.getLevel().noCollision(body), pose + " player in the doorway at depth "
							+ depth + " meets the station: " + body);
				}
			}
			BlockState open = helper.getLevel().getBlockState(lower);
			boolean plate = open.getCollisionShape(helper.getLevel(), lower, CollisionContext.empty()).toAabbs()
					.stream().anyMatch(b -> b.maxY <= ElevatorCarry.FLOOR_TOP + 1.0E-6D && b.minX <= 0.5D
							&& b.maxX >= 0.5D && b.minZ <= 0.5D && b.maxZ >= 0.5D);
			helper.assertTrue(plate, "the open station lends the cabin its floor plate");
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN19 — the owner's round 3 (2026-11-24): riding down, his eyes went over the cabin's
	 *     roof. A player rides floor 1 → 5 → 1 at the default speed and again at {@value #FAST} blocks a tick; for
	 *     every frame of every client tick, the eye (the player drawn between their last two tick positions, as the
	 *     camera is) stays between the floor and the roof of the cabin as the renderer draws it in the same frame
	 *     ({@link ElevatorWinchBlockEntity#drawnCabinHeight}, read at the client's clock: one tick past the tick that
	 *     moved the player, because a client moves its clock on after its entities and block entities ticked).
	 */
	public static void riderLooksOutFromInsideTheDrawnCabin(GameTestHelper helper) {
		ElevatorWinchBlockEntity winch = build(helper);
		int top = abs(helper, FLOORS[2]);
		int bottom = abs(helper, FLOORS[0]);
		Player[] player = new Player[1];
		Vec3[] server = new Vec3[1];
		int[] movingFrames = new int[1];
		double[] range = {Double.MAX_VALUE, -Double.MAX_VALUE};
		helper.runAfterDelay(SETTLE, () -> {
			player[0] = playerAt(helper, FLOORS[0], 0.0D, ElevatorCarry.FLOOR_TOP, 0.0D);
			winch.request(top, null);
		});
		helper.onEachTick(() -> {
			Player p = player[0];
			if (p == null) {
				return;
			}
			long clock = helper.getLevel().getGameTime();
			clientTick(p, 0.0D, 0.0D, winch.cabin(helper.getLevel(), clock), server);
			for (float partial : FRAMES) {
				double eye = p.yo + (p.getY() - p.yo) * partial + p.getEyeHeight();
				double floor = winch.drawnCabinHeight(clock + 1L, partial);
				double over = eye - floor;
				range[0] = Math.min(range[0], over);
				range[1] = Math.max(range[1], over);
				if (winch.trip() != null) {
					movingFrames[0]++;
				}
				helper.assertTrue(over > ElevatorCarry.FLOOR_TOP + NEAR_CLIP && over < CABIN_ROOF - NEAR_CLIP,
						"at tick " + clock + " frame " + partial + " the rider's eye is " + over
								+ " over the drawn cabin floor " + floor + ": outside the cabin (trip "
								+ winch.trip() + ")");
			}
		});
		int leg = 40;
		helper.runAfterDelay(SETTLE + leg, () -> {
			assertOnFloor(helper, player[0], FLOORS[2], "after the ride up");
			winch.request(bottom, null);
		});
		helper.runAfterDelay(SETTLE + 2 * leg, () -> {
			assertOnFloor(helper, player[0], FLOORS[0], "after the ride down");
			try (ConfigOverrides o = ConfigOverrides.sync()) {
				o.set("elevatorSpeed", FAST);
				winch.request(top, null);
			}
		});
		helper.runAfterDelay(SETTLE + 3 * leg, () -> {
			assertOnFloor(helper, player[0], FLOORS[2], "after the fast ride up");
			try (ConfigOverrides o = ConfigOverrides.sync()) {
				o.set("elevatorSpeed", FAST);
				winch.request(bottom, null);
			}
		});
		helper.runAfterDelay(SETTLE + 4 * leg, () -> {
			assertOnFloor(helper, player[0], FLOORS[0], "after the fast ride down");
			helper.assertTrue(movingFrames[0] > 4 * 60, "frames checked while the cabin moved: " + movingFrames[0]);
			helper.succeed();
		});
	}
}
