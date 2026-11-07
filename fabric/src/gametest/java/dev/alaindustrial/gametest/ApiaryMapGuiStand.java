package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;

import dev.alaindustrial.client.screen.ApiaryMapScreen;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.item.apiary.ApiaryMapPoints;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The apiary map's list (MOD-923): a full map with one row in the Nether, the button under the cursor, and an empty
 * map's hint.
 *
 * <p>Built on the client's own menu, like the teleporter remote's stand: the menu reads the map from the player's hand,
 * so a map with rows is put there. The real open path (right-click in the air) and the press itself are covered by the
 * {@code apiary_map_*} game tests.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ApiaryMapGuiStand {

	private ApiaryMapGuiStand() {
	}

	/** Eight rows, the sixth in the Nether; the cursor rests on the third row's button. */
	public static void shoot(ClientGameTestContext context) {
		List<GlobalPos> full = new ArrayList<>();
		int[][] spots = {{-104, 70, 58}, {-98, 71, 61}, {-91, 70, 66}, {-120, 64, 40}, {-77, 68, 80}, {40, 64, -90},
				{1180, 72, -1320}, {-2600, 190, 1800}};
		for (int i = 0; i < spots.length; i++) {
			int[] s = spots[i];
			full.add(GlobalPos.of(i == 5 ? Level.NETHER : Level.OVERWORLD, new BlockPos(s[0], s[1], s[2])));
		}
		shoot(context, "full", full, 2,
				"Apiary map with eight rows: title 'Apiary Map' and '8 / 8' at the top right, rows numbered 1 to 8 "
						+ "with their coordinates, every second row on a lighter band, row 6 adding 'other dimension: "
						+ "minecraft:the_nether' in red before its button, a dark 'Cross out' button at the right of "
						+ "every row inside the panel, the third one lit by the cursor; no text runs over a button");
		shoot(context, "empty", List.of(), -1,
				"An empty apiary map: '0 / 8' at the top right and three hint lines - none written yet, right-click "
						+ "a jar to write it (up to 8), shift+right-click or the button to cross it out - wrapped "
						+ "inside the panel, no buttons");
	}

	private static void shoot(ClientGameTestContext context, String state, List<GlobalPos> rows, int hoverRow,
			String checks) {
		context.runOnClient(mc -> {
			ItemStack map = new ItemStack(ModContent.APIARY_MAP.get());
			map.set(ModDataComponents.APIARY_MAP_POINTS.get(), new ApiaryMapPoints(rows));
			mc.player.getInventory().setSelectedSlot(0);
			mc.player.getInventory().setItem(0, map);
			MenuScreens.create(ModContent.APIARY_MAP_MENU.get(), mc, 0,
					Component.translatable("item.alaindustrial.apiary_map"));
			if (!(mc.gui.screen() instanceof ApiaryMapScreen)) {
				throw new AssertionError("[GUITEST][MOD-923] MenuScreens.create did not open the apiary map for "
						+ state);
			}
		});
		awaitMenuScreen(context);
		double[] cursor = {10, 10};
		context.runOnClient(mc -> {
			if (!(mc.gui.screen() instanceof ApiaryMapScreen screen)) {
				throw new AssertionError("[GUITEST][MOD-923] the apiary map closed before " + state);
			}
			if (screen.getMenu().points().size() != rows.size()) {
				throw new AssertionError("[GUITEST][MOD-923] the menu reads " + screen.getMenu().points().size()
						+ " rows, the map in hand holds " + rows.size());
			}
			if (hoverRow >= 0) {
				double scale = mc.getWindow().getGuiScale();
				// The button sits at the right edge of the panel; probe leftwards until the screen reports it.
				int top = (mc.getWindow().getGuiScaledHeight() - ApiaryMapScreen.PANEL_H) / 2;
				int right = (mc.getWindow().getGuiScaledWidth() + ApiaryMapScreen.PANEL_W) / 2;
				for (int x = right - 8; x > right - 80; x--) {
					int y = top + 20 + hoverRow * 16 + 8;
					if (screen.buttonAt(x, y) == hoverRow) {
						cursor[0] = x * scale;
						cursor[1] = y * scale;
						break;
					}
				}
			}
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
		context.waitTicks(1);
		ShotRecorder.captureScreen("apiary_map", state, ShotRecorder.rules("R-GUI-01", "R-GUI-03"), checks);
	}
}
