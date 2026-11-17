package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.MonitorPanelBlockEntity;
import dev.alaindustrial.compat.ServerDrops;
import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * One tile of a monitoring wall (MOD-480).
 *
 * <p>A panel is a whole block, not a plate, because the wall is meant to be BUILT: the player stacks
 * these the way they would stack any other block, and adjacent panels join into one network by
 * simple adjacency — free 3D shape, so a wall can turn a corner or wrap a pillar. The face the
 * numbers appear on is the one the player faced when placing it, which is what lets a wrapped wall
 * read correctly from every side.
 *
 * <p>Choosing what a panel watches is the item frame's idiom, deliberately: right-click with an item
 * to set it, empty hand to take it back. One panel shows one kind of item — build another panel to
 * watch another kind, which is the whole growth mechanic of the wall.
 */
public class MonitorPanelBlock extends BaseEntityBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;

	/*
	 * Connector flags (MOD-1013): what the model draws on the back of the panel, nothing else. The
	 * network joins panels by adjacency and never reads these. "cw" and "ccw" are relative to FACING
	 * ({@code facing.getClockWise()} / {@code getCounterClockWise()}), so a rotated wall keeps them.
	 */
	/** A panel with the same facing directly above. */
	public static final BooleanProperty WIRE_UP = BooleanProperty.create("wire_up");
	/** A panel with the same facing directly below. */
	public static final BooleanProperty WIRE_DOWN = BooleanProperty.create("wire_down");
	/** A panel with the same facing on the {@code facing.getClockWise()} side. */
	public static final BooleanProperty WIRE_CW = BooleanProperty.create("wire_cw");
	/** A panel with the same facing on the {@code facing.getCounterClockWise()} side. */
	public static final BooleanProperty WIRE_CCW = BooleanProperty.create("wire_ccw");
	/**
	 * A monitor core directly below, and which way it faces: the panel stands on it. The base of the stand
	 * is turned with the core's top, the struts with the panel (see {@link MonitorStand}).
	 */
	public static final EnumProperty<MonitorStand> STAND = EnumProperty.create("stand", MonitorStand.class);

	private static final VoxelShape NORTH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 4.1);
	private static final VoxelShape EAST_SHAPE = Block.box(11.9, 0.0, 0.0, 16.0, 16.0, 16.0);
	private static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 11.9, 16.0, 16.0, 16.0);
	private static final VoxelShape WEST_SHAPE = Block.box(0.0, 0.0, 0.0, 4.1, 16.0, 16.0);

	public MonitorPanelBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false)
				.setValue(WIRE_UP, false).setValue(WIRE_DOWN, false).setValue(WIRE_CW, false)
				.setValue(WIRE_CCW, false).setValue(STAND, MonitorStand.NONE));
	}

	/**
	 * The codec Minecraft 26.2 asks every block for ({@code BaseEntityBlock.codec()} is abstract there; 26.3
	 * removed block codecs): the unit codec of this very instance, as the mod's own block bases answer it
	 * (MOD-703). No {@code @Override} on purpose: on 26.2 it overrides, on 26.3 it is a method of its own that
	 * nothing calls, so this source is the same on both lines. 26.2 reads block codecs only in the datagen
	 * block-list report ({@code BlockTypes.CODEC} -> {@code BlockListReport}, javap).
	 */
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return MapCodec.unit(this);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ACTIVE, WIRE_UP, WIRE_DOWN, WIRE_CW, WIRE_CCW, STAND);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(ACTIVE, false);
		return withConnectors(state, context.getLevel(), context.getClickedPos());
	}

	/**
	 * {@code state} with all five connector flags derived from the four in-plane neighbours and the
	 * block below. The single source of truth for placement and for the once-per-load check in
	 * {@link MonitorPanelBlockEntity}; {@link #updateShape} applies the same rule one face at a time.
	 */
	public static BlockState withConnectors(BlockState state, BlockGetter level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockState below = level.getBlockState(pos.below());
		return state
				.setValue(WIRE_UP, joins(facing, level.getBlockState(pos.above())))
				.setValue(WIRE_DOWN, joins(facing, below))
				.setValue(WIRE_CW, joins(facing, level.getBlockState(pos.relative(facing.getClockWise()))))
				.setValue(WIRE_CCW, joins(facing, level.getBlockState(pos.relative(facing.getCounterClockWise()))))
				.setValue(STAND, standOn(below));
	}

	/** The stand a panel gets from the block below it: a core, with its facing, or nothing. */
	private static MonitorStand standOn(BlockState below) {
		if (below.getBlock() instanceof MonitorCoreBlock && below.hasProperty(MonitorCoreBlock.FACING)) {
			return MonitorStand.onCoreFacing(below.getValue(MonitorCoreBlock.FACING));
		}
		return MonitorStand.NONE;
	}

	/** A connector is drawn only to a panel of the same wall plane: same block, same facing. */
	private static boolean joins(Direction facing, BlockState neighbour) {
		return neighbour.getBlock() instanceof MonitorPanelBlock && neighbour.getValue(FACING) == facing;
	}

	/**
	 * Re-derive the one flag that faces the changed neighbour, from the state handed in (no level
	 * read). The face behind and the face in front carry no connector.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
			BlockState neighbourState, RandomSource random) {
		Direction facing = state.getValue(FACING);
		boolean joins = joins(facing, neighbourState);
		if (directionToNeighbour == Direction.UP) {
			return state.setValue(WIRE_UP, joins);
		}
		if (directionToNeighbour == Direction.DOWN) {
			return state.setValue(WIRE_DOWN, joins)
					.setValue(STAND, standOn(neighbourState));
		}
		if (directionToNeighbour == facing.getClockWise()) {
			return state.setValue(WIRE_CW, joins);
		}
		if (directionToNeighbour == facing.getCounterClockWise()) {
			return state.setValue(WIRE_CCW, joins);
		}
		return state;
	}

	@Override
	protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
			BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case EAST -> EAST_SHAPE;
			case SOUTH -> SOUTH_SHAPE;
			case WEST -> WEST_SHAPE;
			default -> NORTH_SHAPE;
		};
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	/**
	 * A mirror turns the facing like a rotation, but it also swaps left and right: the neighbour that
	 * was on the clockwise side is now on the counter-clockwise one. Up and down stay, and so does the stand:
	 * it copies the core below, which keeps its own facing through a rotation or a mirror.
	 */
	@SuppressWarnings("deprecation")
	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		BlockState turned = state.rotate(mirror.getRotation(state.getValue(FACING)));
		if (mirror == Mirror.NONE) {
			return turned;
		}
		return turned.setValue(WIRE_CW, state.getValue(WIRE_CCW)).setValue(WIRE_CCW, state.getValue(WIRE_CW));
	}

	/**
	 * Set the watched item.
	 *
	 * <p>Returns {@code TRY_WITH_EMPTY_HAND} rather than {@code PASS} when the panel is already
	 * filled: in 26.2 a {@code PASS} here does NOT fall through to {@link #useWithoutItem}, so taking
	 * the filter back while holding anything at all would silently do nothing.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof MonitorPanelBlockEntity panel)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!panel.getFilter().isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		panel.setFilter(stack.copyWithCount(1));
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 1.2f);
		// Say straight away why this panel will (or will not) show a number. Without it the player
		// is left reading a cross with no idea that the answer is a card in the core.
		if (level instanceof ServerLevel serverLevel) {
			reportCapacity(serverLevel, pos, player);
		}
		return InteractionResult.SUCCESS;
	}

	/** Take the watched item back. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof MonitorPanelBlockEntity panel)) {
			return InteractionResult.PASS;
		}
		if (panel.getFilter().isEmpty()) {
			// An empty panel says what it is for. Without this it is a blank slab that swallows clicks.
			if (!level.isClientSide()) {
				player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
						"gui.alaindustrial.monitor_panel.hint"));
			}
			return InteractionResult.SUCCESS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ItemStack filter = panel.getFilter().copy();
		panel.setFilter(ItemStack.EMPTY);
		if (!player.getInventory().add(filter)) {
			ServerDrops.drop(player, filter);
		}
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 1.2f);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			MonitorNetworkManager.onNeighbourChanged(serverLevel, pos);
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state,
			net.minecraft.world.entity.@Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.getBlockEntity(pos) instanceof MonitorPanelBlockEntity panel) {
			panel.ensureRegistered();
		}
	}

	/** Tell the player, in numbers, whether the wall can serve this panel. */
	private static void reportCapacity(ServerLevel level, BlockPos pos, Player player) {
		dev.alaindustrial.core.monitor.MonitorNetwork network =
				dev.alaindustrial.core.monitor.MonitorNetworkManager.networkAt(level, pos);
		if (network == null) {
			return;
		}
		dev.alaindustrial.core.monitor.MonitorNetwork.Capacity capacity = network.capacity();
		if (!capacity.hasCore()) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
					"gui.alaindustrial.monitor.no_core"));
		} else if (capacity.watchedTypes() > capacity.allowance()) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
					"gui.alaindustrial.monitor.capacity_full",
					capacity.watchedTypes(), capacity.allowance()));
		}
	}

	/** Panels join the network to each other and to the core — never to a wire (see the wire's javadoc). */
	public static boolean isPanelOrCore(net.minecraft.world.level.LevelReader level, BlockPos pos) {
		Block block = level.getBlockState(pos).getBlock();
		return block instanceof MonitorPanelBlock || block instanceof MonitorCoreBlock;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MonitorPanelBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof MonitorPanelBlockEntity panel) {
				panel.ensureRegistered();
			}
		};
	}
}
