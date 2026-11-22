package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the geothermal generator: lava-bucket input + empty-bucket output, the obsidian shavings right of the
 * containers (MOD-995) and the blaze-powder catalyst left of the lava (MOD-997). Slots are added in container order,
 * so a menu slot index is its container index.
 */
public class GeothermalGeneratorMenu extends MachineMenu {
	/**
	 * The shavings slot's item corner: the inner 16x16 fill of its frame in {@code geothermal_generator.png} (frame
	 * 121..138 x 33..50, the row of the two lava slots), measured from the atlas, not by eye.
	 */
	public static final int SHAVINGS_X = 122;
	public static final int SHAVINGS_Y = 34;
	/** The catalyst slot's item corner: frame 34..51 x 33..50 in the atlas, left of the lava slot. */
	public static final int CATALYST_X = 35;
	public static final int CATALYST_Y = 34;

	/** Server side. */
	public GeothermalGeneratorMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.GEOTHERMAL_GENERATOR_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.GEOTHERMAL_GENERATOR.get());
	}

	/** Client side. */
	public GeothermalGeneratorMenu(int syncId, Inventory playerInventory) {
		super(ModContent.GEOTHERMAL_GENERATOR_MENU.get(), syncId, playerInventory,
				clientStub(GeothermalGeneratorBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT,
						GeothermalGeneratorBlockEntity.DATA_COUNT),
				ModContent.GEOTHERMAL_GENERATOR.get());
	}

	/** Arm 1 takes the cistern chip here (MOD-996): the overclocker has nothing to do on a generator. */
	@Override
	protected UpgradeSlotKind upgradeSlotKind(int i) {
		return i == 1 ? UpgradeSlotKind.CISTERN : super.upgradeSlotKind(i);
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, GeothermalGeneratorBlockEntity.INPUT_SLOT, 60, 34));   // lava bucket in
		addSlot(new Slot(machine, GeothermalGeneratorBlockEntity.OUTPUT_SLOT, 98, 34));  // empty bucket out
		addSlot(new OutputSlot(machine, GeothermalGeneratorBlockEntity.SHAVINGS_SLOT, SHAVINGS_X, SHAVINGS_Y));
		addSlot(new Slot(machine, GeothermalGeneratorBlockEntity.CATALYST_SLOT, CATALYST_X, CATALYST_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				// The client rejects anything but blaze powder at once, with no server correction to flicker.
				return GeothermalGeneratorBlockEntity.isCatalyst(stack);
			}
		});
	}

	/** Whether a catalysed bucket burns (MOD-997): the flame the screen lights above the catalyst slot. */
	public boolean isCatalysed() {
		return channel(GeothermalGeneratorBlockEntity.Channel.CATALYST) != 0;
	}
}
