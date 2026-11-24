package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.ElevatorWinchBlockEntity;
import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.registry.ModSounds;
import dev.alaindustrial.sound.MachineHum;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The elevator winch (MOD-769): the lid of the glass shaft, the machine room on top of the tube. The cabin runs in
 * the tube right under it and hangs on its rope; the cabin and the rope are drawn by the winch's renderer.
 *
 * <p>{@code assembled} lights the lamp in the front green while the winch reads a whole shaft under itself (at
 * least two stations, no gap) and red while it does not; a right click says what is wrong, or how many floors the
 * shaft has and how much energy the winch holds. {@code lit} shows that the cabin is moving and runs the motor's hum.
 * {@code facing} only turns the front (the lamp) to the player who placed it.
 *
 * <p>No window: the stations are the controls ({@link ElevatorStationBlock}). Power comes in on every face but the
 * front and the bottom, which closes the shaft.
 *
 * <p>Ticks on both sides: the server runs the cabin, the client carries its own player
 * ({@link ElevatorWinchBlockEntity#clientTick}) and plays the hum.
 */
public class ElevatorWinchBlock extends HorizontalMachineBlock implements MachineHumProvider, HasMachineTooltip {

	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

	public ElevatorWinchBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(ASSEMBLED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LIT, ASSEMBLED);
	}

	/** The bottom closes the shaft: no cable arm there, as on the front (R-NRG-03). */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return side != Direction.DOWN && super.isCableConnectable(state, side);
	}

	/** A right click tells the player what the winch sees: what is wrong with the shaft, or that it is ready. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof ElevatorWinchBlockEntity winch) {
			winch.describe(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ElevatorWinchBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide()) {
			MachineHum.ClientHook hook = MachineHum.CLIENT;
			return (lvl, pos, st, be) -> {
				if (be instanceof ElevatorWinchBlockEntity winch) {
					winch.clientTick(lvl);
				}
				if (hook != null) {
					hook.tick(lvl, pos, st);
				}
			};
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof EnergyBlockEntity energyBlock) {
				energyBlock.serverTick(lvl, pos, st);
			}
		};
	}

	/** The motor runs while the cabin moves: the mob wheel's drive, the mod's one geared-motor sound. */
	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.MOB_WHEEL_DRIVE;
	}

	/** Hover tooltip (MOD-716, ADR-040): the buffer, the price of a block of climb and the longest shaft. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.stat("buffer", ServerBalance::elevatorBuffer),
						MachineTooltipSpec.plain("elevator_climb_cost", ServerBalance::elevatorCostPerBlock)),
				List.of(MachineTooltipSpec.plain("elevator_max_height", ServerBalance::elevatorMaxHeight)));
	}
}
