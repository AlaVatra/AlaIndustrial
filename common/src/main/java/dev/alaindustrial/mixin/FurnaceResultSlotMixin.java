package dev.alaindustrial.mixin;

import dev.alaindustrial.block.entity.IronFurnaceBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pays the Iron Furnace's smelting experience when a player takes from its result slot (MOD-971).
 *
 * <p>The iron furnace opens a stock furnace menu, whose result slot pays experience only to a vanilla furnace
 * block entity. This hook adds the same payout for {@link IronFurnaceBlockEntity}. Both take paths of the slot
 * end here: a click ({@code onTake}) and a shift-click ({@code onQuickCraft}, then {@code onTake} again; the
 * second call finds the bank empty). Automation never goes through a slot, so it pays nothing. Injected at the
 * tail, after any loader's own smelted event. Loader-neutral (config in
 * {@code common/alaindustrial.compat-optional.mixins.json}): if another mod rewrites this method, the injection
 * is skipped and the furnace only stops paying experience.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {

	@Shadow
	@Final
	private Player player;

	@Inject(method = "checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
	private void alaindustrial$payIronFurnaceExperience(ItemStack carried, CallbackInfo ci) {
		if (((Slot) (Object) this).container instanceof IronFurnaceBlockEntity furnace
				&& player instanceof ServerPlayer serverPlayer) {
			furnace.awardUsedRecipesAndPopExperience(serverPlayer);
		}
	}
}
