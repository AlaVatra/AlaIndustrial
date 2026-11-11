package dev.alaindustrial.item.tool.forge;

import dev.alaindustrial.item.tool.ElectricHoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.common.ToolAction;

/**
 * Forge {@link ElectricHoeItem} on Minecraft 26.2 (MOD-962): declares the vanilla hoe's tool actions so the hoe can
 * act on this loader. Why the class has to exist: {@link RightClickToolAction}. It extends the common class, so
 * every {@code instanceof} dispatch (energy, tooltips, the upgrade's own logic) still resolves; the twin of
 * NeoForge's {@code ElectricHoeItemNeoForge}.
 */
public class ElectricHoeItemForge extends ElectricHoeItem {

	public ElectricHoeItemForge(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
		return RightClickToolAction.HOE.canPerform(toolAction);
	}

	/** The Forge answer to "would a hoe convert this block?" (MOD-389): {@link RightClickToolAction#wouldTill}. */
	@Override
	protected boolean wouldTill(UseOnContext context) {
		return RightClickToolAction.wouldTill(context);
	}
}
