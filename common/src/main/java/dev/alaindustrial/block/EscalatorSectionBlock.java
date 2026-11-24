package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.EscalatorDriveBlockEntity;
import dev.alaindustrial.core.escalator.EscalatorRules;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * One step section of an escalator (MOD-969): one block of a 1:1 slope, built diagonally like a staircase, two
 * blocks tall like a door (owner's choice of sketch B, 2026-11-22). The {@link #HALF lower half} is the stair between
 * two full side walls; the upper half carries the glass balustrade and the handrail, and is put up and taken down
 * with the lower one. Every part of the model stays inside its own block.
 *
 * <p>No block entity: a long band is mostly sections, and everything a section needs to carry a rider lives in its
 * block state, which reaches every client by itself. {@link #FACING} is the climbing direction, {@link #MODE} what
 * the drive set (stopped, ready, moving), {@link #REVERSED} whether the band runs down and {@link #FAST} whether an
 * MV drive pulls it. The drive writes the last three on both halves of every section of its chain; the upper half
 * also copies them from the lower one whenever that changes ({@link #updateShape}).
 *
 * <p><b>Ends.</b> {@link #BOTTOM} and {@link #TOP} say whether this is the first or the last section of its band
 * (no section of the same facing continues it down or up the diagonal), on both halves. The model reads them: the
 * handrail runs on into the next section's as one line and ends in a newel cap only at the two ends, and a stopped
 * stair closes the 3 px up to the next section's floor with a riser where another section follows (owner,
 * 2026-11-24). Each half reads them from its blocks ({@link #withEnds}) whenever it is placed or a neighbour
 * changes: the next section's stair half is the balustrade half's neighbour, the previous section's balustrade half
 * the stair half's.
 *
 * <p><b>Shape.</b> Lower half: {@link EscalatorRules#SUBSTEPS} steps 2 px apart between the two 2 px walls; upper
 * half: the two balustrades, stepped under the rail line. Both assembled once per facing at class load (ADR-023).
 * The highest step is 15 px, so a rider's feet stay inside the lower cell, where {@link #entityInside} reaches them.
 *
 * <p><b>Placing.</b> A section takes the facing of the section or drive one step down the diagonal, so a band is
 * built by placing each section on the step of the last; with none there it faces the way the player looks.
 */
public class EscalatorSectionBlock extends Block implements HasMachineTooltip {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	public static final EnumProperty<EscalatorMode> MODE = EnumProperty.create("mode", EscalatorMode.class);
	public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");
	public static final BooleanProperty FAST = BooleanProperty.create("fast");
	/** No section continues this one down the diagonal: the handrail's bottom end. */
	public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
	/** No section continues this one up the diagonal: the handrail's top end. */
	public static final BooleanProperty TOP = BooleanProperty.create("top");

	/** Longest diagonal walk when a section looks for its drive: the longest MV chain and then some. */
	public static final int WALK_LIMIT = 64;

	/** Thickness of a side wall and of a balustrade's collision, px: the band between them is 12 px wide. */
	private static final int WALL = 2;

	/** The stair and the balustrade for a section climbing north (low end south), turned onto the other sides once. */
	private static final Map<Direction, VoxelShape> LOWER = Shapes.rotateHorizontal(lowerNorth());
	private static final Map<Direction, VoxelShape> UPPER = Shapes.rotateHorizontal(upperNorth());

	public EscalatorSectionBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
				.setValue(HALF, DoubleBlockHalf.LOWER).setValue(MODE, EscalatorMode.OFF)
				.setValue(REVERSED, false).setValue(FAST, false).setValue(BOTTOM, true).setValue(TOP, true));
	}

	private static VoxelShape lowerNorth() {
		VoxelShape shape = Shapes.or(Block.box(0, 0, 0, WALL, 16, 16), Block.box(16 - WALL, 0, 0, 16, 16, 16));
		int run = 16 / EscalatorRules.SUBSTEPS;
		for (int k = 0; k < EscalatorRules.SUBSTEPS; k++) {
			double southEdge = 16 - k * run;
			shape = Shapes.or(shape,
					Block.box(WALL, 0, southEdge - run, 16 - WALL, EscalatorRules.stepTopPixels(k), southEdge));
		}
		return shape.optimize();
	}

	/** The balustrades' top edge is the cell's diagonal (y = r): four 4 px steps under it on each side. */
	private static VoxelShape upperNorth() {
		VoxelShape shape = Shapes.empty();
		for (int k = 0; k < 4; k++) {
			double north = 16 - 4 * k - 4;
			shape = Shapes.or(shape, Block.box(0, 0, north, WALL, 4 * k + 4, north + 4),
					Block.box(16 - WALL, 0, north, 16, 4 * k + 4, north + 4));
		}
		return shape.optimize();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF, MODE, REVERSED, FAST, BOTTOM, TOP);
	}

	/**
	 * {@code state} (either half of a section at {@code pos}) with its {@link #BOTTOM} and {@link #TOP} read from the
	 * blocks: a section continues down when the previous one's balustrade half stands beside its stair half, one
	 * block back, and up when the next one's stair half stands beside its balustrade half, one block ahead.
	 */
	public static BlockState withEnds(BlockState state, BlockGetter level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
		boolean below = isHalf(level.getBlockState(lower.relative(facing.getOpposite())), facing,
				DoubleBlockHalf.UPPER);
		boolean above = isHalf(level.getBlockState(lower.above().relative(facing)), facing, DoubleBlockHalf.LOWER);
		return state.setValue(BOTTOM, !below).setValue(TOP, !above);
	}

	private static boolean isHalf(BlockState state, Direction facing, DoubleBlockHalf half) {
		return state.getBlock() instanceof EscalatorSectionBlock && state.getValue(FACING) == facing
				&& state.getValue(HALF) == half;
	}

	/** Whether {@code state} is the stair half of a section, the half the chain, the riders and the drive count. */
	public static boolean isLower(BlockState state) {
		return state.getBlock() instanceof EscalatorSectionBlock && state.getValue(HALF) == DoubleBlockHalf.LOWER;
	}

	/**
	 * Needs the cell above for the balustrade. Faces like the section or drive one step down the diagonal, so a
	 * band builds itself the right way round; with none there, the way the player looks (up the future band).
	 */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
			return null;
		}
		Direction below = EscalatorChain.facingFromBelow(level, pos);
		return withEnds(defaultBlockState().setValue(FACING, below != null ? below : context.getHorizontalDirection()),
				level, pos);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER).get(state.getValue(FACING));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/**
	 * The two halves stand and fall together: a half whose partner is gone becomes air (dropping the item from the
	 * lower half, like a door); the upper half takes the lower one's facing, mode, direction and tier. Either half
	 * then reads its ends again ({@link #withEnds}).
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
		if (direction == (lower ? Direction.UP : Direction.DOWN)) {
			if (!neighbour.is(this) || neighbour.getValue(HALF) == state.getValue(HALF)) {
				return Blocks.AIR.defaultBlockState();
			}
			if (!lower) {
				return withEnds(state.setValue(FACING, neighbour.getValue(FACING))
						.setValue(MODE, neighbour.getValue(MODE)).setValue(REVERSED, neighbour.getValue(REVERSED))
						.setValue(FAST, neighbour.getValue(FAST)), level, pos);
			}
		}
		BlockState next = super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
		return next.is(this) ? withEnds(next, level, pos) : next;
	}

	/** Broken from the top in creative: the lower half goes without a drop, as a door's does. */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && player.preventsBlockDrops() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockPos below = pos.below();
			BlockState lower = level.getBlockState(below);
			if (isLower(lower)) {
				level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
				// 2001 = break particles and sound; the constant is named differently on each game version.
				level.levelEvent(player, 2001, below, Block.getId(lower));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/**
	 * A moving band carries whoever stands on it; a sleeping one wakes its drive. Waking walks the diagonal down to
	 * the drive, so it runs on the server only and only while the section sleeps: a moving drive finds its riders
	 * on its own. The balustrade half carries nobody.
	 */
	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
			InsideBlockEffectApplier effectApplier, boolean isInside) {
		if (state.getValue(HALF) != DoubleBlockHalf.LOWER) {
			return;
		}
		EscalatorMode mode = state.getValue(MODE);
		if (mode == EscalatorMode.RUNNING) {
			EscalatorCarry.carry(level, pos, state, entity);
		} else if (mode == EscalatorMode.READY && level instanceof ServerLevel server && EscalatorCarry.rides(entity)
				&& pos.equals(EscalatorCarry.ownerCell(level, entity))) {
			wakeDrive(server, pos, state.getValue(FACING));
		}
	}

	/** An empty-hand click on either half names the band's state above the hotbar, or why it has no drive. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
			serverPlayer.sendOverlayMessage(statusAt(level, lower, state.getValue(FACING)));
		}
		return InteractionResult.SUCCESS;
	}

	/** The status line of the band {@code lower} belongs to, read from its drive. */
	public static Component statusAt(Level level, BlockPos lower, Direction facing) {
		BlockPos drive = EscalatorChain.findDrive(level, lower, facing, WALK_LIMIT);
		if (drive != null && level.getBlockEntity(drive) instanceof EscalatorDriveBlockEntity be) {
			return be.statusMessage();
		}
		return Component.translatable("message.alaindustrial.escalator.no_drive");
	}

	/** Tell the drive of the chain {@code pos} belongs to that someone stepped on; nothing when there is none. */
	static void wakeDrive(ServerLevel level, BlockPos pos, Direction facing) {
		BlockPos drive = EscalatorChain.findDrive(level, pos, facing, WALK_LIMIT);
		if (drive != null && level.getBlockEntity(drive) instanceof EscalatorDriveBlockEntity be) {
			be.onContact(level.getGameTime());
		}
	}

	/** Ask the drive of the chain {@code pos} belongs to to look at its chain again. */
	static void rescanDrive(ServerLevel level, BlockPos pos, Direction facing) {
		BlockPos drive = EscalatorChain.findDrive(level, pos, facing, WALK_LIMIT);
		if (drive != null && level.getBlockEntity(drive) instanceof EscalatorDriveBlockEntity be) {
			be.requestRescan();
		}
	}

	/**
	 * A new lower half puts up its balustrade (by whatever means it was placed: a player, a command, the demo
	 * stand, a test) and joins a chain: its drive looks again at once instead of at its next periodic check.
	 * Either half, placed or set by any means, first reads its ends from the blocks around it: a state written
	 * whole by a command or the demo stand carries the default ends.
	 */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		BlockState ends = withEnds(state, level, pos);
		if (ends != state) {
			level.setBlock(pos, ends, Block.UPDATE_CLIENTS);
		}
		if (oldState.is(this) || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
			return;
		}
		BlockPos above = pos.above();
		if (level.getBlockState(above).isAir()) {
			BlockState upper = ends.setValue(HALF, DoubleBlockHalf.UPPER);
			level.setBlock(above, withEnds(upper, level, above), Block.UPDATE_ALL);
		}
		rescanDrive(server, pos, state.getValue(FACING));
	}

	/**
	 * A section is gone: the sections above the gap lose their drive, so they stop here and now (the drive could
	 * never reach them again to do it), and the drive below looks at what is left of its chain. Skipped when the
	 * block only changed state, and for the balustrade half (its stair half follows and does this).
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		if (level.getBlockState(pos).is(this) || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
			return;
		}
		Direction facing = state.getValue(FACING);
		BlockPos up = EscalatorChain.above(pos, facing);
		for (int i = 0; i < WALK_LIMIT && EscalatorChain.isSection(level.getBlockState(up), facing); i++) {
			EscalatorDriveBlockEntity.writeSection(level, up, EscalatorMode.OFF, null, null);
			up = EscalatorChain.above(up, facing);
		}
		BlockPos down = EscalatorChain.below(pos, facing);
		if (EscalatorChain.isSection(level.getBlockState(down), facing)) {
			rescanDrive(level, down, facing);
		} else if (level.getBlockEntity(down) instanceof EscalatorDriveBlockEntity be) {
			// The bottom section itself went: its drive is the floor block one step down the diagonal.
			be.requestRescan();
		}
	}

	/** Hover tooltip of the item (MOD-716, ADR-040): what it is for. It takes no EU itself, so no tier line. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(null, List.of(), List.of());
	}
}
