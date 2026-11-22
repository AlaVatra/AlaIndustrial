package dev.alaindustrial.item.energy;

import dev.alaindustrial.client.tooltip.ItemTooltip;
import dev.alaindustrial.core.tooltip.TooltipRole;
import dev.alaindustrial.core.tooltip.TooltipUnits;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/**
 * Item form of the geothermal generator: says how much lava a broken generator carries (MOD-998). The machine
 * lines (output, buffer, burn) still come from the block's own description; this adds the one line only the
 * stack knows.
 */
public final class GeothermalGeneratorBlockItem extends BlockItem {
	public GeothermalGeneratorBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	// Soft-deprecated by vanilla, but still the hook for a line computed per stack (see FluidTankBlockItem).
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		Long lava = stack.get(ModDataComponents.STORED_LAVA.get());
		if (lava == null || lava <= 0) {
			return;
		}
		new ItemTooltip()
				.stat(TooltipRole.FLUID, "tooltip.alaindustrial.geothermal_generator.stored_lava",
						TooltipUnits.MILLIBUCKETS, lava)
				.emit(adder);
	}
}
