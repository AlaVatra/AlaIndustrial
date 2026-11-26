package dev.alaindustrial.mixin;

import dev.alaindustrial.block.KokSagyzBlock;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bees take the mature kok sagyz for a flower (MOD-979). {@code Bee.attractsBees(BlockState)} is the one test
 * vanilla asks both when a bee looks for a new flower ({@code BeePollinateGoal.findNearbyFlower}) and when it
 * checks the one it remembers ({@code ValidateFlowerGoal}); it reads {@code #minecraft:bee_attractive}, which
 * names blocks and so cannot single out a growth stage. The stage rule lives in
 * {@link KokSagyzBlock#attractsBees}. Loader-neutral; optional (config
 * {@code common/alaindustrial.compat-optional.mixins.json}): a bee-overhaul mod that replaces the method costs
 * this flower, not the game.
 */
@Mixin(Bee.class)
public abstract class BeeKokSagyzFlowerMixin {

	@Inject(method = "attractsBees(Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"),
			cancellable = true)
	private static void alaindustrial$matureKokSagyzIsAFlower(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (state.getBlock() instanceof KokSagyzBlock) {
			cir.setReturnValue(KokSagyzBlock.attractsBees(state));
		}
	}
}
