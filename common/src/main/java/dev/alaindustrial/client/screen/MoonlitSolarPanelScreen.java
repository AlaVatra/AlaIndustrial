package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Moonlit Solar Panel GUI — post-evolution, night-only panel.
 *
 * <p>Mirrors {@link DaylightSolarPanelScreen} but inverts the active-sky check:
 * the sun indicator lights up when the moon is visible (MODE_NIGHT / MODE_NIGHT_PARTIAL).
 * Since MOD-1031 it carries an evolution-chip slot and an evolution bar, the bar in the blue night tile.
 */
public class MoonlitSolarPanelScreen extends LayoutMachineScreen<MoonlitSolarPanelMenu> {

    private static final Identifier TEXTURE =
            Industrialization.id("textures/gui/container/moonlit_solar_panel.png");

    // Moon indicator — reuses the same 7×7 service-area tile as the solar sun indicator
    private static final int   SUN_FRAME_X = 153;
    private static final int   SUN_FRAME_Y = 17;
    private static final int   SUN_FRAME_W = 7;
    private static final int   SUN_FRAME_H = 7;
    private static final float SUN_UV_X    = 176.0F;
    private static final float SUN_UV_Y    = 65.0F;

    // Evolution bar — same geometry as the daylight panel's, the blue night tile of the service area (MOD-1031).
    private static final int   EVO_X          = 55;
    private static final int   EVO_Y          = 58;
    private static final int   EVO_MAX_W      = 75;
    private static final int   EVO_H          = 7;
    private static final float EVO_UV_X       = 176.0F;
    private static final float EVO_UV_Y_NIGHT = 57.0F;

    /** Atlas and energy bar (MOD-716, CLI-3). */
    private static final MachineLayout LAYOUT = MachineLayout.of(TEXTURE, EnergyBarSpec.LEFT);

    public MoonlitSolarPanelScreen(MoonlitSolarPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, LAYOUT);
    }

    @Override
    protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;

        // Static frame — borders and tick marks
        blitStaticFrame(graphics);

        // Energy bar fill (bottom-up) via the shared MachineScreen helper
        int capacity = this.menu.getCapacity();
        int energy   = this.menu.getEnergy();

        // Moon indicator — blit yellow 7×7 tile when moonlight is available
        int mode = this.menu.getMode();
        boolean moonActive = mode == MoonlitSolarPanelBlockEntity.MODE_NIGHT
                          || mode == MoonlitSolarPanelBlockEntity.MODE_NIGHT_PARTIAL;
        if (moonActive) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + SUN_FRAME_X, y + SUN_FRAME_Y,
                    SUN_UV_X, SUN_UV_Y,
                    SUN_FRAME_W, SUN_FRAME_H,
                    TEX_SIZE, TEX_SIZE);
        }

        // Evolution bar fill (left to right), blitted from the service area; at least one pixel once it counts.
        int evoMax  = this.menu.getEvolveMax();
        int evoProg = this.menu.getEvolveProgress();
        if (evoMax > 0 && evoProg > 0) {
            int evoFill = Math.max(1, Math.min(evoProg * EVO_MAX_W / evoMax, EVO_MAX_W));
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + EVO_X, y + EVO_Y,
                    EVO_UV_X, EVO_UV_Y_NIGHT,
                    evoFill, EVO_H,
                    TEX_SIZE, TEX_SIZE);
        }

        // Text overlay
        graphics.text(this.font, Component.translatable("gui.alaindustrial.energy", energy, capacity),
                x + 30, y + 22, GuiStyle.TEXT, false);
        graphics.text(this.font, outputLine(this.menu.getProductionRate()),
                x + 30, y + 34, GuiStyle.TEXT, false);
        graphics.text(this.font, Component.translatable("gui.alaindustrial.mode", modeLabel(mode)),
                x + 30, y + 46, GuiStyle.TEXT_DIM, false);
    }

    /**
     * The empty chip slot shows a translucent lunar evolution chip (MOD-1033): the night branch's own chip, blue like
     * the panel. The resonance chip is taken too, but its gold face read as a day chip on a night panel.
     */
    @Override
    protected void drawGhostHints(GuiGraphicsExtractor graphics) {
        ghostHint(graphics, MoonlitSolarPanelBlockEntity.CHIP_SLOT,
                ModContent.ALIGNMENT_CHIP_NIGHT.get().getDefaultInstance());
    }

    private static Component modeLabel(int mode) {
        String key = switch (mode) {
            case MoonlitSolarPanelBlockEntity.MODE_NIGHT         -> "night";
            case MoonlitSolarPanelBlockEntity.MODE_NIGHT_WEATHER -> "night_weather";
            case MoonlitSolarPanelBlockEntity.MODE_NIGHT_PARTIAL -> "night_partial";
            case MoonlitSolarPanelBlockEntity.MODE_NIGHT_SNOW    -> "night_snow";
            default                                               -> "idle_day";
        };
        return Component.translatable("gui.alaindustrial.solar_panel.mode." + key);
    }

}
