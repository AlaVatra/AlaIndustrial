package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.ReinforcedSolarPanelBlockEntity;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.environment.SolarSky;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reinforced Solar Panel — the day branch's third rung (MOD-1031), grown from {@link DaylightSolarPanelBlock}. A
 * full block whose output follows the height of the sun; see {@link ReinforcedSolarPanelBlockEntity}.
 */
public class ReinforcedSolarPanelBlock extends AbstractFullSolarPanelBlock implements HasMachineTooltip {
	public ReinforcedSolarPanelBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ReinforcedSolarPanelBlockEntity(pos, state);
	}

	/** Pattern C: the daylight panel's verdict — snow and leaves only dim it, rain stops it. */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return SolarSky.isDaylitActive(level, pos);
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.stat("energy_output_max", ServerBalance::reinforcedSolarPeakEuPerTick),
						MachineTooltipSpec.stat("capacity", ServerBalance::reinforcedSolarBuffer)),
				List.of(MachineTooltipSpec.stat("solar_day", ServerBalance::reinforcedSolarPeakEuPerTick),
						MachineTooltipSpec.stat("solar_night", () -> 0)));
	}
}
