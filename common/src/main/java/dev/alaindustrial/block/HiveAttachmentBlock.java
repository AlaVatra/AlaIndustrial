package dev.alaindustrial.block;

import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.TooltipRole;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The hive attachment (MOD-923): a glass jar pressed against a vanilla beehive or bee nest that shows the hive's honey
 * level and is the dock the apiary drones fly to. No block entity, no energy, no screen.
 *
 * <p><b>Where it may stand</b> is one rule, {@link #placementProblem}, asked both when it is placed and whenever a
 * neighbour changes: against a block of {@code #minecraft:beehives} that has {@code honey_level}, on one of its three
 * sides or on its top — never on its entrance face (a block with collision there locks the bees in,
 * {@code BeehiveBlockEntity.releaseOccupant}) and never under it; one per hive; and never in the cell in front of the
 * entrance of any hive next to it. A hive placed later with its entrance at the jar pops the jar off, as breaking or
 * turning the hive does.
 *
 * <p><b>The jar mirrors the hive.</b> Vanilla writes the hive's level with {@code setBlockAndUpdate} (flags 3), and
 * {@code Level.setBlock} without flag 16 calls {@code updateNeighbourShapes}, so {@link #updateShape} sees every change
 * of it and copies {@link #HONEY_LEVEL}. While a drone drains the jar ({@link #DRAINING}) the mirror pauses — a bee
 * may deliver nectar mid-drain and the jar must still go down — and comes back by {@link #restore}, or on its own by
 * the scheduled {@link #tick} {@link #RESTORE_TICKS} after the first step, so a station that vanished mid-drain cannot
 * leave the jar showing a level the hive does not have.
 */
public class HiveAttachmentBlock extends Block implements HasMachineTooltip {

	/** The hive face the jar is pressed to, pointing from the hive to the jar: four sides or up. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	/** The hive's honey level as the jar shows it, 0..5 — vanilla's own property, the one the hive carries. */
	public static final IntegerProperty HONEY_LEVEL = BlockStateProperties.LEVEL_HONEY;

	/** A drone is draining the jar: the jar steps down on its own and does not copy the hive meanwhile. */
	public static final BooleanProperty DRAINING = BooleanProperty.create("draining");

	/** Ticks after the first drain step at which the jar takes the hive's level again by itself (owner, item 4). */
	public static final int RESTORE_TICKS = 60;

	/** Why the jar may not stand somewhere, or {@link #OK}. Each refusal has its own chat line. */
	public enum Problem {
		OK,
		NOT_A_HIVE,
		ENTRANCE,
		BOTTOM,
		TAKEN,
		IN_FRONT_OF_ENTRANCE;

		public String translationKey() {
			return "message.alaindustrial.hive_attachment.refused." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/**
	 * The jar's box per facing, built once at class-init (ADR-023), never inside {@link #getShape}. On a side it is
	 * 6 px deep against the hive, 14.5 px tall and 10 px wide; on the top it is the same jar standing upright. DOWN
	 * is never placed (the rule forbids the bottom face) but the state exists, so it has the top box turned over.
	 */
	private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

	public HiveAttachmentBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP).setValue(HONEY_LEVEL, 0)
				.setValue(DRAINING, false));
	}

	private static Map<Direction, VoxelShape> buildShapes() {
		Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
		// The hive lies on the side opposite the facing; the jar's back is pressed to that face.
		shapes.put(Direction.EAST, Block.box(0, 0, 3, 6, 14.5, 13));
		shapes.put(Direction.WEST, Block.box(10, 0, 3, 16, 14.5, 13));
		shapes.put(Direction.SOUTH, Block.box(3, 0, 0, 13, 14.5, 6));
		shapes.put(Direction.NORTH, Block.box(3, 0, 10, 13, 14.5, 16));
		shapes.put(Direction.UP, Block.box(5, 0, 3, 11, 13.5, 13));
		shapes.put(Direction.DOWN, Block.box(5, 2.5, 3, 11, 16, 13));
		return shapes;
	}

	/** Whether {@code state} is a hive the jar can serve: in the beehives tag and carrying a honey level. */
	public static boolean isHive(BlockState state) {
		return state.is(BlockTags.BEEHIVES) && state.hasProperty(BeehiveBlock.HONEY_LEVEL);
	}

	/** The cell of the hive a jar at {@code pos} with this facing is pressed to. */
	public static BlockPos hivePos(BlockPos pos, Direction facing) {
		return pos.relative(facing.getOpposite());
	}

	/**
	 * Whether a jar may stand at {@code pos} pressed to the hive face {@code facing}. The "one per hive" search looks
	 * at the hive's other five faces only, so a jar re-checking itself does not find itself.
	 */
	public static Problem placementProblem(LevelReader level, BlockPos pos, Direction facing) {
		BlockPos hive = hivePos(pos, facing);
		BlockState hiveState = level.getBlockState(hive);
		if (!isHive(hiveState)) {
			return Problem.NOT_A_HIVE;
		}
		if (facing == Direction.DOWN) {
			return Problem.BOTTOM;
		}
		if (hiveState.hasProperty(BeehiveBlock.FACING) && hiveState.getValue(BeehiveBlock.FACING) == facing) {
			return Problem.ENTRANCE;
		}
		for (Direction other : Direction.values()) {
			if (other == facing) {
				continue;
			}
			BlockState there = level.getBlockState(hive.relative(other));
			if (there.getBlock() instanceof HiveAttachmentBlock && there.getValue(FACING) == other) {
				return Problem.TAKEN;
			}
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockState neighbour = level.getBlockState(pos.relative(side));
			if (isHive(neighbour) && neighbour.hasProperty(BeehiveBlock.FACING)
					&& neighbour.getValue(BeehiveBlock.FACING) == side.getOpposite()) {
				return Problem.IN_FRONT_OF_ENTRANCE;
			}
		}
		return Problem.OK;
	}

	/** The hive's honey level, or 0 when the cell holds no hive. */
	private static int hiveLevel(BlockState hive) {
		return isHive(hive) ? hive.getValue(BeehiveBlock.HONEY_LEVEL) : 0;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getClickedFace();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Problem problem = placementProblem(level, pos, facing);
		if (problem != Problem.OK) {
			Player player = context.getPlayer();
			if (player != null && !level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable(problem.translationKey())
						.withStyle(ChatFormatting.RED));
			}
			return null;
		}
		return defaultBlockState().setValue(FACING, facing)
				.setValue(HONEY_LEVEL, hiveLevel(level.getBlockState(hivePos(pos, facing))));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return placementProblem(level, pos, state.getValue(FACING)) == Problem.OK;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (!canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		if (state.getValue(DRAINING) || !neighbourPos.equals(hivePos(pos, state.getValue(FACING)))) {
			return state;
		}
		return state.setValue(HONEY_LEVEL, hiveLevel(neighbourState));
	}

	/**
	 * One drain step for the apiary station (slice 2): the jar shows one level less, stops copying the hive and
	 * schedules its own {@link #restore} {@link #RESTORE_TICKS} later. A pending restore is not scheduled twice, so
	 * the clock runs from the first step. Returns false when there is no jar here or it is already empty.
	 */
	public static boolean stepDown(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof HiveAttachmentBlock jar) || state.getValue(HONEY_LEVEL) == 0) {
			return false;
		}
		level.setBlock(pos, state.setValue(HONEY_LEVEL, state.getValue(HONEY_LEVEL) - 1).setValue(DRAINING, true),
				Block.UPDATE_ALL);
		level.scheduleTick(pos, jar, RESTORE_TICKS);
		return true;
	}

	/** Ends a drain: the jar copies the hive again at once (a cancelled dock, or the end of a finished one). */
	public static void restore(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof HiveAttachmentBlock)) {
			return;
		}
		int hive = hiveLevel(level.getBlockState(hivePos(pos, state.getValue(FACING))));
		BlockState next = state.setValue(DRAINING, false).setValue(HONEY_LEVEL, hive);
		if (next != state) {
			level.setBlock(pos, next, Block.UPDATE_ALL);
		}
	}

	/** The self-restore: a drain nobody ended still lets go of the jar. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		restore(level, pos);
	}

	/**
	 * An empty hand reads the hive's honey level off the jar, in the action bar. Vanilla asks this for a held item too
	 * (any main-hand item whose own use passes), so a full hand passes: the apiary map then writes the jar, and a
	 * block in hand is placed next to it.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!player.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			player.sendOverlayMessage(Component.translatable("message.alaindustrial.hive_attachment.level",
					state.getValue(HONEY_LEVEL), BeehiveBlock.MAX_HONEY_LEVELS).withStyle(ChatFormatting.GOLD));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(null,
				List.of(),
				List.of(MachineTooltipSpec.text("tooltip.alaindustrial.hive_attachment.where", TooltipRole.INFO)));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HONEY_LEVEL, DRAINING);
	}
}
