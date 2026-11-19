package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Menu for the LV generator: the fuel slot and, right of the flame, the ash it leaves (MOD-987). */
public class GeneratorMenu extends MachineMenu {
	/**
	 * The ash slot's item corner: the inner 16x16 fill of its frame in {@code generator.png} (frame 147..164 x
	 * 34..51, the same height as the fuel slot's), measured from the atlas, not by eye.
	 */
	public static final int ASH_X = 148;
	public static final int ASH_Y = 35;

	/** Server side. */
	public GeneratorMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.GENERATOR_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.GENERATOR.get());
	}

	/** Client side. */
	public GeneratorMenu(int syncId, Inventory playerInventory) {
		super(ModContent.GENERATOR_MENU.get(), syncId, playerInventory,
				clientStub(GeneratorBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT, MachineBlockEntity.DATA_COUNT),
				ModContent.GENERATOR.get());
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, 0, 79, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				// Explicit client-side check so the client rejects immediately without
				// optimistic placement → server correction → visible flicker.
				if (stack.is(Items.LAVA_BUCKET)) {
					return false;
				}
				return machine.canPlaceItem(0, stack);
			}
		});
		addSlot(new OutputSlot(machine, GeneratorBlockEntity.ASH_SLOT, ASH_X, ASH_Y));
	}
}
