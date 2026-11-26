package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.ElevatorStationBlock;
import dev.alaindustrial.block.ElevatorWinchBlock;
import dev.alaindustrial.block.entity.elevator.ElevatorCarry;
import dev.alaindustrial.block.entity.elevator.ElevatorLandings;
import dev.alaindustrial.block.entity.elevator.ElevatorRescue;
import dev.alaindustrial.block.entity.elevator.ElevatorShaft;
import dev.alaindustrial.core.elevator.ElevatorConfig;
import dev.alaindustrial.core.elevator.ElevatorFare;
import dev.alaindustrial.core.elevator.ElevatorTrip;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.ElevatorPanelMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModSounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

// size-justified: the whole winch — shaft scan and floors, the call queue, a trip and its catches, the fares and
// the panel's reads; MOD-1030 put the refused-press check before the scan and the riders count out for the panel.
/**
 * The elevator's winch (MOD-769): the lid of the glass shaft. It reads the tube under it
 * ({@link ElevatorShaft}), holds the EU buffer, takes the calls of the stations and drives the cabin inside the tube.
 *
 * <p><b>The cabin is a picture with a clock.</b> There is no cabin block and no cabin entity. Where the cabin is
 * comes from {@link ElevatorTrip} and the game time, the way the quarry places its bridge (MOD-952): the server
 * writes a trip once, when it sets off, the client gets it with the block entity's update and computes the same
 * height every frame. The winch's renderer draws the cabin and its rope there.
 *
 * <p><b>Riders.</b> Every tick, on both sides, the winch puts what stands on the cabin's floor back on it and gives
 * it the cabin's speed ({@link ElevatorCarry}); each side moves only what it is the authority for, so a player is
 * moved by their own client and never pulled back by the server.
 *
 * <p><b>Paid up front.</b> A trip is paid in full when the cabin sets off ({@link ElevatorFare}); without the EU the
 * call is refused and the caller told how much it needs, so a cabin never stops in the shaft for want of power. The
 * tube is the cabin's way: a cell of it broken or swapped while the cabin moves sets the safety catches, which lower
 * it to the nearest floor below whose station and tube are still whole.
 *
 * <p><b>Stations.</b> The station doors open only at the floor the cabin stands at, and the station lamps say where
 * it is ({@link ElevatorLandings}). The winch's own lamp ({@code assembled}) is green while the shaft is whole.
 */
public class ElevatorWinchBlockEntity extends EnergyBlockEntity {

	/** Ticks a paid trip takes to reach full speed, and to stop from it. */
	public static final int RAMP_TICKS = 10;
	/** How often a standing winch reads its column, and how often a running one does. */
	static final int SCAN_TICKS = 40;
	static final int SCAN_TICKS_RUNNING = 5;
	/** How far behind the game clock a frame draws the cabin: see {@link #drawnCabinHeight}. */
	public static final int DRAW_LAG_TICKS = 2;
	private static final double EPS = 1.0E-6D;

	/** A call waiting for the cabin: the floor, and who to tell when it is served or refused. */
	private record Call(int floor, @Nullable UUID caller) {
	}

	private ElevatorShaft shaft;
	/** The floors of the last whole shaft: the catches still know them after a frame is gone. Saved and synced. */
	private List<Integer> floors = List.of();
	/** Where the cabin's floor stands while no trip runs; NaN until a whole shaft puts it on its lowest floor. */
	private double cabinY = Double.NaN;
	private @Nullable ElevatorTrip trip;
	private final Deque<Call> calls = new ArrayDeque<>();
	private int scanCooldown;
	private int scans;
	private boolean justLoaded = true;
	/** The last thing the winch told a player: read by the gametests, which watch no chat. */
	private @Nullable Component lastNotice;

	public ElevatorWinchBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ELEVATOR_WINCH_BE.get(), pos, state, EnergyTier.LV, ElevatorConfig.elevatorBuffer,
				ElevatorConfig.elevatorMaxInput, 0L);
		this.shaft = new ElevatorShaft(pos, List.of(), pos.getY(), Integer.MIN_VALUE, ElevatorShaft.Problem.NO_COLUMN);
	}

	/** Power comes in on every face but the front (R-NRG-03) and the bottom, which closes the shaft. */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == Direction.DOWN ? EnergyRole.NONE : facingAwareRole(worldFace, EnergyRole.IN);
	}

	// --- what the cabin is doing --------------------------------------------------------------------------

	/** The cabin floor's height at game time {@code now}; NaN while the winch has never had a whole shaft. */
	public double cabinHeight(double now) {
		return trip != null ? trip.heightAt(now) : cabinY;
	}

	/**
	 * Where a frame draws the cabin's floor: on the clock its riders are drawn on. A client moves its clock on only
	 * after its entities and block entities ticked, and draws an entity between its last two tick positions, so the
	 * entities of a frame stand {@value #DRAW_LAG_TICKS} ticks behind {@code gameTime + partialTick}; a cabin drawn on
	 * the bare clock ran that far ahead of its rider (MOD-769 round 3: eyes over the roof all the way down).
	 */
	public double drawnCabinHeight(long gameTime, float partialTick) {
		return cabinHeight(gameTime - DRAW_LAG_TICKS + (double) partialTick);
	}

	/** The cabin's vertical speed from {@code now} to the next tick, blocks per tick. */
	public double cabinVelocity(double now) {
		return trip != null && !trip.arrivedBy(now) ? trip.velocityAt(now) : 0.0D;
	}

	public @Nullable ElevatorTrip trip() {
		return trip;
	}

	/** The floors of the shaft, from the bottom up (the last whole reading). */
	public List<Integer> floors() {
		return floors;
	}

	public ElevatorShaft shaft() {
		return shaft;
	}

	public @Nullable Component lastNotice() {
		return lastNotice;
	}

	/** The cabin's cell at the height of its floor {@code floor}: in the tube under the winch. */
	public BlockPos cabinCell(double floor) {
		return worldPosition.atY((int) Math.floor(floor + EPS));
	}

	/** Whether the cabin stands at the floor {@code floor}. */
	public boolean standsAt(int floor) {
		return trip == null && Math.abs(cabinY - floor) < EPS;
	}

	// --- server tick --------------------------------------------------------------------------------------

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return 0;
		}
		long now = level.getGameTime();
		if (--scanCooldown <= 0) {
			rescan(serverLevel);
		}
		if (trip != null) {
			runTrip(serverLevel, now);
		} else if (!calls.isEmpty()) {
			Call call = calls.poll();
			dispatch(serverLevel, call.floor(), ElevatorRescue.online(serverLevel, call.caller()));
		}
		if (justLoaded) {
			justLoaded = false;
			ElevatorRescue.rescueAll(serverLevel, this);
		}
		ElevatorCarry.Cabin cabin = cabin(level, now);
		if (cabin != null) {
			ElevatorCarry.carry(level, cabin);
		}
		updateLit(trip != null);
		updateAssembled(shaft.valid());
		return 0;
	}

	/** The winch's lamp: green while the shaft under it is whole, red while it is not. */
	private void updateAssembled(boolean assembled) {
		BlockState state = getBlockState();
		if (level != null && state.hasProperty(ElevatorWinchBlock.ASSEMBLED)
				&& state.getValue(ElevatorWinchBlock.ASSEMBLED) != assembled) {
			level.setBlock(worldPosition, state.setValue(ElevatorWinchBlock.ASSEMBLED, assembled),
					Block.UPDATE_CLIENTS);
		}
	}

	/** Client tick: carry the local player (and what it steers); everything else comes from the server. */
	public void clientTick(Level level) {
		ElevatorCarry.Cabin cabin = cabin(level, level.getGameTime());
		if (cabin != null) {
			ElevatorCarry.carry(level, cabin);
		}
	}

	/**
	 * Where the cabin is at game time {@code now} for the carry: its cell, floor, speed, and the way the doorway of
	 * the open station it stands at faces; {@code null} while the winch has never had a whole shaft.
	 */
	public ElevatorCarry.@Nullable Cabin cabin(Level level, double now) {
		double floor = cabinHeight(now);
		if (Double.isNaN(floor)) {
			return null;
		}
		BlockPos cell = cabinCell(floor);
		Direction door = null;
		BlockState station = level.getBlockState(cell);
		if (trip == null && station.getBlock() instanceof ElevatorStationBlock
				&& station.getValue(ElevatorStationBlock.OPEN)
				&& station.getValue(ElevatorStationBlock.HALF) == DoubleBlockHalf.LOWER) {
			door = station.getValue(ElevatorStationBlock.FACING);
		}
		return new ElevatorCarry.Cabin(cell, floor, cabinVelocity(now), door);
	}

	private void rescan(ServerLevel level) {
		scans++;
		scanCooldown = trip != null ? SCAN_TICKS_RUNNING : SCAN_TICKS;
		shaft = ElevatorShaft.scan(level, worldPosition, ElevatorConfig.elevatorMaxHeight);
		if (shaft.valid() && !shaft.floors().equals(floors)) {
			floors = shaft.floors();
			syncBlockEntityToClient();
			setChanged();
		}
		if (shaft.valid() && Double.isNaN(cabinY)) {
			cabinY = shaft.lowestFloor();
			syncBlockEntityToClient();
			setChanged();
		}
		if (trip == null) {
			ElevatorLandings.sync(level, this);
		}
	}

	private void runTrip(ServerLevel level, long now) {
		ElevatorTrip run = trip;
		if (!run.catching() && !tubeHolds(level, run)) {
			engageCatches(level, now);
			return;
		}
		if (run.arrivedBy(now)) {
			arrive(level, run);
		}
	}

	/** Whether the tube is still whole over the whole trip: from the low floor up to the winch. */
	private boolean tubeHolds(ServerLevel level, ElevatorTrip run) {
		int low = (int) Math.floor(Math.min(run.from(), run.to()) + EPS);
		return ElevatorShaft.firstGap(level, worldPosition, low, worldPosition.getY() - 1) == Integer.MIN_VALUE;
	}

	private void arrive(ServerLevel level, ElevatorTrip run) {
		cabinY = run.to();
		trip = null;
		play(level, SoundEvents.NOTE_BLOCK_BELL.value());
		changed(level);
	}

	/**
	 * The tube broke round the moving cabin: the catches grip the posts and lower the cabin, slowly and for free, to
	 * the nearest floor below whose station and tube up to the cabin are still there. With none, they hold it there.
	 */
	private void engageCatches(ServerLevel level, long now) {
		double at = trip.heightAt(now);
		calls.clear();
		play(level, SoundEvents.ANVIL_LAND);
		int topCell = (int) Math.ceil(at + 2.0D - EPS) - 1;
		for (int i = floors.size() - 1; i >= 0; i--) {
			int floor = floors.get(i);
			if (floor > at + EPS || !ElevatorShaft.stationPresent(level, worldPosition, floor)
					|| ElevatorShaft.firstGap(level, worldPosition, floor, topCell) != Integer.MIN_VALUE) {
				continue;
			}
			trip = Math.abs(at - floor) < EPS ? null
					: new ElevatorTrip(now, at, floor, ElevatorConfig.elevatorCatchSpeed, 0, true);
			cabinY = floor;
			tellRiders(level, Component.translatable("message.alaindustrial.elevator.caught", floorName(floor)));
			changed(level);
			return;
		}
		trip = null;
		cabinY = at;
		tellRiders(level, Component.translatable("message.alaindustrial.elevator.held", (int) Math.floor(at)));
		changed(level);
	}

	private void changed(ServerLevel level) {
		ElevatorLandings.sync(level, this);
		syncBlockEntityToClient();
		setChanged();
		scanCooldown = 1;
	}

	// --- calls --------------------------------------------------------------------------------------------

	/**
	 * A right click on the station of the floor at {@code floor}, at {@code station}: with the cabin standing there it
	 * opens the floor panel ({@link ElevatorPanelMenu}), from the landing or from inside the cabin alike; anywhere else
	 * it calls the cabin here.
	 */
	public void usePanel(ServerPlayer player, int floor, BlockPos station) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		rescan(serverLevel);
		if (!shaft.valid()) {
			tell(player, problemMessage());
			return;
		}
		if (standsAt(floor)) {
			player.openMenu(new SimpleMenuProvider((id, inventory, who) -> new ElevatorPanelMenu(id, inventory, this,
					floor, station), Component.translatable("gui.alaindustrial.elevator_panel")));
			return;
		}
		request(floor, player);
	}

	/**
	 * A floor pressed on the panel opened at the station of {@code here}: the cabin, still standing there, goes to the
	 * {@code index}-th floor from the bottom by the rules of any call (a refusal is told to the presser). Returns
	 * whether the press named a floor the cabin could be sent to.
	 */
	public boolean choose(ServerPlayer player, int here, int index) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		if (!standsAt(here) || index < 0 || index >= floors.size()) { // no scan for a press refused anyway (MOD-1030)
			return false;
		}
		rescan(serverLevel);
		List<Integer> all = shaft.floors();
		if (!shaft.valid() || !standsAt(here) || index < 0 || index >= all.size() || all.get(index) == here) {
			return false;
		}
		request(all.get(index), player);
		return true;
	}

	/**
	 * Ask for the cabin at {@code floor}: at once when it stands, after the trips ahead of it otherwise.
	 * {@code caller} is told what happens; {@code null} for a call nobody made by hand (a test, a redstone line).
	 */
	public void request(int floor, @Nullable ServerPlayer caller) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (trip == null && calls.isEmpty()) {
			dispatch(serverLevel, floor, caller);
			return;
		}
		for (Call call : calls) {
			if (call.floor() == floor) {
				tell(caller, Component.translatable("message.alaindustrial.elevator.queued", floorName(floor)));
				return;
			}
		}
		calls.add(new Call(floor, caller == null ? null : caller.getUUID()));
		tell(caller, Component.translatable("message.alaindustrial.elevator.queued", floorName(floor)));
	}

	private void dispatch(ServerLevel level, int floor, @Nullable ServerPlayer caller) {
		if (!shaft.valid()) {
			tell(caller, problemMessage());
			return;
		}
		if (shaft.floorIndex(floor) < 0 || Double.isNaN(cabinY)) {
			return;
		}
		if (Math.abs(cabinY - floor) < EPS) {
			tell(caller, Component.translatable("message.alaindustrial.elevator.here"));
			ElevatorLandings.sync(level, this);
			return;
		}
		int gap = gapOnTheWayTo(floor);
		if (gap != Integer.MIN_VALUE) {
			refuse(level, caller, Component.translatable("message.alaindustrial.elevator.problem.gap", gap));
			scanCooldown = 1;
			return;
		}
		long fare = fareTo(floor, riders());
		if (energy.getAmount() < fare) {
			refuse(level, caller, Component.translatable("message.alaindustrial.elevator.no_energy", fare,
					energy.getAmount()));
			return;
		}
		energy.drainInternal(fare);
		trip = new ElevatorTrip(level.getGameTime(), cabinY, floor, ElevatorConfig.elevatorSpeed, RAMP_TICKS, false);
		play(level, SoundEvents.PISTON_EXTEND);
		tell(caller, Component.translatable("message.alaindustrial.elevator.sent", floorName(floor), fare));
		changed(level);
		scanCooldown = SCAN_TICKS_RUNNING;
	}

	private void refuse(ServerLevel level, @Nullable ServerPlayer caller, Component why) {
		play(level, ModSounds.TELEPORT_ABORT.get());
		tell(caller, why);
	}

	/** The prices of the trip, from the knobs. */
	public static ElevatorFare.Tariff tariff() {
		return new ElevatorFare.Tariff(ElevatorConfig.elevatorLaunchCost, ElevatorConfig.elevatorCostPerBlock,
				ElevatorConfig.elevatorCostPerRider, ElevatorConfig.elevatorDescentShare);
	}

	/** The first cell missing from the tube between where the cabin stands and {@code floor}; MIN_VALUE if none. */
	public int gapOnTheWayTo(int floor) {
		int low = (int) Math.floor(Math.min(cabinY, floor) + EPS);
		int high = (int) Math.ceil(Math.max(cabinY, floor) + 2.0D - EPS) - 1;
		return level == null ? Integer.MIN_VALUE : ElevatorShaft.firstGap(level, worldPosition, low, high);
	}

	/** A trip from where the cabin stands to {@code floor} with {@code riders} aboard ({@link #riders}). */
	public long fareTo(int floor, int riders) {
		int blocks = (int) Math.ceil(Math.abs(floor - cabinY) - EPS);
		return ElevatorFare.fare(tariff(), blocks, floor > cabinY, riders);
	}

	/** How many ride on the cabin's floor now: counted once for every fare a panel shows (MOD-1030). */
	public int riders() {
		return level == null ? 0 : ElevatorCarry.riders(level, cabinCell(cabinY), cabinY);
	}

	/** Shaft scans since the block entity loaded: what a test counts (MOD-1030), never read by the game. */
	public int scans() {
		return scans;
	}

	/** "Floor 3": floors are numbered from the bottom, one up. */
	public Component floorName(int floor) {
		int index = floors.indexOf(floor);
		return Component.translatable("message.alaindustrial.elevator.floor", index < 0 ? "?" : index + 1);
	}

	private Component problemMessage() {
		String key = "message.alaindustrial.elevator.problem." + shaft.problem().id;
		return shaft.problem() == ElevatorShaft.Problem.GAP ? Component.translatable(key, shaft.gap())
				: Component.translatable(key);
	}

	/**
	 * A right click on the winch: what is wrong with the shaft, or, when it is whole, how many floors it has and how
	 * much energy the winch holds.
	 */
	public void describe(ServerPlayer player) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		rescan(serverLevel);
		updateAssembled(shaft.valid());
		tell(player, shaft.valid() ? Component.translatable("message.alaindustrial.elevator.ready",
				shaft.floors().size(), energy.getAmount(), energy.getCapacity()) : problemMessage());
	}

	private void tell(@Nullable ServerPlayer player, Component message) {
		lastNotice = message;
		if (player != null && player.connection != null) {
			player.sendOverlayMessage(message);
		}
	}

	private void tellRiders(ServerLevel level, Component message) {
		lastNotice = message;
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distManhattan(worldPosition) <= ElevatorConfig.elevatorMaxHeight + 4
					&& player.connection != null) {
				player.sendOverlayMessage(message);
			}
		}
	}

	private void play(ServerLevel level, SoundEvent sound) {
		double floor = Double.isNaN(cabinY) ? worldPosition.getY() : cabinHeight(level.getGameTime());
		BlockPos at = cabinCell(floor).above();
		level.playSound(null, at, sound, SoundSource.BLOCKS, 0.6F, 1.0F);
	}

	// --- persistence: also the update tag the client draws from ------------------------------------------

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putDouble("CabinY", cabinY);
		output.putIntArray("Floors", floors.stream().mapToInt(Integer::intValue).toArray());
		output.putIntArray("Calls", calls.stream().mapToInt(Call::floor).toArray());
		if (trip != null) {
			output.putLong("TripStart", trip.start());
			output.putDouble("TripFrom", trip.from());
			output.putDouble("TripTo", trip.to());
			output.putDouble("TripSpeed", trip.speed());
			output.putInt("TripRamp", trip.rampTicks());
			output.putBoolean("TripCatching", trip.catching());
		}
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		cabinY = input.getDoubleOr("CabinY", Double.NaN);
		List<Integer> read = new ArrayList<>();
		for (int floor : input.getIntArray("Floors").orElse(new int[0])) {
			read.add(floor);
		}
		floors = List.copyOf(read);
		calls.clear();
		for (int floor : input.getIntArray("Calls").orElse(new int[0])) {
			calls.add(new Call(floor, null));
		}
		double speed = input.getDoubleOr("TripSpeed", 0.0D);
		trip = speed > 0.0D ? new ElevatorTrip(input.getLongOr("TripStart", 0L), input.getDoubleOr("TripFrom", 0.0D),
				input.getDoubleOr("TripTo", 0.0D), speed, input.getIntOr("TripRamp", RAMP_TICKS),
				input.getBooleanOr("TripCatching", false)) : null;
	}
}
