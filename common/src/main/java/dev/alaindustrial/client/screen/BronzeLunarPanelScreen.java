package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.BronzeLunarPanelBlockEntity;
import dev.alaindustrial.menu.BronzeLunarPanelMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Bronze Lunar Panel GUI (MOD-1031) — the night branch's third rung. The night twin of
 * {@link ReinforcedSolarPanelScreen}: the indicator lights while the moon is working, and the mode line reads the
 * moonlit panel's labels.
 */
public class BronzeLunarPanelScreen extends LayoutMachineScreen<BronzeLunarPanelMenu> {

    private static final Identifier TEXTURE =
            Industrialization.id("textures/gui/container/bronze_lunar_panel.png");

    // Moon indicator — the same 7×7 service-area tile and position as the panels below it.
    private static final int   SUN_FRAME_X = 153;
    private static final int   SUN_FRAME_Y = 17;
    private static final int   SUN_FRAME_W = 7;
    private static final int   SUN_FRAME_H = 7;
    private static final float SUN_UV_X    = 176.0F;
    private static final float SUN_UV_Y    = 65.0F;

    /** Atlas and energy bar (MOD-716, CLI-3). */
    private static final MachineLayout LAYOUT = MachineLayout.of(TEXTURE, EnergyBarSpec.LEFT);

    public BronzeLunarPanelScreen(BronzeLunarPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, LAYOUT);
    }

    @Override
    protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;

        blitStaticFrame(graphics);

        int capacity = this.menu.getCapacity();
        int energy   = this.menu.getEnergy();

        int mode = this.menu.getMode();
        boolean moonActive = mode == BronzeLunarPanelBlockEntity.MODE_NIGHT
                          || mode == BronzeLunarPanelBlockEntity.MODE_NIGHT_PARTIAL;
        if (moonActive) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + SUN_FRAME_X, y + SUN_FRAME_Y,
                    SUN_UV_X, SUN_UV_Y,
                    SUN_FRAME_W, SUN_FRAME_H,
                    TEX_SIZE, TEX_SIZE);
        }

        graphics.text(this.font, Component.translatable("gui.alaindustrial.energy", energy, capacity),
                x + 30, y + 22, GuiStyle.TEXT, false);
        graphics.text(this.font, outputLine(this.menu.getProductionRate()),
                x + 30, y + 34, GuiStyle.TEXT, false);
        graphics.text(this.font, Component.translatable("gui.alaindustrial.mode", modeLabel(mode)),
                x + 30, y + 46, GuiStyle.TEXT_DIM, false);
    }

    private static Component modeLabel(int mode) {
        String key = switch (mode) {
            case BronzeLunarPanelBlockEntity.MODE_NIGHT         -> "night";
            case BronzeLunarPanelBlockEntity.MODE_NIGHT_WEATHER -> "night_weather";
            case BronzeLunarPanelBlockEntity.MODE_NIGHT_PARTIAL -> "night_partial";
            case BronzeLunarPanelBlockEntity.MODE_NIGHT_SNOW    -> "night_snow";
            default                                              -> "idle_day";
        };
        return Component.translatable("gui.alaindustrial.solar_panel.mode." + key);
    }

}
