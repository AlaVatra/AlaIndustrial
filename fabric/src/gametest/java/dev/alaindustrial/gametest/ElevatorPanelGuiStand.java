package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;

import dev.alaindustrial.block.entity.elevator.ElevatorPanel;
import dev.alaindustrial.block.entity.elevator.ElevatorShaft;
import dev.alaindustrial.client.screen.ElevatorPanelScreen;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.menu.ElevatorPanelMenu;
import dev.alaindustrial.registry.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;

/**
 * The elevator's floor panel (MOD-769, the owner's round 3): five floors with every kind of button, twelve floors in
 * two columns with the cursor on a floor, and a broken shaft with every button shut.
 *
 * <p>Built on the client's own menu with the container data the server would send ({@link ElevatorPanel}'s layout):
 * the real open path (a right click on the station the cabin stands at) and the press are covered by the
 * {@code elevator_panel_*} game tests.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ElevatorPanelGuiStand {

	private static final int OPEN = ElevatorPanel.FloorState.OPEN.ordinal();
	private static final int HERE = ElevatorPanel.FloorState.HERE.ordinal();
	private static final int NO_ENERGY = ElevatorPanel.FloorState.NO_ENERGY.ordinal();
	private static final int GAP = ElevatorPanel.FloorState.GAP.ordinal();

	private ElevatorPanelGuiStand() {
	}

	public static void shoot(ClientGameTestContext context) {
		shoot(context, "five_floors", 5, 2, 0, 60, new int[][] {{OPEN, 54}, {OPEN, 52}, {HERE, 0}, {NO_ENERGY, 66},
				{GAP, 71}}, 0,
				"Elevator panel 'Choose a floor', five floors in one column, Floor 1 at the bottom and Floor 5 at the "
						+ "top: Floor 1 and Floor 2 dark with '54 EU' and '52 EU' at the right, Floor 3 gold-rimmed "
						+ "with 'here', Floor 4 grey with '66 EU' in red, Floor 5 grey with no price; the cursor on "
						+ "Floor 1 lights it and its tooltip says 'Send the cabin to Floor 1 for 54 EU'; nothing runs "
						+ "out of a button or the panel");
		int[][] twelve = new int[12][];
		for (int i = 0; i < twelve.length; i++) {
			twelve[i] = i == 4 ? new int[] {HERE, 0} : new int[] {OPEN, 50 + 2 * Math.abs(i - 4)};
		}
		shoot(context, "twelve_floors", 12, 4, 0, 10_000, twelve, 7,
				"Elevator panel with twelve floors in two columns, read like a cabin's panel: Floor 1 and Floor 2 in "
						+ "the bottom row, Floor 11 and Floor 12 in the top row; Floor 5 gold-rimmed with 'here', "
						+ "every other floor dark with its price; the cursor on Floor 8 lights it and its tooltip "
						+ "names Floor 8 and its price");
		shoot(context, "broken_shaft", 3, 0, ElevatorShaft.Problem.GAP.ordinal(), 10_000,
				new int[][] {{HERE, 0}, {OPEN, 66}, {OPEN, 82}}, -1,
				"Elevator panel of a broken shaft: three floors, Floor 1 gold-rimmed with 'here', Floor 2 and Floor 3 "
						+ "grey, and under the buttons in red 'The shaft has a gap at height 71' wrapped inside the "
						+ "panel");
	}

	private static void shoot(ClientGameTestContext context, String state, int count, int here, int problem,
			int energy, int[][] rows, int hoverRow, String checks) {
		context.runOnClient(mc -> {
			MenuScreens.create(ModContent.ELEVATOR_PANEL_MENU.get(), mc, 0,
					Component.translatable("gui.alaindustrial.elevator_panel"));
			if (!(mc.gui.screen() instanceof ElevatorPanelScreen screen)) {
				throw new AssertionError("[GUITEST][MOD-769] MenuScreens.create did not open the elevator panel for "
						+ state);
			}
			ElevatorPanelMenu menu = screen.getMenu();
			menu.setData(ElevatorPanel.COUNT, count);
			menu.setData(ElevatorPanel.HERE, here);
			menu.setData(ElevatorPanel.PROBLEM, problem);
			menu.setData(ElevatorPanel.PROBLEM_AT, 71);
			menu.setData(ElevatorPanel.ENERGY, energy);
			menu.setData(ElevatorPanel.FIRST, 0);
			for (int row = 0; row < rows.length; row++) {
				menu.setData(ElevatorPanel.stateSlot(row), rows[row][0]);
				menu.setData(ElevatorPanel.valueSlot(row), rows[row][1]);
			}
		});
		awaitMenuScreen(context);
		double[] cursor = {10, 10};
		context.runOnClient(mc -> {
			if (!(mc.gui.screen() instanceof ElevatorPanelScreen screen)) {
				throw new AssertionError("[GUITEST][MOD-769] the elevator panel closed before " + state);
			}
			if (screen.getMenu().shownCount() != count) {
				throw new AssertionError("[GUITEST][MOD-769] the panel shows " + screen.getMenu().shownCount()
						+ " floors, the data says " + count);
			}
			if (hoverRow >= 0) {
				double scale = mc.getWindow().getGuiScale();
				int[] middle = screen.buttonMiddle(hoverRow);
				if (screen.buttonAt(middle[0], middle[1]) != hoverRow) {
					throw new AssertionError("[GUITEST][MOD-769] the middle of row " + hoverRow
							+ "'s button is not its button");
				}
				cursor[0] = middle[0] * scale;
				cursor[1] = middle[1] * scale;
			}
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
		context.waitTicks(1);
		ShotRecorder.captureScreen("elevator_panel", state, ShotRecorder.rules("R-GUI-01", "R-GUI-03"), checks);
	}
}
