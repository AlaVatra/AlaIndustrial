package dev.alaindustrial.client;

import dev.alaindustrial.core.todo.TodoHave;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What the local player carries, counted for the to-do clipboard's "have/need" counters (MOD-905): one tally for
 * every sheet and window on screen, rebuilt only when the inventory changed — or once a second as a backstop for a
 * change that bumped no counter — never once per sheet per frame. Nothing is asked of the server.
 *
 * <p>Every slot of the inventory counts, armour and the off hand included: the counter answers "do I have it on
 * me". What counts as the same item is {@link TodoHave}'s rule.
 */
public final class TodoInventoryTally {

	private static final long BACKSTOP_MS = 1000L;
	private static final TodoHave TALLY = new TodoHave();
	private static @Nullable Player seenPlayer;
	private static int seenChanges = Integer.MIN_VALUE;
	private static long seenAt = Long.MIN_VALUE;

	private TodoInventoryTally() {
	}

	/** The local player's tally, fresh as of the last inventory change; empty with no player. Render thread. */
	public static TodoHave current() {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			seenPlayer = null;
			TALLY.clear();
			return TALLY;
		}
		Inventory inventory = player.getInventory();
		long now = System.currentTimeMillis();
		if (player != seenPlayer || inventory.getTimesChanged() != seenChanges || now - seenAt >= BACKSTOP_MS) {
			seenPlayer = player;
			seenChanges = inventory.getTimesChanged();
			seenAt = now;
			TALLY.clear();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (!stack.isEmpty()) {
					TALLY.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
				}
			}
		}
		return TALLY;
	}
}
