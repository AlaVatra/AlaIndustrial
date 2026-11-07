package dev.alaindustrial.menu;

import dev.alaindustrial.item.apiary.ApiaryMapItem;
import dev.alaindustrial.item.apiary.ApiaryMapPoints;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The apiary map's list (MOD-923): its rows and a "cross out" button on each.
 *
 * <p>Like the teleporter remote's menu it holds no slots and no stack: it reads the map out of the player's hand on
 * both sides, so nothing travels through the open packet (which differs per loader). A press is vanilla's own
 * container-button packet carrying the row index ({@link #clickMenuButton}); the mod needs no payload of its own
 * (owner decision 2026-11-07, item 17), and the server re-reads the live map rather than trusting the client.
 */
public class ApiaryMapMenu extends AbstractContainerMenu {

	private final Player player;

	public ApiaryMapMenu(int syncId, Inventory playerInventory) {
		super(ModContent.APIARY_MAP_MENU.get(), syncId);
		this.player = playerInventory.player;
	}

	/** The live map — either hand, main first — or empty once the player stops holding one. */
	public ItemStack map() {
		return ApiaryMapItem.heldMap(player);
	}

	/** The rows on the live map, read fresh on every call. */
	public ApiaryMapPoints points() {
		ItemStack stack = map();
		return stack.isEmpty() ? ApiaryMapPoints.EMPTY : ApiaryMapItem.points(stack);
	}

	/** The player whose hand holds the map; the screen asks it which dimension "here" is. */
	public Player player() {
		return player;
	}

	/** Crosses out the row {@code buttonId}; an index the live map does not have is refused. */
	@Override
	public boolean clickMenuButton(Player clicker, int buttonId) {
		ItemStack stack = map();
		ApiaryMapPoints points = points();
		if (stack.isEmpty() || !points.isValidIndex(buttonId)) {
			return false;
		}
		stack.set(ModDataComponents.APIARY_MAP_POINTS.get(), points.without(buttonId));
		if (clicker instanceof ServerPlayer serverPlayer) {
			// No slot of this menu tracks the hand, so the edited stack goes back through the inventory menu, which
			// keeps its synchronizer while another container is open (the remote's menu does the same).
			serverPlayer.inventoryMenu.broadcastChanges();
		}
		return true;
	}

	/** No slots: nothing to shift-click. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	/** Open only while a hand still holds a map. */
	@Override
	public boolean stillValid(Player player) {
		return !map().isEmpty();
	}
}
