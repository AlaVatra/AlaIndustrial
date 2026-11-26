package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.environment.SolarCurve;
import dev.alaindustrial.core.environment.SolarSky;
import dev.alaindustrial.core.environment.SolarSkyCache;
import dev.alaindustrial.menu.BronzeLunarPanelMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LV Bronze Lunar Panel — the night branch's third rung (MOD-1031), grown from the moonlit panel with a resonance
 * chip. A full block.
 *
 * <p>Output follows the phase of the moon ({@link SolarCurve#moonShare}, fed the game's own moon brightness):
 * {@link GeneratorConfig#bronzeLunarPeakEuPerTick} under a full moon, {@link GeneratorConfig#bronzeLunarNewMoonFactor}
 * of it at the new moon. Unlike the day branch it keeps working in bad weather: rain and thunder keep
 * {@link GeneratorConfig#bronzeLunarWeatherFactor} of the current figure, at least
 * {@link GeneratorConfig#bronzeLunarWeatherMinEuPerTick}; snow keeps {@link GeneratorConfig#bronzeLunarSnowFactor};
 * light through leaves keeps {@link GeneratorConfig#solarTransparentFactor}. Nothing by day.
 *
 * <p>Sync channels: {@code progress} = production (EU/t), {@code maxProgress} = mode — the moonlit panel's codes.
 */
public class BronzeLunarPanelBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	/** Cap on what leaves the block per tick, above the full-moon figure. */
	private static final int MAX_EXTRACT = 20;
	/** No machine slots: nothing to evolve into yet. The GUI is a readout plus the upgrade panel. */
	public static final int SLOT_COUNT = 0;

	/** Caches the sky/weather verdict for {@link GeneratorConfig#solarSkySampleTicks} ticks. */
	private final SolarSkyCache skyCache = new SolarSkyCache();

	/** Mode codes shared with the screen. */
	public static final int MODE_DAY = 0;
	public static final int MODE_NIGHT = 1;
	public static final int MODE_NIGHT_WEATHER = 2;
	public static final int MODE_NIGHT_PARTIAL = 3;
	public static final int MODE_NIGHT_SNOW = 4;

	public BronzeLunarPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.BRONZE_LUNAR_PANEL_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				GeneratorConfig.bronzeLunarBuffer, MAX_EXTRACT);
	}

	/** Top face is the working surface and emits no EU; the other five are generator outputs (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == Direction.UP ? EnergyRole.NONE : EnergyRole.OUT;
	}

	/**
	 * Moon brightness of tonight's phase at {@code pos}, 0 (new moon) to 1 (full moon). Only a server level knows
	 * the phase through its environment attributes; production runs on the server, so the fallback is never used
	 * there.
	 */
	public static float moonBrightness(Level level, BlockPos pos) {
		return level instanceof ServerLevel server ? server.getMoonBrightness(pos) : 1.0f;
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		skyCache.sample(level, pos);
		SolarSky.Access sky = level.dimension().equals(Level.OVERWORLD)
				? skyCache.sky()
				: SolarSky.Access.BLOCKED;

		int production = 0;
		int mode = MODE_DAY;
		if (sky != SolarSky.Access.BLOCKED && !level.isBrightOutside()) {
			production = SolarCurve.scaled(GeneratorConfig.bronzeLunarPeakEuPerTick,
					SolarCurve.moonShare(moonBrightness(level, pos), GeneratorConfig.bronzeLunarNewMoonFactor));
			mode = MODE_NIGHT;
			switch (skyCache.weather()) {
				case RAIN -> {
					// The night branch's mark since T2: bad weather dims it, it does not stop it.
					production = SolarCurve.weatherOutput(production, GeneratorConfig.bronzeLunarWeatherFactor,
							GeneratorConfig.bronzeLunarWeatherMinEuPerTick);
					mode = MODE_NIGHT_WEATHER;
				}
				case SNOW -> {
					production = SolarCurve.snowOutput(production, GeneratorConfig.bronzeLunarSnowFactor);
					mode = MODE_NIGHT_SNOW;
				}
				case NONE -> {
					if (sky == SolarSky.Access.PARTIAL) {
						production = Math.round(production * GeneratorConfig.solarTransparentFactor);
						mode = MODE_NIGHT_PARTIAL;
					}
				}
			}
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
		return new BronzeLunarPanelMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
