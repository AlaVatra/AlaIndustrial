package dev.alaindustrial.item;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * The item of a withdrawn block (MOD-1031): it stays registered so a chest or an inventory saved before the
 * withdrawal keeps it, and it turns into its successor the moment a player carries it — same count, same
 * components. Placed straight from a chest, the block itself puts down the successor.
 */
public class WithdrawnBlockItem extends BlockItem {
	private final Supplier<? extends ItemLike> successor;

	public WithdrawnBlockItem(Block block, Supplier<? extends ItemLike> successor, Properties properties) {
		super(block, properties);
		this.successor = successor;
	}

	/** The stack {@code stack} turns into: its successor, with the same count and components. */
	public ItemStack successorOf(ItemStack stack) {
		return stack.transmuteCopy(successor.get(), stack.getCount());
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		if (!(entity instanceof Player player)) {
			return;
		}
		// The tick names no slot index, so find the slot by identity: it is this very stack object.
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i) == stack) {
				inventory.setItem(i, successorOf(stack));
				return;
			}
		}
	}
}
