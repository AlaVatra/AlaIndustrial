package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.ThermoelectricGeneratorBlockEntity;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.environment.ThermoelectricOutput;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.TooltipRole;
import dev.alaindustrial.core.tooltip.TooltipUnits;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * LV thermoelectric generator (MOD-811, spec: alaindustrial:thermoelectric_generator): a full, direction-less
 * cube that makes EU from a hot face across from a cold one. The block itself carries only what the model draws
 * — one {@link ThermoAxis} per axis — and the block entity sets it when the picture changes; every face gives
 * energy out, so a cable connects anywhere ({@link AbstractMachineBlock#isCableConnectable()}).
 *
 * <p>No sound and no particles, on purpose: a farm is dozens of these side by side.
 */
public class ThermoelectricGeneratorBlock extends AbstractMachineBlock implements HasMachineTooltip {
	/** The vertical axis (down, up). */
	public static final EnumProperty<ThermoAxis> AXIS_Y = EnumProperty.create("axis_y", ThermoAxis.class);
	/** The north-south axis. */
	public static final EnumProperty<ThermoAxis> AXIS_Z = EnumProperty.create("axis_z", ThermoAxis.class);
	/** The west-east axis. */
	public static final EnumProperty<ThermoAxis> AXIS_X = EnumProperty.create("axis_x", ThermoAxis.class);

	/** The three axis properties in {@code ThermoelectricOutput}'s axis order: vertical, north-south, west-east. */
	public static final List<EnumProperty<ThermoAxis>> AXES = List.of(AXIS_Y, AXIS_Z, AXIS_X);

	/** Prefix of this block's own tooltip keys. */
	private static final String KEY = "tooltip.alaindustrial.thermoelectric_generator.";

	/** The best axis there is: the top hot level against the top cold one. */
	private static final int BEST_AXIS_DEGREES = 2 * ThermoelectricOutput.MAX_LEVEL;

	public ThermoelectricGeneratorBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(AXIS_Y, ThermoAxis.OFF).setValue(AXIS_Z, ThermoAxis.OFF)
				.setValue(AXIS_X, ThermoAxis.OFF));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(AXIS_Y, AXIS_Z, AXIS_X);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ThermoelectricGeneratorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/**
	 * Without [SHIFT]: the purpose and what one hot-cold face pair gives at best, like the other passive generators
	 * (one number line). Under it: the buffer, the rate per degree, the row that shares one cable, and what counts as
	 * hot and as cold. The live state of the faces is on the screen; the ice that melts by lava is in the guide.
	 */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.stat(TooltipRole.ENERGY,
						KEY + "best_axis", TooltipUnits.EU_PER_TICK,
						() -> milliAsEu(BEST_AXIS_DEGREES * ServerBalance.thermoelectricMilliEuPerDegree()))),
				List.of(MachineTooltipSpec.stat("buffer", ServerBalance::thermoelectricBuffer),
						MachineTooltipSpec.stat(TooltipRole.ENERGY,
								KEY + "per_degree", TooltipUnits.EU_PER_TICK,
								() -> milliAsEu(ServerBalance.thermoelectricMilliEuPerDegree())),
						MachineTooltipSpec.fact(TooltipRole.INFO, KEY + "row", null,
								ServerBalance::thermoelectricRowMaxGenerators),
						MachineTooltipSpec.note(KEY + "hot", TooltipRole.INFO),
						MachineTooltipSpec.note(KEY + "cold", TooltipRole.INFO)));
	}

	/** Thousandths of an EU as the tooltip writes a fraction: 250 is "0.25", 2000 is "2". */
	private static TooltipUnits.Decimal milliAsEu(int milli) {
		return TooltipUnits.factor(milli / (double) ThermoelectricOutput.MILLI_PER_EU);
	}
}
