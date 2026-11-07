package dev.alaindustrial.item;

import dev.alaindustrial.core.tooltip.TooltipRole;
import dev.alaindustrial.client.tooltip.ItemTooltip;
import dev.alaindustrial.client.ServerBalance;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The carbon briquette (MOD-590): fired in the alloy smelter, and split into ceramic plates by the
 * quench press.
 *
 * <p><b>The briquette has no right-click behaviour, and that is the point (MOD-594).</b> It used to
 * quench one briquette by hand against any water source — no build, no redstone, one plate less. That
 * was a deliberate design decision in MOD-590, not an accident, and the owner reversed it: plates that
 * can be had by clicking any puddle make the press decoration rather than a machine. The class
 * therefore overrides nothing but the tooltip; the split lives entirely in the piston hook.
 *
 * @see QuenchPress
 */
public class CarbonBriquetteItem extends Item {

	public CarbonBriquetteItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> lines, TooltipFlag flag) {
		// Style 09b (MOD-958): the first hint is the purpose line.
		new ItemTooltip().info("item.alaindustrial.carbon_briquette.hint2")
				.detail(TooltipRole.INFO, "item.alaindustrial.carbon_briquette.press", null,
						ServerBalance.ceramicPlatesFromPress(), ServerBalance.ceramicPressRedstoneCost())
				.emit(lines);
	}
}
