package dev.alaindustrial.client;

import dev.alaindustrial.item.wearable.JetpackItem;
import dev.alaindustrial.item.wearable.JetpackMotion;
import dev.alaindustrial.item.wearable.JetpackTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Client half of the jetpack (MOD-148): player motion is client-authoritative in 26.2, so the
 * velocity change lives here, ticked from every loader's end-of-client-tick hook (the same trio the
 * key handling uses). The server never applies motion — it reads the identical held-jump state from
 * the vanilla input sync and burns the EU / resets the fall distance on its side
 * ({@link JetpackItem#serverFlightTick}); the charge component is network-synchronized, so both
 * sides evaluate {@link JetpackItem#isPowered} against the same charge — and against the server's ceiling, which
 * the client reads from {@link ServerBalance} (MOD-791). Which mode a tick is in is {@link JetpackMotion#mode}, the
 * rule the server asks too.
 *
 * <p>Thrust is an acceleration, not a set speed: gravity keeps pulling, releasing jump starts a
 * normal fall, and the rise speed saturates at {@link JetpackMotion#MAX_RISE_SPEED} so the ascent reads as an
 * engine, not an elevator. The powerless glide clamps only the downward speed — sideways momentum
 * (and an updraft) is left alone.
 *
 * <p>The Mk2 (MOD-977) multiplies the thrust by the server's {@code jetpackMk2RiseMultiplier}, adds air control by
 * its {@code jetpackMk2HorizontalMultiplier} while the engine runs, and hovers: with the mode on and after the first
 * thrust of a flight, a released jump holds the player and lets them sink by the server's
 * {@code jetpackMk2HoverSinkPerTick} (faster with sneak held); a held jump climbs as in a plain flight.
 */
public final class JetpackFlight {

	/**
	 * Client mirror of the server's airborne-thrust session: the powerless glide and the hover only engage if the
	 * engine actually fired during THIS airborne stretch (an empty jetpack must fall like no jetpack
	 * at all — playtest feedback). Cleared on the ground; local player only, so a plain boolean.
	 */
	private static boolean thrustedThisFlight;

	private JetpackFlight() {
	}

	/** One end-of-client-tick step; registered by every client entrypoint. */
	public static void clientTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!(chest.getItem() instanceof JetpackItem)) {
			thrustedThisFlight = false;
			return;
		}
		if (player.onGround()) {
			thrustedThisFlight = false;
			return;
		}
		// Creative/spectator flight and riding have their own movement authorities — stay out.
		if (player.getAbilities().flying || player.isSpectator() || player.isPassenger()) {
			return;
		}
		// The engine only runs airborne; a held jump on the ground is a vanilla jump, the thrust picks up on the
		// next tick. The server's ceiling, not this client's file (MOD-791): the server burns EU only below its own.
		JetpackMotion.Mode mode = JetpackMotion.mode(player.input.keyPresses.jump(), player.input.keyPresses.shift(),
				JetpackItem.isPowered(chest, player, ServerBalance.jetpackMaxY()),
				JetpackItem.hoverActive(chest, player), thrustedThisFlight);
		if (mode == JetpackMotion.Mode.OFF) {
			return;
		}
		if (mode == JetpackMotion.Mode.THRUST) {
			thrustedThisFlight = true;
		}
		JetpackTier tier = JetpackItem.tierOf(chest);
		Vec3 velocity = player.getDeltaMovement();
		double rise = tier.pick(1.0, ServerBalance.jetpackMk2RiseMultiplier());
		double sink = tier.pick(0.0, ServerBalance.jetpackMk2HoverSinkPerTick());
		player.setDeltaMovement(velocity.x, JetpackMotion.verticalSpeed(mode, velocity.y, rise, sink), velocity.z);
		if (mode.engineRuns()) {
			float boost = JetpackMotion.horizontalBoost(
					tier.pick(1.0, ServerBalance.jetpackMk2HorizontalMultiplier()), player.isSprinting());
			if (boost > 0.0f) {
				// The same input vector vanilla's air step used this tick (xxa, zza), so the boost points where
				// the player steers and settles the speed at the multiplier (JetpackMotion.horizontalBoost).
				player.moveRelative(boost, new Vec3(player.xxa, 0.0, player.zza));
			}
		}
		// The client accumulates its own fallDistance; left alone it plays the fall-landing thud on touchdown even
		// though the server dealt no damage (playtest feedback). Reset it in step with the server's reset.
		player.resetFallDistance();
	}
}
