package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.MonitorPanelBlock;
import dev.alaindustrial.block.MonitorStand;
import dev.alaindustrial.block.entity.MonitorCoreBlockEntity;
import dev.alaindustrial.block.entity.MonitorPanelBlockEntity;
import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import dev.alaindustrial.core.monitor.MonitorReadout;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The connector flags of a monitor panel (MOD-1013): the blockstate the panel model reads to draw wires
 * on its back and a stand on a core. The network itself does not read them; these scenarios pin that the
 * flags follow the neighbours, that a wall turning a corner draws no wire across the corner while staying
 * one network, and that a wall loaded with every flag false (a world saved before the flags existed)
 * gets its connectors on the panel's first server tick.
 *
 * <p>Rig along {@code z = 1}, every panel facing north, so "clockwise" is east (+x) and "counter-clockwise"
 * is west (-x).
 */
public final class MonitorConnectorScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MonitorConnectorScenarios::sideBySidePanelsJoinAndBreakingOneClearsIt,
						"mod1013_monitor_side_by_side_panels_join"),
				RosterEntry.of(MonitorConnectorScenarios::aCornerDrawsNoWireButStaysOneNetwork,
						"mod1013_monitor_corner_draws_no_wire"),
				RosterEntry.of(MonitorConnectorScenarios::aPanelOnTheCoreStandsAndStacksJoin,
						"mod1013_monitor_panel_on_core_stands"),
				RosterEntry.of(MonitorConnectorScenarios::aWallLoadedBareGetsItsConnectors,
						"mod1013_monitor_old_wall_gets_connectors"),
				RosterEntry.of(MonitorConnectorScenarios::mirrorSwapsTheSidesRotateKeepsThem,
						"mod1013_monitor_mirror_swaps_sides"));

		private Roster() {}
	}

	private static final BlockPos LEFT = new BlockPos(2, 2, 1);
	private static final BlockPos RIGHT = new BlockPos(3, 2, 1);
	private static final BlockPos CORE = new BlockPos(2, 1, 1);
	private static final BlockPos ABOVE = new BlockPos(2, 3, 1);

	private MonitorConnectorScenarios() {
	}

	private static BlockState facing(Direction facing) {
		return ModContent.MONITOR_PANEL.get().defaultBlockState().setValue(MonitorPanelBlock.FACING, facing);
	}

	/** Place a panel the way a world does (neighbours get {@code updateShape}), then run its first tick. */
	private static void placePanel(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, facing(facing));
		firstTick(helper, pos);
	}

	private static void firstTick(GameTestHelper helper, BlockPos pos) {
		helper.getBlockEntity(pos, MonitorPanelBlockEntity.class).ensureRegistered();
	}

	private static BlockState state(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos);
	}

	/** Fails the test and returns {@code false} when {@code property} at {@code pos} is not {@code expected}. */
	private static boolean expect(GameTestHelper helper, BlockPos pos, BooleanProperty property, boolean expected,
			String what) {
		boolean actual = state(helper, pos).getValue(property);
		if (actual != expected) {
			helper.fail(what + ": " + property.getName() + " at " + pos + " is " + actual + ", expected " + expected
					+ " (" + state(helper, pos) + ")");
			return false;
		}
		return true;
	}

	/** Fails the test and returns {@code false} when the panel at {@code pos} does not stand as {@code expected}. */
	private static boolean expectStand(GameTestHelper helper, BlockPos pos, MonitorStand expected, String what) {
		MonitorStand actual = state(helper, pos).getValue(MonitorPanelBlock.STAND);
		if (actual != expected) {
			helper.fail(what + ": stand at " + pos + " is " + actual.getSerializedName() + ", expected "
					+ expected.getSerializedName() + " (" + state(helper, pos) + ")");
			return false;
		}
		return true;
	}

	/**
	 * Two panels side by side with one facing draw a wire to each other; breaking one clears the other's.
	 *
	 * @implements MOD-1013-MC01 — side-by-side panels of one facing join, a broken neighbour clears the wire
	 */
	public static void sideBySidePanelsJoinAndBreakingOneClearsIt(GameTestHelper helper) {
		placePanel(helper, LEFT, Direction.NORTH);
		if (!expect(helper, LEFT, MonitorPanelBlock.WIRE_CW, false, "lone panel")
				|| !expect(helper, LEFT, MonitorPanelBlock.WIRE_CCW, false, "lone panel")) {
			return;
		}
		placePanel(helper, RIGHT, Direction.NORTH);
		if (!expect(helper, LEFT, MonitorPanelBlock.WIRE_CW, true, "right neighbour placed")
				|| !expect(helper, RIGHT, MonitorPanelBlock.WIRE_CCW, true, "left neighbour present")
				|| !expect(helper, LEFT, MonitorPanelBlock.WIRE_CCW, false, "nothing on the left")
				|| !expect(helper, RIGHT, MonitorPanelBlock.WIRE_CW, false, "nothing on the right")) {
			return;
		}
		helper.setBlock(RIGHT, Blocks.AIR);
		if (expect(helper, LEFT, MonitorPanelBlock.WIRE_CW, false, "right neighbour broken")) {
			helper.succeed();
		}
	}

	/**
	 * Around a corner the facings differ: no wire across it, yet the two panels stay one network,
	 * because the network joins by adjacency and never reads the flags.
	 *
	 * @implements MOD-1013-MC02 — a corner draws no wire and the network is unchanged
	 */
	public static void aCornerDrawsNoWireButStaysOneNetwork(GameTestHelper helper) {
		placePanel(helper, LEFT, Direction.NORTH);
		placePanel(helper, RIGHT, Direction.EAST);
		if (!expect(helper, LEFT, MonitorPanelBlock.WIRE_CW, false, "corner")
				|| !expect(helper, RIGHT, MonitorPanelBlock.WIRE_CW, false, "corner")
				|| !expect(helper, RIGHT, MonitorPanelBlock.WIRE_CCW, false, "corner")) {
			return;
		}
		var left = MonitorNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(LEFT));
		if (left == null || left != MonitorNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RIGHT))) {
			helper.fail("panels around a corner must still be one monitor network");
			return;
		}
		helper.succeed();
	}

	/**
	 * A panel on a core stands on it, its stand turned the way the core faces (the base sits on the core's
	 * top, which turns with the core); turning the core turns the stand; a panel stacked above joins it
	 * vertically; breaking the core takes the stand away.
	 *
	 * @implements MOD-1013-MC03 — a panel on the core stands, stacked panels join, the stand goes with the core
	 */
	public static void aPanelOnTheCoreStandsAndStacksJoin(GameTestHelper helper) {
		helper.setBlock(CORE, ModContent.MONITOR_CORE.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH));
		helper.getBlockEntity(CORE, MonitorCoreBlockEntity.class).ensureRegistered();
		placePanel(helper, LEFT, Direction.NORTH);
		placePanel(helper, ABOVE, Direction.NORTH);
		if (!expectStand(helper, LEFT, MonitorStand.SOUTH, "panel on a core facing the other way")
				|| !expect(helper, LEFT, MonitorPanelBlock.WIRE_DOWN, false, "a core is not a panel")
				|| !expect(helper, LEFT, MonitorPanelBlock.WIRE_UP, true, "panel stacked above")
				|| !expect(helper, ABOVE, MonitorPanelBlock.WIRE_DOWN, true, "panel below")
				|| !expectStand(helper, ABOVE, MonitorStand.NONE, "a panel is not a core")) {
			return;
		}
		helper.setBlock(CORE, helper.getBlockState(CORE).setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		if (!expectStand(helper, LEFT, MonitorStand.EAST, "core turned")) {
			return;
		}
		helper.setBlock(CORE, Blocks.AIR);
		if (expectStand(helper, LEFT, MonitorStand.NONE, "core broken")) {
			helper.succeed();
		}
	}

	/**
	 * A wall written with every flag false and no shape updates (what a chunk saved before MOD-1013
	 * loads as) gets its connectors on a panel's first tick; the lit flag set afterwards keeps them.
	 *
	 * @implements MOD-1013-MC04 — a wall from an old world gets its connectors without being rebuilt
	 */
	public static void aWallLoadedBareGetsItsConnectors(GameTestHelper helper) {
		int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		helper.getLevel().setBlock(helper.absolutePos(CORE), ModContent.MONITOR_CORE.get().defaultBlockState(), quiet);
		for (BlockPos pos : List.of(LEFT, RIGHT, ABOVE)) {
			helper.getLevel().setBlock(helper.absolutePos(pos), facing(Direction.NORTH), quiet);
		}
		if (!expect(helper, LEFT, MonitorPanelBlock.WIRE_CW, false, "loaded bare")
				|| !expectStand(helper, LEFT, MonitorStand.NONE, "loaded bare")) {
			return;
		}
		firstTick(helper, LEFT);
		if (!expect(helper, LEFT, MonitorPanelBlock.WIRE_CW, true, "first tick after load")
				|| !expect(helper, LEFT, MonitorPanelBlock.WIRE_UP, true, "first tick after load")
				|| !expectStand(helper, LEFT, MonitorStand.NORTH, "first tick after load")) {
			return;
		}
		// RIGHT and ABOVE were reached by the vanilla shape cascade of LEFT's write, not by a tick of their own.
		if (!expect(helper, RIGHT, MonitorPanelBlock.WIRE_CCW, true, "neighbour of the reconciled panel")
				|| !expect(helper, ABOVE, MonitorPanelBlock.WIRE_DOWN, true, "neighbour of the reconciled panel")) {
			return;
		}
		MonitorPanelBlockEntity left = helper.getBlockEntity(LEFT, MonitorPanelBlockEntity.class);
		left.setFilter(new ItemStack(Items.COBBLESTONE));
		// A filter alone does not light the screen: only a live number does (the network's verdict).
		if (!expect(helper, LEFT, MonitorPanelBlock.ACTIVE, false, "filter set, no verdict yet")) {
			return;
		}
		left.publish(MonitorReadout.OK, 1L);
		if (expect(helper, LEFT, MonitorPanelBlock.ACTIVE, true, "live number")
				&& expect(helper, LEFT, MonitorPanelBlock.WIRE_CW, true, "lighting up keeps the connectors")
				&& expectStand(helper, LEFT, MonitorStand.NORTH, "lighting up keeps the connectors")) {
			helper.succeed();
		}
	}

	/**
	 * Mirroring a structure swaps left and right, so the clockwise and counter-clockwise flags swap;
	 * a rotation turns the facing with the wall and keeps both, since they are relative to the facing.
	 *
	 * @implements MOD-1013-MC05 — mirror swaps the side flags, rotate keeps them
	 */
	public static void mirrorSwapsTheSidesRotateKeepsThem(GameTestHelper helper) {
		BlockState state = facing(Direction.NORTH).setValue(MonitorPanelBlock.WIRE_CW, true)
				.setValue(MonitorPanelBlock.WIRE_UP, true).setValue(MonitorPanelBlock.STAND, MonitorStand.WEST);
		for (Mirror mirror : List.of(Mirror.LEFT_RIGHT, Mirror.FRONT_BACK)) {
			BlockState mirrored = state.mirror(mirror);
			if (mirrored.getValue(MonitorPanelBlock.WIRE_CW) || !mirrored.getValue(MonitorPanelBlock.WIRE_CCW)
					|| !mirrored.getValue(MonitorPanelBlock.WIRE_UP)
					|| mirrored.getValue(MonitorPanelBlock.STAND) != MonitorStand.WEST) {
				helper.fail(mirror + " must swap wire_cw and wire_ccw and keep the rest: " + mirrored);
				return;
			}
		}
		if (state.mirror(Mirror.NONE) != state) {
			helper.fail("Mirror.NONE must leave the state as it is");
			return;
		}
		BlockState rotated = state.rotate(Rotation.CLOCKWISE_90);
		if (rotated.getValue(MonitorPanelBlock.FACING) != Direction.EAST
				|| !rotated.getValue(MonitorPanelBlock.WIRE_CW) || rotated.getValue(MonitorPanelBlock.WIRE_CCW)) {
			helper.fail("a rotation must turn the facing and keep the side flags: " + rotated);
			return;
		}
		helper.succeed();
	}
}
