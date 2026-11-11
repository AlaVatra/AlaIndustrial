package dev.alaindustrial.item.tool.forge;

import dev.alaindustrial.item.tool.ElectricShovelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolAction;

/**
 * Forge {@link ElectricShovelItem} on Minecraft 26.2 (MOD-962): declares the vanilla shovel's tool actions so the
 * shovel can act on this loader. Why the class has to exist: {@link RightClickToolAction}. It extends the common
 * class, so every {@code instanceof} dispatch (energy, tooltips, the upgrade's own logic) still resolves; the twin
 * of NeoForge's {@code ElectricShovelItemNeoForge}.
 */
public class ElectricShovelItemForge extends ElectricShovelItem {

	public ElectricShovelItemForge(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
		return RightClickToolAction.SHOVEL.canPerform(toolAction);
	}
}
