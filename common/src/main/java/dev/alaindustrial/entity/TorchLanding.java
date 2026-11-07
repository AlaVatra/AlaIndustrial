package dev.alaindustrial.entity;

import dev.alaindustrial.core.world.PlaceEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Where a slingshot torch lands (MOD-871): the cell in front of the hit face, placed through the torch's own
 * {@code BlockItem.place}, so the vanilla rules of "floor or wall", {@code canSurvive}, replaceable cells and our
 * torch's water and oil logging all apply as for a torch placed by hand.
 *
 * <p>Three things differ from a hand placement, each on purpose:
 * <ul>
 *   <li>The side comes from the hit face, never from where the shooter looks: {@link FacePlaceContext} offers the
 *   block exactly one direction, toward the hit block. The top face gives a standing torch, a side face a wall
 *   torch, the bottom face nothing (no torch hangs from a ceiling).</li>
 *   <li>A torch never goes into a cell whose fluid it would put out: a cell holding a fluid takes only a block that
 *   can hold one, and a placement that leaves the cell with a different fluid than before is undone.</li>
 *   <li>The shooter must be online and allowed to build there ({@code Player.mayUseItemAt}, {@code Level.mayInteract},
 *   {@code Projectile.mayInteract}); the loader's place event is asked through {@link PlaceEvents}.</li>
 * </ul>
 * The placement works on a copy of the carried torch, so the projectile's own stack is never spent twice.
 */
public final class TorchLanding {

	private TorchLanding() {
	}

	/** Places the projectile's torch against {@code hit}; false when it did not land and should drop. */
	public static boolean place(ServerLevel level, TorchProjectile projectile, BlockHitResult hit) {
		ItemStack torch = projectile.getItem();
		if (!(torch.getItem() instanceof BlockItem blockItem)) {
			return false;
		}
		Entity owner = projectile.getOwner();
		if (!(owner instanceof Player player)) {
			return false;
		}
		Direction face = hit.getDirection();
		BlockPos pos = hit.getBlockPos().relative(face);
		if (!player.mayUseItemAt(pos, face, torch) || !level.mayInteract(player, pos)
				|| !projectile.mayInteract(level, pos)) {
			return false;
		}
		BlockState before = level.getBlockState(pos);
		FluidState fluidBefore = before.getFluidState();
		if (!fluidBefore.isEmpty() && !(blockItem.getBlock() instanceof LiquidBlockContainer)) {
			return false;
		}
		return PlaceEvents.get().place(level, pos, face, player, () -> {
			ItemStack copy = torch.copyWithCount(1);
			if (!blockItem.place(new FacePlaceContext(level, pos, face, copy)).consumesAction()) {
				return false;
			}
			if (level.getFluidState(pos).getType() != fluidBefore.getType()) {
				level.setBlock(pos, before, Block.UPDATE_ALL);
				return false;
			}
			return true;
		});
	}

	/**
	 * A placement context that offers the block one direction only: from the new cell toward the hit block. Every
	 * torch block reads its side from {@code getNearestLookingDirections()}; vanilla {@link DirectionalPlaceContext}
	 * would offer "down" first and so stand a torch on the floor below even after a side hit.
	 */
	static final class FacePlaceContext extends DirectionalPlaceContext {
		private final Direction towardSupport;

		FacePlaceContext(Level level, BlockPos pos, Direction face, ItemStack torch) {
			super(level, pos, face.getOpposite(), torch, face);
			this.towardSupport = face.getOpposite();
		}

		@Override
		public Direction getNearestLookingDirection() {
			return towardSupport;
		}

		@Override
		public Direction[] getNearestLookingDirections() {
			return new Direction[] {towardSupport};
		}
	}
}
