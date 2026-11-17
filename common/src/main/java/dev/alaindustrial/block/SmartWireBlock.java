package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.SmartWireBlockEntity;
import dev.alaindustrial.core.monitor.ContainerScan;
import dev.alaindustrial.core.monitor.MonitorNetwork;
import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The smart wire (MOD-480): a line that carries NUMBERS, not items.
 *
 * <p>It links the containers a player already owns to a monitor core, and nothing it touches ever
 * moves — the whole system only ever looks. That is also why it has no face modes: with no direction
 * of flow there is nothing to switch.
 *
 * <p><b>A flat ribbon with a plug (MOD-1014).</b> Each face says what it is joined to ({@link WireLink}):
 * the ribbon continues toward another wire or the core, and a plug goes into a container only when the
 * wall gives that container one of its {@link MonitorNetwork#WIRE_CHANNELS} channels. {@link #FILL} is
 * the number of channels the whole wall uses, written into every segment, so the ribbon shows that many
 * lit cores. The wire sets {@code wire}/{@code none} itself; {@code device} and {@code fill} come only
 * from the network ({@link SmartWireBlockEntity#showChannels}).
 *
 * <p><b>The shapes are a table, built once.</b> {@code initCache()} asks every state for its shape twenty
 * times over (ADR-023). The 6561 states share 729 shapes, since {@code fill} changes the look but not the
 * geometry, and all of them are assembled into {@link #SHAPES} at class-init.
 */
public class SmartWireBlock extends BaseEntityBlock {

	public static final MapCodec<SmartWireBlock> CODEC = simpleCodec(SmartWireBlock::new);

	public static final EnumProperty<WireLink> NORTH = EnumProperty.create("north", WireLink.class);
	public static final EnumProperty<WireLink> EAST = EnumProperty.create("east", WireLink.class);
	public static final EnumProperty<WireLink> SOUTH = EnumProperty.create("south", WireLink.class);
	public static final EnumProperty<WireLink> WEST = EnumProperty.create("west", WireLink.class);
	public static final EnumProperty<WireLink> UP = EnumProperty.create("up", WireLink.class);
	public static final EnumProperty<WireLink> DOWN = EnumProperty.create("down", WireLink.class);
	/** How many channels the wall uses: one value for the whole network, in every segment. */
	public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, MonitorNetwork.WIRE_CHANNELS);

	public static final Map<Direction, EnumProperty<WireLink>> LINK_BY_DIRECTION = Map.of(
			Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH,
			Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN);

	// The geometry in pixels, fitted to the exported models (tools/model_sources/smart_wire/SHAPE.md).
	// Horizontal parts are written for the north face and turned for the other three.
	/** The ribbon's width across its run. */
	private static final double RIBBON_MIN = 3;
	private static final double RIBBON_MAX = 13;
	/**
	 * The flat ribbon is drawn 2 px thick (y 2..4), but its shape is 4 px (y 1..5), the height of the
	 * junction box: a 2 px target is easy to miss at a glancing angle, and the wider band keeps the whole
	 * outline one height. It is still far below the step height, so walking over a wire is unchanged.
	 */
	private static final double RIBBON_BOTTOM = 1;
	private static final double RIBBON_TOP = 5;
	/** How far into the cell a joined face reaches: to the middle, where the two halves meet. */
	private static final double HALF = 8;
	/** How far past the middle the tail of a ribbon that ends here reaches. */
	private static final double TAIL = 13;
	/** A vertical run stands on edge in the middle of the cell, this thick. */
	private static final double RISER_MIN = 7;
	private static final double RISER_MAX = 9;
	/** Where the bend of a rise (and the tail of a ribbon that ends going up) meets the vertical run. */
	private static final double RISE_TOP = 7;
	/** The top of the tail of a ribbon that ends going down. */
	private static final double DROP_TAIL_TOP = 12;
	/** The plug: socket, tongue and grip, a little taller and deeper than the ribbon. */
	private static final double PLUG_BOTTOM = 1;
	private static final double PLUG_TOP = 6;
	private static final double PLUG_RISER_MIN = 6;
	private static final double PLUG_RISER_MAX = 11;
	private static final double PLUG_DOWN_TOP = 7;

	/** The flat pad of a segment with no joins, and the floor every horizontal join meets. */
	private static final VoxelShape PAD = Block.box(RIBBON_MIN, RIBBON_BOTTOM, 2, RIBBON_MAX, RIBBON_TOP, 14);
	/** The junction box: drawn where three or more faces join, or where a bend cannot be a fold. */
	private static final VoxelShape BOX = Block.box(2, 1, 2, 14, 5, 14);
	/** The fold of a flat bend between two horizontal wires. */
	private static final VoxelShape FOLD = Block.box(RIBBON_MIN, RIBBON_BOTTOM, RIBBON_MIN, RIBBON_MAX, RIBBON_TOP,
			RIBBON_MAX);
	/** The ribbon on edge where a north-south run bends up, and the tail of a ribbon that ends going up. */
	private static final VoxelShape RISE = Block.box(RIBBON_MIN, RIBBON_BOTTOM, RISER_MIN, RIBBON_MAX, RISE_TOP,
			RISER_MAX);
	/** The tail of a ribbon that ends going down: the middle of the drop and the edge above it. */
	private static final VoxelShape DROP_TAIL = Block.box(RIBBON_MIN, RIBBON_BOTTOM, RISER_MIN, RIBBON_MAX,
			DROP_TAIL_TOP, RISER_MAX);

	private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
	private static final Map<Direction, VoxelShape> PLUGS = new EnumMap<>(Direction.class);
	/** The tail past the middle of a ribbon whose only join is this face (the "end" of SHAPE.md). */
	private static final Map<Direction, VoxelShape> TAILS = new EnumMap<>(Direction.class);

	static {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			ARMS.put(dir, turned(dir, RIBBON_MIN, RIBBON_BOTTOM, 0, RIBBON_MAX, RIBBON_TOP, HALF));
			PLUGS.put(dir, turned(dir, RIBBON_MIN, PLUG_BOTTOM, 0, RIBBON_MAX, PLUG_TOP, HALF));
			TAILS.put(dir, turned(dir, RIBBON_MIN, RIBBON_BOTTOM, HALF, RIBBON_MAX, RIBBON_TOP, TAIL));
		}
		TAILS.put(Direction.UP, RISE);
		TAILS.put(Direction.DOWN, DROP_TAIL);
		ARMS.put(Direction.UP, Block.box(RIBBON_MIN, RIBBON_TOP, RISER_MIN, RIBBON_MAX, 16, RISER_MAX));
		ARMS.put(Direction.DOWN, Block.box(RIBBON_MIN, 0, RISER_MIN, RIBBON_MAX, RIBBON_TOP, RISER_MAX));
		PLUGS.put(Direction.UP, Block.box(RIBBON_MIN, RIBBON_TOP, PLUG_RISER_MIN, RIBBON_MAX, 16, PLUG_RISER_MAX));
		PLUGS.put(Direction.DOWN, Block.box(RIBBON_MIN, 0, PLUG_RISER_MIN, RIBBON_MAX, PLUG_DOWN_TOP,
				PLUG_RISER_MAX));
	}

	/** A box given for the north face, turned clockwise (seen from above) to {@code face}, as the blockstate does. */
	private static VoxelShape turned(Direction face, double x0, double y0, double z0, double x1, double y1,
			double z1) {
		return switch (face) {
			case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
			case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
			case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
			default -> Block.box(x0, y0, z0, x1, y1, z1);
		};
	}

	private static final int LINKS = WireLink.values().length;

	/**
	 * Every shape this block can have, indexed by the six faces as base-3 digits ({@link #shapeIndex}).
	 * Built once here rather than assembled inside {@code getShape} — see the class javadoc and ADR-023.
	 */
	private static final VoxelShape[] SHAPES = buildShapes();

	public SmartWireBlock(Properties properties) {
		super(properties);
		BlockState state = stateDefinition.any().setValue(FILL, 0);
		for (EnumProperty<WireLink> prop : LINK_BY_DIRECTION.values()) {
			state = state.setValue(prop, WireLink.NONE);
		}
		registerDefaultState(state);
	}

	private static VoxelShape[] buildShapes() {
		int faces = Direction.values().length;
		int count = 1;
		for (int i = 0; i < faces; i++) {
			count *= LINKS;
		}
		VoxelShape[] shapes = new VoxelShape[count];
		for (int index = 0; index < count; index++) {
			WireLink[] links = new WireLink[faces];
			int rest = index;
			for (Direction dir : Direction.values()) {
				links[dir.ordinal()] = WireLink.values()[rest % LINKS];
				rest /= LINKS;
			}
			VoxelShape shape = centre(links);
			for (Direction dir : Direction.values()) {
				WireLink link = links[dir.ordinal()];
				if (link == WireLink.WIRE) {
					shape = Shapes.or(shape, ARMS.get(dir));
				} else if (link == WireLink.DEVICE) {
					shape = Shapes.or(shape, PLUGS.get(dir));
				}
			}
			shapes[index] = shape;
		}
		return shapes;
	}

	/**
	 * The middle of the cell, by the same classes the models use (SHAPE.md): a lone pad, the tail of a
	 * ribbon with one join, a fold for a flat bend of two wires, the ribbon on edge of a north-south rise
	 * up, the junction box for three or more joins, an east-west run meeting a vertical, or a bend or rise
	 * that ends in a plug; otherwise the arms meet on their own.
	 */
	private static VoxelShape centre(WireLink[] links) {
		int joined = 0;
		for (WireLink link : links) {
			if (link != WireLink.NONE) {
				joined++;
			}
		}
		if (joined == 0) {
			return PAD;
		}
		if (joined >= 3) {
			return BOX;
		}
		Direction first = null;
		Direction second = null;
		for (Direction dir : Direction.values()) {
			if (links[dir.ordinal()] != WireLink.NONE) {
				if (first == null) {
					first = dir;
				} else {
					second = dir;
				}
			}
		}
		if (second == null) {
			return TAILS.get(first);
		}
		if (first.getOpposite() == second) {
			return Shapes.empty();
		}
		boolean plugged = links[first.ordinal()] == WireLink.DEVICE || links[second.ordinal()] == WireLink.DEVICE;
		boolean vertical = first.getAxis() == Direction.Axis.Y || second.getAxis() == Direction.Axis.Y;
		Direction flat = first.getAxis() == Direction.Axis.Y ? second : first;
		if (plugged || (vertical && flat.getAxis() == Direction.Axis.X)) {
			return BOX;
		}
		if (vertical) {
			// A rise down needs nothing more: the arm down already fills the drop beside the flat arm.
			return first == Direction.UP || second == Direction.UP ? RISE : Shapes.empty();
		}
		return FOLD;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, FILL);
	}

	private static int shapeIndex(BlockState state) {
		int index = 0;
		int weight = 1;
		for (Direction dir : Direction.values()) {
			index += state.getValue(LINK_BY_DIRECTION.get(dir)).ordinal() * weight;
			weight *= LINKS;
		}
		return index;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return SHAPES[shapeIndex(state)];
	}

	/** The ribbon continues toward wires and the core; a plug waits for the network to grant a channel. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		for (Map.Entry<Direction, EnumProperty<WireLink>> entry : LINK_BY_DIRECTION.entrySet()) {
			if (isNetworkNode(level, pos.relative(entry.getKey()))) {
				state = state.setValue(entry.getValue(), WireLink.WIRE);
			}
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
			BlockState neighbourState, RandomSource random) {
		return state.setValue(LINK_BY_DIRECTION.get(directionToNeighbour),
				linkToward(state, level, pos, directionToNeighbour));
	}

	/**
	 * What the face toward {@code dir} shows, as far as the wire itself can tell.
	 *
	 * <p>A wire or core next door is always {@code wire}. A plug is kept only while the neighbour is still
	 * a readable container, so a chest that is broken or replaced pulls its plug at once; whether a NEW
	 * container gets a plug is the network's call, made on its next refresh. Off the server the
	 * container cannot be checked, and the face keeps what the server last sent.
	 */
	private static WireLink linkToward(BlockState state, LevelReader level, BlockPos pos, Direction dir) {
		BlockPos neighbour = pos.relative(dir);
		if (isNetworkNode(level, neighbour)) {
			return WireLink.WIRE;
		}
		WireLink current = state.getValue(LINK_BY_DIRECTION.get(dir));
		if (!(level instanceof ServerLevel serverLevel)) {
			return current == WireLink.WIRE ? WireLink.NONE : current;
		}
		return current == WireLink.DEVICE && ContainerScan.isReadable(serverLevel, neighbour)
				? WireLink.DEVICE
				: WireLink.NONE;
	}

	/**
	 * {@code state} with every face re-read from the neighbours by the wire's own rule. Used once per
	 * load: a wire saved with the old boolean faces loads with every face {@code none} (MOD-1014).
	 */
	public static BlockState withLinksFromNeighbours(BlockState state, LevelReader level, BlockPos pos) {
		BlockState result = state;
		for (Map.Entry<Direction, EnumProperty<WireLink>> entry : LINK_BY_DIRECTION.entrySet()) {
			result = result.setValue(entry.getValue(), linkToward(state, level, pos, entry.getKey()));
		}
		return result;
	}

	/**
	 * {@code state} as the network wants it shown: {@code wire} toward wires and the core, {@code device}
	 * toward the {@code plugged} faces, {@code none} elsewhere, and the wall's {@code fill}.
	 */
	public static BlockState withChannels(BlockState state, LevelReader level, BlockPos pos, Set<Direction> plugged,
			int fill) {
		BlockState result = state.setValue(FILL, Math.clamp(fill, 0, MonitorNetwork.WIRE_CHANNELS));
		for (Map.Entry<Direction, EnumProperty<WireLink>> entry : LINK_BY_DIRECTION.entrySet()) {
			Direction dir = entry.getKey();
			WireLink link;
			if (isNetworkNode(level, pos.relative(dir))) {
				link = WireLink.WIRE;
			} else if (plugged.contains(dir)) {
				link = WireLink.DEVICE;
			} else {
				link = WireLink.NONE;
			}
			result = result.setValue(entry.getValue(), link);
		}
		return result;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@org.jetbrains.annotations.Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			MonitorNetworkManager.onNeighbourChanged(serverLevel, pos);
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state,
			net.minecraft.world.entity.@org.jetbrains.annotations.Nullable LivingEntity placer,
			net.minecraft.world.item.ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.getBlockEntity(pos) instanceof SmartWireBlockEntity wire) {
			wire.ensureRegistered();
		}
	}

	/**
	 * Whether two network nodes are wired together: the neighbour is another wire or a monitor core.
	 *
	 * <p>Panels are deliberately absent. They join the network through the core, not through the
	 * wire, so a wire run brushing past a monitoring wall does not silently graft itself onto it.
	 * Containers answer {@code true} here too, which is harmless: the network asks this only for a
	 * neighbour that is itself a node.
	 */
	public static boolean connectsTo(LevelReader level, BlockPos target) {
		BlockState state = level.getBlockState(target);
		if (state.getBlock() instanceof SmartWireBlock || state.getBlock() instanceof MonitorCoreBlock) {
			return true;
		}
		return level instanceof ServerLevel serverLevel && ContainerScan.isReadable(serverLevel, target);
	}

	/** Grid connectivity for the network graph: wire to wire, wire to core. */
	public static boolean isNetworkNode(LevelReader level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof SmartWireBlock || state.getBlock() instanceof MonitorCoreBlock;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SmartWireBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof SmartWireBlockEntity wire) {
				wire.serverTick();
			}
		};
	}
}
