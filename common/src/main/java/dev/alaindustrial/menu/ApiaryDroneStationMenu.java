package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.ApiaryDroneStationBlockEntity;
import dev.alaindustrial.block.entity.ApiaryEntryStatus;
import dev.alaindustrial.block.entity.ApiaryStationStatus;
import dev.alaindustrial.core.apiary.ApiaryRules;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu of the apiary station (MOD-923): the map, the four bays in a cross (north, east, south, west — as on the
 * block), the comb output, and the channels for the tank, the status, the bays and the map's rows.
 *
 * <p>Each slot filter is written here for the client's prediction and in the block entity's {@code canPlaceItem}
 * for the server; one map, one drone per bay.
 */
public class ApiaryDroneStationMenu extends MachineMenu {

	/** Slot positions: these coordinates ARE the GUI art (tools/model_sources/apiary/build_apiary.py). */
	public static final int MAP_X = 44;
	public static final int MAP_Y = 21;
	public static final int COMB_X = 44;
	public static final int COMB_Y = 45;
	/** The bays' cross: north, east, south, west. */
	public static final int[] BAY_X = {90, 108, 90, 72};
	public static final int[] BAY_Y = {17, 35, 53, 35};

	/** Server side. */
	public ApiaryDroneStationMenu(int syncId, Inventory playerInventory, ApiaryDroneStationBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.APIARY_DRONE_STATION_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.APIARY_DRONE_STATION.get());
	}

	/** Client side — the data width must match the block entity's channel count exactly (MOD-235). */
	public ApiaryDroneStationMenu(int syncId, Inventory playerInventory) {
		super(ModContent.APIARY_DRONE_STATION_MENU.get(), syncId, playerInventory,
				clientStub(ApiaryDroneStationBlockEntity.SLOT_COUNT, ApiaryDroneStationBlockEntity.DATA_COUNT),
				ModContent.APIARY_DRONE_STATION.get());
	}

	@Override
	protected void addMachineSlots() {
		Container container = machine;
		addSlot(new Slot(container, ApiaryDroneStationBlockEntity.MAP_SLOT, MAP_X, MAP_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return getItem().isEmpty() && stack.is(ModContent.APIARY_MAP.get());
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		for (int i = 0; i < ApiaryRules.BAYS; i++) {
			addSlot(new Slot(container, ApiaryDroneStationBlockEntity.BAY_SLOT_START + i, BAY_X[i], BAY_Y[i]) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return getItem().isEmpty() && stack.is(ModContent.APIARY_DRONE.get());
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		addSlot(new OutputSlot(container, ApiaryDroneStationBlockEntity.COMB_SLOT, COMB_X, COMB_Y));
	}

	/** The atlas is 42 pixels taller than the machine standard: the band that holds the map's rows. */
	@Override
	protected int playerInventoryY() {
		return 140;
	}

	@Override
	protected int hotbarY() {
		return 198;
	}

	public ApiaryStationStatus getStatus() {
		return ApiaryStationStatus.byOrdinal(channel(ApiaryDroneStationBlockEntity.Channel.STATUS));
	}

	/** Honey level as a permille (0..1000). */
	public int getFluidPermille() {
		return channel(ApiaryDroneStationBlockEntity.Channel.LEVEL_PERMILLE);
	}

	/** Registry id of the fluid in the tank, or {@link ApiaryDroneStationBlockEntity#FLUID_ID_NONE}. */
	public int getFluidRegistryId() {
		return channel(ApiaryDroneStationBlockEntity.Channel.FLUID_ID);
	}

	/** The phase ordinal of bay {@code bay} ({@link dev.alaindustrial.block.entity.machine.DroneBay.Phase}). */
	public int bayPhase(int bay) {
		return channel(ApiaryDroneStationBlockEntity.Channel.valueOf("BAY_" + bay));
	}

	/** Row {@code row} of the map: its state. */
	public ApiaryEntryStatus entryStatus(int row) {
		int value = channel(ApiaryDroneStationBlockEntity.Channel.valueOf("ENTRY_" + row));
		return ApiaryEntryStatus.byOrdinal(value / 256);
	}

	/** Row {@code row} of the map: its distance in whole blocks (0 for another dimension). */
	public int entryDistance(int row) {
		return channel(ApiaryDroneStationBlockEntity.Channel.valueOf("ENTRY_" + row)) % 256;
	}
}
