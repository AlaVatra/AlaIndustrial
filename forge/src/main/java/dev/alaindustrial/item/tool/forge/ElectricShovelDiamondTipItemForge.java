package dev.alaindustrial.item.tool.forge;

import dev.alaindustrial.item.tool.ElectricShovelDiamondTipItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolAction;

/**
 * Forge {@link ElectricShovelDiamondTipItem} on Minecraft 26.2 (MOD-962): declares the vanilla shovel's tool actions
 * so the shovel upgrade can act on this loader. Why the class has to exist: {@link RightClickToolAction}. It extends
 * the common class, so every {@code instanceof} dispatch (energy, tooltips, the upgrade's own logic) still resolves;
 * the twin of NeoForge's {@code ElectricShovelDiamondTipItemNeoForge}.
 */
public class ElectricShovelDiamondTipItemForge extends ElectricShovelDiamondTipItem {

	public ElectricShovelDiamondTipItemForge(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
		return RightClickToolAction.SHOVEL.canPerform(toolAction);
	}
}
