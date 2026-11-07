package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.ApiaryDroneStationBlockEntity;
import dev.alaindustrial.block.entity.ApiaryEntryStatus;
import dev.alaindustrial.block.entity.ApiaryStationStatus;
import dev.alaindustrial.block.entity.machine.DroneBay;
import dev.alaindustrial.core.apiary.ApiaryRules;
import dev.alaindustrial.item.apiary.ApiaryMapItem;
import dev.alaindustrial.item.apiary.ApiaryMapPoints;
import dev.alaindustrial.menu.ApiaryDroneStationMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * Screen of the apiary station (MOD-923): the energy bar, the map slot over the comb slot, the four bays in a cross
 * as they stand on the block, the honey gauge, then the map's eight rows (distance and state, coordinates on hover)
 * and one status line. The atlas is drawn by {@code tools/model_sources/apiary/build_apiary.py}.
 */
public class ApiaryDroneStationScreen extends MachineScreen<ApiaryDroneStationMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/apiary_drone_station.png");

	private static final int GUI_WIDTH = 176;
	private static final int GUI_HEIGHT = 222;

	/** The honey gauge: 10 px of inner width, rows 18..63, filling bottom-up. */
	private static final int GAUGE_X = 152;
	private static final int GAUGE_W = 10;
	private static final int GAUGE_H = 46;
	private static final int GAUGE_BOTTOM = 64;

	/** The map's rows: two columns of four. */
	private static final int ROWS_X = 30;
	private static final int ROWS_Y = 73;
	private static final int COLUMN_W = 72;
	private static final int ROW_H = 9;

	private static final int STATUS_X = 30;
	private static final int STATUS_Y = 112;

	private static final int TANK_MB = (int) ApiaryDroneStationBlockEntity.TANK_CAPACITY;

	public ApiaryDroneStationScreen(ApiaryDroneStationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, GUI_WIDTH, GUI_HEIGHT);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected EnergyBarSpec energyBar() {
		return EnergyBarSpec.LEFT;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		int fill = fillHeight();
		if (fill > 0) {
			FluidGauge.draw(graphics, tankFluid(), this.leftPos + GAUGE_X, this.topPos + GAUGE_BOTTOM - fill,
					GAUGE_W, fill);
		}
		// A bay whose drone is out shows an amber bar under its slot.
		for (int i = 0; i < ApiaryRules.BAYS; i++) {
			if (this.menu.bayPhase(i) != DroneBay.Phase.PARKED.ordinal()) {
				int x = this.leftPos + ApiaryDroneStationMenu.BAY_X[i];
				int y = this.topPos + ApiaryDroneStationMenu.BAY_Y[i] + 16;
				graphics.fill(x, y, x + 16, y + 1, 0xFFE8A838);
			}
		}
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		for (int row = 0; row < ApiaryMapPoints.MAX; row++) {
			ApiaryEntryStatus entry = this.menu.entryStatus(row);
			if (entry == ApiaryEntryStatus.NONE) {
				continue;
			}
			graphics.text(this.font, rowLine(row, entry), this.leftPos + rowX(row), this.topPos + rowY(row),
					0xFF404040, false);
		}
		graphics.text(this.font, statusLine(), this.leftPos + STATUS_X, this.topPos + STATUS_Y, 0xFF404040, false);
	}

	private static int rowX(int row) {
		return ROWS_X + row / 4 * COLUMN_W;
	}

	private static int rowY(int row) {
		return ROWS_Y + row % 4 * ROW_H;
	}

	/** "1 · 12 · full": the row's number, its distance in blocks and its state, coloured by what it means. */
	private Component rowLine(int row, ApiaryEntryStatus entry) {
		ChatFormatting colour = switch (entry) {
			case FULL, DRONE_OUT -> ChatFormatting.DARK_GREEN;
			case WAITING, NONE -> ChatFormatting.DARK_GRAY;
			case TOO_FAR, UNLOADED, OTHER_DIMENSION, NO_JAR -> ChatFormatting.DARK_RED;
		};
		Component state = Component.translatable(entry.translationKey()).withStyle(colour);
		if (entry == ApiaryEntryStatus.OTHER_DIMENSION) {
			return Component.translatable("gui.alaindustrial.apiary_drone_station.row_far", row + 1, state);
		}
		return Component.translatable("gui.alaindustrial.apiary_drone_station.row", row + 1,
				this.menu.entryDistance(row), state);
	}

	private Component statusLine() {
		ApiaryStationStatus status = this.menu.getStatus();
		ChatFormatting colour = switch (status) {
			case NO_HIVE, NO_MAP, NO_DRONES, NO_ENERGY -> ChatFormatting.RED;
			case TANK_FULL, OUTPUT_FULL -> ChatFormatting.GOLD;
			case WAITING, WORKING -> ChatFormatting.DARK_GRAY;
		};
		return Component.translatable(status.translationKey()).withStyle(colour);
	}

	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, ApiaryDroneStationBlockEntity.MAP_SLOT, new ItemStack(ModContent.APIARY_MAP.get()));
		for (int i = 0; i < ApiaryRules.BAYS; i++) {
			ghostHint(graphics, ApiaryDroneStationBlockEntity.BAY_SLOT_START + i,
					new ItemStack(ModContent.APIARY_DRONE.get()));
		}
	}

	@Override
	protected void gaugeTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.menu.getFluidRegistryId() != ApiaryDroneStationBlockEntity.FLUID_ID_NONE
				&& this.isHovering(GAUGE_X, GAUGE_BOTTOM - GAUGE_H, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
			int mb = this.menu.getFluidPermille() * TANK_MB / 1000;
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.fluid", FluidGauge.displayName(tankFluid()), mb, TANK_MB),
					mouseX, mouseY);
		}
		// A row's coordinates on hover: the map in the slot says where each row is.
		ItemStack map = this.menu.machineSlot(ApiaryDroneStationBlockEntity.MAP_SLOT).getItem();
		ApiaryMapPoints points = ApiaryMapItem.points(map);
		for (int row = 0; row < points.size(); row++) {
			if (this.isHovering(rowX(row), rowY(row), COLUMN_W - 2, ROW_H, mouseX, mouseY)) {
				GlobalPos point = points.get(row);
				graphics.setTooltipForNextFrame(this.font, Component.translatable(
						"gui.alaindustrial.apiary_drone_station.row_where", point.pos().getX(), point.pos().getY(),
						point.pos().getZ(), point.dimension().identifier().toString()), mouseX, mouseY);
			}
		}
	}

	private int fillHeight() {
		int permille = this.menu.getFluidPermille();
		if (this.menu.getFluidRegistryId() == ApiaryDroneStationBlockEntity.FLUID_ID_NONE || permille <= 0) {
			return 0;
		}
		return Math.max(1, permille * GAUGE_H / 1000);
	}

	private Fluid tankFluid() {
		return BuiltInRegistries.FLUID.byId(this.menu.getFluidRegistryId());
	}
}
