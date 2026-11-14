package dev.alaindustrial.gametest;

import dev.alaindustrial.compat.L3Chunks;
import dev.alaindustrial.gametest.visual.VisualWorld;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.tool.DrillBore;
import dev.alaindustrial.item.tool.DrillUpgrades;
import dev.alaindustrial.registry.ModContent;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MOD-967: the outline of a bored drill's zone reaches the frame.
 *
 * <p>A survival player holds a charged drill with both bore modules and looks at the middle of a 3 x 3 stone
 * wall. With the modules switched off only the aimed stone is outlined; switched on, the four stones of the cross
 * get the game's own block outline too and the four corners do not. The pixel gate is the claim, as in
 * {@link NetworkOverlayStand}: the frame with the zone must differ from the frame without it by far more than two
 * frames without it differ from each other. The two states differ only in the modules' switches — same item,
 * same charge, same HUD readout.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DrillBoreZoneStand {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");
    /** Well above the price of one aimed block and four extras of any drill tier. */
    private static final long CHARGE = 100_000L;

    private DrillBoreZoneStand() {
    }

    public static void check(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        server.runCommand("gamerule doDaylightCycle false");
        server.runCommand("time set day");
        server.runCommand("weather clear");
        // Survival: the drill bores nothing in creative, and a spectator may not build.
        server.runCommand("gamemode survival @p");
        AtomicReference<BlockPos> at = new AtomicReference<>();
        server.runOnServer(mc -> at.set(mc.getPlayerList().getPlayers().get(0).blockPosition()));
        BlockPos p = at.get();
        int x = p.getX();
        int y = p.getY();
        int z = p.getZ();
        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:smooth_stone",
                x - 3, y - 1, z - 5, x + 3, y - 1, z + 1));
        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", x - 3, y, z - 5, x + 3, y + 3, z + 1));
        // The 3 x 3 wall, three blocks in front of the player; the eye (y + 1.62) looks at its middle.
        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone",
                x - 1, y, z - 3, x + 1, y + 2, z - 3));
        server.runCommand(String.format("tp @p %d.5 %d %d.5 180 0", x, y, z));
        hold(server, false);
        VisualWorld.awaitNoScreen(context);
        L3Chunks.waitRender(singleplayer);
        context.waitTicks(40);

        Path offA = VisualStandSupport.takeCleanScreenshot(context, "drill_bore_zone_off_a");
        context.waitTicks(5);
        Path offB = VisualStandSupport.takeCleanScreenshot(context, "drill_bore_zone_off_b");
        hold(server, true);
        context.waitTicks(10);
        Path on = VisualStandSupport.takeCleanScreenshot(context, "drill_bore_zone_on");

        int noise = VisualStandSupport.differingPixels(offA, offB);
        int signal = VisualStandSupport.differingPixels(offA, on);
        LOG.info("[GUITEST][DRILL-ZONE] noise={} signal={}", noise, signal);
        // Leave the player as NetworkOverlayStand did: empty hand, spectator.
        server.runOnServer(mc -> mc.getPlayerList().getPlayers().get(0)
                .setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        server.runCommand("gamemode spectator @p");
        if (signal <= Math.max(200, noise * 10)) {
            throw new AssertionError("MOD-967 the drill bore zone did not reach the frame: " + signal
                    + " pixels changed with the modules on against " + noise + " between two frames with them off");
        }
    }

    /** Puts a charged drill with both bore modules in the main hand, switched on or off. */
    private static void hold(TestServerContext server, boolean modulesOn) {
        server.runOnServer(mc -> {
            ServerPlayer player = mc.getPlayerList().getPlayers().get(0);
            ItemStack drill = new ItemStack(ModContent.ELECTRIC_DRILL.get());
            ItemEnergy.set(drill, CHARGE);
            if (!DrillUpgrades.install(drill, DrillUpgrades.COLUMN_BORE)
                    || !DrillUpgrades.install(drill, DrillUpgrades.ROW_BORE)) {
                throw new AssertionError("fixture error: a bore module did not install on the drill");
            }
            DrillBore.setColumnEnabled(drill, modulesOn);
            DrillBore.setRowEnabled(drill, modulesOn);
            player.setItemInHand(InteractionHand.MAIN_HAND, drill);
        });
    }
}
