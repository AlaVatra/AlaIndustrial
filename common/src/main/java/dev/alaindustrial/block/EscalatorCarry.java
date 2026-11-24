package dev.alaindustrial.block;

import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.escalator.EscalatorRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How a running escalator moves whoever stands on it (MOD-969). Called from {@code entityInside} of a section on
 * both sides: vanilla runs that hook on the server for every entity and on the client for the local player only
 * ({@code LivingEntity.aiStep}: {@code !isClientSide || isLocalInstanceAuthoritative}), so the player's own client
 * moves the player and the server moves mobs and items. No velocity packet is sent, so nothing is corrected back.
 *
 * <p><b>One push per entity per tick.</b> {@code entityInside} fires for every cell the entity's box touches; only
 * the entity's one owner cell ({@link #ownerCell}) pushes.
 */
public final class EscalatorCarry {

	private EscalatorCarry() {
	}

	/**
	 * The section that carries {@code entity} this tick, or {@code null}: of the sections its box touches at its feet
	 * (the cells at the feet's height and one above), the highest; between two at one height, the one with the least
	 * X, then Z. On a seam the box touches the step it stands on and the next, higher one, and the higher one carries
	 * it, so it never stalls against the riser between them. Only stair halves count: the balustrade half above a
	 * rider's own step carries nobody. Vanilla calls {@code entityInside} for every touched cell, and only this one
	 * pushes.
	 */
	public static @Nullable BlockPos ownerCell(BlockGetter level, Entity entity) {
		return ownerCell(level, entity.getBoundingBox().deflate(1.0E-7));
	}

	/** {@link #ownerCell(BlockGetter, Entity)} for a box the caller chose. */
	static @Nullable BlockPos ownerCell(BlockGetter level, AABB box) {
		int y0 = Mth.floor(box.minY);
		BlockPos best = null;
		for (int y = y0 + 1; y >= y0 && best == null; y--) {
			if (y >= box.maxY) {
				continue;
			}
			for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX) && best == null; x++) {
				for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
					BlockPos at = new BlockPos(x, y, z);
					if (EscalatorSectionBlock.isLower(level.getBlockState(at))) {
						best = at;
						break;
					}
				}
			}
		}
		return best;
	}

	/**
	 * Whether a band would carry {@code entity} at all: a sneaking player, a spectator, a player flying in creative
	 * and anything riding something stay where they are.
	 */
	public static boolean rides(Entity entity) {
		if (entity.isSpectator() || entity.isPassenger() || !entity.isAlive()) {
			return false;
		}
		if (entity instanceof Player player) {
			return !player.isShiftKeyDown() && !player.getAbilities().flying;
		}
		return true;
	}

	/** Band speed of a running section, blocks per tick, read through the server's numbers on both sides. */
	public static double speed(BlockState section) {
		return section.getValue(EscalatorSectionBlock.FAST) ? ServerBalance.escalatorSpeedMv()
				: ServerBalance.escalatorSpeedLv();
	}

	/**
	 * Push {@code entity} along the band of the section at {@code pos}. Returns whether it was pushed, so the test
	 * lane can count pushes.
	 */
	public static boolean carry(BlockGetter level, BlockPos pos, BlockState state, Entity entity) {
		if (entity instanceof ItemEntity || state.getValue(EscalatorSectionBlock.MODE) != EscalatorMode.RUNNING
				|| !rides(entity) || !pos.equals(ownerCell(level, entity))) {
			return false;
		}
		push(state, entity);
		return true;
	}

	/**
	 * A dropped item rides too, but is pushed by the drive, not by {@code entityInside}: a quarter-block item that a
	 * riser has stopped touches only the air beside the next step (its box ends exactly on the step's face, and
	 * vanilla's own check is shrunk by a hair), so no section would see it and it would stall at every seam. The
	 * drive looks for the item's section with a box widened by a hundredth and reaching a fifth of a block down.
	 * Returns whether it was pushed.
	 */
	public static boolean carryItem(BlockGetter level, ItemEntity item) {
		if (!rides(item)) {
			return false;
		}
		BlockPos owner = ownerCell(level, item.getBoundingBox().inflate(0.01, 0.0, 0.01).expandTowards(0.0, -0.2, 0.0));
		if (owner == null) {
			return false;
		}
		BlockState state = level.getBlockState(owner);
		if (state.getValue(EscalatorSectionBlock.MODE) != EscalatorMode.RUNNING) {
			return false;
		}
		push(state, item);
		return true;
	}

	private static void push(BlockState state, Entity entity) {
		Direction facing = state.getValue(EscalatorSectionBlock.FACING);
		Vec3 motion = entity.getDeltaMovement();
		boolean down = state.getValue(EscalatorSectionBlock.REVERSED);
		double[] next = EscalatorRules.carry(motion.x, motion.y, motion.z, facing.getStepX(), facing.getStepZ(),
				down, speed(state));
		// An item (or any non-living entity) takes its gravity BEFORE it moves (ItemEntity.tick: applyGravity, then
		// move), a living one after; climbing, give the former its gravity back so both follow the 1:1 slope.
		double lift = !down && !(entity instanceof LivingEntity) ? entity.getGravity() : 0.0;
		entity.setDeltaMovement(next[0], next[1] + lift, next[2]);
		entity.resetFallDistance();
	}
}
