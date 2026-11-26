package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.BronzeLunarPanelBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Menu for the Bronze Lunar Panel (MOD-1031) — no machine slots; shows energy, production and sky mode. */
public class BronzeLunarPanelMenu extends MachineMenu {
	/** Server side. */
	public BronzeLunarPanelMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.BRONZE_LUNAR_PANEL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.BRONZE_LUNAR_PANEL.get());
	}

	/** Client side. */
	public BronzeLunarPanelMenu(int syncId, Inventory playerInventory) {
		super(ModContent.BRONZE_LUNAR_PANEL_MENU.get(), syncId, playerInventory,
				clientStub(BronzeLunarPanelBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT,
						MachineBlockEntity.DATA_COUNT),
				ModContent.BRONZE_LUNAR_PANEL.get());
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
