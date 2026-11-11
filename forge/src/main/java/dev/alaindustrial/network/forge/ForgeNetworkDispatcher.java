package dev.alaindustrial.network.forge;

import dev.alaindustrial.network.NetworkDispatcher;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Forge implementation of the neutral {@link NetworkDispatcher} (MOD-962, slice 2.4): every payload goes out on
 * the one {@link ForgeNetwork} channel.
 *
 * <p>{@code PacketDistributor.SERVER} dereferences {@code Minecraft.getInstance()} only when it is used, so this
 * class stays loadable on a dedicated server, where nothing calls {@link #sendToServer}.
 */
public final class ForgeNetworkDispatcher implements NetworkDispatcher {

	@Override
	public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		ForgeNetwork.channel().send(payload, PacketDistributor.PLAYER.with(player));
	}

	/**
	 * Per channel, not per payload type: Forge records which channels the remote side has, and every payload of
	 * the mod lives in the one channel. A client with another channel version is refused at login anyway (exact
	 * version test), so "has the channel" and "has this payload" agree. NeoForge asks {@code hasChannel(type)}.
	 *
	 * <p>A fake player's connection has no netty channel, and Forge's per-connection state dereferences it, so a
	 * player without one cannot receive anything and answers {@code false} without throwing (MOD-513: callers
	 * such as {@code ConfigSync.sendOnJoin} ask before sending).
	 */
	@Override
	public boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		if (player.connection == null) {
			return false;
		}
		Connection connection = player.connection.getConnection();
		return connection != null && connection.channel() != null
				&& ForgeNetwork.channel().isRemotePresent(connection);
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		ForgeNetwork.channel().send(payload, PacketDistributor.SERVER.noArg());
	}
}
