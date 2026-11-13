package dev.alaindustrial.menu.todo;

import dev.alaindustrial.compat.ServerDrops;
import dev.alaindustrial.core.todo.TodoList;
import dev.alaindustrial.item.misc.TodoClipboardItem;
import dev.alaindustrial.registry.ModContent;
import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The list of a to-do clipboard held in a hand, as the window edits it (MOD-905, owner decision 2026-11-12: a right
 * click in the air opens the same window for the list on the item).
 *
 * <p><b>The holder owns it here.</b> Whoever holds the clipboard writes on it, the access step included; the owner
 * of a hung clipboard is whoever hangs it, so nothing about the owner is kept on the item.
 *
 * <p><b>An edit goes into the stack the window was opened with, or nowhere.</b> The window holds that very stack
 * object and the hand it was in; {@link #stillValid} asks that the same object is still in that hand. A player who
 * switches slots, drops the clipboard or picks it up in the window gets the window closed, and an edit already on
 * its way is refused rather than written into whatever is in the hand now — a deferred action writes to the item it
 * started with (the project's rule).
 *
 * <p><b>A stack of blank clipboards</b> keeps one in the hand on the first write and puts the rest back into the
 * inventory: a written clipboard does not stack, and the list must land on exactly one.
 */
public final class HeldTodoClipboard implements TodoListSource {

	private final Player holder;
	private final InteractionHand hand;
	private final ItemStack stack;
	private int revision;

	public HeldTodoClipboard(Player holder, InteractionHand hand, ItemStack stack) {
		this.holder = holder;
		this.hand = hand;
		this.stack = stack;
	}

	@Override
	public TodoList list() {
		return TodoClipboardItem.listOf(stack);
	}

	@Override
	public int revision() {
		return revision;
	}

	@Override
	public UUID owner() {
		return holder.getUUID();
	}

	@Override
	public String ownerName() {
		return holder.getName().getString();
	}

	@Override
	public boolean stillValid(Player player) {
		return player == holder && player.isAlive() && player.getItemInHand(hand) == stack
				&& stack.is(ModContent.TODO_CLIPBOARD_ITEM.get());
	}

	@Override
	public void store(TodoList next) {
		if (stack.getCount() > 1 && !next.isEmpty()) {
			ItemStack rest = stack.split(stack.getCount() - 1);
			TodoClipboardItem.writeList(stack, next);
			// Inventory.add, not the give-back facade: the open window's own sync carries the slots to the client,
			// and a written clipboard in the hand no longer merges with the blanks, so they land in other slots.
			if (!holder.getInventory().add(rest)) {
				ServerDrops.drop(holder, rest);
			}
		} else {
			TodoClipboardItem.writeList(stack, next);
		}
		revision++;
	}
}
