package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.entity.machine.EvolutionHelper;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.environment.SolarSky;
import dev.alaindustrial.core.environment.SolarSkyCache;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV Moonlit (Lunar) Solar Panel — produces EU from moonlight. Night, under open sky, in the
 * Overworld: 3 EU/t ({@link GeneratorConfig#moonlitEuPerTick}); rain/thunder: a flat 1 EU/t trickle
 * ({@link GeneratorConfig#moonlitWeatherEuPerTick}) rather than the day panels' zero; daytime: 0 (MOD-003).
 * Buffer 8000, LV output (20).
 *
 * <p>The night-mirror of {@link SolarPanelBlockEntity}: where the day panel uses
 * {@code level.isBrightOutside()} as its sunlight test, the moonlit panel negates it.
 *
 * <p>Evolution (MOD-1031, MOD-1033): with a {@linkplain ModContent#ALIGNMENT_CHIP_NIGHT lunar evolution chip} or a
 * {@linkplain ModContent#RESONANCE_CHIP resonance chip} in its slot, the panel banks active night-time and, at
 * {@link GeneratorConfig#solarEvolveTicks}, becomes the Bronze Lunar Panel — carrying its stored energy, owner and
 * upgrade chips and consuming one chip. The lunar chip is the night branch's own: a player who grew this panel with
 * it reaches for it again, and the slot refusing it read as a broken panel (owner check of MOD-1031).
 *
 * <p>Sync channels: 0 energy, 1 capacity, 2 production (EU/t), 3 mode (0 inactive/day, 1 night clear, 2 night
 * weather, 3 night partial, 4 night snow), 4 evolution progress (permille 0..1000), 5 denominator (constant 1000).
 */
public class MoonlitSolarPanelBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	private static final int MAX_EXTRACT = 20;
	public static final int CHIP_SLOT = 0;
	/**
	 * One evolution-chip slot (MOD-1031; before it the panel had none, see
	 * {@code BlockEntityDataMigrations#v3ToV4ShiftMoonlitUpgradeSlots}); the client menu stub sizes its container
	 * from this.
	 */
	public static final int SLOT_COUNT = 1;

	/** Caches the sky/weather verdict for {@link GeneratorConfig#solarSkySampleTicks} ticks to avoid a per-tick column scan. */
	private final SolarSkyCache skyCache = new SolarSkyCache();

	/** Mode codes shared with the screen. */
	public static final int MODE_DAY = 0;
	public static final int MODE_NIGHT = 1;
	public static final int MODE_NIGHT_WEATHER = 2;
	public static final int MODE_NIGHT_PARTIAL = 3;
	public static final int MODE_NIGHT_SNOW = 4;

	private int evolveProgress;
	/** Which chip the counter above belongs to; see {@link EvolutionHelper#saveEvolveChip}. */
	private int evolveChip = EvolutionHelper.EVOLVE_CHIP_NONE;

	public MoonlitSolarPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.MOONLIT_SOLAR_PANEL_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				GeneratorConfig.solarBuffer, MAX_EXTRACT);
	}

	/**
	 * Top face is the working surface (captures moonlight) and must not emit EU; the other five sides are
	 * standard generator outputs (R-NRG-03) — mirrors {@link SolarPanelBlockEntity}. Without this override
	 * the evolved panel inherited all-faces-OUT and leaked power from its working surface (spec: 5-side).
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == Direction.UP ? EnergyRole.NONE : EnergyRole.OUT;
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		// Sample sky access + weather on a cadence (GeneratorConfig.solarSkySampleTicks); cached to avoid the
		// per-tick column scan above the panel.
		skyCache.sample(level, pos);
		int production = 0;
		int mode = MODE_DAY;
		SolarSky.Access sky = level.dimension().equals(Level.OVERWORLD)
				? skyCache.sky()
				: SolarSky.Access.BLOCKED;
		boolean night = sky != SolarSky.Access.BLOCKED && !level.isBrightOutside();

		// --- Evolution (MOD-1031, MOD-1033): bank night-time while an evolution chip is in the slot. ---
		ItemStack chip = items.get(CHIP_SLOT);
		// The counter belongs to the chip that earned it (MOD-601): pull the chip out and that run is abandoned.
		int chipNow = EvolutionHelper.evolveChipOf(chip);
		if (chipNow != evolveChip) {
			// An UNATTRIBUTED counter is adopted, not cleared — the state of a counter seeded by a test rig.
			if (evolveChip != EvolutionHelper.EVOLVE_CHIP_NONE) {
				evolveProgress = 0;
			}
			evolveChip = chipNow;
			setChanged();
		}
		if (isEvolutionChip(chip) && night) {
			evolveProgress++;
			if (evolveProgress >= GeneratorConfig.solarEvolveTicks) {
				evolveInto(level, pos, ModContent.BRONZE_LUNAR_PANEL.get());
				return 0; // this block entity is gone after the transform
			}
			setChanged();
		}

		if (night) {
			production = GeneratorConfig.moonlitEuPerTick;
			mode = MODE_NIGHT;
			switch (skyCache.weather()) {
				case RAIN -> {
					// Unlike the day panels, the moonlit panel keeps a small weather trickle in rain/thunder
					// instead of going dark — the moon is still faintly effective.
					production = GeneratorConfig.moonlitWeatherEuPerTick;
					mode = MODE_NIGHT_WEATHER;
				}
				case SNOW -> {
					// Snow dims moonlight to a floored trickle (≥1 EU/t), same rule as the other tiers.
					production = Math.max(1, Math.round(production * GeneratorConfig.solarSnowFactor));
					mode = MODE_NIGHT_SNOW;
				}
				case NONE -> {
					if (sky == SolarSky.Access.PARTIAL) {
						// Moonlight filtered through a translucent block (leaves, cobweb): reduced output (MOD-004).
						production = Math.round(production * GeneratorConfig.solarTransparentFactor);
						mode = MODE_NIGHT_PARTIAL;
					}
				}
			}
		}
		// Surface the mode to the GUI via the generic sync channel; the rate is published separately below.
		this.maxProgress = mode;
		return production;
	}

	/**
	 * Rate on channel 2 ({@code progress}), written after the global multiplier rather than inside
	 * {@link #produce} so the GUI shows the rate the buffer gains while it has room (MOD-356). Mirrors the
	 * T1 panel: no renderer or sound gate reads channel 2 on a panel, so it doubles as the readout channel.
	 */
	@Override
	protected void publishEffectiveRate(int effectiveEuPerTick) {
		this.progress = effectiveEuPerTick;
	}

	/** Replace this panel with the Bronze Lunar Panel, carrying stored energy; ONE chip is consumed. */
	private void evolveInto(Level level, BlockPos pos, Block target) {
		ItemStack remainder = items.get(CHIP_SLOT).copy();
		remainder.shrink(1);
		// The upgrade chips move to the same arms of the T3 panel (MOD-1031): its panel starts at slot 0.
		EvolutionHelper.evolveIntoCarryingUpgrades(this, items, level, pos, target,
				java.util.Map.of(CHIP_SLOT, remainder));
	}

	/** The chips that grow this panel into the Bronze Lunar Panel: the lunar evolution chip or the resonance chip. */
	public static boolean isEvolutionChip(ItemStack stack) {
		return stack.is(ModContent.ALIGNMENT_CHIP_NIGHT.get()) || stack.is(ModContent.RESONANCE_CHIP.get());
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		// One evolution chip at a time, as on the daylight panel (MOD-211: the slot must be EMPTY, or automation
		// stacks chips and the transform wipes the rest).
		return items.get(CHIP_SLOT).isEmpty()
				&& slot == CHIP_SLOT
				&& isEvolutionChip(stack);
	}

	/**
	 * GUI sync channels (MOD-712, BE-7): the base four, then the evolution progress in permille and its denominator.
	 */
	public enum Channel { ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, EVOLVE_PERMILLE, EVOLVE_MAX }

	/** Width of {@link #getDataAccess()}, which the menu's client stub sizes itself from (MOD-235). */
	public static final int DATA_COUNT = Channel.values().length;

	@Override
	protected SyncChannels createChannels() {
		return channels(Channel.class)
				.read(Channel.EVOLVE_PERMILLE, () -> evolveProgress <= 0 ? 0
						: Math.max(1, (int) Math.min((long) evolveProgress * 1000 / GeneratorConfig.solarEvolveTicks,
								1000)))
				.read(Channel.EVOLVE_MAX, () -> 1000)
				.build();
	}

	/** Raw accumulated evolution counter in ticks, persisted in NBT. Channel 4 carries only a permille projection. */
	public int getEvolveProgressTicks() {
		return evolveProgress;
	}

	/** Seed the raw evolution counter directly (tick scale). Test/setup helper — bypasses the sync channel. */
	public void setEvolveProgressTicks(int ticks) {
		this.evolveProgress = ticks;
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new MoonlitSolarPanelMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		EvolutionHelper.saveEvolve(output, evolveProgress);
		EvolutionHelper.saveEvolveChip(output, evolveChip);
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		evolveProgress = EvolutionHelper.loadEvolve(input);
		evolveChip = EvolutionHelper.loadEvolveChip(input);
	}
}
