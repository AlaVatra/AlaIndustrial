package dev.alaindustrial.item.tool;

import dev.alaindustrial.core.tooltip.TooltipRole;
import dev.alaindustrial.client.tooltip.ItemTooltip;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Magnet filter module (MOD-592): goes into a module slot on the electromagnet's own screen and decides
 * which loose items the magnet pulls. Its settings live on this stack ({@link MagnetFilter}), so the
 * filter keeps its list when it is taken out and fitted to another magnet.
 *
 * <p>Crafted from the blank module — the common base every future module starts from.
 */
public class MagnetFilterModuleItem extends Item {

	public MagnetFilterModuleItem(Properties properties) {
		super(properties);
	}

	// MOD-498 — the only hook for tooltip lines an item computes itself (see MagnetItem).
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		// Style 09b (MOD-958): what it is for is its purpose line; then the filter's settings and where it goes.
		String base = getDescriptionId();
		MagnetFilter filter = MagnetFilter.of(stack);
		new ItemTooltip()
				.stat(TooltipRole.MODULE, filter.allowList() ? base + ".mode_allow" : base + ".mode_deny", null)
				.stat(TooltipRole.MODULE, base + ".match_" + filter.match().key(), null)
				.info(base + ".entries", filter.filled(), MagnetFilter.CELLS)
				.key(base + ".hint")
				.emit(adder);
	}
}
