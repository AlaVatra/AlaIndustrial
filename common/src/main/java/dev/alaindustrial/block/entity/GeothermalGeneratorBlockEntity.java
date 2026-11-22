package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.entity.machine.FireHeat;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.DrawMeter;
import dev.alaindustrial.core.environment.FuelRuntime;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.environment.GeothermalLava;
import dev.alaindustrial.core.environment.LavaBurnTally;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.core.heat.HeatSource;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

// size-justified: one generator's whole lava path — the two-stage store with its cistern capacity and overfill,
// the item-form lava, the blaze catalyst and the obsidian shavings, the heat for the machine on top, the runtime
// readout and the burnt-lava tally all read the one burn rule of produce(); each feature's arithmetic already lives
// outside (GeothermalLava, FireHeat, FuelRuntime, LavaBurnTally) and what stays here is the wiring to the block.
/**
 * LV geothermal generator: burns lava into EU. Two feeds, one burn model:
 * <ul>
 *   <li>a {@link #fluidTank} (10 buckets) exposed via a neutral {@link FluidPort} so adjacent blocks
 *       (e.g. a {@link PumpBlockEntity}) can insert lava directly; and</li>
 *   <li>the legacy lava-bucket item slot, kept as a compatibility filler.</li>
 * </ul>
 * Each tick, when {@code lavaTicks} is low and the tank holds ≥1 bucket, it drains 1 bucket from the
 * tank and adds {@link GeneratorConfig#geothermalBurnTicks} burn ticks. Produces {@link
 * GeneratorConfig#geothermalEuPerTick}
 * EU/t while lava remains; buffer 4000, LV output (32). Built on {@link AbstractGeneratorBlockEntity};
 * {@code progress}/{@code maxProgress} expose the lava-tick level.
 *
 * <p><b>MOD-028 multiloader migration.</b> Moved from {@code fabric} to {@code common}: the Fabric Fluid
 * Transfer API ({@code FluidVariant}/{@code SingleVariantStorage}) is replaced by the neutral
 * {@link FluidTank}/{@link FluidPort}, so this class no longer imports any loader-specific fluid type.
 * Each loader supplies its own adapter ({@code FabricFluidPort}/{@code NeoForgeFluidPort}) — see
 * {@link FluidPort} class doc.
 *
 * <p><b>Blaze catalyst (MOD-997).</b> Blaze powder in {@link #CATALYST_SLOT} catalyses one bucket each: the
 * bucket leaves the {@code lavaTicks} pool whole and becomes {@code catalystEu}, the EU still to be paid out at
 * {@link #catalysedEuPerTick()}; the whole bucket is worth {@link #catalysedBucketEu()}.
 *
 * <p><b>Obsidian shavings (MOD-995).</b> Every bucket burnt leaves one shaving in {@link #SHAVINGS_SLOT}, counted by
 * buckets of plain burn ticks (a catalysed bucket hands over the plain ticks it was worth as its EU is paid out), so
 * the catalyst adds none. A full slot loses the shaving and stops nothing, as the fuel generator's ash does (MOD-987).
 */
public class GeothermalGeneratorBlockEntity extends AbstractGeneratorBlockEntity
		implements MenuProvider, FluidPortHost, FireHeat.Host, FuelRuntime.Source, LavaBurnTally.Source {
	public static final int INPUT_SLOT = 0;
	public static final int OUTPUT_SLOT = 1;
	/**
	 * Where the burnt lava leaves its obsidian shavings (MOD-995): an output slot, taken by hand or by automation on
	 * a working face, never filled from outside.
	 */
	public static final int SHAVINGS_SLOT = 2;
	/** Blaze powder that catalyses the next bucket (MOD-997); filled by hand or by automation like the lava slot. */
	public static final int CATALYST_SLOT = 3;
	/**
	 * Machine-slot count (indices before the upgrade block) — the client menu stub sizes its container from this
	 * (MOD-439). The two slots of MOD-995/997 moved the upgrade block from 2..5 to 4..7: see
	 * {@code BlockEntityDataMigrations.v2ToV3ShiftGeothermalUpgradeSlots} for older saves.
	 */
	public static final int SLOT_COUNT = 4;

	/** Save key of the EU left in the catalysed bucket (MOD-997); written only while it is not zero. */
	private static final String KEY_CATALYST_EU = "CatalystEu";
	/** Save key of the lava burnt towards the next shaving, in burn ticks (MOD-995); written only while not zero. */
	private static final String KEY_SHAVING_LAVA_TICKS = "ShavingLavaTicks";

	/** Fluid tank capacity without a cistern chip: 10 buckets, in mB. */
	public static final long TANK_CAPACITY = FluidAmounts.BUCKET * 10;
	/** MOD-996 — the cistern chip doubles both stages: the tank and the burn reserve. */
	public static final int CISTERN_FACTOR = 2;
	/** Fluid tank capacity with a cistern chip: 20 buckets, in mB. */
	public static final long CISTERN_TANK_CAPACITY = TANK_CAPACITY * CISTERN_FACTOR;
	/**
	 * The most lava this generator can ever hold, tank and burn reserve together (40 buckets, with a cistern). The
	 * one ceiling a stored volume is clamped to: never the current capacity, which a chip dropped apart lowers.
	 */
	public static final long MAX_STORED_MB = 2 * CISTERN_TANK_CAPACITY;

	private int lavaTicks;

	/**
	 * EU the catalysed bucket still pays out (MOD-997); zero when no catalysed bucket burns. The bucket left the
	 * {@code lavaTicks} pool whole when its powder was taken, so nothing of it is counted twice.
	 */
	private long catalystEu;

	/**
	 * Lava burnt since the last shaving, in burn ticks (MOD-995): below one shaving's worth after every tick; kept
	 * in the save so half a bucket today and half tomorrow still make a shaving.
	 */
	private int shavingLavaTicks;

	/** The heat the machine standing on the generator gets while lava really burns (MOD-994), level 2. */
	private final FireHeat fireHeat = new FireHeat(HeatSource.GEOTHERMAL_GENERATOR);

	/** What the line draws from this generator (MOD-1000); runs only while the statistics chip measures. */
	private final DrawMeter drawMeter = new DrawMeter();

	/** Save keys of the burnt-lava tally (MOD-999): written only once something was counted. */
	private static final String KEY_LAVA_BURNT_MB = "StatsLavaBurntMb";
	private static final String KEY_LAVA_EU = "StatsLavaEu";

	/**
	 * Lava burnt and the EU it gave (MOD-999). Counted only while the statistics chip measures, like every
	 * statistics counter (MOD-125), and saved only once something was counted, so a generator without the chip
	 * saves exactly as before.
	 */
	private final LavaBurnTally lavaBurnt = new LavaBurnTally();

	/**
	 * Lava-only tank, insertable from any side, never extractable (R-CON-08 — the generator burns its own fuel).
	 * Sized for the cistern; what it accepts follows the chip (MOD-996). A tank over its current capacity (lava
	 * kept from a broken generator that had a cistern) takes nothing until it burns down below it.
	 */
	public final FluidTank fluidTank = new FluidTank(CISTERN_TANK_CAPACITY,
			fluid -> fluid.is(Fluids.LAVA),
			fluid -> false,
			this::setChanged) {
		@Override
		public long insert(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
			long room = tankCapacityMb() - amount;
			return room <= 0 ? 0 : super.insert(fluid, Math.min(maxAmount, room), txn);
		}

		@Override
		public long getCapacity() {
			return tankCapacityMb();
		}
	};

	public GeothermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.GEOTHERMAL_GENERATOR_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				GeneratorConfig.geothermalBuffer,
				EnergyTier.LV.maxVoltage());
		this.maxProgress = tankCapacity();
	}

	/** The burn reserve holds ~10 buckets' worth of burn ticks (config-driven), twice that with a cistern. */
	private int tankCapacity() {
		return (hasCistern() ? CISTERN_FACTOR : 1) * 10 * GeneratorConfig.geothermalBurnTicks;
	}

	/** The intake tank's current capacity in mB: 10 buckets, 20 with a cistern (MOD-996). */
	private long tankCapacityMb() {
		return hasCistern() ? CISTERN_TANK_CAPACITY : TANK_CAPACITY;
	}

	/** The burn reserve's current capacity in mB — the one lava gauge the GUI and the capability show. */
	private long lavaCapacityMb() {
		return GeothermalLava.reserveMb(tankCapacity(), GeneratorConfig.geothermalBurnTicks);
	}

	/** Whether a cistern chip sits in the upgrade panel (MOD-996). */
	public boolean hasCistern() {
		for (int i = 0; i < dev.alaindustrial.block.entity.machine.UpgradePanel.SLOT_COUNT; i++) {
			if (getUpgradeStack(i).is(ModContent.CISTERN_CHIP.get())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The cistern stays in while either stage holds more than fits without it, so taking it out never strands
	 * lava over the plain tank (owner decision 2026-11-19, MOD-996). Every other chip comes out as before.
	 */
	@Override
	public boolean mayRemoveUpgrade(ItemStack chip) {
		if (!chip.is(ModContent.CISTERN_CHIP.get())) {
			return true;
		}
		return reserveTicks(true) <= 10 * GeneratorConfig.geothermalBurnTicks && fluidTank.amount <= TANK_CAPACITY;
	}

	/**
	 * Every face exposes the same lava fuel view — the generator has no per-face fluid restriction.
	 *
	 * <p><b>MOD-126.</b> This deliberately does <em>not</em> hand out the raw {@link #fluidTank}. The
	 * generator holds lava in two stages: the {@code fluidTank} intake buffer (fed by adjacent pipes/pumps)
	 * <em>and</em> the {@code lavaTicks} burn reserve the GUI actually draws. Bucket-fed lava lands straight
	 * in the burn reserve, so the raw {@code fluidTank} reads empty in normal play — and HUD mods
	 * (Jade / WTHIT / TOP), which read {@code fluid()}/{@code getAmount()} off this capability on the server,
	 * then showed "Empty" over a generator visibly full of lava. {@link LavaFuelView} reports the combined
	 * lava the player sees (intake + burn reserve, clamped to the one 10-bucket gauge the GUI shows) so any
	 * capability reader sees the real fuel, while insertion/extraction still delegate to the real intake tank.
	 */
	@Override
	public FluidPort fluidPort(Direction side) {
		return fuelView;
	}

	/** The burn reserve ({@link #reserveTicks}) expressed in mB — same derivation as the GUI lava-gauge tooltip. */
	private long lavaFuelMb() {
		return GeothermalLava.reserveMb(reserveTicks(true), GeneratorConfig.geothermalBurnTicks);
	}

	/**
	 * The whole burn reserve in plain burn ticks: the {@code lavaTicks} pool plus what is left of a catalysed bucket,
	 * scaled back to the plain ticks it was worth (MOD-997). Rounded up for the gauge, so a bucket still burning never
	 * reads empty; rounded down where the reserve leaves the block as lava (the dropped item), so nothing is made.
	 */
	private int reserveTicks(boolean roundUp) {
		if (catalystEu <= 0) {
			return lavaTicks;
		}
		long bucketEu = Math.max(1L, catalysedBucketEu());
		long left = Math.min(catalystEu, bucketEu) * Math.max(1, burnTicksPerBucket());
		return lavaTicks + (int) (roundUp ? (left + bucketEu - 1) / bucketEu : left / bucketEu);
	}

	/** EU/t of a catalysed bucket (MOD-997): {@code geothermalEuPerTick × geothermalCatalystRatePercent / 100}. */
	public static int catalysedEuPerTick() {
		return Math.max(1, GeneratorConfig.geothermalEuPerTick * GeneratorConfig.geothermalCatalystRatePercent / 100);
	}

	/**
	 * EU a catalysed bucket pays out in all (MOD-997): a plain bucket ({@code geothermalEuPerTick ×
	 * geothermalBurnTicks}) times {@code geothermalCatalystEuPercent / 100}.
	 */
	public static long catalysedBucketEu() {
		return (long) GeneratorConfig.geothermalEuPerTick * GeneratorConfig.geothermalBurnTicks
				* GeneratorConfig.geothermalCatalystEuPercent / 100L;
	}

	/** Whether a catalysed bucket burns now (MOD-997): the flame above the catalyst slot. */
	public boolean isCatalysed() {
		return catalystEu > 0;
	}

	/**
	 * The mechanical EU the next burning tick makes, before the global multiplier: what is left of the catalysed rate
	 * while a catalysed bucket burns, else the plain rate, else 0 with no lava (MOD-997).
	 */
	public int currentEuPerTick() {
		if (catalystEu > 0) {
			return (int) Math.min(catalysedEuPerTick(), catalystEu);
		}
		return lavaTicks > 0 ? GeneratorConfig.geothermalEuPerTick : 0;
	}

	/** EU left in the catalysed bucket, for the gametests. */
	public long catalystEu() {
		return catalystEu;
	}

	/** Lava burnt towards the next shaving, in burn ticks, for the gametests. */
	public int shavingLavaTicks() {
		return shavingLavaTicks;
	}

	/** The burn-tick pool outside a catalysed bucket, for the gametests. */
	public int lavaTicks() {
		return lavaTicks;
	}

	/**
	 * Read-through fluid view exposed to the capability (MOD-126): reports the combined lava the player
	 * sees ({@code fluidTank} intake + {@code lavaTicks} burn reserve, clamped to one 10-bucket gauge) so
	 * HUD mods stop showing "Empty" over a lava-filled generator. Insertion/extraction and the
	 * insertion/extraction predicates delegate to the real {@link #fluidTank} unchanged: fluid transport
	 * ({@code FluidMover}, the pump push loop) drives ports through {@code insert()}/{@code extract()} return
	 * values and never reads the reported amount/capacity, so surfacing the combined level changes nothing
	 * about how lava actually moves in or out.
	 */
	private final class LavaFuelView implements FluidPort {
		@Override
		public long insert(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
			return fluidTank.insert(fluid, maxAmount, txn);
		}

		@Override
		public long extract(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
			return fluidTank.extract(fluid, maxAmount, txn);
		}

		@Override
		public FluidHolder fluid() {
			if (!fluidTank.fluid.isEmpty()) {
				return fluidTank.fluid;
			}
			return reserveTicks(true) > 0 ? FluidHolder.of(Fluids.LAVA) : FluidHolder.EMPTY;
		}

		@Override
		public long getAmount() {
			return Math.min(getCapacity(), fluidTank.amount + lavaFuelMb());
		}

		/** The one gauge the GUI shows: the burn reserve's current capacity in mB (doubled by a cistern). */
		@Override
		public long getCapacity() {
			return lavaCapacityMb();
		}

		@Override
		public boolean supportsInsertion() {
			return fluidTank.supportsInsertion();
		}

		@Override
		public boolean supportsExtraction() {
			return fluidTank.supportsExtraction();
		}
	}

	private final FluidPort fuelView = new LavaFuelView();

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		measureDraw();
		int tank = tankCapacity();
		// Load lava from a filler item (vanilla lava bucket OR a lava-filled Vacuum Capsule) into the burn
		// buffer whenever there is room, returning the emptied container to the output slot. This is
		// independent of the energy buffer: lavaTicks is an intermediate store, not EU. EU generation is
		// gated separately below so lava is never wasted (R-NRG-11). MOD-077: capsule parity — same
		// all-or-nothing exchange as the bucket, returning an empty capsule instead of an empty bucket.
		// The room counts a catalysed bucket still burning (MOD-997), so the reserve never passes its capacity.
		int perBucket = burnTicksPerBucket();
		if (reserveTicks(true) + perBucket <= tank) {
			ItemStack input = items.get(INPUT_SLOT);
			ItemStack container = fillerContainer(input);
			if (!container.isEmpty() && canReturn(container)) {
				input.shrink(1);
				putReturn(container);
				lavaTicks += perBucket;
			}
		}

		// Drain lava from the fluid tank into the burn buffer when there is room.
		// This is the generator consuming its OWN fuel, so it bypasses the tank's canExtract guard
		// (which is false to stop neighbours pulling lava back out) by mutating the tank directly.
		// produce() runs outside any transaction, so a direct field update is the correct internal path.
		if (reserveTicks(true) + perBucket <= tank
				&& fluidTank.amount >= FluidAmounts.BUCKET
				&& fluidTank.fluid.is(Fluids.LAVA)) {
			fluidTank.amount -= FluidAmounts.BUCKET;
			if (fluidTank.amount == 0) {
				fluidTank.fluid = FluidHolder.EMPTY;
			}
			lavaTicks += perBucket;
			setChanged();
		}

		int made = burn(level, pos);
		this.progress = reserveTicks(true);
		this.maxProgress = tank;
		fireHeat.update(level, pos, made > 0);
		return made;
	}

	/**
	 * One tick of the burn. The reserve turns into EU only when one burn tick's whole output fits in the buffer
	 * (R-NRG-11, TC-GEO-001-NEG04). Lava is already in the reserve, so pausing here wastes nothing; asking only for
	 * "any room" burnt a full tick of lava for every few EU a light load drew and threw the rest away (the fuel
	 * generator's MOD-986 rule, here since MOD-1000, whose span assumes it). MOD-994: unless the machine on top waits
	 * for this lava's heat; then the lava goes into heat, every tick, so the heat stays steady (as MOD-988).
	 *
	 * <p>MOD-997: a catalysed bucket starts only when the buffer takes a whole catalysed tick, so no powder is spent
	 * while the generator waits or burns for heat alone; it then pays out at the catalysed rate, and its last tick pays
	 * only what is left, so the bucket gives exactly {@link #catalysedBucketEu()}. Every burning tick hands the plain
	 * burn ticks it used up to {@link #lavaUsedUp} (tally, shavings) and raises the geothermal milestones (MOD-1001).
	 */
	private int burn(Level level, BlockPos pos) {
		long room = energy.getCapacity() - energy.getAmount();
		if (room >= effective(catalysedEuPerTick())) {
			startCatalysedBucket();
		}
		if (catalystEu <= 0 && lavaTicks <= 0) {
			return 0;
		}
		if (room < effectiveEuPerTick() && !fireHeat.wantedAbove(level, pos)) {
			return 0;
		}
		int made;
		if (catalystEu > 0) {
			int before = reserveTicks(false);
			made = (int) Math.min(catalysedEuPerTick(), catalystEu);
			catalystEu -= made;
			lavaUsedUp(before - reserveTicks(false));
		} else {
			lavaTicks--;
			made = euPerBurnTick();
			lavaUsedUp(1);
		}
		if (level instanceof ServerLevel serverLevel) {
			GeothermalAdvancements.lavaBurnt(serverLevel, this); // MOD-1001: "First Lava" and "Station"
		}
		return made;
	}

	/**
	 * Start a catalysed bucket (MOD-997) when none burns, the pool holds a whole bucket and blaze powder waits in
	 * the catalyst slot: one powder and one bucket of the pool become {@code catalystEu}.
	 */
	private void startCatalysedBucket() {
		int bucket = burnTicksPerBucket();
		ItemStack catalyst = items.get(CATALYST_SLOT);
		if (catalystEu > 0 || lavaTicks < bucket || !isCatalyst(catalyst)) {
			return;
		}
		catalyst.shrink(1);
		lavaTicks -= bucket;
		catalystEu = catalysedBucketEu();
		setChanged();
	}

	/** Whether {@code stack} catalyses a bucket (MOD-997): blaze powder only, by the owner's decision. */
	public static boolean isCatalyst(ItemStack stack) {
		return stack.is(Items.BLAZE_POWDER);
	}

	/**
	 * {@code burnTicks} plain burn ticks of lava were used up this tick: the burnt-lava tally counts them (MOD-999)
	 * and the shavings count moves on (MOD-995). A catalysed bucket hands its ticks over as its EU is paid out, the
	 * plain ticks it was worth and no more, so it counts as one bucket for both.
	 */
	private void lavaUsedUp(int burnTicks) {
		if (burnTicks <= 0) {
			return;
		}
		countLavaBurnt(burnTicks);
		countShavings(burnTicks);
	}

	/**
	 * Count {@code burnTicks} of lava burnt and leave one obsidian shaving in {@link #SHAVINGS_SLOT} for every
	 * {@code geothermalBucketsPerShaving} buckets of it (MOD-995). A full slot takes no more and the shaving is
	 * lost; the count still moves on and the burn never waits for room (the decision of MOD-987).
	 */
	private void countShavings(int burnTicks) {
		int perShaving = Math.max(1, GeneratorConfig.geothermalBucketsPerShaving) * Math.max(1, burnTicksPerBucket());
		shavingLavaTicks += burnTicks;
		ItemStack shaving = new ItemStack(ModContent.OBSIDIAN_SHAVINGS.get());
		while (shavingLavaTicks >= perShaving) {
			shavingLavaTicks -= perShaving;
			if (canOutput(SHAVINGS_SLOT, shaving)) {
				addOutput(SHAVINGS_SLOT, shaving.copy());
			}
		}
	}

	/**
	 * MOD-1000: the line's draw is measured only while the chip is in (MOD-125, MOD-692), as on the fuel generator
	 * (MOD-990): the counter it reads runs only then, and without the chip nothing is kept at all.
	 */
	private void measureDraw() {
		if (energy.countersEnabled()) {
			drawMeter.tick(energy.getTotalEnergyOut());
		} else {
			drawMeter.reset();
		}
	}

	/** Whether lava burned on the last tick: the generator is a heat source for the machine on top (MOD-994). */
	public boolean isBurning() {
		return fireHeat.isBurning();
	}

	@Override
	public FireHeat fireHeat() {
		return fireHeat;
	}

	/**
	 * What one burn tick of lava makes before the global multiplier: {@link #produce}'s full rate, the catalysed one
	 * while a catalysed bucket burns (MOD-997). A method and not the knob, so whatever changes the output of this block
	 * changes the burn rule, the span and the load readout alike (MOD-1000).
	 */
	public int euPerBurnTick() {
		return catalystEu > 0 ? catalysedEuPerTick() : GeneratorConfig.geothermalEuPerTick;
	}

	/**
	 * Burn ticks one bucket of lava gives: what {@link #produce} adds per bucket. A method for the same reason as
	 * {@link #euPerBurnTick()} (MOD-1000).
	 */
	public int burnTicksPerBucket() {
		return GeneratorConfig.geothermalBurnTicks;
	}

	/** What one burn tick puts in the buffer: {@link #euPerBurnTick()} after the base tick's global multiplier. */
	public int effectiveEuPerTick() {
		return effective(euPerBurnTick());
	}

	/** A mechanical rate after the base tick's global multiplier, rounded as the base rounds it. */
	private static int effective(int mechanical) {
		return Math.max(1, Math.round(mechanical * Config.globalEuRateMultiplier));
	}

	/**
	 * How long the lava lasts at the line's real draw and what that draw is (MOD-1000), for the statistics
	 * snapshot; {@code null} without the statistics chip, so nothing is worked out for a block that does not
	 * measure. The rule is the fuel generator's ({@link FuelRuntime#ticksLeft}, MOD-990). The lava still to come is
	 * counted as {@link #produce} will burn it: the burn reserve, every whole bucket in the tank (a part bucket never
	 * burns; lava kept over the plain tank from a broken cistern generator counts too, MOD-996), and the fillers in
	 * the intake slot while the output slot takes their containers. The catalysed bucket still burning, and one more
	 * catalysed bucket per powder waiting while whole buckets remain, pay their catalysed EU (MOD-997); all of it is
	 * handed over as ticks at the current full rate, so the span and the "of N EU/t" read the same output.
	 */
	@Override
	public FuelRuntime.@Nullable Readout fuelReadout() {
		if (!energy.countersEnabled()) {
			return null;
		}
		long perBucket = Math.max(1, burnTicksPerBucket());
		long plainTicks = Math.max(0, lavaTicks) + fluidTank.amount / FluidAmounts.BUCKET * perBucket;
		ItemStack input = items.get(INPUT_SLOT);
		ItemStack container = fillerContainer(input);
		if (!container.isEmpty() && canReturn(container)) {
			plainTicks += perBucket * input.getCount();
		}
		ItemStack powder = items.get(CATALYST_SLOT);
		long catalysedAhead = isCatalyst(powder) ? Math.min(powder.getCount(), plainTicks / perBucket) : 0L;
		long fuelEu = Math.max(0L, catalystEu) + catalysedAhead * catalysedBucketEu()
				+ (plainTicks - catalysedAhead * perBucket) * GeneratorConfig.geothermalEuPerTick;
		long rate = Math.max(1, euPerBurnTick());
		long fuelTicks = (fuelEu + rate - 1) / rate;
		int euPerTick = effectiveEuPerTick();
		DrawMeter.Window draw = drawMeter.window();
		long ticksLeft = FuelRuntime.ticksLeft(fuelTicks, euPerTick, energy.getAmount(), draw.eu(), draw.ticks());
		return new FuelRuntime.Readout(ticksLeft, draw.eu(), draw.ticks(), euPerTick, FuelRuntime.Kind.LAVA);
	}

	/**
	 * {@code reserveTicks} burn ticks of lava left the reserve (MOD-999), counted only while the statistics chip
	 * measures. A burn tick is a {@link #burnTicksPerBucket()}-th of the bucket that filled it, the same divisor
	 * {@link #produce} fills the reserve with.
	 */
	private void countLavaBurnt(int reserveTicks) {
		if (energy.countersEnabled()) {
			lavaBurnt.burn(reserveTicks, burnTicksPerBucket());
		}
	}

	/** The EU the burnt lava really put in the buffer (MOD-999): every EU this generator makes comes from lava. */
	@Override
	protected void onCredited(Level level, long credited) {
		if (energy.countersEnabled()) {
			lavaBurnt.addEu(credited);
		}
	}

	/** The burnt lava for the statistics snapshot (MOD-999); {@code null} without the chip or before any burnt. */
	@Override
	public LavaBurnTally.@Nullable Shown lavaBurnt() {
		if (!energy.countersEnabled() || lavaBurnt.isEmpty()) {
			return null;
		}
		return lavaBurnt.shown();
	}

	/** The tally itself, for the gametests. */
	public LavaBurnTally lavaBurntTally() {
		return lavaBurnt;
	}

	/**
	 * The empty container an accepted lava filler leaves behind — a single empty bucket for a
	 * {@link Items#LAVA_BUCKET}, a single empty {@link ModContent#VACUUM_CAPSULE} for a lava-filled
	 * capsule — or {@link ItemStack#EMPTY} if {@code input} is not a lava filler this machine accepts.
	 */
	private static ItemStack fillerContainer(ItemStack input) {
		if (input.is(Items.LAVA_BUCKET)) {
			return new ItemStack(Items.BUCKET);
		}
		if (dev.alaindustrial.item.fluid.CapsuleFuel.isLavaCapsule(input)) {
			return new ItemStack(ModContent.VACUUM_CAPSULE.get());
		}
		return ItemStack.EMPTY;
	}

	/** Whether the output slot can accept one more {@code returned} container (empty slot or a matching stack). */
	private boolean canReturn(ItemStack returned) {
		ItemStack out = items.get(OUTPUT_SLOT);
		return out.isEmpty()
				|| (ItemStack.isSameItemSameComponents(out, returned) && out.getCount() < out.getMaxStackSize());
	}

	/** Place one {@code returned} container into the output slot (must have passed {@link #canReturn}). */
	private void putReturn(ItemStack returned) {
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			items.set(OUTPUT_SLOT, returned);
		} else {
			out.grow(1);
		}
	}

	/**
	 * The lava filler goes into the input slot and blaze powder into the catalyst slot (MOD-997), by hand or by a
	 * hopper or pipe on a working face alike; the container and shavings slots are filled by the generator alone.
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == CATALYST_SLOT) {
			return isCatalyst(stack);
		}
		return slot == INPUT_SLOT
				&& (stack.is(Items.LAVA_BUCKET) || dev.alaindustrial.item.fluid.CapsuleFuel.isLavaCapsule(stack));
	}

	/** Automation takes the shavings and nothing else (MOD-995); the empty-container slot keeps its old rule. */
	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == SHAVINGS_SLOT;
	}

	/** GUI sync channels (MOD-712, BE-7): the base four, then whether a catalysed bucket burns (MOD-997). */
	public enum Channel { ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, CATALYST }

	/** Width of {@link #getDataAccess()}, which the menu's client stub sizes itself from (MOD-235). */
	public static final int DATA_COUNT = Channel.values().length;

	@Override
	protected SyncChannels createChannels() {
		return channels(Channel.class)
				.read(Channel.CATALYST, () -> catalystEu > 0 ? 1 : 0)
				.build();
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new GeothermalGeneratorMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	// --- item-form lava (MOD-998: the generator drops with its lava, the portable-tank pattern) ---

	/**
	 * All the lava this generator holds, tank and burn reserve, as one volume in mB on the dropped item. The EU
	 * charge deliberately stays behind (the coal generator's rule); an empty generator gets no component and
	 * keeps stacking like a fresh one.
	 */
	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		// A catalysed bucket still burning goes along as the plain lava it was worth, rounded down (MOD-997): its
		// powder is spent, so the bonus stays behind with the charge.
		long lava = GeothermalLava.totalMb(fluidTank.amount, reserveTicks(false), GeneratorConfig.geothermalBurnTicks);
		if (lava > 0) {
			builder.set(ModDataComponents.STORED_LAVA.get(), lava);
			// A generator holding lava must not stack with an empty one (same rule as the portable tank).
			builder.set(DataComponents.MAX_STACK_SIZE, 1);
		}
	}

	/**
	 * Put the carried lava back: the burn reserve first, the rest into the tank. The tank is filled past its
	 * current capacity if it must (a cistern that dropped apart on the break), up to {@link #MAX_STORED_MB}: the
	 * portable tank's clamp, applied to the most this generator can ever hold, so no lava is lost (MOD-996).
	 */
	@Override
	protected void applyImplicitComponents(DataComponentGetter getter) {
		super.applyImplicitComponents(getter);
		GeothermalLava.Split split = GeothermalLava.split(getter.getOrDefault(ModDataComponents.STORED_LAVA.get(), 0L),
				tankCapacity(), MAX_STORED_MB - lavaCapacityMb(), GeneratorConfig.geothermalBurnTicks);
		lavaTicks = split.lavaTicks();
		catalystEu = 0L; // the carried lava is plain lava: a catalysed bucket never travels (MOD-997)
		fluidTank.amount = split.tankMb();
		fluidTank.fluid = fluidTank.amount > 0 ? FluidHolder.of(Fluids.LAVA) : FluidHolder.EMPTY;
		this.progress = lavaTicks;
		setChanged();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("LavaTicks", lavaTicks);
		// The tank only ever holds lava, so persist the amount alone (fluid identity is implicit).
		output.putLong("FluidTankMb", fluidTank.amount);
		// MOD-997/995: nothing carried, nothing written, so a generator that never had them saves as before.
		if (catalystEu > 0) {
			output.putLong(KEY_CATALYST_EU, catalystEu);
		}
		if (shavingLavaTicks > 0) {
			output.putInt(KEY_SHAVING_LAVA_TICKS, shavingLavaTicks);
		}
		if (lavaBurnt.mb() > 0L || lavaBurnt.eu() > 0L) {
			output.putLong(KEY_LAVA_BURNT_MB, lavaBurnt.mb());
			output.putLong(KEY_LAVA_EU, lavaBurnt.eu());
		}
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		lavaTicks = input.getIntOr("LavaTicks", 0);
		// An older save has neither: no catalysed bucket burns, and the next shaving is a whole bucket away.
		catalystEu = Math.max(0L, input.getLongOr(KEY_CATALYST_EU, 0L));
		shavingLavaTicks = Math.max(0, input.getIntOr(KEY_SHAVING_LAVA_TICKS, 0));
		// MOD-028: prefer the new mB-valued key; fall back to the legacy Fabric v0.1.0 droplet-valued
		// "FluidTank" key, converting ÷81 (81000 droplets/bucket ÷ 81 = 1000 mB/bucket, exact — machine
		// transactions always move whole buckets, so legacy values are always bucket-multiples).
		long amount = input.getLong("FluidTankMb")
				.orElseGet(() -> input.getLong("FluidTank")
						.map(dr -> dr / FluidAmounts.FABRIC_DROPLETS_PER_MB).orElse(0L));
		// MOD-996: clamped to the most the generator can hold, not to the current capacity — the upgrade slots
		// load with the inventory, and a clamp to the plain tank would eat a cistern's lava on every load.
		fluidTank.amount = Math.max(0L, Math.min(MAX_STORED_MB, amount));
		fluidTank.fluid = fluidTank.amount > 0 ? FluidHolder.of(Fluids.LAVA) : FluidHolder.EMPTY;
		// MOD-999: an older save, or a generator that never measured, has no keys and starts from zero.
		lavaBurnt.restore(input.getLongOr(KEY_LAVA_BURNT_MB, 0L), input.getLongOr(KEY_LAVA_EU, 0L));
	}
}
