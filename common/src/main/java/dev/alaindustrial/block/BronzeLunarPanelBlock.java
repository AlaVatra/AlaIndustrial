package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.BronzeLunarPanelBlockEntity;
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
 * Bronze Lunar Panel — the night branch's third rung (MOD-1031), grown from {@link MoonlitSolarPanelBlock}. A full
 * block whose output follows the phase of the moon; see {@link BronzeLunarPanelBlockEntity}.
 */
public class BronzeLunarPanelBlock extends AbstractFullSolarPanelBlock implements HasMachineTooltip {
	public BronzeLunarPanelBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BronzeLunarPanelBlockEntity(pos, state);
	}

	/** Pattern C: the moonlit panel's verdict — at night under any sky that is not walled off. */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return SolarSky.isMoonlitActive(level, pos);
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.stat("energy_output_max", ServerBalance::bronzeLunarPeakEuPerTick),
						MachineTooltipSpec.stat("capacity", ServerBalance::bronzeLunarBuffer)),
				List.of(MachineTooltipSpec.stat("solar_day", () -> 0),
						MachineTooltipSpec.stat("solar_night", ServerBalance::bronzeLunarPeakEuPerTick)));
	}
}
