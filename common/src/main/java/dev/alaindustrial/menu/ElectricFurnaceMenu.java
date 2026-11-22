package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.AbstractProcessingMachineBlockEntity;
import dev.alaindustrial.block.entity.ProcessingMachineStatus;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.machine.ResultChoiceHost;
import dev.alaindustrial.network.ResultChoicePayload;
import dev.alaindustrial.network.ResultChoiceSync;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * Menu for the LV electric furnace (input slot + result-only output slot), with the result button of MOD-1018:
 * a press rides {@link #clickMenuButton} with {@link ResultChoiceMenu#BUTTON_CYCLE_RESULT}, and the options come
 * back as a {@link ResultChoicePayload} from {@link #broadcastChanges}.
 */
public class ElectricFurnaceMenu extends MachineMenu implements ResultChoiceMenu {
	/** Server side: the furnace, for the result button; {@code null} on the client. */
	private final @Nullable ResultChoiceHost furnace;

	/** The result button's options: sent from the server copy, kept by the client copy. */
	private final ResultChoiceSync resultSync;

	/** The player whose screen this is: the one viewer the options are sent to. */
	private final Player viewer;

	/** Server side. */
	public ElectricFurnaceMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.ELECTRIC_FURNACE_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.ELECTRIC_FURNACE.get());
		this.furnace = be instanceof ResultChoiceHost host ? host : null;
		this.resultSync = new ResultChoiceSync(syncId);
		this.viewer = playerInventory.player;
	}

	/** Client side. */
	public ElectricFurnaceMenu(int syncId, Inventory playerInventory) {
		super(ModContent.ELECTRIC_FURNACE_MENU.get(), syncId, playerInventory,
				clientStub(AbstractProcessingMachineBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT,
						AbstractProcessingMachineBlockEntity.DATA_COUNT),
				ModContent.ELECTRIC_FURNACE.get());
		this.furnace = null;
		this.resultSync = new ResultChoiceSync(syncId);
		this.viewer = playerInventory.player;
	}

	@Override
	protected void addMachineSlots() {
		// Slot positions aligned to the electric_furnace.png GUI atlas.
		addSlot(new Slot(machine, 0, 56, 35));
		// Output slot: result only, no manual insertion (spec).
		addSlot(new OutputSlot(machine, 1, 117, 35));
	}
	/** Why the machine is idle (MOD-458), read from synced data — works on both sides. */
	public ProcessingMachineStatus getStatus() {
		return ProcessingMachineStatus.byOrdinal(channel(AbstractProcessingMachineBlockEntity.Channel.STATUS));
	}

	/** The result button press; every other id goes to the base (the battery drawer). Server side only. */
	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == BUTTON_CYCLE_RESULT) {
			if (furnace == null || !(player instanceof ServerPlayer)) {
				return false;
			}
			furnace.cycleResult();
			return true;
		}
		return super.clickMenuButton(player, id);
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		resultSync.broadcast(furnace, viewer);
	}

	@Override
	public ResultChoicePayload resultChoices() {
		return resultSync.received();
	}

	@Override
	public void acceptResultChoices(ResultChoicePayload payload) {
		resultSync.accept(payload);
	}
}
