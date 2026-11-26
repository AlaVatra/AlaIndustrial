package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Moonlit Solar Panel — one evolution-chip slot (MOD-1031; since MOD-1033 the lunar evolution chip or
 * the resonance chip); shows energy, production, sky mode and
 * evolution. Night twin of {@link DaylightSolarPanelMenu}.
 */
public class MoonlitSolarPanelMenu extends MachineMenu {
	/** Server side. */
	public MoonlitSolarPanelMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.MOONLIT_SOLAR_PANEL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.MOONLIT_SOLAR_PANEL.get());
	}

	/** Client side. */
	public MoonlitSolarPanelMenu(int syncId, Inventory playerInventory) {
		super(ModContent.MOONLIT_SOLAR_PANEL_MENU.get(), syncId, playerInventory,
				clientStub(MoonlitSolarPanelBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT,
						MoonlitSolarPanelBlockEntity.DATA_COUNT),
				ModContent.MOONLIT_SOLAR_PANEL.get());
	}

	@Override
	protected void addMachineSlots() {
		// Evolution-chip slot (MOD-1031), where the daylight panel has its own; what may go in is the block
		// entity's call.
		addSlot(new Slot(machine, MoonlitSolarPanelBlockEntity.CHIP_SLOT, 149, 27) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(MoonlitSolarPanelBlockEntity.CHIP_SLOT, stack);
			}

			// MOD-211: one chip at a time.
			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
	}

	/** Current production rate (EU/t), carried on the progress channel. */
	public int getProductionRate() {
		return getProgress();
	}

	/**
	 * Sky mode: 0 inactive/day, 1 night clear, 2 night weather; carried on the maxProgress channel.
	 */
	public int getMode() {
		return getMaxProgress();
	}

	/** Evolution progress on a permille scale (0..1000); ≥1 as soon as any progress accrues. */
	public int getEvolveProgress() {
		return channel(MoonlitSolarPanelBlockEntity.Channel.EVOLVE_PERMILLE);
	}

	/** Evolution denominator (constant 1000) — permille scale, kept short-safe for DataSlot sync. */
	public int getEvolveMax() {
		return channel(MoonlitSolarPanelBlockEntity.Channel.EVOLVE_MAX);
	}
}
