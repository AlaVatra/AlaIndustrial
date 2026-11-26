package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.ReinforcedSolarPanelBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Menu for the Reinforced Solar Panel (MOD-1031) — no machine slots; shows energy, production and sky mode. */
public class ReinforcedSolarPanelMenu extends MachineMenu {
	/** Server side. */
	public ReinforcedSolarPanelMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.REINFORCED_SOLAR_PANEL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.REINFORCED_SOLAR_PANEL.get());
	}

	/** Client side. */
	public ReinforcedSolarPanelMenu(int syncId, Inventory playerInventory) {
		super(ModContent.REINFORCED_SOLAR_PANEL_MENU.get(), syncId, playerInventory,
				clientStub(ReinforcedSolarPanelBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT,
						MachineBlockEntity.DATA_COUNT),
				ModContent.REINFORCED_SOLAR_PANEL.get());
	}

	@Override
	protected void addMachineSlots() {
		// No inventory slots — the next rung has no function yet, so there is no chip to slot.
	}

	/** Current production rate (EU/t), carried on the progress channel. */
	public int getProductionRate() {
		return getProgress();
	}

	/** Sky mode; carried on the maxProgress channel. See the {@code MODE_*} codes on the block entity. */
	public int getMode() {
		return getMaxProgress();
	}
}
