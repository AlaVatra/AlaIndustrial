package dev.alaindustrial.core.forge;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The server player a machine acts as on Forge (MOD-962, slice 2.5): Forge has no {@code FakePlayer}, so this is
 * our own, with the overrides of NeoForge's {@code FakePlayer} that exist on Forge 26.3 and a connection that
 * swallows everything.
 *
 * <p><b>Two fixes NeoForge makes by patching vanilla, done here after construction</b> (owner decision of
 * 2026-11, MOD-962 research notes; a mixin only if these fail):
 * <ul>
 *   <li><b>Survival.</b> The {@code ServerPlayer} constructor takes the server's default (or forced) game mode; a
 *       creative fake player would break blocks without drops. The mode is set to survival. Vanilla's
 *       {@code changeGameModeForPlayer} also broadcasts a player-info update for this profile, as the breaker's
 *       own "lend survival" step already did on every loader; it happens only when the world's default is not
 *       survival.</li>
 *   <li><b>The owner's advancements.</b> The constructor asks {@code PlayerList.getPlayerAdvancements(this)},
 *       which finds the owner's object by UUID and points it at this fake player, so the owner's rewards and
 *       announcements would go to the fake. It is pointed back at the owner when the owner is online; an
 *       offline owner's object is pointed back by vanilla at the owner's next login. The fake itself answers
 *       {@link #getAdvancements()} with a throw-away object of its own, as NeoForge's fake player does, so a
 *       trigger it fires credits nobody.</li>
 * </ul>
 */
public final class ForgeFakePlayer extends ServerPlayer {

	private final @Nullable PlayerAdvancements ownAdvancements;

	public ForgeFakePlayer(ServerLevel level, GameProfile profile) {
		super(level.getServer(), level, profile, ClientInformation.createDefault());
		this.connection = new FakeNetHandler(level.getServer(), this);
		this.setPermanentlyInvulnerable(true);
		if (this.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
			this.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
		}
		MinecraftServer server = level.getServer();
		ServerPlayer owner = server.getPlayerList().getPlayer(profile.id());
		if (owner != null && owner != this) {
			owner.getAdvancements().setPlayer(owner);
		}
		// Not in PlayerList's map, never saved, no listeners: a trigger the fake fires reaches nobody.
		this.ownAdvancements = new PlayerAdvancements(server.getFixerUpper(), server.getPlayerList(),
				server.getAdvancements(), server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR)
						.resolve("_alaindustrial_fake_player.json"), this);
		this.ownAdvancements.clearTriggers();
	}

	/**
	 * The fake's own throw-away advancements (NeoForge's {@code FakePlayerAdvancements} does the same by patch),
	 * never the owner's object the constructor bound: through that object a trigger would credit the owner while
	 * online, hand the rewards to the fake while offline and stop working after the owner's relog.
	 */
	@Override
	public PlayerAdvancements getAdvancements() {
		return ownAdvancements != null ? ownAdvancements : super.getAdvancements(); // null only inside super()
	}

	@Override
	public void sendSystemMessage(Component message, boolean overlay) {
	}

	@Override
	public void awardStat(Stat<?> stat, int amount) {
	}

	@Override
	public boolean canHarmPlayer(Player player) {
		return false;
	}

	@Override
	public void die(DamageSource source) {
	}

	@Override
	public void tick() {
	}

	@Override
	public void updateOptions(ClientInformation information) {
	}

	@Override
	public OptionalInt openMenu(@Nullable MenuProvider provider) {
		return OptionalInt.empty();
	}

	/**
	 * Forge's {@code IForgeServerPlayer.openMenu(MenuProvider, Consumer)} does not route to
	 * {@link #openMenu(MenuProvider)}: it opens the menu and sends Forge's packet itself, so it is stubbed too
	 * (its {@code BlockPos} overload routes here).
	 */
	@Override
	public void openMenu(MenuProvider provider, Consumer<FriendlyByteBuf> extraDataWriter) {
	}

	@Override
	public void openHorseInventory(AbstractHorse horse, Container container) {
	}

	@Override
	public boolean startRiding(Entity vehicle, boolean force, boolean sendEventAndTriggers) {
		return false;
	}

	/** A play listener that sends nothing and never disconnects; no packet ever arrives on it. */
	private static final class FakeNetHandler extends ServerGamePacketListenerImpl {

		FakeNetHandler(MinecraftServer server, ServerPlayer player) {
			super(server, new FakeConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		@Override
		public void tick() {
		}

		@Override
		public void send(Packet<?> packet) {
		}

		@Override
		public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
		}

		@Override
		public void disconnect(Component message) {
		}

		@Override
		public void disconnect(DisconnectionDetails details) {
		}

		@Override
		public void teleport(double x, double y, double z, float yaw, float pitch) {
		}

		@Override
		public void teleport(PositionMoveRotation position, Set<Relative> relatives) {
		}

		@Override
		public void onDisconnect(DisconnectionDetails details) {
		}
	}

	/**
	 * A connection with no netty channel; {@code ForgeNetworkDispatcher} reads that as "cannot send". Named in
	 * full: a type inherited by this class's supertypes is also called {@code Connection} and shadows the import.
	 */
	private static final class FakeConnection extends net.minecraft.network.Connection {

		FakeConnection() {
			super(PacketFlow.SERVERBOUND);
		}

		@Override
		public void setListenerForServerboundHandshake(PacketListener listener) {
		}
	}
}
