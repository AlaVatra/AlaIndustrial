package dev.alaindustrial.mixin;

import dev.alaindustrial.attachment.PlayerDataCarryOver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MOD-962: the loader-neutral copy of a player's mod data ({@link PlayerDataCarryOver}) rides on the player's own
 * save and load, on every loader — both methods are vanilla's, have one return each on all three loaders (javap of
 * the merged, NeoForge-patched and Forge-recompiled 26.3 jars), and receive the root of the player's tag.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDataCarryOverMixin implements PlayerDataCarryOver.PendingHolder {

	@Unique
	private @Nullable Object alaindustrial$pendingPlayerData;

	@Override
	public @Nullable Object alaindustrial$pendingPlayerData() {
		return alaindustrial$pendingPlayerData;
	}

	@Override
	public void alaindustrial$pendingPlayerData(@Nullable Object pending) {
		alaindustrial$pendingPlayerData = pending;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"), require = 1)
	private void alaindustrial$writeNeutralCopy(ValueOutput output, CallbackInfo ci) {
		PlayerDataCarryOver.write((ServerPlayer) (Object) this, output);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"), require = 1)
	private void alaindustrial$readNeutralCopy(ValueInput input, CallbackInfo ci) {
		PlayerDataCarryOver.read((ServerPlayer) (Object) this, input);
	}
}
