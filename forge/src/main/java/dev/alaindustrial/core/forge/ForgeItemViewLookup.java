package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.monitor.ItemView;
import dev.alaindustrial.core.monitor.ItemViewLookup;
import dev.alaindustrial.core.monitor.StockTally;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;

/**
 * The read-only storage lookup (MOD-480) on MinecraftForge: the same handler {@link ForgeItemLookup} resolves,
 * read slot by slot. The stack {@code getStackInSlot} returns is never changed, as {@link IItemHandler} demands;
 * a sample matches on item and components, count ignored, as NeoForge's {@code ItemResource.matches} does.
 */
public final class ForgeItemViewLookup implements ItemViewLookup {

	@Override
	public @Nullable ItemView find(Level level, BlockPos pos, @Nullable Direction side) {
		IItemHandler handler = ForgeItemLookup.handler(level, pos, side);
		return handler == null ? null : sink -> tally(handler, sink);
	}

	private static void tally(IItemHandler handler, StockTally sink) {
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			ItemStack stack = handler.getStackInSlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			for (int i = 0; i < sink.size(); i++) {
				if (ItemStack.isSameItemSameComponents(stack, sink.sample(i))) {
					sink.add(i, stack.getCount());
					break;
				}
			}
		}
	}
}
