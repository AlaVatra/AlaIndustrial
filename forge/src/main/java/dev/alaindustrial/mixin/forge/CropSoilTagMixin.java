package dev.alaindustrial.mixin.forge;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Crops on Forge accept the soil the game's own tag names (MOD-962), so a field behaves the same on every loader.
 *
 * <p>Minecraft 26.3 decides where a crop may stand by {@code #minecraft:supports_crops}: farmland by default, and
 * any soil a datapack adds. Fabric and NeoForge keep that rule. Forge 66.1.0 patches both
 * {@code CropBlock.mayPlaceOn} and {@code PitcherCropBlock.mayPlaceOn} back to {@code instanceof FarmlandBlock}
 * (its {@code CropBlock} and {@code PitcherCropBlock} patches), and the tag is never read: wheat neither goes
 * onto a tagged soil by hand nor is planted there by the garden drone, which asks the crop's {@code canSurvive}.
 *
 * <p>This only widens Forge's answer with the vanilla tag. With no datapack the tag holds farmland alone and
 * nothing changes; Forge's own path ({@code canSustainPlant}, a soil that overrides it) keeps working, because
 * it reaches this method through {@code Block.canSustainPlant}. What the widening must not touch is the growth
 * rate, which vanilla reads from a different tag: {@code CropGrowthSoilMixin}.
 */
@Mixin({CropBlock.class, PitcherCropBlock.class})
public abstract class CropSoilTagMixin {

	@ModifyReturnValue(method = "mayPlaceOn", at = @At("RETURN"))
	private boolean alaindustrial$vanillaSoilTag(boolean forgeAnswer, BlockState soil, BlockGetter level,
			BlockPos pos) {
		return forgeAnswer || soil.is(BlockTags.SUPPORTS_CROPS);
	}
}
