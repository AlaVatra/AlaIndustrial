package dev.alaindustrial.block;

import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The apiary station's upper half (MOD-923): the "honeycomb balconies" — a glass honey column with four drone pads
 * inside the block on two levels (north and south low, east and west high), a crown and a beacon. No block entity:
 * the tank, the slots and the drones live in the lower half, which reads whether this block stands on it and writes
 * its {@link #LIT} beacon. On its own it is a decorative block.
 *
 * <p>Not a full cube, so it occludes nothing (R-PHY-05); the outline is the column with its four pads, assembled once
 * at class-init (ADR-023).
 */
public class ApiaryDroneHiveBlock extends Block implements HasMachineTooltip {

	/** The beacon is lit: the station below is ready or working. */
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** Pad tops in model pixels: north and south low, east and west high (owner's pick, "pads inside the block"). */
	public static final float PAD_TOP_LOW = 6.0F;
	public static final float PAD_TOP_HIGH = 11.0F;

	/** The column with its four pads: what collides, and the outline of a hive standing on its own. */
	public static final VoxelShape SHAPE = Shapes.or(
			Block.box(4, 0, 4, 12, 15.5, 12),
			Block.box(5, 5, 0, 11, 6, 5),
			Block.box(5, 5, 11, 11, 6, 16),
			Block.box(0, 10, 5, 5, 11, 11),
			Block.box(11, 10, 5, 16, 11, 11));
	/**
	 * On a station the pair reads as one machine (owner, in-game round of 2026-11-07): the outline takes in the
	 * casing below. Assembled once (ADR-023); collision stays {@link #SHAPE}.
	 */
	private static final VoxelShape PAIR_OUTLINE = Shapes.or(SHAPE, Block.box(0, -16, 0, 16, 0, 16));

	public ApiaryDroneHiveBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false));
	}

	/** Whether {@code state} is the station's upper half. */
	public static boolean isHive(BlockState state) {
		return state.getBlock() instanceof ApiaryDroneHiveBlock;
	}

	/** Lights or darkens the beacon of the hive above {@code station}, if there is one. */
	public static void setLit(Level level, BlockPos station, boolean lit) {
		BlockPos pos = station.above();
		BlockState state = level.getBlockState(pos);
		if (isHive(state) && state.getValue(LIT) != lit) {
			level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
		}
	}

	/** Put on a station's casing, the pair clicks together; the station itself notices through its neighbour update. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && level.getBlockState(pos.below()).getBlock() instanceof ApiaryDroneStationBlock) {
			level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return onStation(level, pos) ? PAIR_OUTLINE : SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return SHAPE;
	}

	/** On a station, a right click on the hive opens the station's screen: the pair is one machine. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		BlockState below = level.getBlockState(pos.below());
		if (below.getBlock() instanceof ApiaryDroneStationBlock) {
			return below.useWithoutItem(level, player, hit.withPosition(pos.below()));
		}
		return super.useWithoutItem(state, level, pos, player, hit);
	}

	private static boolean onStation(BlockGetter level, BlockPos pos) {
		return level.getBlockState(pos.below()).getBlock() instanceof ApiaryDroneStationBlock;
	}

	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(null,
				List.of(),
				List.of());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}
}
