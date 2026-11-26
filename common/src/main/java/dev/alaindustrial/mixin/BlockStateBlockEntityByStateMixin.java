package dev.alaindustrial.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.alaindustrial.block.BlockEntityByState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code BlockState.hasBlockEntity()} per state for a {@link BlockEntityByState} block (MOD-1025: the quarry's socket
 * foundation carries a block entity only while it is the socket, so that Forge, which attaches capabilities to block
 * entities only, can lend the controller's inventory and tank to another mod's pipe as Fabric and NeoForge do by
 * block). Every other block answers as vanilla: the extra test runs only where vanilla already said yes.
 *
 * <p>Registered in {@code alaindustrial.mixins.json} (required): without it every foundation would count as a
 * block-entity block on every loader.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBlockEntityByStateMixin {

	@Shadow
	public abstract Block getBlock();

	@Shadow
	protected abstract BlockState asState();

	@ModifyReturnValue(method = "hasBlockEntity", at = @At("RETURN"))
	private boolean alaindustrial$byState(boolean has) {
		return has && (!(getBlock() instanceof BlockEntityByState byState) || byState.hasBlockEntity(asState()));
	}
}
