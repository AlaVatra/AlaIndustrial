package dev.alaindustrial.mixin.forge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the growth rate of a crop on Forge what it is in the game (MOD-962), the other half of
 * {@code CropSoilTagMixin}.
 *
 * <p>Vanilla 26.3 has two tags: {@code #minecraft:supports_crops} says where a crop may stand,
 * {@code #minecraft:grows_crops} says which soil under and around it speeds the growth. Forge 66.1.0 replaced
 * the second check in {@code CropBlock.getGrowthSpeed} with {@code BlockState.canSustainPlant}, and
 * {@code Block.canSustainPlant} asks the crop's {@code mayPlaceOn} first. Once {@code mayPlaceOn} is widened by
 * the first tag, a soil that is only in {@code supports_crops} would speed crops up on Forge and on no other
 * loader.
 *
 * <p>So the soil bonus is refused to a soil that is tagged {@code supports_crops}, is not farmland and is not in
 * {@code grows_crops} - the vanilla rule for a tagged soil, whichever way Forge's {@code canSustainPlant} answered
 * (a Forge mod's soil that overrides it but carries only the first tag loses the bonus it had on Forge alone).
 * Every other answer of Forge stands.
 */
@Mixin(CropBlock.class)
public abstract class CropGrowthSoilMixin {

	@WrapOperation(method = "getGrowthSpeed", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;canSustainPlant"
					+ "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
					+ "Lnet/minecraft/core/Direction;Lnet/minecraftforge/common/IPlantable;)Z"))
	private static boolean alaindustrial$soilBonusFollowsVanillaTag(BlockState soil, BlockGetter level,
			BlockPos pos, Direction side, IPlantable crop, Operation<Boolean> original) {
		if (!original.call(soil, level, pos, side, crop)) {
			return false;
		}
		boolean letInByWideningOnly = soil.is(BlockTags.SUPPORTS_CROPS)
				&& !(soil.getBlock() instanceof FarmlandBlock) && !soil.is(BlockTags.GROWS_CROPS);
		return !letInByWideningOnly;
	}
}
