package dev.alaindustrial.entity;

import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.registry.ModEntityTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A torch fired from the slingshot (MOD-871). It flies like a snowball, drawn by {@code ThrownItemRenderer} as the
 * torch's icon with a trail of flame and smoke, and on landing either becomes a placed torch
 * ({@link TorchLanding}) or drops as an item where it is. A mob it hits takes {@code ToolConfig.slingshotHitDamage}
 * and burns for {@code ToolConfig.slingshotIgniteSeconds}; the torch then drops. A torch still in the air after
 * {@link #MAX_FLIGHT_TICKS} drops too.
 *
 * <p>The carried stack is exactly the one torch the shot spent ({@code ProjectileWeaponItem.useAmmo}). A shot from
 * a creative player carries {@code DataComponents.INTANGIBLE_PROJECTILE} — nothing was spent — and so drops nothing
 * when it does not land, the same rule a creative player's arrow follows.
 */
public class TorchProjectile extends ThrowableItemProjectile {

	/** Ten seconds in the air: longer than any arc a full draw can fly, short enough not to linger. */
	public static final int MAX_FLIGHT_TICKS = 200;

	public TorchProjectile(EntityType<? extends TorchProjectile> type, Level level) {
		super(type, level);
	}

	public TorchProjectile(Level level, LivingEntity shooter, ItemStack torch) {
		super(ModEntityTypes.TORCH_PROJECTILE.type(), shooter, level, torch);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.TORCH;
	}

	@Override
	public void tick() {
		super.tick();
		if (!isAlive()) {
			return;
		}
		Level level = level();
		if (level.isClientSide()) {
			level.addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
			if (tickCount % 2 == 0) {
				level.addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
			}
		} else if (tickCount > MAX_FLIGHT_TICKS && level instanceof ServerLevel serverLevel) {
			dropTorch(serverLevel);
			discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Entity target = hit.getEntity();
		float damage = (float) ToolConfig.slingshotHitDamage;
		if (damage > 0.0F) {
			target.hurtServer(serverLevel, damageSources().thrown(this, getOwner()), damage);
		}
		float seconds = (float) ToolConfig.slingshotIgniteSeconds;
		if (seconds > 0.0F) {
			target.igniteForSeconds(seconds);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (!(level() instanceof ServerLevel serverLevel) || !isAlive()) {
			return;
		}
		boolean placed = hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit
				&& TorchLanding.place(serverLevel, this, blockHit);
		if (!placed) {
			dropTorch(serverLevel);
		}
		discard();
	}

	/** Drops the carried torch where the projectile is; a creative shot's torch was never spent and is not dropped. */
	private void dropTorch(ServerLevel level) {
		ItemStack torch = getItem();
		if (torch.isEmpty() || torch.has(DataComponents.INTANGIBLE_PROJECTILE)) {
			return;
		}
		ItemStack drop = torch.copyWithCount(1);
		spawnAtLocation(level, drop);
	}
}
