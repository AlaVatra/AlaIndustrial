package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.ApiaryDroneHiveBlock;
import dev.alaindustrial.block.HiveAttachmentBlock;
import dev.alaindustrial.block.entity.machine.DroneBay;
import dev.alaindustrial.block.entity.machine.DroneFlight;
import dev.alaindustrial.core.drone.DroneRoute;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.core.apiary.ApiaryConfig;
import dev.alaindustrial.core.apiary.ApiaryRules;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.item.apiary.ApiaryMapItem;
import dev.alaindustrial.item.apiary.ApiaryMapPoints;
import dev.alaindustrial.menu.ApiaryDroneStationMenu;
import dev.alaindustrial.settlement.drone.DroneAirspace;
import dev.alaindustrial.registry.ModContent;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

// size-justified: one machine's whole round — the four bays' phases, the map scan with its reserves, the tank and the
// ports — kept in one place so the order a tick acts in reads top to bottom; the arithmetic is already in ApiaryRules
// and the bay's state in DroneBay.
/**
 * The apiary station (MOD-923, slice 2): four bumblebee drones that drain full hives through the jars written on the
 * station's apiary map, and a tank the honey goes into.
 *
 * <p><b>The drones are not entities.</b> Each bay is a {@link DroneBay} — its phase, its jar, what it carries — and
 * the renderer draws the drone from it, as the garden drone is drawn from its {@link DroneFlight}. A drone cannot be
 * lost, killed or stranded in an unloaded chunk.
 *
 * <p><b>EU is paid for a docking that took honey, never per tick.</b> A launch only checks that the buffer covers
 * the flight on top of what is held for the drones already out; the price is drained at the end of the docking, and
 * only if the hive is still full. A drone that finds the hive taken by another station flies home empty and costs
 * nothing. An idle station spends nothing and sleeps for the scan interval.
 *
 * <p><b>Nothing is ever voided.</b> A launch waits for room in the tank and the comb slot for every drone that may
 * still come back with honey and a comb; a drone pulled out of its bay, or a hive taken off the casing, pours what it
 * carries straight into the tank, whose room was held for it.
 */
public final class ApiaryDroneStationBlockEntity extends MachineBlockEntity
		implements FluidPortHost, MenuProvider, NoUpgradePanel {

	/** The apiary map. */
	public static final int MAP_SLOT = 0;
	/** The four bays' drones, north, east, south, west. */
	public static final int BAY_SLOT_START = 1;
	/** Combs the drones bring; taken out by hand or by an item pipe on any face. */
	public static final int COMB_SLOT = BAY_SLOT_START + ApiaryRules.BAYS;
	public static final int SLOT_COUNT = COMB_SLOT + 1;

	/** The tank: ten buckets, like every machine with a tank. */
	public static final long TANK_CAPACITY = FluidAmounts.BUCKET * 10;

	/** Sentinel for an empty tank on the fluid-id sync channel. */
	public static final int FLUID_ID_NONE = FluidTank.FLUID_ID_NONE;

	/** Steps of the tank level a watching client is told about: a change smaller than 1/32 is not worth a packet. */
	public static final int TANK_SYNC_STEPS = 32;
	/** Fewest ticks between two tank-level updates to watching clients. */
	public static final int TANK_SYNC_INTERVAL = 10;

	/** Honey out. Filled only by the drones; any face lets a pipe or a pump drain it, none fills it. */
	public final FluidTank honeyTank = new FluidTank(TANK_CAPACITY,
			fluid -> false,
			fluid -> true,
			() -> {
				setChanged();
				// A neighbour that just drained a full tank lets the station send drones again.
				wake();
			});

	private final DroneBay[] bays = new DroneBay[ApiaryRules.BAYS];

	private ApiaryStationStatus status = ApiaryStationStatus.WAITING;

	/** The map's rows as the screen shows them: {@link ApiaryEntryStatus} ordinal times 256 plus the distance. */
	private final int[] entries = new int[ApiaryMapPoints.MAX];

	/** Ticks until the next look at the hives while drones are out (an idle station sleeps instead). */
	private int scanCountdown;
	/** The tank level last sent to watching clients, in {@link #TANK_SYNC_STEPS}, and when. */
	private int syncedTankStep = -1;
	private long syncedTankAt;
	/** A game test's stand-in for {@code Level.isLoaded}; {@code null} in play. */
	@Nullable
	private Predicate<BlockPos> loadedProbe;
	/** EU paid in this tick, for the statistics panel; never persisted. */
	private long euSpentThisTick;

	/** Client only: the honey column as drawn, catching up with the tank (see {@link #shownTankFill}). */
	private float shownFill = -1.0F;
	private float shownAt;

	public ApiaryDroneStationBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.APIARY_DRONE_STATION_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				ApiaryConfig.apiaryStationBuffer, EnergyTier.LV.maxVoltage(), 0L);
		for (int i = 0; i < bays.length; i++) {
			bays[i] = new DroneBay();
		}
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		euSpentThisTick = 0L;
		int sleep = level instanceof ServerLevel server ? runStation(server, pos) : IDLE_SLEEP_TICKS;
		recordEuRate((int) Math.min(Integer.MAX_VALUE, euSpentThisTick));
		syncTankLevel(level);
		return sleep;
	}

	private int runStation(ServerLevel level, BlockPos pos) {
		if (!ApiaryDroneHiveBlock.isHive(level.getBlockState(pos.above()))) {
			recallAll(level);
			java.util.Arrays.fill(entries, 0);
			return finish(level, ApiaryStationStatus.NO_HIVE);
		}
		boolean busy = false;
		for (int i = 0; i < bays.length; i++) {
			tickBay(level, i);
			busy |= bays[i].busy();
		}
		if (busy && --scanCountdown > 0) {
			return finish(level, ApiaryStationStatus.WORKING);
		}
		scanCountdown = Math.max(1, ApiaryConfig.apiaryScanIntervalTicks);
		ApiaryStationStatus reason = scanAndLaunch(level, pos);
		return finish(level, anyBusy() ? ApiaryStationStatus.WORKING : reason);
	}

	/** Records the status, lights the beacon, and says how long the station may sleep. */
	private int finish(ServerLevel level, ApiaryStationStatus next) {
		if (status != next) {
			status = next;
			setChangedQuietly();
		}
		ApiaryDroneHiveBlock.setLit(level, worldPosition,
				next == ApiaryStationStatus.WORKING || next == ApiaryStationStatus.WAITING);
		return anyBusy() ? 0 : Math.max(1, ApiaryConfig.apiaryScanIntervalTicks);
	}

	/** One tick of bay {@code i}: the leg counts down, the docking steps the jar, the pour finishes. */
	private void tickBay(ServerLevel level, int i) {
		DroneBay bay = bays[i];
		if (!bay.busy()) {
			return;
		}
		if (items.get(BAY_SLOT_START + i).isEmpty()) {
			// The drone was taken out mid-round (by hand, or through any other path that empties the slot).
			dropRound(level, bay);
			return;
		}
		long now = level.getGameTime();
		switch (bay.phase()) {
			case OUTBOUND -> {
				if (bay.flight().tick() != DroneFlight.Phase.ARRIVED) {
					return;
				}
				if (jarServed(level, bay.jar())) {
					bay.dock(now);
				} else {
					bay.returnHome(now, 0L, false);
				}
				changed();
			}
			case DOCKING -> tickDocking(level, bay, now);
			case RETURNING -> {
				if (bay.flight().tick() != DroneFlight.Phase.HOME) {
					return;
				}
				bay.land(now);
				pour(bay);
				changed();
			}
			case POURING -> {
				if (bay.count() >= Math.max(1, ApiaryConfig.apiaryPourTicks)) {
					bay.park();
					changed();
				}
			}
			default -> {
			}
		}
	}

	/** The docking: the jar steps down while the drone pumps; at the end the hive is drained, if it is still full. */
	private void tickDocking(ServerLevel level, DroneBay bay, long now) {
		BlockPos jar = bay.jar();
		if (!jarServed(level, jar)) {
			if (jar != null && level.isLoaded(jar)) {
				HiveAttachmentBlock.restore(level, jar);
			}
			bay.returnHome(now, 0L, false);
			changed();
			return;
		}
		int dock = Math.max(5, ApiaryConfig.apiaryDockTicks);
		int before = ApiaryRules.drainStepsDone(bay.elapsed(), dock);
		int elapsed = bay.count();
		if (ApiaryRules.drainStepsDone(elapsed, dock) > before && HiveAttachmentBlock.stepDown(level, jar)) {
			level.sendParticles(ParticleTypes.DRIPPING_HONEY, jar.getX() + 0.5, jar.getY() + 0.85, jar.getZ() + 0.5,
					2, 0.08, 0.02, 0.08, 0.0);
		}
		if (elapsed < dock) {
			return;
		}
		long honey = 0L;
		boolean comb = false;
		BlockState jarState = level.getBlockState(jar);
		BlockPos hivePos = HiveAttachmentBlock.hivePos(jar, jarState.getValue(HiveAttachmentBlock.FACING));
		BlockState hive = level.getBlockState(hivePos);
		long price = bay.reservedEu();
		if (hiveFull(hive) && energy.getAmount() >= price) {
			drainHive(level, hive, hivePos);
			euSpentThisTick += energy.drainInternal(price);
			creditUsefulWork(level, price);
			honey = Math.max(1, ApiaryConfig.apiaryHoneyPerHive);
			comb = ApiaryRules.combWon(ApiaryConfig.apiaryCombChance, level.getRandom().nextDouble());
		}
		HiveAttachmentBlock.restore(level, jar);
		bay.returnHome(now, honey, comb);
		changed();
	}

	/**
	 * The hive's level goes to 0 as under a campfire's smoke: vanilla's own {@code resetHoneyLevel}, no player, so the
	 * bees stay inside, are not angered and keep their routine. A modded hive of the tag that is no
	 * {@link BeehiveBlock} gets the same state written directly.
	 */
	private static void drainHive(ServerLevel level, BlockState hive, BlockPos hivePos) {
		if (hive.getBlock() instanceof BeehiveBlock beehive) {
			beehive.resetHoneyLevel(level, hive, hivePos);
		} else {
			level.setBlock(hivePos, hive.setValue(BeehiveBlock.HONEY_LEVEL, 0), Block.UPDATE_ALL);
		}
	}

	private static boolean hiveFull(BlockState hive) {
		return HiveAttachmentBlock.isHive(hive)
				&& hive.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS;
	}

	/** A drone home with honey: the honey goes into the tank and the comb into its slot at once; the pour is shown. */
	private void pour(DroneBay bay) {
		if (bay.honey() > 0) {
			honeyTank.fluid = FluidHolder.of(ModContent.HONEY.get());
			honeyTank.amount = Math.min(honeyTank.capacity, honeyTank.amount + bay.honey());
		}
		if (bay.comb()) {
			ItemStack comb = new ItemStack(Items.HONEYCOMB);
			if (canOutput(COMB_SLOT, comb)) {
				addOutput(COMB_SLOT, comb);
			}
		}
	}

	/** A round broken off where it is: a docked jar is let go, carried honey and comb land at once. */
	private void dropRound(ServerLevel level, DroneBay bay) {
		BlockPos jar = bay.jar();
		if (bay.phase() == DroneBay.Phase.DOCKING && jar != null && level.isLoaded(jar)) {
			HiveAttachmentBlock.restore(level, jar);
		}
		if (bay.phase() == DroneBay.Phase.RETURNING) {
			pour(bay);
		}
		bay.park();
		changed();
	}

	/** The hive was taken off the casing: every drone is back in its bay at once, its honey in the tank. */
	private void recallAll(ServerLevel level) {
		for (DroneBay bay : bays) {
			if (bay.busy()) {
				dropRound(level, bay);
			}
		}
	}

	// ---------------------------------------------------------------- the map

	/**
	 * Refreshes the screen's rows and sends a drone to every full hive it can serve, in the map's order, each from
	 * the free bay nearest to it. Returns why nothing (more) flew.
	 */
	private ApiaryStationStatus scanAndLaunch(ServerLevel level, BlockPos pos) {
		ItemStack map = items.get(MAP_SLOT);
		java.util.Arrays.fill(entries, 0);
		if (!map.is(ModContent.APIARY_MAP.get())) {
			return ApiaryStationStatus.NO_MAP;
		}
		ApiaryMapPoints points = ApiaryMapItem.points(map);
		boolean anyFull = false;
		ApiaryStationStatus blocker = null;
		for (int row = 0; row < points.size(); row++) {
			GlobalPos point = points.get(row);
			double distance = Math.sqrt(point.pos().distSqr(pos));
			ApiaryEntryStatus entry = entryStatus(level, pos, point, distance);
			if (entry == ApiaryEntryStatus.FULL) {
				anyFull = true;
				ApiaryStationStatus refused = tryLaunch(level, pos, point.pos(), distance);
				if (refused == null) {
					entry = ApiaryEntryStatus.DRONE_OUT;
				} else if (blocker == null) {
					blocker = refused;
				}
			}
			int blocks = point.dimension().equals(level.dimension()) ? ApiaryRules.blocksOf(distance) : 0;
			entries[row] = entry.ordinal() * 256 + Math.min(255, blocks);
		}
		if (!anyDrone()) {
			return ApiaryStationStatus.NO_DRONES;
		}
		return anyFull && blocker != null ? blocker : ApiaryStationStatus.WAITING;
	}

	/** What the station makes of one row of its map. */
	private ApiaryEntryStatus entryStatus(ServerLevel level, BlockPos station, GlobalPos point, double distance) {
		if (!point.dimension().equals(level.dimension())) {
			return ApiaryEntryStatus.OTHER_DIMENSION;
		}
		if (!ApiaryRules.inRange(distance, ApiaryConfig.apiaryStationRange)) {
			return ApiaryEntryStatus.TOO_FAR;
		}
		BlockPos jar = point.pos();
		if (!loaded(level, jar)) {
			return ApiaryEntryStatus.UNLOADED;
		}
		BlockState jarState = level.getBlockState(jar);
		if (!(jarState.getBlock() instanceof HiveAttachmentBlock)) {
			return ApiaryEntryStatus.NO_JAR;
		}
		BlockPos hivePos = HiveAttachmentBlock.hivePos(jar, jarState.getValue(HiveAttachmentBlock.FACING));
		if (!loaded(level, hivePos)) {
			return ApiaryEntryStatus.UNLOADED;
		}
		BlockState hive = level.getBlockState(hivePos);
		if (!HiveAttachmentBlock.isHive(hive)) {
			return ApiaryEntryStatus.NO_JAR;
		}
		if (droneServing(jar)) {
			return ApiaryEntryStatus.DRONE_OUT;
		}
		return hiveFull(hive) ? ApiaryEntryStatus.FULL : ApiaryEntryStatus.WAITING;
	}

	/** Sends a drone to the full hive behind {@code jar}, or says why it cannot; {@code null} means one flew. */
	@Nullable
	private ApiaryStationStatus tryLaunch(ServerLevel level, BlockPos station, BlockPos jar, double distance) {
		boolean[] free = new boolean[bays.length];
		double[] distanceSq = new double[bays.length];
		long reserved = 0L;
		int honeyOut = 0;
		for (int i = 0; i < bays.length; i++) {
			DroneBay bay = bays[i];
			free[i] = !bay.busy() && !items.get(BAY_SLOT_START + i).isEmpty();
			double[] pad = ApiaryRules.padSpot(i);
			double dx = station.getX() + pad[0] - (jar.getX() + 0.5);
			double dy = station.getY() + pad[1] - (jar.getY() + 0.5);
			double dz = station.getZ() + pad[2] - (jar.getZ() + 0.5);
			distanceSq[i] = dx * dx + dy * dy + dz * dz;
			reserved += bay.reservedEu();
			if (bay.honeyStillOut()) {
				honeyOut++;
			}
		}
		int chosen = ApiaryRules.chooseBay(distanceSq, free);
		if (chosen < 0) {
			return anyDrone() ? ApiaryStationStatus.WORKING : ApiaryStationStatus.NO_DRONES;
		}
		long perHive = Math.max(1, ApiaryConfig.apiaryHoneyPerHive);
		boolean sameFluid = honeyTank.fluid.isEmpty() || honeyTank.fluid.equals(FluidHolder.of(ModContent.HONEY.get()));
		if (!sameFluid || !ApiaryRules.tankHasRoom(honeyTank.capacity, honeyTank.amount, honeyOut, perHive)) {
			return ApiaryStationStatus.TANK_FULL;
		}
		ItemStack combs = items.get(COMB_SLOT);
		int combStack = new ItemStack(Items.HONEYCOMB).getMaxStackSize();
		if (!combs.isEmpty() && !combs.is(Items.HONEYCOMB)
				|| !ApiaryRules.combSlotHasRoom(combs.getCount(), combStack, honeyOut)) {
			return ApiaryStationStatus.OUTPUT_FULL;
		}
		long price = ApiaryRules.launchCost(ApiaryConfig.apiaryDroneLaunchEu, ApiaryConfig.apiaryDroneEuPerBlock,
				distance);
		if (!ApiaryRules.energyCovers(energy.getAmount(), reserved, price)) {
			return ApiaryStationStatus.NO_ENERGY;
		}
		// The way round trees and walls (owner, in-game round of 2026-11-07): the town drones' search over open cells,
		// from the hive on top to a cell next to the jar. With none, the drone keeps the plain arc.
		DroneRoute route = DroneAirspace.route(level, station.above(), jar, null);
		int[] corners = route.found() ? route.points() : new int[0];
		double flown = route.found() ? Math.max(distance, route.length()) : distance;
		bays[chosen].launch(jar, jar, corners, ApiaryRules.legTicks(flown), level.getGameTime(), price,
				(float) ApiaryRules.arcHeight(distance));
		changed();
		return null;
	}

	/** Whether the round to {@code jar} may go on: the map still lists it, and a jar on a hive still stands there. */
	private boolean jarServed(ServerLevel level, @Nullable BlockPos jar) {
		if (jar == null) {
			return false;
		}
		ItemStack map = items.get(MAP_SLOT);
		if (!map.is(ModContent.APIARY_MAP.get())
				|| !ApiaryMapItem.points(map).contains(GlobalPos.of(level.dimension(), jar))
				|| !loaded(level, jar)) {
			return false;
		}
		BlockState state = level.getBlockState(jar);
		return state.getBlock() instanceof HiveAttachmentBlock
				&& HiveAttachmentBlock.isHive(level.getBlockState(
						HiveAttachmentBlock.hivePos(jar, state.getValue(HiveAttachmentBlock.FACING))));
	}

	/** Whether {@code pos}'s chunk is loaded; the station never loads one (it reads only what is already there). */
	private boolean loaded(Level level, BlockPos pos) {
		return loadedProbe != null ? loadedProbe.test(pos) : level.isLoaded(pos);
	}

	/**
	 * A game test's answer to "is this cell's chunk loaded", as the town's drone nest takes it: a gametest cannot
	 * unload a chunk next to its own structure. {@code null} goes back to the level. Not saved.
	 */
	public void useLoadedProbe(@Nullable Predicate<BlockPos> probe) {
		this.loadedProbe = probe;
	}

	private boolean droneServing(BlockPos jar) {
		for (DroneBay bay : bays) {
			if (bay.busy() && jar.equals(bay.jar())) {
				return true;
			}
		}
		return false;
	}

	private boolean anyDrone() {
		for (int i = 0; i < bays.length; i++) {
			if (!items.get(BAY_SLOT_START + i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private boolean anyBusy() {
		for (DroneBay bay : bays) {
			if (bay.busy()) {
				return true;
			}
		}
		return false;
	}

	/** A phase changed: saved, and sent to the clients that draw the drones. */
	private void changed() {
		setChangedQuietly();
		syncBlockEntityToClient();
	}

	/** Tells watching clients about the tank level in steps of 1/32, at most every {@link #TANK_SYNC_INTERVAL}. */
	private void syncTankLevel(Level level) {
		int step = (int) (honeyTank.amount * TANK_SYNC_STEPS / TANK_CAPACITY);
		long now = level.getGameTime();
		if (step != syncedTankStep && now - syncedTankAt >= TANK_SYNC_INTERVAL) {
			syncedTankStep = step;
			syncedTankAt = now;
			syncBlockEntityToClient();
		}
	}

	// ---------------------------------------------------------------- read by the renderer, the screen, the tests

	/** Bay {@code i}: its phase, its flight, what it carries. */
	public DroneBay bay(int i) {
		return bays[i];
	}

	/** Whether bay {@code i} holds a drone. */
	public boolean hasDrone(int i) {
		return !items.get(BAY_SLOT_START + i).isEmpty();
	}

	/** Whether any drone is in the air, out or home-bound — the flight loop's "working". */
	public boolean anyInFlight() {
		for (DroneBay bay : bays) {
			if (bay.phase() == DroneBay.Phase.OUTBOUND || bay.phase() == DroneBay.Phase.RETURNING) {
				return true;
			}
		}
		return false;
	}

	public ApiaryStationStatus status() {
		return status;
	}

	/** The honey tank, for the menu and the gametests. */
	public FluidPort honeyPort() {
		return honeyTank;
	}

	/** How full the tank is, 0..1. */
	public float tankFill() {
		return honeyTank.amount <= 0 ? 0.0F : (float) honeyTank.amount / TANK_CAPACITY;
	}

	/**
	 * Client only: the honey column as drawn at {@code time}, catching up with {@link #tankFill} over about the pour's
	 * 20 ticks rather than jumping when a packet arrives.
	 */
	public float shownTankFill(float time) {
		float target = tankFill();
		if (shownFill < 0.0F || time < shownAt) {
			shownFill = target;
		} else {
			float k = Math.min(1.0F, (time - shownAt) / 7.0F);
			shownFill += (target - shownFill) * k;
		}
		shownAt = time;
		return shownFill;
	}

	@Override
	public FluidPort fluidPort(Direction side) {
		return honeyTank;
	}

	/** Every face takes EU, the front included (owner, 2026-10-31, item 2); mirrored by the block's cable arms. */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.IN;
	}

	/** Every face, the front included, is open to item pipes; what each slot allows is said below. */
	@Override
	public int[] getSlotsForFace(Direction side) {
		int[] slots = new int[SLOT_COUNT];
		for (int i = 0; i < SLOT_COUNT; i++) {
			slots[i] = i;
		}
		return slots;
	}

	/** By hand only: one map, one drone per bay; the comb slot is filled by the drones. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == MAP_SLOT) {
			return items.get(MAP_SLOT).isEmpty() && stack.is(ModContent.APIARY_MAP.get());
		}
		if (slot >= BAY_SLOT_START && slot < BAY_SLOT_START + ApiaryRules.BAYS) {
			return items.get(slot).isEmpty() && stack.is(ModContent.APIARY_DRONE.get());
		}
		return false;
	}

	/** Hoppers and pipes neither put a map or a drone in nor take one out (spec): only combs leave by pipe. */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return false;
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == COMB_SLOT;
	}

	/** Pays per docking (ADR-049): the network plans it as a store, nearest first. */
	@Override
	public long perTickDemand() {
		return 0L;
	}

	// ---------------------------------------------------------------- sync channels

	/**
	 * GUI sync channels (ADR-042): the base four, the tank in permille and its fluid, the status, each bay's phase,
	 * and the map's eight rows ({@link ApiaryEntryStatus} ordinal times 256 plus the distance in blocks).
	 */
	public enum Channel {
		ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, LEVEL_PERMILLE, FLUID_ID, STATUS,
		BAY_0, BAY_1, BAY_2, BAY_3,
		ENTRY_0, ENTRY_1, ENTRY_2, ENTRY_3, ENTRY_4, ENTRY_5, ENTRY_6, ENTRY_7
	}

	public static final int DATA_COUNT = Channel.values().length;

	@Override
	protected SyncChannels createChannels() {
		SyncChannels.Builder<Channel> builder = channels(Channel.class)
				.read(Channel.LEVEL_PERMILLE, () -> SyncChannels.permille(honeyTank.amount, TANK_CAPACITY))
				.read(Channel.FLUID_ID, () -> honeyTank.fluidSyncId())
				.read(Channel.STATUS, () -> status.ordinal());
		for (int i = 0; i < ApiaryRules.BAYS; i++) {
			int bay = i;
			builder.read(Channel.valueOf("BAY_" + i), () -> bays[bay].phase().ordinal());
		}
		for (int i = 0; i < ApiaryMapPoints.MAX; i++) {
			int row = i;
			builder.read(Channel.valueOf("ENTRY_" + i), () -> entries[row]);
		}
		return builder.build();
	}

	// ---------------------------------------------------------------- persistence

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		honeyTank.save(output, "Honey");
		output.putInt("Status", status.ordinal());
		for (int i = 0; i < bays.length; i++) {
			bays[i].save(output.child("Bay" + i));
		}
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		honeyTank.load(input, "Honey");
		status = ApiaryStationStatus.byOrdinal(input.getIntOr("Status", ApiaryStationStatus.WAITING.ordinal()));
		for (int i = 0; i < bays.length; i++) {
			bays[i].load(input.childOrEmpty("Bay" + i));
		}
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ApiaryDroneStationMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
