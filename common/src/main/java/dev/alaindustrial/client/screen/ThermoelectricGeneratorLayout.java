package dev.alaindustrial.client.screen;

import java.util.List;

/**
 * Where the thermoelectric generator's screen draws (MOD-811), in one Minecraft-free place: the screen reads these
 * numbers, {@code tools/gen_thermoelectric_generator_assets.py} bakes the wells and sprites at them, and the L1 text
 * test fits every translation against them.
 *
 * <p>The frame is the water mill's 176 x 178 panel with its ticked energy bar on the left. Each axis of the cube is
 * one row: its two faces as two wells, the thermocouple bar between them lit from the hot end to the cold one, a
 * flame or a snowflake outside a hot or a cold face, and the axis's rate on the right. The status row under the
 * rows says why the block is idle, or what it makes.
 */
public final class ThermoelectricGeneratorLayout {

	public static final int IMAGE_WIDTH = 176;
	public static final int IMAGE_HEIGHT = 178;
	/** Where the title starts, as every machine screen writes it. */
	public static final int TITLE_X = 8;

	/** Top of each axis row's wells: vertical, north-south, west-east. */
	public static final int[] ROW_Y = {17, 36, 55};
	/** Side of a face well, its bevel included. */
	public static final int CELL = 18;
	public static final int CELL_LEFT_X = 40;
	public static final int CELL_RIGHT_X = 78;

	/** The thermocouple bar's well between the two faces, and the lit strip inside it. */
	public static final int BAR_X = 59;
	public static final int BAR_TOP_IN_ROW = 6;
	public static final int STRIP_W = 16;
	public static final int STRIP_H = 4;
	public static final int STRIP_HOT_LEFT_U = 190;
	public static final int STRIP_HOT_LEFT_V = 10;
	public static final int STRIP_HOT_RIGHT_U = 190;
	public static final int STRIP_HOT_RIGHT_V = 16;

	/** The flame beside a hot face and the snowflake beside a cold one: 7 x 7 sprites of the atlas. */
	public static final int ICON = 7;
	public static final int ICON_LEFT_X = 31;
	public static final int ICON_RIGHT_X = 98;
	public static final int ICON_TOP_IN_ROW = 6;
	public static final int FLAME_U = 190;
	public static final int FLAKE_U = 198;
	public static final int ICON_V = 0;

	/** The band each row's rate is centred in, and its baseline inside the row. */
	public static final int RATE_LEFT = 107;
	public static final int RATE_RIGHT = 168;
	public static final int RATE_TOP_IN_ROW = 5;

	/** The status row: under the rows, above the inventory label, right of the energy bar. */
	public static final int STATUS_Y = 75;
	public static final int STATUS_LEFT = 32;
	public static final int STATUS_RIGHT = 168;
	public static final int INVENTORY_LABEL_Y = 85;

	/** A row's rate: {@code "%s EU/t"}. */
	public static final String RATE_KEY = "gui.alaindustrial.thermoelectric_generator.rate";
	/** The status row: idle for want of a pair, idle because nothing draws, or what the block makes. */
	public static final String NO_PAIR_KEY = "gui.alaindustrial.thermoelectric_generator.no_pair";
	public static final String IDLE_KEY = "gui.alaindustrial.output_idle";
	public static final String OUTPUT_KEY = "gui.alaindustrial.output";
	public static final List<String> STATUS_KEYS = List.of(NO_PAIR_KEY, IDLE_KEY, OUTPUT_KEY);

	private ThermoelectricGeneratorLayout() {
	}
}
