package dev.alaindustrial.mixin.forge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.alaindustrial.Industrialization;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets the mod's items be used on a block with no player on Forge, as on the other loaders (MOD-962).
 *
 * <p>A machine of another mod may build a {@link UseOnContext} without a player. Vanilla, Fabric and NeoForge
 * hand it to {@code Item.useOn} and skip only the player's part (the adventure-mode check, the statistic).
 * Forge 66.1.0 routes the server side of {@code ItemStack.useOn} through
 * {@code ForgeHooks.onPlaceItemIntoWorld}, which reads {@code player.getItemInHand} after any consuming use and
 * throws for a context without a player, once the item has already done its work.
 *
 * <p>For an item of this mod and no player the hook is skipped and the vanilla path is taken. With a player, and
 * for every other mod's item, Forge's hook runs unchanged: its block snapshots and place events need a player
 * anyway.
 */
@Mixin(ItemStack.class)
public abstract class PlayerlessUseMixin {

	@WrapOperation(method = "useOn", at = @At(value = "INVOKE",
			target = "Lnet/minecraftforge/common/ForgeHooks;onPlaceItemIntoWorld"
					+ "(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
	private InteractionResult alaindustrial$ownItemWithoutPlayer(UseOnContext context,
			Operation<InteractionResult> original) {
		ItemStack stack = (ItemStack) (Object) this;
		if (context.getPlayer() == null && Industrialization.MOD_ID.equals(
				BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
			return stack.getItem().useOn(context);
		}
		return original.call(context);
	}
}
