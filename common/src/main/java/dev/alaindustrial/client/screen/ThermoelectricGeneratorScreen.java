package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.ThermoelectricGeneratorBlock;
import dev.alaindustrial.block.entity.ThermoelectricGeneratorBlockEntity;
import dev.alaindustrial.core.environment.ThermoelectricOutput;
import dev.alaindustrial.core.tooltip.TooltipUnits;
import dev.alaindustrial.menu.ThermoelectricGeneratorMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Screen of the thermoelectric generator (MOD-811), built from the mod's standard parts: the water mill's frame and
 * ticked energy bar on the left, then one row per axis of the cube — its two faces as wells, the thermocouple bar
 * between them lit from the hot end to the cold one, a flame or a snowflake beside a hot or a cold face, the axis's
 * rate on the right — and the status row under them: why the block is idle, or what it makes. Every number is in
 * {@link ThermoelectricGeneratorLayout}.
 *
 * <p><b>Who says what.</b> The temperatures, the axis count and the rate come from the server (sync channels): the
 * table is a server datapack and the rate carries the server's multiplier. The icon and the name of what stands at
 * a face come from the client's own world — the block the player opened, read when the screen opens; when that is
 * not this generator (a screen opened by a test or another mod), the wells show colour and level only.
 */
public class ThermoelectricGeneratorScreen extends MachineScreen<ThermoelectricGeneratorMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/thermoelectric_generator.png");

	/**
	 * The faces of each row, left well first: up and down, north and south, west and east — the last as a map draws
	 * it. The row index is {@code ThermoelectricOutput}'s axis index.
	 */
	private static final Direction[][] ROWS = {
		{Direction.UP, Direction.DOWN},
		{Direction.NORTH, Direction.SOUTH},
		{Direction.WEST, Direction.EAST},
	};

	/** The generator this screen belongs to in the client's world, or null when it cannot be told. */
	private BlockPos target;

	public ThermoelectricGeneratorScreen(ThermoelectricGeneratorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, ThermoelectricGeneratorLayout.IMAGE_WIDTH,
				ThermoelectricGeneratorLayout.IMAGE_HEIGHT);
		this.inventoryLabelX = 8;
		this.inventoryLabelY = ThermoelectricGeneratorLayout.INVENTORY_LABEL_Y;
	}

	@Override
	protected void init() {
		super.init();
		target = null;
		if (this.minecraft != null && this.minecraft.level != null
				&& this.minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
				&& this.minecraft.level.getBlockState(hit.getBlockPos()).getBlock()
						instanceof ThermoelectricGeneratorBlock) {
			target = hit.getBlockPos();
		}
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	/** The energy bar and its tooltip, drawn by the base: the taller frame's left bar. */
	@Override
	protected EnergyBarSpec energyBar() {
		return EnergyBarSpec.LEFT_WINDMILL;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		for (int row = 0; row < ROWS.length; row++) {
			int y = this.topPos + ThermoelectricGeneratorLayout.ROW_Y[row];
			int left = this.menu.faceTemperature(ROWS[row][0]);
			int right = this.menu.faceTemperature(ROWS[row][1]);
			drawCell(graphics, ROWS[row][0], left, this.leftPos + ThermoelectricGeneratorLayout.CELL_LEFT_X, y);
			drawCell(graphics, ROWS[row][1], right, this.leftPos + ThermoelectricGeneratorLayout.CELL_RIGHT_X, y);
			drawMark(graphics, left, this.leftPos + ThermoelectricGeneratorLayout.ICON_LEFT_X, y);
			drawMark(graphics, right, this.leftPos + ThermoelectricGeneratorLayout.ICON_RIGHT_X, y);
			if (left * right < 0) {
				boolean hotLeft = left > 0;
				graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
						this.leftPos + ThermoelectricGeneratorLayout.BAR_X + 1,
						y + ThermoelectricGeneratorLayout.BAR_TOP_IN_ROW + 1,
						hotLeft ? ThermoelectricGeneratorLayout.STRIP_HOT_LEFT_U
								: ThermoelectricGeneratorLayout.STRIP_HOT_RIGHT_U,
						hotLeft ? ThermoelectricGeneratorLayout.STRIP_HOT_LEFT_V
								: ThermoelectricGeneratorLayout.STRIP_HOT_RIGHT_V,
						ThermoelectricGeneratorLayout.STRIP_W, ThermoelectricGeneratorLayout.STRIP_H,
						TEX_SIZE, TEX_SIZE);
			}
		}
	}

	/** One face: a frame in the face's temperature colour, what stands there, and its level in the corner. */
	private void drawCell(GuiGraphicsExtractor graphics, Direction face, int temperature, int x, int y) {
		int cell = ThermoelectricGeneratorLayout.CELL;
		if (temperature != 0) {
			int frame = temperature > 0 ? GuiStyle.HEAT_FILL : GuiStyle.LEVEL_FILL;
			graphics.fill(x, y, x + cell, y + 1, frame);
			graphics.fill(x, y + cell - 1, x + cell, y + cell, frame);
			graphics.fill(x, y, x + 1, y + cell, frame);
			graphics.fill(x + cell - 1, y, x + cell, y + cell, frame);
		}
		ItemStack icon = neighbourIcon(face);
		if (!icon.isEmpty()) {
			graphics.item(icon, x + 1, y + 1);
		}
		if (temperature != 0) {
			String digit = Integer.toString(Math.abs(temperature));
			graphics.text(this.font, digit, x + cell - 1 - this.font.width(digit), y + cell - 9, GuiStyle.CHIP_TEXT,
					true);
		}
	}

	/** The flame beside a hot face, the snowflake beside a cold one, nothing beside a neutral one. */
	private void drawMark(GuiGraphicsExtractor graphics, int temperature, int x, int rowTop) {
		if (temperature == 0) {
			return;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, rowTop + ThermoelectricGeneratorLayout.ICON_TOP_IN_ROW,
				temperature > 0 ? ThermoelectricGeneratorLayout.FLAME_U : ThermoelectricGeneratorLayout.FLAKE_U,
				ThermoelectricGeneratorLayout.ICON_V, ThermoelectricGeneratorLayout.ICON,
				ThermoelectricGeneratorLayout.ICON, TEX_SIZE, TEX_SIZE);
	}

	@Override
	protected void drawFrameText(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int[] faces = new int[ThermoelectricOutput.FACES];
		for (Direction face : Direction.values()) {
			faces[face.get3DDataValue()] = this.menu.faceTemperature(face);
		}
		// Each axis's share of the server's total, by its degrees: the rate is linear in the degrees, so the share is
		// exact, and the screen never multiplies by a config of its own (a dedicated server sends none).
		int totalDegrees = ThermoelectricOutput.totalDifference(faces);
		for (int row = 0; row < ROWS.length; row++) {
			int degrees = ThermoelectricOutput.axisDifference(faces, row);
			int centi = totalDegrees > 0 ? (int) ((long) this.menu.rateCenti() * degrees / totalDegrees) : 0;
			drawFittedStatus(graphics, Component.translatable(ThermoelectricGeneratorLayout.RATE_KEY, decimal(centi)),
					ThermoelectricGeneratorLayout.ROW_Y[row] + ThermoelectricGeneratorLayout.RATE_TOP_IN_ROW,
					ThermoelectricGeneratorLayout.RATE_LEFT, ThermoelectricGeneratorLayout.RATE_RIGHT,
					degrees > 0 ? GuiStyle.TEXT : GuiStyle.TEXT_DIM);
		}
		Component status;
		int colour;
		if (this.menu.mode() == ThermoelectricGeneratorBlockEntity.MODE_NO_PAIR) {
			status = Component.translatable(ThermoelectricGeneratorLayout.NO_PAIR_KEY);
			colour = GuiStyle.STATUS_BLOCKING;
		} else if (this.menu.getCapacity() > 0 && this.menu.getEnergy() >= this.menu.getCapacity()) {
			status = Component.translatable(ThermoelectricGeneratorLayout.IDLE_KEY);
			colour = GuiStyle.TEXT_DIM;
		} else {
			status = Component.translatable(ThermoelectricGeneratorLayout.OUTPUT_KEY, decimal(this.menu.rateCenti()));
			colour = GuiStyle.TEXT;
		}
		drawFittedStatus(graphics, status, ThermoelectricGeneratorLayout.STATUS_Y,
				ThermoelectricGeneratorLayout.STATUS_LEFT, ThermoelectricGeneratorLayout.STATUS_RIGHT, colour);
	}

	@Override
	protected void gaugeTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int[] xs = {ThermoelectricGeneratorLayout.CELL_LEFT_X, ThermoelectricGeneratorLayout.CELL_RIGHT_X};
		for (int row = 0; row < ROWS.length; row++) {
			for (int side = 0; side < 2; side++) {
				if (isHovering(xs[side], ThermoelectricGeneratorLayout.ROW_Y[row], ThermoelectricGeneratorLayout.CELL,
						ThermoelectricGeneratorLayout.CELL, mouseX, mouseY)) {
					Direction face = ROWS[row][side];
					List<Component> lines = new ArrayList<>();
					lines.add(Component.translatable("gui.alaindustrial.thermoelectric_generator.face."
							+ face.getSerializedName()));
					BlockState state = neighbour(face);
					if (state != null) {
						lines.add(state.getBlock().getName());
					}
					lines.add(temperatureLine(this.menu.faceTemperature(face)));
					graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
					return;
				}
			}
		}
	}

	private static Component temperatureLine(int temperature) {
		if (temperature > 0) {
			return Component.translatable("gui.alaindustrial.thermoelectric_generator.hot", temperature);
		}
		if (temperature < 0) {
			return Component.translatable("gui.alaindustrial.thermoelectric_generator.cold", -temperature);
		}
		return Component.translatable("gui.alaindustrial.thermoelectric_generator.neutral");
	}

	/** Hundredths as the language writes a fraction: 375 is "3.75" in English, "3,75" in Russian. */
	private static Component decimal(int hundredths) {
		TooltipUnits.Decimal value = TooltipUnits.factor(hundredths / 100.0);
		return value.fraction() == null ? Component.literal(value.whole())
				: Component.translatable(TooltipUnits.DECIMAL_KEY, value.whole(), value.fraction());
	}

	/** What stands at {@code face} in the client's world, as an icon: its item, or the bucket of its fluid. */
	private ItemStack neighbourIcon(Direction face) {
		BlockState state = neighbour(face);
		if (state == null) {
			return ItemStack.EMPTY;
		}
		Item item = state.getBlock().asItem();
		if (item == Items.AIR) {
			FluidState fluid = state.getFluidState();
			item = fluid.isEmpty() ? Items.AIR : fluid.getType().getBucket();
		}
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	private BlockState neighbour(Direction face) {
		Level level = this.minecraft == null ? null : this.minecraft.level;
		if (target == null || level == null) {
			return null;
		}
		return level.getBlockState(target.relative(face));
	}
}
