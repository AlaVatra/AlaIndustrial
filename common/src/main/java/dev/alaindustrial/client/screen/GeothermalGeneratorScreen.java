package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.environment.GeothermalLava;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * Texture-backed screen for the Geothermal Generator.
 *
 * <p>Left bar: lava level (bottom-up), drawn with lava's real block texture via {@link FluidGauge} —
 * the same tiled, animated rendering the Pump uses (MOD-099), instead of a flat baked sprite. The
 * generator only ever burns lava, so the fluid is hardcoded rather than read off a synced registry id.
 * Right bar: energy (orange sprite, bottom-up) — shows stored EU / buffer capacity.
 * Slots: lava-bucket input at (60,34), empty-bucket output at (98,34), obsidian shavings at (122,34) (MOD-995),
 * the blaze-powder catalyst at (35,34) with a flame above it, lit while a catalysed bucket burns (MOD-997).
 */
public class GeothermalGeneratorScreen extends MachineScreen<GeothermalGeneratorMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/geothermal_generator.png");

	// Lava level fill (LEFT bar): inner trough x=16-26 (11px wide), y=19-65 (44px tall).
	private static final int LAVA_W = 11, LAVA_H = 44;
	private static final int LAVA_X = 16, LAVA_BOTTOM = 65;

	/**
	 * The catalyst flame (MOD-997): a 10x12 sprite at u=212, v=0 of the atlas, blitted over the unlit flame baked
	 * into the frame at (38,19), centred above the catalyst slot's frame (34..51). Both measured from the atlas.
	 */
	private static final int FLAME_SU = 212, FLAME_SV = 0, FLAME_W = 10, FLAME_H = 12;
	private static final int FLAME_X = 38, FLAME_Y = 19;

	/**
	 * Click area of the recipe viewers (MOD-716): the arrow between the fuel and container slots, x 82..91,
	 * y 38..44 by a pixel scan of the atlas. It is baked into the frame — this screen draws no progress sprite —
	 * so the rectangle IS the measurement.
	 */
	public static final GuiRect PROGRESS_AREA = new GuiRect(82, 38, 10, 7);

	public GeothermalGeneratorScreen(GeothermalGeneratorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	/** The energy bar and its tooltip, drawn by the base (MOD-716). */
	@Override
	protected EnergyBarSpec energyBar() {
		return EnergyBarSpec.RIGHT;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		// Static frame: imageWidth × imageHeight region at (0,0) of the 256×256 atlas.
		blitStaticFrame(graphics);

		// Lava level fill: grows bottom-up proportional to remaining lava ticks, in lava's own animated texture.
		int maxLava = this.menu.getMaxProgress();
		int lavaFill = maxLava > 0 ? (int) ((long) this.menu.getProgress() * LAVA_H / maxLava) : 0;
		if (lavaFill > 0) {
			FluidGauge.draw(graphics, Fluids.LAVA, x + LAVA_X, y + LAVA_BOTTOM - lavaFill, LAVA_W, lavaFill);
		}

		// MOD-997: the flame above the catalyst slot is lit while a catalysed bucket burns.
		if (this.menu.isCatalysed()) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + FLAME_X, y + FLAME_Y, (float) FLAME_SU,
					(float) FLAME_SV, FLAME_W, FLAME_H, TEX_SIZE, TEX_SIZE);
		}
	}

	/**
	 * A lava bucket in the intake slot: the generator burns lava and nothing else, and the two bare
	 * slots said none of that. The emptied bucket lands in the slot beside it and needs no hint —
	 * the machine puts it there.
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		// MOD-997: the catalyst slot takes blaze powder and nothing else.
		ghostHint(graphics, GeothermalGeneratorBlockEntity.CATALYST_SLOT, new ItemStack(Items.BLAZE_POWDER));
	}

	@Override
	protected void gaugeTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// Left bar — lava burn buffer as millibuckets: ticks over the server's burn per bucket, so both the
		// level and the capacity follow the cistern chip (MOD-996: 10 000 mB, 20 000 with it).
		int maxProgress = this.menu.getMaxProgress();
		if (maxProgress > 0 && this.isHovering(LAVA_X, LAVA_BOTTOM - LAVA_H, LAVA_W, LAVA_H, mouseX, mouseY)) {
			int burnTicks = ServerBalance.geothermalBurnTicks();
			long lavaMb = GeothermalLava.reserveMb(this.menu.getProgress(), burnTicks);
			long capacityMb = GeothermalLava.reserveMb(maxProgress, burnTicks);
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.lava", lavaMb, capacityMb),
					mouseX, mouseY);
		}
	}
}
