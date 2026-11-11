package dev.alaindustrial.item.tool.forge;

import dev.alaindustrial.item.tool.HammerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Forge {@link HammerItem}: routes the stack-aware {@code IForgeItem#getCraftingRemainder(ItemStack)} hook
 * (reached through the patched {@code ItemStack#getCraftingRemainder()} from
 * {@code CraftingRecipe.defaultCraftingReminder}, which maps a {@code null} template to an empty slot) to the
 * shared {@link HammerItem#damagedRemainder(ItemStack)} body, as the Fabric and NeoForge hammers do. See
 * {@code docs/blocks/items/forge_hammer.md}.
 */
public class HammerItemForge extends HammerItem {

	public HammerItemForge(Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable ItemStackTemplate getCraftingRemainder(ItemStack stack) {
		return damagedRemainder(stack);
	}
}
