package dev.alaindustrial.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/**
 * The Mob Beacon's search (MOD-968): every mob in the zone cube gets the game's own glowing effect, short and renewed
 * by {@link GlowRenewal}. One static entry point, so the three tiers share it and a gametest can drive one search.
 *
 * <p><b>Why the game's effect.</b> The owner's decision of 2026-11-13: the outline is vanilla glowing, given by the
 * server. Every player who sees the mob sees its outline, on every loader, with no client code; the mod's one
 * precedent is the settlement worker's «Find» ({@code ShiftWorkerLocator}).
 *
 * <p><b>Mobs, not players.</b> The search asks for {@link Mob}: players, armour stands, items and minecarts are not
 * mobs, so they never glow. Hostile, passive, neutral, villagers and golems all do.
 */
public final class MobBeaconGlow {

	private MobBeaconGlow() {
	}

	/**
	 * Gives or renews the glow of every living mob within {@code range} of {@code center} (a cube).
	 *
	 * @return how many mobs had their glow given or renewed by this search
	 */
	public static int sweep(ServerLevel level, BlockPos center, int range, int intervalTicks) {
		AABB zone = new AABB(center).inflate(Math.max(0, range));
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, zone, Mob::isAlive);
		int glowTicks = GlowRenewal.glowTicks(intervalTicks);
		int renewed = 0;
		for (Mob mob : mobs) {
			MobEffectInstance current = mob.getEffect(MobEffects.GLOWING);
			boolean renew = current == null
					? GlowRenewal.shouldRenew(false, false, 0, intervalTicks)
					: GlowRenewal.shouldRenew(true, current.isInfiniteDuration(), current.getDuration(), intervalTicks);
			// No particles and no icon: the outline is the whole message, as in ShiftWorkerLocator.
			if (renew && mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, glowTicks, 0, false, false))) {
				renewed++;
			}
		}
		return renewed;
	}
}
