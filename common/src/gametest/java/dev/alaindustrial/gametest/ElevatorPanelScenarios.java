package dev.alaindustrial.gametest;

import dev.alaindustrial.block.ElevatorStationBlock;
import dev.alaindustrial.block.ElevatorWinchBlock;
import dev.alaindustrial.block.entity.ElevatorWinchBlockEntity;
import dev.alaindustrial.block.entity.elevator.ElevatorPanel;
import dev.alaindustrial.core.elevator.ElevatorFare;
import dev.alaindustrial.menu.ElevatorPanelMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametests of the elevator's floor panel (MOD-769, suite TC-ELV-001, the owner's round 3 of
 * 2026-11-24: in a three-floor shaft he could not find how to reach floor 1 from floor 3). A right click on the
 * station the cabin stands at opens a panel with a button per floor; a press sends the cabin to that floor by the
 * rules of any call; a floor the cabin cannot reach is shut and says why; a right click where the cabin is not still
 * calls it.
 *
 * <p>The rigs are {@link ElevatorScenarios}'s: winch at (2, 7, 3), stations facing north in the column x 2, z 3 at
 * floors 1, 3 and 5. The player is a survival mock in the level, so it can open a
 * menu; a press is the menu's own {@code clickMenuButton}, as vanilla's container-button packet calls it.
 */
public final class ElevatorPanelScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ElevatorPanelScenarios::panelSendsTheCabinFromTheTopToTheBottom,
						"elevator_panel_reaches_every_floor").ticks(120),
				RosterEntry.of(ElevatorPanelScenarios::panelShutsWhatTheCabinCannotReach,
						"elevator_panel_shuts_unreachable").ticks(20),
				RosterEntry.of(ElevatorPanelScenarios::stationWithoutTheCabinCallsIt,
						"elevator_station_calls_without_panel").ticks(20),
				RosterEntry.of(ElevatorPanelScenarios::aPressThatCannotBeTakenScansNoShaft,
						"mod1030_elevator_refused_press_scans_no_shaft").ticks(20));

		private Roster() {}
	}

	private ElevatorPanelScenarios() {
	}

	private static final BlockPos WINCH = new BlockPos(2, 7, 3);
	private static final int[] FLOORS = {1, 3, 5};
	private static final long FULL = 10_000L;
	private static final int SETTLE = 3;
	/** A 4-block trip takes 30 ticks: wait that and a little. */
	private static final int RIDE = 40;

	private static BlockPos cell(int y) {
		return new BlockPos(WINCH.getX(), y, WINCH.getZ());
	}

	private static void station(GameTestHelper helper, int floor) {
		BlockState lower = ModContent.ELEVATOR_STATION.get().defaultBlockState()
				.setValue(ElevatorStationBlock.FACING, Direction.NORTH);
		helper.setBlock(cell(floor), lower);
		helper.setBlock(cell(floor + 1), lower.setValue(ElevatorStationBlock.HALF, DoubleBlockHalf.UPPER));
	}

	private static ElevatorWinchBlockEntity winch(GameTestHelper helper, long charge) {
		helper.setBlock(WINCH, ModContent.ELEVATOR_WINCH.get().defaultBlockState()
				.setValue(ElevatorWinchBlock.FACING, Direction.NORTH));
		ElevatorWinchBlockEntity winch = helper.getBlockEntity(WINCH, ElevatorWinchBlockEntity.class);
		winch.getEnergyStorage().setAmountUntracked(charge);
		return winch;
	}

	private static int abs(GameTestHelper helper, int relativeY) {
		return helper.absolutePos(new BlockPos(0, relativeY, 0)).getY();
	}

	/** A survival player on the landing in front of the station of {@code floor}, its doors in reach. */
	private static ServerPlayer playerBefore(GameTestHelper helper, int floor) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(cell(floor).north()));
		player.setPos(at.x, at.y, at.z);
		return player;
	}

	private static String key(Component notice) {
		return notice != null && notice.getContents() instanceof TranslatableContents t ? t.getKey() : "<none>";
	}

	/** The panel the player has open, or a failure naming what is open instead. */
	private static ElevatorPanelMenu panel(GameTestHelper helper, ServerPlayer player, String when) {
		if (!(player.containerMenu instanceof ElevatorPanelMenu menu)) {
			throw helper.assertionException(when + ": a right click at the cabin's floor should open the floor panel, "
					+ "the player has " + player.containerMenu.getClass().getSimpleName() + " open");
		}
		return menu;
	}

	/**
	 * @implements TC-ELV-001-FUN20 — the owner's round 3: three floors, the cabin at floor 3. A right click on the
	 *     station of floor 3 opens the panel: three rows, floor 3 marked as the cabin's, floors 1 and 2 open with
	 *     their fares; pressing floor 1 sends the cabin straight down to floor 1 (not to floor 2), charges exactly the
	 *     fare the panel showed, and the panel closes as the cabin moves off.
	 */
	public static void panelSendsTheCabinFromTheTopToTheBottom(GameTestHelper helper) {
		for (int floor : FLOORS) {
			station(helper, floor);
		}
		ElevatorWinchBlockEntity winch = winch(helper, FULL);
		int top = abs(helper, FLOORS[2]);
		int bottom = abs(helper, FLOORS[0]);
		ServerPlayer[] player = new ServerPlayer[1];
		helper.runAfterDelay(SETTLE, () -> winch.request(top, null));
		helper.runAfterDelay(SETTLE + RIDE, () -> {
			helper.assertTrue(winch.standsAt(top), "the empty cabin went up to floor 3 first");
			player[0] = playerBefore(helper, FLOORS[2]);
			helper.useBlock(cell(FLOORS[2]), player[0]);
			ElevatorPanelMenu menu = panel(helper, player[0], "at floor 3");
			helper.assertTrue(menu.floorCount() == 3 && menu.shownCount() == 3 && menu.firstShown() == 0,
					"the panel shows the three floors, got " + menu.floorCount() + " from " + menu.firstShown());
			helper.assertTrue(menu.hereIndex() == 2 && menu.state(2) == ElevatorPanel.FloorState.HERE,
					"floor 3 is marked as the cabin's, got here " + menu.hereIndex() + ", " + menu.state(2));
			long down4 = ElevatorFare.fare(ElevatorWinchBlockEntity.tariff(), 4, false, 0);
			long down2 = ElevatorFare.fare(ElevatorWinchBlockEntity.tariff(), 2, false, 0);
			helper.assertTrue(menu.pressable(0) && menu.value(0) == down4,
					"floor 1 is open at " + down4 + " EU, got " + menu.state(0) + " " + menu.value(0));
			helper.assertTrue(menu.pressable(1) && menu.value(1) == down2,
					"floor 2 is open at " + down2 + " EU, got " + menu.state(1) + " " + menu.value(1));
			helper.assertTrue(!menu.clickMenuButton(player[0], 2), "pressing the cabin's own floor does nothing");
			long before = winch.getEnergyStorage().getAmount();
			helper.assertTrue(menu.clickMenuButton(player[0], 0), "pressing floor 1 is taken");
			helper.assertTrue(winch.trip() != null && winch.trip().to() == bottom,
					"the cabin goes straight to floor 1, trip " + winch.trip());
			helper.assertTrue(before - winch.getEnergyStorage().getAmount() == menu.value(0),
					"the trip cost what the panel showed: " + menu.value(0) + " EU, charged "
							+ (before - winch.getEnergyStorage().getAmount()));
			helper.assertTrue(!menu.stillValid(player[0]), "the panel closes once the cabin has left its floor");
		});
		helper.runAfterDelay(SETTLE + 2 * RIDE, () -> {
			helper.assertTrue(winch.standsAt(bottom), "the cabin stands at floor 1");
			helper.assertTrue(!(player[0].containerMenu instanceof ElevatorPanelMenu), "the server closed the panel");
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN21 — what the panel shuts: with 70 EU the cabin at floor 1 reaches floor 2 (66 EU)
	 *     but not floor 3 (82 EU), shut as "no energy" with its fare, and its press is refused with the message and
	 *     no charge; a press past the last floor is refused; with the station of floor 2 taken out of the tube, floor 3
	 *     is shut as cut off at the gap's height.
	 */
	public static void panelShutsWhatTheCabinCannotReach(GameTestHelper helper) {
		for (int floor : FLOORS) {
			station(helper, floor);
		}
		ElevatorWinchBlockEntity winch = winch(helper, 70L);
		ElevatorPanelMenu[] panelOpen = new ElevatorPanelMenu[1];
		helper.runAfterDelay(SETTLE, () -> {
			ServerPlayer player = playerBefore(helper, FLOORS[0]);
			helper.useBlock(cell(FLOORS[0]), player);
			ElevatorPanelMenu menu = panel(helper, player, "at floor 1");
			long up2 = ElevatorFare.fare(ElevatorWinchBlockEntity.tariff(), 2, true, 0);
			long up4 = ElevatorFare.fare(ElevatorWinchBlockEntity.tariff(), 4, true, 0);
			helper.assertTrue(up2 <= 70L && up4 > 70L, "the rig's charge sits between the two fares: " + up2 + ", "
					+ up4);
			helper.assertTrue(menu.pressable(1) && menu.value(1) == up2, "floor 2 is open at " + up2 + " EU, got "
					+ menu.state(1) + " " + menu.value(1));
			helper.assertTrue(menu.state(2) == ElevatorPanel.FloorState.NO_ENERGY && menu.value(2) == up4,
					"floor 3 is shut for want of energy at " + up4 + " EU, got " + menu.state(2) + " " + menu.value(2));
			helper.assertTrue(menu.energy() == 70, "the panel knows the charge, got " + menu.energy());
			menu.clickMenuButton(player, 2);
			helper.assertTrue(winch.trip() == null && winch.getEnergyStorage().getAmount() == 70L,
					"a press on a shut floor sends nothing and charges nothing");
			helper.assertTrue(key(winch.lastNotice()).equals("message.alaindustrial.elevator.no_energy"),
					"the presser is told why, got " + key(winch.lastNotice()));
			helper.assertTrue(!menu.clickMenuButton(player, 3), "a press past the last floor is refused");
			panelOpen[0] = menu;
			// The tube cut over floor 1: the station of floor 2 taken out (its lower half goes with the upper).
			helper.setBlock(cell(FLOORS[1] + 1), Blocks.STONE);
		});
		helper.runAfterDelay(SETTLE + 1, () -> {
			ElevatorPanelMenu menu = panelOpen[0];
			int gap = abs(helper, FLOORS[1]);
			helper.assertTrue(menu.state(2) == ElevatorPanel.FloorState.GAP && menu.value(2) == gap,
					"floor 3 is shut as cut off at the gap's height " + gap + ", got " + menu.state(2) + " "
							+ menu.value(2));
			helper.succeed();
		});
	}

	/**
	 * MOD-1030: a press comes from the client, so a press that cannot be taken — the cabin is not at the panel's floor,
	 * or the button is past the floors — is refused before the shaft is scanned; a press that can be taken scans it.
	 */
	public static void aPressThatCannotBeTakenScansNoShaft(GameTestHelper helper) {
		for (int floor : FLOORS) {
			station(helper, floor);
		}
		ElevatorWinchBlockEntity winch = winch(helper, FULL);
		helper.runAfterDelay(SETTLE, () -> {
			int bottom = abs(helper, FLOORS[0]);
			int top = abs(helper, FLOORS[2]);
			helper.assertTrue(winch.standsAt(bottom), "the cabin starts at floor 1");
			ServerPlayer player = playerBefore(helper, FLOORS[0]);
			int before = winch.scans();
			helper.assertTrue(!winch.choose(player, top, 0), "a press at floor 3 while the cabin is at floor 1");
			helper.assertTrue(!winch.choose(player, bottom, 7), "a press past the last floor");
			helper.assertTrue(!winch.choose(player, bottom, -1), "a press before the first floor");
			helper.assertTrue(winch.scans() == before, "refused presses scanned the shaft " + (winch.scans() - before)
					+ " time(s)");
			helper.assertTrue(winch.choose(player, bottom, 1), "a press on floor 2 is taken");
			helper.assertTrue(winch.scans() == before + 1, "a press that is taken reads the shaft once");
			helper.succeed();
		});
	}

	/**
	 * @implements TC-ELV-001-FUN22 — a right click on the station of a floor the cabin is not at opens no panel and
	 *     calls the cabin there, as before the panel.
	 */
	public static void stationWithoutTheCabinCallsIt(GameTestHelper helper) {
		for (int floor : FLOORS) {
			station(helper, floor);
		}
		ElevatorWinchBlockEntity winch = winch(helper, FULL);
		helper.runAfterDelay(SETTLE, () -> {
			ServerPlayer player = playerBefore(helper, FLOORS[1]);
			helper.useBlock(cell(FLOORS[1]).above(), player);
			helper.assertTrue(!(player.containerMenu instanceof ElevatorPanelMenu),
					"no panel opens where the cabin is not");
			helper.assertTrue(winch.trip() != null && winch.trip().to() == abs(helper, FLOORS[1]),
					"the right click called the cabin to floor 2, trip " + winch.trip());
			helper.succeed();
		});
	}
}
