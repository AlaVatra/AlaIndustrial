package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.RadiantSolarPanelBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * The withdrawn Mirror Concentrator (MOD-602, withdrawn by MOD-1031): kept registered only so a world saved before
 * the withdrawal loads, and hidden from players and recipe viewers.
 *
 * <p>It turns into the Reinforced Solar Panel, the day branch's new third rung, with its charge, owner and upgrade
 * chips, the first time the world touches it: the first tick of its block entity, a scheduled tick after it is set
 * by a command or a structure, or a random tick if a save lost its block entity. Placing its item places the
 * Reinforced Solar Panel directly. See {@link WithdrawnSolarBlocks}.
 *
 * <p>The {@code assembled} and {@code facing} properties stay because the chunk palette stores them: a value the
 * block no longer declared would not decode.
 */
public class RadiantSolarPanelBlock extends AbstractSolarPanelBlock {
	/** Saved by worlds that assembled the concentrator; frozen. */
	public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
	/** Saved by worlds that assembled the concentrator; frozen. */
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	public RadiantSolarPanelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(ASSEMBLED, Boolean.FALSE)
				.setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(ASSEMBLED, FACING);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RadiantSolarPanelBlockEntity(pos, state);
	}

	/** Never working: it is gone after its first tick. */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return false;
	}

	/** An old item placed from a chest puts down its successor straight away. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return ModContent.REINFORCED_SOLAR_PANEL.get().defaultBlockState();
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
		WithdrawnSolarBlocks.convertConcentrator(level, pos);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		WithdrawnSolarBlocks.convertConcentrator(level, pos);
	}
}
