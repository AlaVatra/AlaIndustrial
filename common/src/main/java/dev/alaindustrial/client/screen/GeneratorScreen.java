package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.environment.FuelRuntime;
import dev.alaindustrial.menu.GeneratorMenu;
import dev.alaindustrial.network.MachineStatsPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the LV Generator. Static frame from atlas PNG; dynamic layer draws
 * the energy fill (bottom-up orange bar) and burn flame (top-shrinks as fuel is consumed).
 */
public class GeneratorScreen extends LayoutMachineScreen<GeneratorMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/generator.png");

	// Burn indicator: flame sprite starts at u=188 (teardrop inner pixels, 14px wide at widest).
	// u=176-185 is the energy-bar sprite — must NOT include it in this blit.
	// Top-anchored: bright yellow-orange tip shows while fuel remains; base shrinks last.
	private static final int FLAME_SU = 188, FLAME_SV = 0, FLAME_W = 14, FLAME_H = 21;
	private static final int FLAME_X = 124, FLAME_Y = 31;

	/**
	 * The two lines of the statistics chip (MOD-990) — "Fuel lasts ~12 min" and "Load 2 of 8 EU/t" — under the
	 * slot and the flame: the atlas is plain panel from y=52 (the slot frame and the flame end on row 51) to the
	 * "Inventory" label at y=72, measured from generator.png. Two 8 px lines with a 1 px gap fit at 54 and 63.
	 */
	public static final int FUEL_LASTS_Y = 54;
	public static final int LOAD_Y = 63;

	/** Atlas and energy bar (MOD-716, CLI-3). */
	private static final MachineLayout LAYOUT = MachineLayout.of(TEXTURE, EnergyBarSpec.LEFT);

	public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, LAYOUT);
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		// Static frame: visible imageWidth × imageHeight region at top-left of the 256×256 atlas.
		blitStaticFrame(graphics);


		// Burn indicator: bottom-anchored — base stays, tip disappears from top as fuel burns.
		// dest y = FLAME_Y + (FLAME_H - flameFill) shifts the blit up as fill grows.
		int maxBurn = this.menu.getMaxProgress();
		int flameFill = maxBurn > 0 ? this.menu.getProgress() * FLAME_H / maxBurn : 0;
		if (flameFill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + FLAME_X, y + FLAME_Y + FLAME_H - flameFill,
					(float) FLAME_SU, (float) (FLAME_SV + FLAME_H - flameFill),
					FLAME_W, flameFill, TEX_SIZE, TEX_SIZE);
		}
	}

	/**
	 * MOD-990: with the statistics chip in, the span of the fuel and the line's load, as extra lines of the window
	 * itself. They come with the chip's statistics snapshot, so without the chip there is nothing to draw and
	 * nothing was sent; both lines stay clear of the energy bar on the left (the shared status band).
	 */
	@Override
	protected void drawFrameText(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		MachineStatsPayload stats = this.menu.stats();
		if (stats == null || stats.fuel().isEmpty()) {
			return;
		}
		FuelRuntime.Readout fuel = stats.fuel().get();
		drawFittedStatus(graphics, FuelRuntimeText.lastsLine(fuel), FUEL_LASTS_Y, STATUS_ROW_LEFT, STATUS_ROW_RIGHT,
				GuiStyle.LABEL);
		drawFittedStatus(graphics, FuelRuntimeText.loadLine(fuel), LOAD_Y, STATUS_ROW_LEFT, STATUS_ROW_RIGHT,
				GuiStyle.LABEL);
	}

}
