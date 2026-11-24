package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.ElevatorWinchBlockEntity;
import dev.alaindustrial.block.entity.elevator.ElevatorPanel;
import dev.alaindustrial.block.entity.elevator.ElevatorShaft;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The elevator's floor panel (MOD-769, the owner's round 3): opened by a right click on the station the cabin stands
 * at, one button per floor of the shaft.
 *
 * <p>Like the apiary map it holds no slots: the floors, their states and fares are container data
 * ({@link ElevatorPanel}), and a press is vanilla's own container-button packet carrying the floor's index from the
 * bottom ({@link #clickMenuButton}); the server sends the cabin by the rules of any call
 * ({@link ElevatorWinchBlockEntity#choose}), so a client that ignores a shut button gets nowhere. The panel stays open
 * only while the cabin stands at its floor: the moment a press sends it away, the server closes it.
 */
public class ElevatorPanelMenu extends AbstractContainerMenu {

	private final ContainerData data;
	private final @Nullable ElevatorWinchBlockEntity winch;
	private final int here;
	private final ContainerLevelAccess access;

	/** Server side: the panel of the station at {@code station}, whose floor is {@code here}. */
	public ElevatorPanelMenu(int syncId, Inventory playerInventory, ElevatorWinchBlockEntity winch, int here,
			BlockPos station) {
		super(ModContent.ELEVATOR_PANEL_MENU.get(), syncId);
		this.winch = winch;
		this.here = here;
		this.access = winch.getLevel() == null ? ContainerLevelAccess.NULL
				: ContainerLevelAccess.create(winch.getLevel(), station);
		this.data = new ElevatorPanel(winch, here);
		addDataSlots(data);
	}

	/** Client side: vanilla fills the data from the server's copy. */
	public ElevatorPanelMenu(int syncId, Inventory playerInventory) {
		super(ModContent.ELEVATOR_PANEL_MENU.get(), syncId);
		this.winch = null;
		this.here = Integer.MIN_VALUE;
		this.access = ContainerLevelAccess.NULL;
		this.data = new SimpleContainerData(ElevatorPanel.SIZE);
		addDataSlots(data);
	}

	/** How many floors the shaft has. */
	public int floorCount() {
		return data.get(ElevatorPanel.COUNT);
	}

	/** The index, from the bottom, of the floor the cabin stands at. */
	public int hereIndex() {
		return data.get(ElevatorPanel.HERE);
	}

	/** The index of the floor in the panel's first row. */
	public int firstShown() {
		return data.get(ElevatorPanel.FIRST);
	}

	/** How many floors the panel shows. */
	public int shownCount() {
		return Math.max(0, Math.min(ElevatorPanel.MAX_ROWS, floorCount() - firstShown()));
	}

	/** Why the shaft does not run, {@link ElevatorShaft.Problem#NONE} while it does. */
	public ElevatorShaft.Problem problem() {
		ElevatorShaft.Problem[] all = ElevatorShaft.Problem.values();
		int ordinal = data.get(ElevatorPanel.PROBLEM);
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : ElevatorShaft.Problem.NONE;
	}

	/** The height of the gap a {@link ElevatorShaft.Problem#GAP} names. */
	public int problemAt() {
		return data.get(ElevatorPanel.PROBLEM_AT);
	}

	/** The EU the winch holds. */
	public int energy() {
		return data.get(ElevatorPanel.ENERGY);
	}

	/** The state of the floor in row {@code row}. */
	public ElevatorPanel.FloorState state(int row) {
		return ElevatorPanel.FloorState.of(data.get(ElevatorPanel.stateSlot(row)));
	}

	/** The number of the floor in row {@code row}: its fare, or the gap's height for a floor cut off. */
	public int value(int row) {
		return data.get(ElevatorPanel.valueSlot(row));
	}

	/** Whether the button of row {@code row} sends the cabin. */
	public boolean pressable(int row) {
		return problem() == ElevatorShaft.Problem.NONE && state(row) == ElevatorPanel.FloorState.OPEN;
	}

	/** A press: send the cabin to the floor of index {@code buttonId} from the bottom; the server re-checks all. */
	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (winch == null || !(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}
		return winch.choose(serverPlayer, here, buttonId);
	}

	/** No slots: nothing to shift-click. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	/** Open while the player is at the station and the cabin still stands at its floor. */
	@Override
	public boolean stillValid(Player player) {
		if (winch == null) {
			return true;
		}
		return !winch.isRemoved() && winch.standsAt(here)
				&& AbstractContainerMenu.stillValid(access, player, ModContent.ELEVATOR_STATION.get());
	}
}
