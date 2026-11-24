package dev.alaindustrial.block.entity.elevator;

import dev.alaindustrial.core.elevator.ElevatorClearance;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How the cabin carries what stands in it (MOD-769). The cabin is a picture, not a block or an entity: while it
 * stands at a floor the open station lends it a floor plate to stand on, and while it runs nothing collides with
 * its floor. So, at the end of every tick, whatever stands in the cabin is put back on its floor and given the
 * cabin's vertical speed for the next tick, and its own move takes it exactly where the cabin will be.
 *
 * <p><b>Who moves whom.</b> Each side moves only the entities it is the authority for
 * ({@link Entity#isLocalInstanceAuthoritative()}): the server its mobs and dropped items, a client its own player
 * (and a boat that player steers). A player's position comes from their client, so the server only clears what
 * would punish the ride: the fall distance and the "floating too long" counter that kicks a player for flying
 * ({@code ServerGamePacketListenerImpl.resetFlyingTicks}, 80 ticks). Both sides run this from the winch's ticker,
 * which the game runs after it has moved the entities of that tick, server and client alike.
 *
 * <p><b>Who rides.</b> An entity whose centre is over the cabin's cell rides when the floor's top is inside its
 * body or a little under its feet ({@link #CATCH_ABOVE}), unless it is on its way up faster than the cabin (a jump).
 * "Inside its body" and not "a few pixels under its feet": a floor that rises into a body has lifted it, however
 * late the client heard of the trip, so a rider is never left behind by a cabin that outran its last tick.
 *
 * <p><b>Off the walls.</b> While the cabin has no open door at its height (it runs, or the catches hold it), whatever
 * is in it is kept clear of the tube's walls ({@link ElevatorClearance#inside}). A rider that stood in the doorway's
 * recess when the doors shut would otherwise overlap the wall of every cell above and below it, and the game stops
 * any move toward a wall a box already reaches into: the cabin would leave and the rider would stay.
 *
 * <p><b>Into the doorway.</b> While the cabin stands at an open station, a body heading for the doorway is lined up
 * with it ({@link ElevatorClearance#guideStep}): the doorway is only a little wider than a player, and the game does
 * not slide a box round a jamb.
 */
public final class ElevatorCarry {

	/** The cabin's floor plate is one pixel thick: feet stand this far over the floor's height. */
	public static final double FLOOR_TOP = ElevatorClearance.FLOOR_TOP;
	/** How far over the floor's top a foot is put back down: more is a jump, not a stand. */
	static final double CATCH_ABOVE = 0.25D;
	/** An entity rising faster than the cabin by more than this is jumping and is left alone. */
	static final double JUMP_MARGIN = 0.05D;
	/** The cabin's inside height: what is anywhere in here is in the cabin. */
	static final double CABIN_HEIGHT = 2.0D;
	/** The guide looks for feet this far round the floor's height: someone walking in on the landing's level. */
	static final double DOOR_FEET = 0.6D;
	/** A box this close to the doorway's plane and stopped by it is pressing into the doorway. */
	static final double TOUCH = 0.02D;
	private static final double EPS = 1.0E-6D;

	/**
	 * Where the cabin is for this tick: its cell, its floor's height, its vertical speed (blocks per tick, up
	 * positive), and the way the open doorway at its height faces, or {@code null} when it has none (it runs, or the
	 * catches hold it between floors).
	 */
	public record Cabin(BlockPos cell, double floor, double velocity, @Nullable Direction door) {

		double top() {
			return floor + FLOOR_TOP;
		}
	}

	private ElevatorCarry() {
	}

	/** The box the cabin's riders are looked for in: its cell column, from a body under its floor to its roof. */
	public static AABB searchBox(BlockPos cell, double floor) {
		return new AABB(cell.getX(), floor - CABIN_HEIGHT, cell.getZ(), cell.getX() + 1.0D, floor + CABIN_HEIGHT,
				cell.getZ() + 1.0D);
	}

	/** Whether {@code entity} stands on the floor at {@code floor} of the cabin in {@code cell}. */
	public static boolean onFloor(Entity entity, BlockPos cell, double floor) {
		if (!inColumn(entity, cell)) {
			return false;
		}
		double feet = entity.getY();
		double top = floor + FLOOR_TOP;
		return feet > top - entity.getBbHeight() && feet <= top + CATCH_ABOVE;
	}

	/** Whether {@code entity} is anywhere in the cabin: over its cell, its body between the floor and the roof. */
	static boolean inCabin(Entity entity, BlockPos cell, double floor) {
		double feet = entity.getY();
		double top = floor + FLOOR_TOP;
		return inColumn(entity, cell) && feet > top - entity.getBbHeight() && feet < floor + CABIN_HEIGHT;
	}

	/** Whether {@code entity}'s centre is over the cabin's cell. */
	public static boolean inColumn(Entity entity, BlockPos cell) {
		double x = entity.getX();
		double z = entity.getZ();
		return x >= cell.getX() && x < cell.getX() + 1.0D && z >= cell.getZ() && z < cell.getZ() + 1.0D;
	}

	/**
	 * This side's tick of the cabin: every entity it is the authority for is moved ({@link #move}); a player the
	 * server is not the authority for is spared the fall and the flying kick while it is in the cabin.
	 *
	 * @return how many entities this side put on the floor
	 */
	public static int carry(Level level, Cabin cabin) {
		int carried = 0;
		AABB around = searchBox(cabin.cell(), cabin.floor());
		if (cabin.door() != null) {
			around = around.minmax(searchBox(cabin.cell().relative(cabin.door()), cabin.floor()));
		}
		List<Entity> found = level.getEntitiesOfClass(Entity.class, around,
				entity -> !entity.isSpectator() && !entity.isPassenger() && !entity.noPhysics);
		for (Entity entity : found) {
			if (entity.isLocalInstanceAuthoritative()) {
				if (move(entity, cabin)) {
					carried++;
				}
			} else if (entity instanceof ServerPlayer player && inCabin(player, cabin.cell(), cabin.floor())) {
				player.resetFallDistance();
				if (player.connection != null) {
					player.connection.resetFlyingTicks();
				}
			}
		}
		return carried;
	}

	/**
	 * Move one entity the way its authority does at the end of a tick: line it up with the open doorway it heads for,
	 * keep it off the walls of a cabin without an open door, and put it on the floor if it rides.
	 *
	 * @return whether it was put on the floor
	 */
	public static boolean move(Entity entity, Cabin cabin) {
		if (cabin.door() != null) {
			guide(entity, cabin);
		} else if (inCabin(entity, cabin.cell(), cabin.floor())) {
			keepOffTheWalls(entity, cabin.cell());
		}
		if (!onFloor(entity, cabin.cell(), cabin.floor())) {
			return false;
		}
		Vec3 motion = entity.getDeltaMovement();
		if (motion.y > cabin.velocity() + JUMP_MARGIN) {
			return false;
		}
		entity.setPos(entity.getX(), cabin.top(), entity.getZ());
		entity.setDeltaMovement(motion.x, cabin.velocity(), motion.z);
		entity.setOnGround(true);
		entity.resetFallDistance();
		return true;
	}

	/** Shift {@code entity} sideways so that its box is clear of the tube's walls, if the place it goes is free. */
	private static void keepOffTheWalls(Entity entity, BlockPos cell) {
		double width = entity.getBbWidth();
		double middleX = cell.getX() + 0.5D;
		double middleZ = cell.getZ() + 0.5D;
		double x = middleX + ElevatorClearance.inside(entity.getX() - middleX, width);
		double z = middleZ + ElevatorClearance.inside(entity.getZ() - middleZ, width);
		shift(entity, x - entity.getX(), z - entity.getZ());
	}

	/**
	 * Line {@code entity} up with the open doorway at {@code cabin}'s floor when it heads for it: its centre within
	 * the doorway's width, its feet about the floor's height, its leading edge within
	 * {@link ElevatorClearance#APPROACH} of the doorway, and it either moving mostly inward or pressing into it.
	 */
	private static void guide(Entity entity, Cabin cabin) {
		Direction out = cabin.door();
		Direction side = out.getClockWise();
		double dx = entity.getX() - (cabin.cell().getX() + 0.5D);
		double dz = entity.getZ() - (cabin.cell().getZ() + 0.5D);
		double along = dx * out.getStepX() + dz * out.getStepZ();
		double lateral = dx * side.getStepX() + dz * side.getStepZ();
		double width = entity.getBbWidth();
		double gap = along - width / 2.0D - 0.5D;
		if (Math.abs(lateral) >= 0.5D || gap < -EPS || gap > ElevatorClearance.APPROACH
				|| Math.abs(entity.getY() - cabin.floor()) > DOOR_FEET || !ElevatorClearance.fits(width)) {
			return;
		}
		Vec3 motion = entity.getDeltaMovement();
		double inward = -(motion.x * out.getStepX() + motion.z * out.getStepZ());
		double sideways = Math.abs(motion.x * side.getStepX() + motion.z * side.getStepZ());
		boolean heading = inward > 0.01D && inward >= sideways;
		boolean pressing = gap <= TOUCH && entity.horizontalCollision;
		if (!heading && !pressing) {
			return;
		}
		double shiftBy = ElevatorClearance.guideStep(lateral, width, gap) - lateral;
		shift(entity, shiftBy * side.getStepX(), shiftBy * side.getStepZ());
	}

	private static void shift(Entity entity, double dx, double dz) {
		if (Math.abs(dx) < EPS && Math.abs(dz) < EPS) {
			return;
		}
		AABB moved = entity.getBoundingBox().move(dx, 0.0D, dz);
		if (entity.level().noCollision(entity, moved)) {
			entity.setPos(entity.getX() + dx, entity.getY(), entity.getZ() + dz);
		}
	}

	/** The passengers that make the climb dearer: every living thing on the floor. */
	public static int riders(Level level, BlockPos cell, double floor) {
		int riders = 0;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, searchBox(cell, floor),
				entity -> !entity.isSpectator())) {
			if (onFloor(entity, cell, floor)) {
				riders++;
			}
		}
		return riders;
	}
}
