package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.ElevatorWinchBlockEntity;
import dev.alaindustrial.block.entity.elevator.ElevatorShaft;
import dev.alaindustrial.core.elevator.ElevatorClearance;
import dev.alaindustrial.core.elevator.ElevatorShaftPart;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A landing station of the elevator (MOD-769): two cells of the glass shaft with the floor's doorway in the front, the
 * way a door is two blocks high. The lower half carries the call panel in the right-hand jamb and the sill, the upper
 * half the floor lamp and the screen over the doors. The cabin's floor at this station is the lower half's floor.
 *
 * <p>Right click (either half) calls the cabin here; when the cabin already stands here, from the landing or from
 * inside the cabin, it opens the floor panel: one button per floor, the top floor at the top
 * ({@code ElevatorPanelMenu}). So the panel is the call button where the cabin is not, and the floor selector where it
 * is.
 *
 * <p>{@code open} is set by the winch only ({@code ElevatorLandings}): the doors are open, and the lamp green, exactly
 * while the cabin stands at this floor. Closed, the front is a wall like the frame's; open, the doorway is as wide as
 * the tube's inside and as high as the station, and the lower half carries the cabin's floor plate, so the cabin is
 * walked into from the landing like any room (the jambs' look may be wider, their collision is not).
 */
public class ElevatorStationBlock extends Block implements ElevatorShaftPart {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

	private static final double WALL = ElevatorClearance.WALL_PX;
	private static final double FAR = 16.0 - WALL;
	/**
	 * Back and side walls and the open doorway's jambs, flush with the side walls: the doorway is as wide as the
	 * tube's inside ({@link ElevatorClearance#OPENING}) and free up to the station's top, with no lintel to meet the
	 * head of a player standing on the cabin's floor plate. Built once per facing (ADR-023).
	 */
	private static final VoxelShape SHELL = Shapes.or(Block.box(0.0, 0.0, FAR, 16.0, 16.0, 16.0),
			Block.box(0.0, 0.0, 0.0, WALL, 16.0, FAR), Block.box(FAR, 0.0, 0.0, 16.0, 16.0, FAR));
	/** The cabin standing here lends the doorway's lower half its floor plate: the landing steps onto it. */
	private static final VoxelShape FLOOR = Block.box(0.0, 0.0, 0.0, 16.0, ElevatorClearance.FLOOR_PX, 16.0);
	private static final Map<Direction, VoxelShape> OPEN_LOWER = Shapes.rotateHorizontal(Shapes.or(SHELL, FLOOR));
	private static final Map<Direction, VoxelShape> OPEN_UPPER = Shapes.rotateHorizontal(SHELL);

	public ElevatorStationBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
				.setValue(HALF, DoubleBlockHalf.LOWER).setValue(OPEN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF, OPEN);
	}

	/** The doorway faces the player who places it; the upper half needs room above. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
			return null;
		}
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
			ItemStack stack) {
		level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
	}

	/** One half without the other is gone: the station is one thing, as a door is. */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
		if (direction.getAxis() == Direction.Axis.Y && lower == (direction == Direction.UP)) {
			boolean partner = neighborState.is(this) && neighborState.getValue(HALF) != state.getValue(HALF);
			return partner ? state : Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	/** A creative player breaking the upper half takes the lower half with it, and nothing drops. */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && player.preventsBlockDrops() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockPos below = pos.below();
			BlockState lower = level.getBlockState(below);
			if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
				level.setBlock(below, Blocks.AIR.defaultBlockState(),
						Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
				level.levelEvent(player, 2001, below, Block.getId(lower));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** The height of the cabin's floor at this station: the lower half's. */
	public static int floorOf(BlockPos pos, BlockState state) {
		return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.getY() - 1 : pos.getY();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		ElevatorWinchBlockEntity winch = ElevatorShaft.winchAbove(level, pos);
		if (winch == null) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.alaindustrial.elevator.no_winch"));
			return InteractionResult.SUCCESS;
		}
		winch.usePanel(serverPlayer, floorOf(pos, state), pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (!state.getValue(OPEN)) {
			return ElevatorShaftBlock.WALLS;
		}
		Direction facing = state.getValue(FACING);
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? OPEN_LOWER.get(facing) : OPEN_UPPER.get(facing);
	}

	/** Mobs walk in through the open doorway, and not through a closed one. */
	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return type == PathComputationType.LAND && state.getValue(OPEN);
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	@Override
	protected int getLightDampening(BlockState state) {
		return 0;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}
}
