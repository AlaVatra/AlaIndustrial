package dev.alaindustrial.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.gametest.visual.VisualWorld;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.wearable.JetpackItem;
import dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor;
import dev.alaindustrial.registry.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Jetpack Mk2 as a player sees it (MOD-977): on the back in third person, charged and drained, and in the
 * hotbar beside the tier-one jetpack, with the Mk2's tooltip charged, drained and with Shift held, and the drained
 * tier-one tooltip to compare its size with (owner's review 2026-11-12). Pictures for review; nothing is asserted.
 */
final class JetpackMk2Stand {

	private static final int MIN_X = 34;
	private static final int MIN_Z = 20;
	private static final int STAND_X = 39;
	private static final int STAND_Z = 25;
	/** Hotbar slot of the charged Mk2, the one whose tooltip is shown. */
	private static final int TOOLTIP_SLOT = 2;
	/** Hotbar slots of the drained tier-one jetpack and the drained Mk2. */
	private static final int TIER_ONE_EMPTY_SLOT = 1;
	private static final int MK2_EMPTY_SLOT = 3;

	private JetpackMk2Stand() {
	}

	static void shoot(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		StandPad.lay(singleplayer, MIN_X, MIN_Z, MIN_X + 10, MIN_Z + 10);
		server.runCommand("clear @p");
		server.runCommand("gamemode survival @p");
		server.runCommand("tp @p " + STAND_X + ".5 " + StandPad.BLOCK_Y + " " + STAND_Z + ".5 180 0");
		context.waitTicks(20);

		for (boolean lit : new boolean[] {true, false}) {
			server.runOnServer(mc -> mc.getPlayerList().getPlayers().get(0)
					.setItemSlot(EquipmentSlot.CHEST, charged(ModContent.JETPACK_MK2.get(), lit)));
			String state = lit ? "lit" : "off";
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			ShotRecorder.capture("worn_jetpack_mk2_" + state + "_back");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			ShotRecorder.capture("worn_jetpack_mk2_" + state + "_front");
		}
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		server.runCommand("clear @p");

		shootHotbar(context, server);
		server.runCommand("clear @p");
		server.runCommand("gamemode creative @p");
		context.waitTicks(3);
	}

	/** Tier one charged and drained, then the Mk2 charged and drained, in hotbar slots 0 to 3. */
	private static void shootHotbar(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(mc -> {
			Inventory inventory = mc.getPlayerList().getPlayers().get(0).getInventory();
			inventory.setItem(0, charged(ModContent.JETPACK.get(), true));
			inventory.setItem(TIER_ONE_EMPTY_SLOT, charged(ModContent.JETPACK.get(), false));
			inventory.setItem(TOOLTIP_SLOT, charged(ModContent.JETPACK_MK2.get(), true));
			inventory.setItem(MK2_EMPTY_SLOT, charged(ModContent.JETPACK_MK2.get(), false));
		});
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(8));
		context.waitTicks(5);
		context.getInput().pressKey(options -> options.keyInventory);
		VisualWorld.awaitContainerScreen(context, "inventory (jetpacks)");
		context.getInput().setCursorPos(4, 4);
		context.waitTicks(2);
		ShotRecorder.capture("item_jetpack_icons");

		hover(context, TOOLTIP_SLOT);
		ShotRecorder.capture("item_jetpack_mk2_tooltip");
		hover(context, MK2_EMPTY_SLOT);
		ShotRecorder.capture("item_jetpack_mk2_tooltip_empty");
		context.getInput().holdKey(InputConstants.KEY_LSHIFT);
		context.waitTicks(3);
		ShotRecorder.capture("item_jetpack_mk2_tooltip_shift");
		context.getInput().releaseKey(InputConstants.KEY_LSHIFT);
		hover(context, TIER_ONE_EMPTY_SLOT);
		ShotRecorder.capture("item_jetpack_mk2_tooltip_tier_one");
		VisualWorld.awaitNoScreen(context);
	}

	/** Puts the cursor over hotbar slot {@code hotbar} of the open inventory and lets the tooltip draw. */
	private static void hover(ClientGameTestContext context, int hotbar) {
		double[] cursor = context.computeOnClient(mc -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
			AbstractContainerScreenAccessor at = (AbstractContainerScreenAccessor) screen;
			double scale = mc.getWindow().getGuiScale();
			for (Slot slot : screen.getMenu().slots) {
				if (slot.container instanceof Inventory && slot.getContainerSlot() == hotbar) {
					return new double[] {(at.alaindustrial$getLeftPos() + slot.x + 8) * scale,
							(at.alaindustrial$getTopPos() + slot.y + 8) * scale};
				}
			}
			throw new AssertionError("[JETPACK] the inventory screen has no slot for hotbar " + hotbar);
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
		context.waitTicks(3);
	}

	private static ItemStack charged(Item jetpack, boolean lit) {
		ItemStack stack = new ItemStack(jetpack);
		long eu = lit ? ItemEnergy.capacity(stack) : 0;
		ItemEnergy.set(stack, eu);
		JetpackItem.refreshWornAsset(stack, eu);
		return stack;
	}
}
