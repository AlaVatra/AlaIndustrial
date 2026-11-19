package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.compat.FurnaceFuel;
import dev.alaindustrial.core.environment.DrawMeter;
import dev.alaindustrial.core.environment.FuelRuntime;
import dev.alaindustrial.core.environment.FuelTally;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.heat.HeatConsumer;
import dev.alaindustrial.core.heat.HeatSource;
import dev.alaindustrial.skill.SkillMachine;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.GeneratorMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import com.mojang.serialization.Codec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * LV fuel generator: burns a furnace fuel item into EU. Built on {@link AbstractGeneratorBlockEntity};
 * {@code progress}/{@code maxProgress} expose the current burn for the GUI.
 */
public class GeneratorBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider, FuelRuntime.Source {
	public static final int FUEL_SLOT = 0;
	/**
	 * Where the burnt coal leaves its ash (MOD-987): an output slot, taken by hand or by automation on a working
	 * face, never filled from outside. Right after the fuel slot, so the upgrade block that follows moved one slot
	 * up — see {@code BlockEntityDataMigrations.v1ToV2ShiftGeneratorUpgradeSlots} for older saves.
	 */
	public static final int ASH_SLOT = 1;
	/** Machine-slot count (indices before the upgrade block) — the client menu stub sizes its container from this (MOD-439). */
	public static final int SLOT_COUNT = 2;

	/** Units of ash-giving fuel a block of it counts as: a block of coal crafts into nine pieces (MOD-987). */
	public static final int ASH_UNITS_PER_BLOCK = 9;

	/** Save key of the ash count carried towards the next ash (MOD-987); written only while it is not zero. */
	private static final String KEY_ASH_UNITS = "AshUnits";

	private int burnTime;
	private int burnDuration;
	/**
	 * Whether fuel really burned on the last tick (MOD-988): the heat a machine standing on the generator gets.
	 * Not the burn left: a paused generator has burn left and no fire. Not saved, the first tick sets it again.
	 */
	private boolean burning;

	/**
	 * Units of ash-giving fuel burnt since the last ash (MOD-987), always below
	 * {@link GeneratorConfig#generatorFuelPerAsh} after a burn starts; kept in the save so the eighth coal of
	 * tomorrow completes the seven of today.
	 */
	private int ashUnits;

	/** What the line draws from this generator (MOD-990); runs only while the statistics chip measures. */
	private final DrawMeter drawMeter = new DrawMeter();

	/** Save keys of the burnt-fuel tally (MOD-992): written only once something was counted. */
	private static final String KEY_FUEL_BURNT = "StatsFuelBurnt";
	private static final String KEY_FUEL_BURNT_OTHER = "StatsFuelBurntOther";
	private static final Codec<Map<String, Long>> FUEL_BURNT_CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG);

	/**
	 * Pieces of fuel burnt, by item id (MOD-992). Counted only while the statistics chip measures, like every
	 * other statistic (MOD-125); a generator that never had the chip keeps it empty and writes nothing for it.
	 */
	private final FuelTally fuelBurnt = new FuelTally();

	public GeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.GENERATOR_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT, GeneratorConfig.generatorBuffer,
				EnergyTier.LV.maxVoltage());
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		// MOD-990: the line's draw is measured only while the chip is in (MOD-125, MOD-692): the counter it
		// reads runs only then, and without the chip nothing is kept at all.
		if (energy.countersEnabled()) {
			drawMeter.tick(energy.getTotalEnergyOut());
		} else {
			drawMeter.reset();
		}
		int made = 0;
		// Burn only while one burn tick's whole output fits in the buffer: otherwise the fire pauses so fuel is not
		// wasted. Asking only for "any room" burnt a full tick for every few EU a light load drew and threw the
		// rest away (MOD-986). MOD-988: unless the machine on top waits for this fire's heat; then the fuel goes
		// into heat, every tick, so the heat stays steady instead of blinking with the buffer.
		boolean mayBurn = energy.getCapacity() - energy.getAmount() >= effectiveEuPerTick()
				|| heatWantedAbove(level, pos);
		if (burnTime > 0 && mayBurn) {
			burnTime--;
			made = GeneratorConfig.fuelEuPerTick;
		}
		if (burnTime <= 0 && mayBurn) {
			ItemStack fuel = items.get(FUEL_SLOT);
			// The line's fuel lookup (compat.FurnaceFuel); produce() runs on the server tick only.
			int duration = level instanceof ServerLevel serverLevel
					? FurnaceFuel.burnDuration(serverLevel, this, fuel)
					: 0;
			if (duration > 0) {
				// MOD-483 Steady Hands: fuel lasts longer. Applied to the burn LENGTH rather than to
				// EU/t, because a solar panel makes 1 EU/t and a percentage of one is zero.
				duration = SkillMachine.burnDuration(duration, level, getOwner());
				burnTime = duration;
				burnDuration = duration;
				// Return the fuel's crafting remainder (e.g. empty bucket from a lava bucket).
				// getCraftingRemainder() is null for fuels with no remainder (coal, planks, ...).
				// MOD-498 — Item#getCraftingRemainder() is deprecated by NeoForge only, in favour of its own
				// getCraftingRemainder(ItemStack) overload, which vanilla does not declare. This class is
				// compiled for Fabric too, where the no-arg form is the only one that exists.
				@SuppressWarnings("deprecation")
				net.minecraft.world.item.ItemStackTemplate rem = fuel.getItem().getCraftingRemainder();
				ItemStack remainder = rem == null ? ItemStack.EMPTY : rem.create();
				// MOD-992: a piece taken from the slot is burnt for good, so it counts now; only with the chip.
				if (energy.countersEnabled()) {
					fuelBurnt.record(BuiltInRegistries.ITEM.getKey(fuel.getItem()).toString());
				}
				leaveAsh(ashUnits(fuel));
				fuel.shrink(1);
				if (items.get(FUEL_SLOT).isEmpty() && !remainder.isEmpty()) {
					items.set(FUEL_SLOT, remainder);
				}
				// MOD-991: the slot changed, so a comparator must re-read it now; setChanged() tells it.
				setChanged();
			}
		}
		this.maxProgress = burnDuration;
		this.progress = burnTime;
		setBurning(level, pos, made > 0);
		return made;
	}

	/** What one burn tick puts in the buffer: {@link #produce}'s rate after the base tick's global multiplier. */
	public static int effectiveEuPerTick() {
		return Math.max(1, Math.round(GeneratorConfig.fuelEuPerTick * Config.globalEuRateMultiplier));
	}

	/** Whether fuel burned on the last tick: the generator is a heat source for the machine on top (MOD-988). */
	public boolean isBurning() {
		return burning;
	}

	/**
	 * Whether the machine on top takes this generator's heat and waits for it now (MOD-988). Asked only with a full
	 * buffer, where the answer decides between burning and pausing: one block-entity lookup and the machine's own
	 * status, nothing heavier.
	 */
	private static boolean heatWantedAbove(Level level, BlockPos pos) {
		return level.getBlockEntity(pos.above()) instanceof HeatConsumer consumer
				&& consumer.acceptsHeatFrom(HeatSource.GENERATOR) && consumer.isWaitingOnHeat();
	}

	/**
	 * Records whether the fire burns and tells the machine on top the moment that changes (MOD-988). The lit state
	 * is written with {@code UPDATE_CLIENTS} only and notifies no neighbour, so without this a machine asleep on
	 * "no heat" would notice the fire one idle sleep late, and one still running would keep a heat that is gone.
	 */
	private void setBurning(Level level, BlockPos pos, boolean now) {
		if (burning == now) {
			return;
		}
		burning = now;
		if (level.getBlockEntity(pos.above()) instanceof HeatConsumer consumer) {
			consumer.onHeatNeighbourChanged();
		}
	}

	/**
	 * How many units of ash-giving fuel one piece of {@code fuel} is (MOD-987): one for the
	 * {@code alaindustrial:ash_fuels} tag (coal, charcoal), {@link #ASH_UNITS_PER_BLOCK} for the
	 * {@code alaindustrial:ash_fuel_blocks} tag (a block of coal), none for anything else.
	 */
	public static int ashUnits(ItemStack fuel) {
		if (fuel.is(ModTags.Items.ASH_FUEL_BLOCKS)) {
			return ASH_UNITS_PER_BLOCK;
		}
		return fuel.is(ModTags.Items.ASH_FUELS) ? 1 : 0;
	}

	/**
	 * Count {@code units} of burnt fuel and drop one ash into {@link #ASH_SLOT} for each
	 * {@link GeneratorConfig#generatorFuelPerAsh} of them. A full slot takes no more and the ash is lost — the
	 * count still moves on and the burn never waits for room (owner decision 2026-11-18, MOD-987).
	 */
	private void leaveAsh(int units) {
		if (units <= 0) {
			return;
		}
		int perAsh = Math.max(1, GeneratorConfig.generatorFuelPerAsh);
		ashUnits += units;
		ItemStack ash = new ItemStack(ModContent.ASH.get());
		while (ashUnits >= perAsh) {
			ashUnits -= perAsh;
			if (canOutput(ASH_SLOT, ash)) {
				addOutput(ASH_SLOT, ash.copy());
			}
		}
	}

	/** Units of ash-giving fuel carried towards the next ash, for the gametests. */
	public int ashUnits() {
		return ashUnits;
	}

	/**
	 * Comparator output (MOD-991): the vanilla container rule ({@code getRedstoneSignalFromContainer}) applied to
	 * the fuel slot alone, so upgrade slots never count. Empty reads 0, any item at least 1, a full stack 15.
	 */
	public int comparatorSignal() {
		ItemStack fuel = items.get(FUEL_SLOT);
		if (fuel.isEmpty()) {
			return 0;
		}
		return Mth.lerpDiscrete((float) fuel.getCount() / getMaxStackSize(fuel), 0, 15);
	}

	/**
	 * How long the fuel lasts at the line's real draw and what that draw is (MOD-990), for the statistics
	 * snapshot; {@code null} without the statistics chip, so nothing is worked out for a block that does not
	 * measure. The fuel still to come is the rest of the burning item plus every item in the slot at its own
	 * burn length, Steady Hands included, exactly as {@link #produce} will burn them.
	 */
	@Override
	public FuelRuntime.@Nullable Readout fuelReadout() {
		if (!energy.countersEnabled() || !(getLevel() instanceof ServerLevel serverLevel)) {
			return null;
		}
		ItemStack fuel = items.get(FUEL_SLOT);
		long fuelTicks = Math.max(0, burnTime);
		if (!fuel.isEmpty()) {
			int each = SkillMachine.burnDuration(FurnaceFuel.burnDuration(serverLevel, this, fuel), serverLevel,
					getOwner());
			fuelTicks += (long) Math.max(0, each) * fuel.getCount();
		}
		// What one burn tick puts in the buffer: the same rounding as the base tick applies to produce().
		int euPerTick = effectiveEuPerTick();
		DrawMeter.Window draw = drawMeter.window();
		long ticksLeft = FuelRuntime.ticksLeft(fuelTicks, euPerTick, energy.getAmount(), draw.eu(), draw.ticks());
		return new FuelRuntime.Readout(ticksLeft, draw.eu(), draw.ticks(), euPerTick);
	}

	/**
	 * The fuel burnt by kind for the statistics snapshot (MOD-992): the kinds burnt most and the rest as one
	 * figure; {@code null} without the statistics chip or before anything was counted, so nothing is sent then.
	 */
	@Override
	public FuelTally.@Nullable Top fuelBurnt() {
		if (!energy.countersEnabled() || fuelBurnt.isEmpty()) {
			return null;
		}
		return fuelBurnt.top(FuelTally.SHOWN_KINDS);
	}

	/** The whole burnt-fuel tally, for the gametests. */
	public FuelTally fuelBurntTally() {
		return fuelBurnt;
	}

	/** Automation takes the ash and nothing else; the fuel stays where the player put it (MOD-987). */
	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == ASH_SLOT;
	}

	/** Only the fuel slot takes anything: the ash slot is filled by the generator alone (MOD-987). */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot != FUEL_SLOT) {
			return false;
		}
		// Lava is handled by the dedicated geothermal generator (a lava bucket here would out-produce
		// it ~10×); the fuel generator is solid/furnace fuel only.
		if (stack.is(net.minecraft.world.item.Items.LAVA_BUCKET)) {
			return false;
		}
		// Reject non-fuel (R-GUI-02). What to answer without a level (client side, before placement) is
		// the line's: see FurnaceFuel.isFuelOrUnknown.
		return FurnaceFuel.isFuelOrUnknown(getLevel(), stack);
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new GeneratorMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("BurnTime", burnTime);
		output.putInt("BurnDuration", burnDuration);
		// MOD-987: nothing carried, nothing written, so a generator that never burnt coal saves as before.
		if (ashUnits > 0) {
			output.putInt(KEY_ASH_UNITS, ashUnits);
		}
		// MOD-992: nothing counted, nothing written, so a generator that never had the chip saves as before.
		if (!fuelBurnt.isEmpty()) {
			output.store(KEY_FUEL_BURNT, FUEL_BURNT_CODEC, fuelBurnt.counts());
			if (fuelBurnt.other() > 0L) {
				output.putLong(KEY_FUEL_BURNT_OTHER, fuelBurnt.other());
			}
		}
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		burnTime = input.getIntOr("BurnTime", 0);
		burnDuration = input.getIntOr("BurnDuration", 0);
		// An older save has no count: the next ash is a full eight pieces away.
		ashUnits = Math.max(0, input.getIntOr(KEY_ASH_UNITS, 0));
		// An older save has no tally: it starts from zero.
		fuelBurnt.restore(input.read(KEY_FUEL_BURNT, FUEL_BURNT_CODEC).orElse(Map.of()),
				input.getLongOr(KEY_FUEL_BURNT_OTHER, 0L));
	}
}
