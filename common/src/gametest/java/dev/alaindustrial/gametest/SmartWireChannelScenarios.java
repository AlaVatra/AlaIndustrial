package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.SmartWireBlock;
import dev.alaindustrial.block.WireLink;
import dev.alaindustrial.block.entity.MonitorCoreBlockEntity;
import dev.alaindustrial.block.entity.MonitorPanelBlockEntity;
import dev.alaindustrial.block.entity.SmartWireBlockEntity;
import dev.alaindustrial.core.monitor.MonitorNetwork;
import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import dev.alaindustrial.core.monitor.MonitorReadout;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The smart wire's channels (MOD-1014): a wall reads at most {@link MonitorNetwork#WIRE_CHANNELS}
 * containers, a double chest is one of them, and the wire shows the answer — a {@code device} face toward
 * each served container and the same {@code fill} in every segment.
 *
 * <p>Every rig is a wire run along {@code y = 2, z = 3} with the core at its west end. Each network is
 * ticked directly, never through {@code tickAll}, so a refresh happens exactly when the scenario says.
 */
public final class SmartWireChannelScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(SmartWireChannelScenarios::theNinthContainerWaitsForAFreeChannel,
						"mod1014_monitor_ninth_container_waits_for_a_channel"),
				RosterEntry.of(SmartWireChannelScenarios::aDoubleChestIsOneChannel,
						"mod1014_monitor_double_chest_is_one_channel"),
				RosterEntry.of(SmartWireChannelScenarios::anOldWireGetsItsFacesOnTheFirstTick,
						"mod1014_monitor_old_wire_gets_faces_on_first_tick"),
				RosterEntry.of(SmartWireChannelScenarios::aWireWithoutOneCoreKeepsItsPlugsButGoesDark,
						"mod1014_monitor_wire_without_one_core_holds"),
				RosterEntry.of(SmartWireChannelScenarios::theWireGoesDarkWhenTheCoreRunsOutOfPower,
						"mod1014_monitor_wire_dark_without_power"),
				RosterEntry.of(SmartWireChannelScenarios::theShapeCoversEveryDrawnPart,
						"mod1014_monitor_wire_shape_covers_model"),
				RosterEntry.of(SmartWireChannelScenarios::theWirePlugsIntoStorageNotIntoMachines,
						"mod1014_monitor_storage_only_not_machines"));

		private Roster() {}
	}

	private static final int Y = 2;
	private static final int Z = 3;
	private static final BlockPos CORE = new BlockPos(0, Y, Z);
	private static final BlockPos PANEL = new BlockPos(0, Y + 1, Z);

	private SmartWireChannelScenarios() {
	}

	private static BlockPos wire(int x) {
		return new BlockPos(x, Y, Z);
	}

	private static BlockEntity be(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(pos));
	}

	private static void placeWire(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModContent.SMART_WIRE.get());
		helper.getBlockEntity(pos, SmartWireBlockEntity.class).ensureRegistered();
	}

	/** A powered core with one card at {@link #CORE}, and a panel on top of it watching cobblestone. */
	private static void placeWorkingCore(GameTestHelper helper) {
		helper.setBlock(CORE, ModContent.MONITOR_CORE.get());
		MonitorCoreBlockEntity core = helper.getBlockEntity(CORE, MonitorCoreBlockEntity.class);
		core.ensureRegistered();
		core.insertCard(new ItemStack(ModContent.CAPACITY_CARD.get()));
		core.getEnergyStorage().setAmountUntracked(core.getEnergyStorage().getCapacity());
		helper.setBlock(PANEL, ModContent.MONITOR_PANEL.get());
		MonitorPanelBlockEntity panel = helper.getBlockEntity(PANEL, MonitorPanelBlockEntity.class);
		panel.ensureRegistered();
		panel.setFilter(new ItemStack(Items.COBBLESTONE));
	}

	/** A barrel holding {@code cobblestone} cobblestone. */
	private static void placeBarrel(GameTestHelper helper, BlockPos pos, int cobblestone) {
		placeHolding(helper, pos, Blocks.BARREL.defaultBlockState(), cobblestone);
	}

	/** {@code state} at {@code pos}, its first slot holding {@code cobblestone} cobblestone. */
	private static void placeHolding(GameTestHelper helper, BlockPos pos, BlockState state, int cobblestone) {
		helper.setBlock(pos, state);
		if (be(helper, pos) instanceof Container container) {
			container.setItem(0, new ItemStack(Items.COBBLESTONE, cobblestone));
		} else {
			throw new IllegalStateException("no container at " + pos + " for " + state);
		}
	}

	private static MonitorNetwork tickAt(GameTestHelper helper, BlockPos pos) {
		MonitorNetwork network = MonitorNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(pos));
		if (network == null) {
			throw new IllegalStateException("no monitor network at " + pos);
		}
		network.markDirty();
		network.tick();
		return network;
	}

	/**
	 * Tick the network at {@code pos} through one full scan interval WITHOUT marking it dirty: no refresh
	 * runs, so whatever changes on the wires comes from the scan alone.
	 */
	private static void scanWithoutRefresh(GameTestHelper helper, BlockPos pos) {
		MonitorNetwork network = MonitorNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(pos));
		if (network == null) {
			throw new IllegalStateException("no monitor network at " + pos);
		}
		for (int i = 0; i < Math.max(1, Config.monitorScanIntervalTicks); i++) {
			network.tick();
		}
	}

	private static WireLink face(GameTestHelper helper, BlockPos wire, Direction dir) {
		return helper.getBlockState(wire).getValue(SmartWireBlock.LINK_BY_DIRECTION.get(dir));
	}

	private static boolean expectFace(GameTestHelper helper, BlockPos wire, Direction dir, WireLink expected,
			String what) {
		WireLink actual = face(helper, wire, dir);
		if (actual != expected) {
			helper.fail(what + ": wire at " + wire + " shows " + actual + " toward " + dir + ", expected " + expected);
			return false;
		}
		return true;
	}

	/** Every wire in {@code xs} carries {@code fill}: the value belongs to the wall, not to a segment. */
	private static boolean expectFill(GameTestHelper helper, int fill, String what, int... xs) {
		for (int x : xs) {
			int actual = helper.getBlockState(wire(x)).getValue(SmartWireBlock.FILL);
			if (actual != fill) {
				helper.fail(what + ": wire at " + wire(x) + " has fill " + actual + ", expected " + fill);
				return false;
			}
		}
		return true;
	}

	private static boolean expectCount(GameTestHelper helper, long count, String what) {
		MonitorPanelBlockEntity panel = helper.getBlockEntity(PANEL, MonitorPanelBlockEntity.class);
		if (panel.getReadout() != MonitorReadout.OK || panel.getCount() != count) {
			helper.fail(what + ": panel reads " + panel.getReadout() + " " + panel.getCount() + ", expected OK "
					+ count);
			return false;
		}
		return true;
	}

	/**
	 * Nine barrels on one wire run: the first eight in geometric order get a channel, the ninth gets no
	 * plug and its cobblestone is not counted. Breaking one of the eight frees a channel, and the ninth
	 * plugs in on the next refresh.
	 *
	 * @implements MOD-1014-SW01 — a wall serves at most eight containers
	 * @implements MOD-1014-SW02 — a freed channel goes to the waiting container
	 */
	public static void theNinthContainerWaitsForAFreeChannel(GameTestHelper helper) {
		placeWorkingCore(helper);
		for (int x = 1; x <= 6; x++) {
			placeWire(helper, wire(x));
		}
		for (int x = 1; x <= 4; x++) {
			placeBarrel(helper, new BlockPos(x, Y, Z - 1), 1);
			placeBarrel(helper, new BlockPos(x, Y, Z + 1), 1);
		}
		BlockPos ninth = new BlockPos(6, Y, Z - 1);
		placeBarrel(helper, ninth, 50);
		tickAt(helper, CORE);
		if (!expectFill(helper, MonitorNetwork.WIRE_CHANNELS, "nine barrels", 1, 2, 3, 4, 5, 6)
				|| !expectFace(helper, wire(1), Direction.NORTH, WireLink.DEVICE, "first barrel")
				|| !expectFace(helper, wire(4), Direction.SOUTH, WireLink.DEVICE, "eighth barrel")
				|| !expectFace(helper, wire(6), Direction.NORTH, WireLink.NONE, "ninth barrel")
				|| !expectFace(helper, wire(1), Direction.WEST, WireLink.WIRE, "toward the core")
				|| !expectFace(helper, wire(2), Direction.EAST, WireLink.WIRE, "toward the next wire")
				|| !expectCount(helper, 8, "the ninth barrel is not read")) {
			return;
		}
		helper.setBlock(new BlockPos(1, Y, Z - 1), Blocks.AIR);
		if (!expectFace(helper, wire(1), Direction.NORTH, WireLink.NONE, "barrel broken")) {
			return;
		}
		tickAt(helper, CORE);
		if (expectFill(helper, MonitorNetwork.WIRE_CHANNELS, "a channel freed", 1, 2, 3, 4, 5, 6)
				&& expectFace(helper, wire(6), Direction.NORTH, WireLink.DEVICE, "ninth barrel after the free channel")
				&& expectCount(helper, 57, "the ninth barrel is read")) {
			helper.succeed();
		}
	}

	/**
	 * A double chest touched at both halves is one channel: both halves get a plug, the fill counts it
	 * once, and its contents are counted once.
	 *
	 * @implements MOD-1014-SW03 — a double chest is one channel
	 * @implements MOD-1014-SW04 — every segment of a wall carries the same fill
	 */
	public static void aDoubleChestIsOneChannel(GameTestHelper helper) {
		placeWorkingCore(helper);
		placeWire(helper, wire(1));
		placeWire(helper, wire(2));
		placeWire(helper, wire(3));
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
		// Facing south, the right half sits west of the left one.
		helper.setBlock(new BlockPos(1, Y, Z - 1), chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
		helper.setBlock(new BlockPos(2, Y, Z - 1), chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
		if (be(helper, new BlockPos(1, Y, Z - 1)) instanceof Container right
				&& be(helper, new BlockPos(2, Y, Z - 1)) instanceof Container left) {
			right.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
			left.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
		} else {
			throw new IllegalStateException("the double chest did not place");
		}
		placeBarrel(helper, new BlockPos(3, Y, Z + 1), 1);
		tickAt(helper, CORE);
		if (expectFill(helper, 2, "double chest and barrel", 1, 2, 3)
				&& expectFace(helper, wire(1), Direction.NORTH, WireLink.DEVICE, "right half")
				&& expectFace(helper, wire(2), Direction.NORTH, WireLink.DEVICE, "left half")
				&& expectFace(helper, wire(3), Direction.SOUTH, WireLink.DEVICE, "barrel")
				&& expectCount(helper, 16, "the double chest is counted once")) {
			helper.succeed();
		}
	}

	/**
	 * A wire from an old world loads with every face {@code none}: its faces used to be booleans. On its
	 * first server tick it reads its wire faces back from the neighbours, and the wall's refresh adds the
	 * plug and the fill.
	 *
	 * @implements MOD-1014-SW05 — an old wire gets its faces on the first tick
	 */
	public static void anOldWireGetsItsFacesOnTheFirstTick(GameTestHelper helper) {
		placeWorkingCore(helper);
		placeBarrel(helper, wire(2), 3);
		helper.setBlock(wire(1), ModContent.SMART_WIRE.get());
		BlockState loaded = helper.getBlockState(wire(1));
		if (loaded != loaded.getBlock().defaultBlockState()) {
			helper.fail("the rig must start from the default state, as an old wire loads: " + loaded);
			return;
		}
		helper.getBlockEntity(wire(1), SmartWireBlockEntity.class).serverTick();
		if (!expectFace(helper, wire(1), Direction.WEST, WireLink.WIRE, "first tick")) {
			return;
		}
		tickAt(helper, CORE);
		if (expectFace(helper, wire(1), Direction.EAST, WireLink.DEVICE, "after the refresh")
				&& expectFill(helper, 1, "after the refresh", 1)
				&& expectCount(helper, 3, "after the refresh")) {
			helper.succeed();
		}
	}

	/**
	 * A wire run with no core keeps its plugs but lights no cores, and so does one joining two cores,
	 * which the wall refuses to run: neither case throws, and the panel says there is no single core.
	 *
	 * @implements MOD-1014-SW06 — a wire without exactly one core keeps its plugs and goes dark
	 */
	public static void aWireWithoutOneCoreKeepsItsPlugsButGoesDark(GameTestHelper helper) {
		placeWire(helper, wire(2));
		placeBarrel(helper, new BlockPos(2, Y, Z + 1), 4);
		tickAt(helper, wire(2));
		if (!expectFill(helper, 0, "no core", 2)
				|| !expectFace(helper, wire(2), Direction.SOUTH, WireLink.DEVICE, "no core")) {
			return;
		}
		placeWorkingCore(helper);
		placeWire(helper, wire(1));
		BlockPos second = wire(3);
		helper.setBlock(second, ModContent.MONITOR_CORE.get());
		helper.getBlockEntity(second, MonitorCoreBlockEntity.class).ensureRegistered();
		tickAt(helper, CORE);
		MonitorPanelBlockEntity panel = helper.getBlockEntity(PANEL, MonitorPanelBlockEntity.class);
		if (panel.getReadout() != MonitorReadout.NO_CORE) {
			helper.fail("a wall with two cores must refuse to run, panel reads " + panel.getReadout());
			return;
		}
		if (expectFill(helper, 0, "two cores", 1, 2)
				&& expectFace(helper, wire(2), Direction.EAST, WireLink.WIRE, "toward the second core")
				&& expectFace(helper, wire(2), Direction.SOUTH, WireLink.DEVICE, "two cores")) {
			helper.succeed();
		}
	}

	/**
	 * The wire goes dark with the panels. A powered wall shows its channels; once the core has no charge
	 * left to pay its upkeep, the next scan writes {@code fill = 0} into every segment while the plugs
	 * stay where they are; charge coming back lights the channels again. Neither change touches the
	 * topology, so the network is ticked without being marked dirty: the scan alone must relight the wire.
	 *
	 * @implements MOD-1014-SW08 — the wire lights its cores only while the wall runs
	 */
	public static void theWireGoesDarkWhenTheCoreRunsOutOfPower(GameTestHelper helper) {
		placeWorkingCore(helper);
		for (int x = 1; x <= 3; x++) {
			placeWire(helper, wire(x));
		}
		placeBarrel(helper, new BlockPos(1, Y, Z - 1), 1);
		placeBarrel(helper, new BlockPos(3, Y, Z + 1), 2);
		tickAt(helper, CORE);
		if (!expectFill(helper, 2, "powered", 1, 2, 3)) {
			return;
		}
		MonitorCoreBlockEntity core = helper.getBlockEntity(CORE, MonitorCoreBlockEntity.class);
		core.getEnergyStorage().setAmountUntracked(0L);
		scanWithoutRefresh(helper, CORE);
		MonitorPanelBlockEntity panel = helper.getBlockEntity(PANEL, MonitorPanelBlockEntity.class);
		if (panel.getReadout() != MonitorReadout.NO_POWER) {
			helper.fail("a drained core must leave the panel without power, panel reads " + panel.getReadout());
			return;
		}
		if (!expectFill(helper, 0, "drained", 1, 2, 3)
				|| !expectFace(helper, wire(1), Direction.NORTH, WireLink.DEVICE, "plug kept while drained")
				|| !expectFace(helper, wire(3), Direction.SOUTH, WireLink.DEVICE, "plug kept while drained")) {
			return;
		}
		core.getEnergyStorage().setAmountUntracked(core.getEnergyStorage().getCapacity());
		scanWithoutRefresh(helper, CORE);
		if (expectFill(helper, 2, "power back", 1, 2, 3) && expectCount(helper, 3, "power back")) {
			helper.succeed();
		}
	}

	/** The wire's state with the given faces; every other face {@code none}. */
	private static BlockState wireState(Object... faceLinkPairs) {
		BlockState state = ModContent.SMART_WIRE.get().defaultBlockState();
		for (int i = 0; i < faceLinkPairs.length; i += 2) {
			state = state.setValue(SmartWireBlock.LINK_BY_DIRECTION.get((Direction) faceLinkPairs[i]),
					(WireLink) faceLinkPairs[i + 1]);
		}
		return state;
	}

	/** Every box (pixels, as in SHAPE.md, already cut to the cell) lies inside the shape of {@code state}. */
	private static boolean covers(GameTestHelper helper, String what, BlockState state, double[]... boxes) {
		VoxelShape shape = state.getShape(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		for (double[] b : boxes) {
			VoxelShape part = Shapes.box(b[0] / 16, b[1] / 16, b[2] / 16, b[3] / 16, b[4] / 16, b[5] / 16);
			if (Shapes.joinIsNotEmpty(part, shape, BooleanOp.ONLY_FIRST)) {
				helper.fail(what + ": the part " + java.util.Arrays.toString(b) + " is drawn but has no shape, so"
						+ " the cursor goes through it; shape " + shape.toAabbs());
				return false;
			}
		}
		return true;
	}

	private static double[] px(double x0, double y0, double z0, double x1, double y1, double z1) {
		return new double[] {x0, y0, z0, x1, y1, z1};
	}

	/**
	 * The shape covers every part the model draws (tools/model_sources/smart_wire/SHAPE.md), so the cursor
	 * picks the wire wherever the player sees it. The parts are the table's own numbers for one state of
	 * each class; the end of a lone ribbon past the middle is the one that went missing.
	 *
	 * @implements MOD-1014-SW09 — the outline covers every visible part of the wire
	 */
	public static void theShapeCoversEveryDrawnPart(GameTestHelper helper) {
		WireLink w = WireLink.WIRE;
		WireLink d = WireLink.DEVICE;
		boolean ok = covers(helper, "lone segment", wireState(), px(3, 2, 2, 13, 4, 14))
				&& covers(helper, "end on north", wireState(Direction.NORTH, w),
						px(3, 2, 0, 13, 4, 8), px(3, 2, 8, 13, 4, 13))
				&& covers(helper, "end on east", wireState(Direction.EAST, w),
						px(13, 2, 3, 16, 4, 13), px(8, 2, 3, 13, 4, 13), px(3, 2, 3, 8, 4, 13))
				&& covers(helper, "plug on north", wireState(Direction.NORTH, d),
						px(4, 1, 0, 12, 6, 1), px(5, 2, 0, 11, 5, 4), px(3, 1, 4, 13, 6, 8), px(3, 2, 8, 13, 4, 13))
				&& covers(helper, "end on up", wireState(Direction.UP, w),
						px(3, 7, 7, 13, 16, 9), px(3, 2, 7, 13, 7, 9))
				&& covers(helper, "end on down", wireState(Direction.DOWN, w),
						px(3, 0, 7, 13, 1, 9), px(3, 1, 7, 13, 7, 9), px(3, 7, 7, 13, 12, 9))
				&& covers(helper, "plug on up", wireState(Direction.UP, d),
						px(3, 7, 6, 13, 16, 11), px(3, 2, 7, 13, 7, 9))
				&& covers(helper, "plug on down", wireState(Direction.DOWN, d),
						px(5, 0, 7, 11, 1, 10), px(3, 1, 6, 13, 7, 11), px(3, 7, 7, 13, 12, 9))
				&& covers(helper, "rise up", wireState(Direction.NORTH, w, Direction.UP, w),
						px(3, 2, 0, 13, 4, 7), px(3, 2, 7, 13, 7, 9), px(3, 7, 7, 13, 16, 9))
				&& covers(helper, "rise down", wireState(Direction.NORTH, w, Direction.DOWN, w),
						px(3, 2, 0, 13, 4, 7), px(3, 1, 7, 13, 4, 9), px(3, 0, 7, 13, 1, 9))
				&& covers(helper, "bend", wireState(Direction.NORTH, w, Direction.EAST, w),
						px(3, 2, 0, 13, 4, 3), px(13, 2, 3, 16, 4, 13), px(3, 2, 3, 13, 5, 13))
				&& covers(helper, "straight with a plug", wireState(Direction.NORTH, d, Direction.SOUTH, w),
						px(3, 1, 4, 13, 6, 8), px(3, 2, 8, 13, 4, 16))
				&& covers(helper, "box under a rise", wireState(Direction.EAST, w, Direction.UP, w),
						px(2, 1, 2, 14, 5, 14), px(3, 5, 7, 13, 7, 9), px(3, 7, 7, 13, 16, 9));
		if (ok) {
			helper.succeed();
		}
	}

	/**
	 * The wire plugs into storage only. A machine of the mod, a vanilla furnace and a hopper all hold
	 * cobblestone and expose an inventory, yet none of them gets a plug or a channel; the chest, the
	 * barrel and the shulker box across the run do, and only their cobblestone is counted.
	 *
	 * @implements MOD-1014-SW07 — a wall reads storage, never machines
	 */
	public static void theWirePlugsIntoStorageNotIntoMachines(GameTestHelper helper) {
		placeWorkingCore(helper);
		for (int x = 1; x <= 3; x++) {
			placeWire(helper, wire(x));
		}
		placeHolding(helper, new BlockPos(1, Y, Z - 1), Blocks.FURNACE.defaultBlockState(), 7);
		placeHolding(helper, new BlockPos(2, Y, Z - 1), Blocks.HOPPER.defaultBlockState(), 7);
		placeHolding(helper, new BlockPos(3, Y, Z - 1), ModContent.ELECTRIC_FURNACE.get().defaultBlockState(), 7);
		placeHolding(helper, new BlockPos(1, Y, Z + 1), Blocks.CHEST.defaultBlockState(), 1);
		placeBarrel(helper, new BlockPos(2, Y, Z + 1), 2);
		placeHolding(helper, new BlockPos(3, Y, Z + 1), Blocks.SHULKER_BOX.defaultBlockState(), 4);
		tickAt(helper, CORE);
		if (expectFace(helper, wire(1), Direction.NORTH, WireLink.NONE, "vanilla furnace")
				&& expectFace(helper, wire(2), Direction.NORTH, WireLink.NONE, "hopper")
				&& expectFace(helper, wire(3), Direction.NORTH, WireLink.NONE, "machine of the mod")
				&& expectFace(helper, wire(1), Direction.SOUTH, WireLink.DEVICE, "chest")
				&& expectFace(helper, wire(2), Direction.SOUTH, WireLink.DEVICE, "barrel")
				&& expectFace(helper, wire(3), Direction.SOUTH, WireLink.DEVICE, "shulker box")
				&& expectFill(helper, 3, "storage only", 1, 2, 3)
				&& expectCount(helper, 7, "only the storage is counted")) {
			helper.succeed();
		}
	}
}
