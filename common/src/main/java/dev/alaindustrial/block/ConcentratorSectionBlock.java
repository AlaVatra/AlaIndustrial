package dev.alaindustrial.block;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * The withdrawn Concentrator Section (MOD-603, withdrawn by MOD-1031): kept registered only so a world saved before
 * the withdrawal loads, and hidden from players and recipe viewers.
 *
 * <p>It is refunded as one machine casing the first time the world touches it ({@link
 * WithdrawnSolarBlocks#convertSection}): a loose section turns into the casing block, a cell of an assembled
 * structure leaves air and drops the casing item, so the Reinforced Solar Panel that replaces the core sees the sky.
 * The paths: any neighbour update (the concentrator it surrounded turning into the panel sets off a chain through
 * every cell of the old structure), a scheduled tick after it is set by a command or a structure, or a random tick
 * for a section no update reaches — a loose one, or a cell whose core sat in a chunk that was not loaded. Its item
 * places a machine casing. A casing costs a little more than a section did; the player keeps every block.
 *
 * <p>The {@code part} and {@code facing} properties stay because the chunk palette stores them.
 */
public class ConcentratorSectionBlock extends Block {
	/** Saved by worlds with the section; frozen, see {@link ConcentratorPart}. */
	public static final EnumProperty<ConcentratorPart> PART = EnumProperty.create(
			"part", ConcentratorPart.class, part -> part != ConcentratorPart.CORE);
	/** Saved by worlds with the section; frozen. */
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	public ConcentratorSectionBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(PART, ConcentratorPart.LOOSE)
				.setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART, FACING);
	}

	/** An old item placed from a chest puts down a machine casing straight away. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return ModContent.MACHINE_CASING.get().defaultBlockState();
	}

	/**
	 * A loose section turns into the casing on the spot. A cell of an assembled structure waits for its scheduled
	 * tick: returning air here would let vanilla destroy the block through its loot table, and the refund belongs
	 * to {@link WithdrawnSolarBlocks#convertSection} alone.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
			BlockState neighbourState, RandomSource random) {
		if (!WithdrawnSolarBlocks.wasAssembled(state)) {
			return ModContent.MACHINE_CASING.get().defaultBlockState();
		}
		ticks.scheduleTick(pos, this, 1);
		return state;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide()) {
			level.scheduleTick(pos, this, 1);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		WithdrawnSolarBlocks.convertSection(level, pos);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		WithdrawnSolarBlocks.convertSection(level, pos);
	}
}
