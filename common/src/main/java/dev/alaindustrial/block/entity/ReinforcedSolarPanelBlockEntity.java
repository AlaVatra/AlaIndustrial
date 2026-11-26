package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.environment.SolarCurve;
import dev.alaindustrial.core.environment.SolarSky;
import dev.alaindustrial.core.environment.SolarSkyCache;
import dev.alaindustrial.menu.ReinforcedSolarPanelMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LV Reinforced Solar Panel — the day branch's third rung (MOD-1031), grown from the daylight panel with a
 * resonance chip. A full block.
 *
 * <p>Output follows the height of the sun ({@link SolarCurve#dayShare}):
 * {@link GeneratorConfig#reinforcedSolarPeakEuPerTick} at noon, {@link GeneratorConfig#reinforcedSolarEdgeFactor} of
 * it at sunrise and sunset. Rain and thunder stop it; snow keeps {@link GeneratorConfig#solarSnowFactor} of the
 * current figure (at least 1); light through leaves keeps {@link GeneratorConfig#solarTransparentFactor}. Nothing at
 * night. Buffer {@link GeneratorConfig#reinforcedSolarBuffer}.
 *
 * <p>Sync channels: {@code progress} = production (EU/t), {@code maxProgress} = mode, one of the {@code MODE_*}
 * codes below — the same codes as the daylight panel, so the two screens read the same labels.
 */
public class ReinforcedSolarPanelBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	/** Cap on what leaves the block per tick, above the noon figure so no produced EU is stranded in the buffer. */
	private static final int MAX_EXTRACT = 20;
	/** No machine slots: nothing to evolve into yet. The GUI is a readout plus the upgrade panel. */
	public static final int SLOT_COUNT = 0;

	/** Caches the sky/weather verdict for {@link GeneratorConfig#solarSkySampleTicks} ticks. */
	private final SolarSkyCache skyCache = new SolarSkyCache();

	/** Mode codes shared with the screen. */
	public static final int MODE_NIGHT = 0;
	public static final int MODE_DAY = 1;
	public static final int MODE_DAY_WEATHER = 2;
	public static final int MODE_DAY_PARTIAL = 3;
	public static final int MODE_DAY_SNOW = 4;

	public ReinforcedSolarPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.REINFORCED_SOLAR_PANEL_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				GeneratorConfig.reinforcedSolarBuffer, MAX_EXTRACT);
	}

	/** Top face is the working surface and emits no EU; the other five are generator outputs (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == Direction.UP ? EnergyRole.NONE : EnergyRole.OUT;
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		skyCache.sample(level, pos);
		SolarSky.Access sky = level.dimension().equals(Level.OVERWORLD)
				? skyCache.sky()
				: SolarSky.Access.BLOCKED;
		boolean overworldSky = sky != SolarSky.Access.BLOCKED;

		int production = 0;
		int mode = MODE_NIGHT;
		if (overworldSky && level.isBrightOutside()) {
			production = SolarCurve.scaled(GeneratorConfig.reinforcedSolarPeakEuPerTick,
					SolarCurve.dayShare(level.getOverworldClockTime(), GeneratorConfig.reinforcedSolarEdgeFactor));
			mode = MODE_DAY;
			switch (skyCache.weather()) {
				case RAIN -> {
					// Rain and thunder hide the sun: nothing at all, as on the two rungs below.
					production = 0;
					mode = MODE_DAY_WEATHER;
				}
				case SNOW -> {
					production = SolarCurve.snowOutput(production, GeneratorConfig.solarSnowFactor);
					mode = MODE_DAY_SNOW;
				}
				case NONE -> {
					if (sky == SolarSky.Access.PARTIAL) {
						// Light through leaves or cobweb: the same share the panels below keep (MOD-004).
						production = Math.round(production * GeneratorConfig.solarTransparentFactor);
						mode = MODE_DAY_PARTIAL;
					}
				}
			}
		} else if (overworldSky && SolarSky.isClockDaytime(level) && level.isThundering()) {
			// A daytime storm darkens the sky past isBrightOutside(): output is honestly zero, but the label
			// says weather, not night (MOD-602).
			mode = MODE_DAY_WEATHER;
		}
		this.maxProgress = mode;
		return production;
	}

	/** Written after the global multiplier so the GUI shows the rate the buffer actually gains (MOD-356). */
	@Override
	protected void publishEffectiveRate(int effectiveEuPerTick) {
		this.progress = effectiveEuPerTick;
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ReinforcedSolarPanelMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
