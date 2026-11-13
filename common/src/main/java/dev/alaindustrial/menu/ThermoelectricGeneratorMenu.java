package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.ThermoelectricGeneratorBlockEntity;
import dev.alaindustrial.block.entity.ThermoelectricGeneratorBlockEntity.Channel;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;

/**
 * Menu for the thermoelectric generator (MOD-811): no machine slots, only the upgrade panel. It carries what the
 * server read on each face, how many axes work, the mode and the effective rate.
 */
public class ThermoelectricGeneratorMenu extends MachineMenu {
	/** Server side. */
	public ThermoelectricGeneratorMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.THERMOELECTRIC_GENERATOR_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.THERMOELECTRIC_GENERATOR.get());
	}

	/** Client side. */
	public ThermoelectricGeneratorMenu(int syncId, Inventory playerInventory) {
		super(ModContent.THERMOELECTRIC_GENERATOR_MENU.get(), syncId, playerInventory,
				clientStub(ThermoelectricGeneratorBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT,
						ThermoelectricGeneratorBlockEntity.DATA_COUNT),
				ModContent.THERMOELECTRIC_GENERATOR.get());
	}

	@Override
	protected void addMachineSlots() {
		// No machine slots: the generator burns nothing.
	}

	/** The taller 176×178 frame (the six face cells, the axis row and the status): the inventory sits lower. */
	@Override
	protected int playerInventoryY() {
		return 96;
	}

	@Override
	protected int hotbarY() {
		return 154;
	}

	/** The temperature the server read on {@code face}: hot {@code 1..4}, cold {@code -1..-4}, neutral 0. */
	public int faceTemperature(Direction face) {
		return channel(switch (face) {
			case DOWN -> Channel.FACE_DOWN;
			case UP -> Channel.FACE_UP;
			case NORTH -> Channel.FACE_NORTH;
			case SOUTH -> Channel.FACE_SOUTH;
			case WEST -> Channel.FACE_WEST;
			case EAST -> Channel.FACE_EAST;
		});
	}

	/** The effective output in hundredths of an EU/t (375 is 3.75 EU/t). */
	public int rateCenti() {
		return channel(Channel.RATE_CENTI);
	}

	/** How many of the three axes work (0..3). */
	public int workingAxes() {
		return getProgress();
	}

	/** The mode: {@link ThermoelectricGeneratorBlockEntity#MODE_OK} or {@code MODE_NO_PAIR}. */
	public int mode() {
		return getMaxProgress();
	}
}
