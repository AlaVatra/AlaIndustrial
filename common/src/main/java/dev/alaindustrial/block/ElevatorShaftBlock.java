package dev.alaindustrial.block;

import dev.alaindustrial.core.elevator.ElevatorClearance;
import dev.alaindustrial.core.elevator.ElevatorShaftPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The elevator's shaft frame (MOD-769): four steel posts and four glass panes, one cell of the glass tube the cabin
 * rides in. Stacked frames join by themselves: {@code up} and {@code down} say that the tube goes on above or below,
 * and only an end of the tube shows a graphite ring, so a finished shaft reads as one sheet of glass.
 *
 * <p>Only the four walls collide: the inside is the cabin's way, and nobody gets into the shaft but through a
 * station's open door. The model is open glass, so it hides none of its neighbours' faces and lets light through
 * (R-PHY-05: a block that is not a full collision cube does not occlude).
 */
public class ElevatorShaftBlock extends Block implements ElevatorShaftPart {

	public static final BooleanProperty UP = BlockStateProperties.UP;
	public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

	/** The four walls, {@link ElevatorClearance#WALL_PX} thick, built once (ADR-023). */
	static final VoxelShape WALLS = walls(ElevatorClearance.WALL_PX);

	private static VoxelShape walls(double wall) {
		double far = 16.0 - wall;
		return Shapes.or(Block.box(0.0, 0.0, 0.0, 16.0, 16.0, wall), Block.box(0.0, 0.0, far, 16.0, 16.0, 16.0),
				Block.box(0.0, 0.0, wall, wall, 16.0, far), Block.box(far, 0.0, wall, 16.0, 16.0, far));
	}

	public ElevatorShaftBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(UP, false).setValue(DOWN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(UP, DOWN);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		return joined(defaultBlockState(), context.getLevel().getBlockState(pos.above()),
				context.getLevel().getBlockState(pos.below()));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (direction == Direction.UP) {
			return state.setValue(UP, continuesAbove(neighborState));
		}
		if (direction == Direction.DOWN) {
			return state.setValue(DOWN, neighborState.getBlock() instanceof ElevatorShaftPart);
		}
		return state;
	}

	private static BlockState joined(BlockState state, BlockState above, BlockState below) {
		return state.setValue(UP, continuesAbove(above)).setValue(DOWN, below.getBlock() instanceof ElevatorShaftPart);
	}

	/** The tube goes on above: another cell of the shaft, or the winch that closes it. */
	private static boolean continuesAbove(BlockState above) {
		return above.getBlock() instanceof ElevatorShaftPart || above.getBlock() instanceof ElevatorWinchBlock;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return WALLS;
	}

	/** Mobs do not walk into the shaft: there is no way in but a station's door. */
	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
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
