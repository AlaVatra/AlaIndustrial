package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.wearable.JetpackItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Switch the hover mode of the worn Jetpack Mk2 (MOD-977).
 *
 * <p>A key, sent as a packet, for the reason of {@link FluxweaveStepAssistPayload}: the mode lives in a component
 * on the server's copy of the worn stack, and a custom {@code KeyMapping} is not part of vanilla's input sync.
 *
 * <p><b>Nothing is trusted.</b> The payload has no body; the server re-reads the stack in the player's chest slot
 * ({@link JetpackItem#toggleHover}) and does nothing unless it is a jetpack.
 */
public record JetpackHoverTogglePayload() implements CustomPacketPayload {
	public static final Type<JetpackHoverTogglePayload> TYPE =
			new Type<>(Industrialization.id("jetpack_hover_toggle"));

	/** No payload body — the packet's arrival is the whole message. */
	public static final StreamCodec<RegistryFriendlyByteBuf, JetpackHoverTogglePayload> CODEC =
			StreamCodec.unit(new JetpackHoverTogglePayload());

	/**
	 * Flip the hover mode and say which it is now on the action bar, with the copper-bulb click the drill's mode key
	 * uses. A tier-one jetpack says it cannot hover (otherwise the key looks broken); no jetpack at all is silent.
	 */
	public static void handle(JetpackHoverTogglePayload payload, ServerPlayer player) {
		JetpackItem.HoverToggle result = JetpackItem.toggleHover(player);
		switch (result) {
			case NOT_WORN -> {
			}
			case UNSUPPORTED -> player.sendSystemMessage(
					Component.translatable("message.alaindustrial.jetpack.hover.unsupported")
							.withStyle(ChatFormatting.GRAY),
					true);
			case ON, OFF -> {
				boolean on = result == JetpackItem.HoverToggle.ON;
				player.sendSystemMessage(
						Component.translatable(on
								? "message.alaindustrial.jetpack.hover.on"
								: "message.alaindustrial.jetpack.hover.off")
								.withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY),
						true);
				player.level().playSound(null, player.blockPosition(),
						on ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
						SoundSource.PLAYERS, 0.7f, on ? 1.15f : 0.9f);
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
