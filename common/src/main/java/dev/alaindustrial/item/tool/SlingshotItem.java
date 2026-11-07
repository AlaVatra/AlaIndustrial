package dev.alaindustrial.item.tool;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.TorchProjectile;
import dev.alaindustrial.item.ToolConfig;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The slingshot (MOD-871): a first-tier wooden weapon that fires torches. It draws like a bow — the power of the
 * shot grows with the time held, by vanilla {@link BowItem#getPowerForTime} — and fires one torch of
 * {@link #AMMO} as a {@link TorchProjectile}, which lands as a placed torch.
 *
 * <p>It extends {@link ProjectileWeaponItem}, because only then does {@code Player.getProjectile} look for its
 * ammunition: the hands first (the off hand before the main hand), then the inventory. The shot itself is the
 * parent's {@code draw} + {@code shoot}: exactly one torch is split off the found stack (none for a creative player,
 * who fires an intangible copy), and the slingshot wears by one. A creative player with no torch does not fire —
 * {@code getProjectile} would hand him an arrow, which is not a torch.
 *
 * <p>The draw frames are item-model data (a {@code range_dispatch} on {@code minecraft:use_duration} in
 * {@code items/slingshot.json}); {@link ItemUseAnimation#BOW} gives the two-handed pose in third person. Sounds
 * are vanilla: the bow's shot here, the torch's own placement sound on landing.
 */
public class SlingshotItem extends ProjectileWeaponItem {

	/** Every torch the slingshot fires; a datapack or another mod adds its own torch to this tag. */
	public static final TagKey<Item> AMMO = TagKey.create(Registries.ITEM, Industrialization.id("slingshot_ammo"));

	private static final Predicate<ItemStack> IS_AMMO = stack -> stack.is(AMMO);

	/** Shots before it breaks: a wooden weapon, below a bow's 384. */
	public static final int DURABILITY = 256;

	/** Ticks of draw to full power, as the bow ({@code BowItem.MAX_DRAW_DURATION}). */
	private static final int USE_DURATION = 72_000;

	/** Range the vanilla goals read; a little under the bow's 15, as the slingshot's arc is. */
	private static final int DEFAULT_RANGE = 12;

	public SlingshotItem(Properties properties) {
		super(properties);
	}

	/** The item's properties: one per slot, {@link #DURABILITY} shots. */
	public static Properties slingshotProperties(Properties properties) {
		return properties.durability(DURABILITY);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!IS_AMMO.test(player.getProjectile(player.getItemInHand(hand)))) {
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public boolean releaseUsing(ItemStack slingshot, Level level, LivingEntity entity, int remainingTime) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		ItemStack torch = player.getProjectile(slingshot);
		if (!IS_AMMO.test(torch)) {
			return false;
		}
		float power = BowItem.getPowerForTime(getUseDuration(slingshot, entity) - remainingTime);
		if (power < 0.1F) {
			return false;
		}
		List<ItemStack> fired = draw(slingshot, torch, player);
		if (level instanceof ServerLevel serverLevel && !fired.isEmpty()) {
			shoot(serverLevel, player, player.getUsedItemHand(), slingshot, fired,
					power * (float) ToolConfig.slingshotMaxVelocity, 1.0F, false, null);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
				1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
		player.awardStat(Stats.ITEM_USED.get(this));
		return true;
	}

	@Override
	protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack torch,
			boolean isCrit) {
		return new TorchProjectile(level, shooter, torch);
	}

	@Override
	protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float power,
			float uncertainty, float angle, @Nullable LivingEntity target) {
		projectile.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot() + angle, 0.0F, power, uncertainty);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return USE_DURATION;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BOW;
	}

	@Override
	public Predicate<ItemStack> getAllSupportedProjectiles() {
		return IS_AMMO;
	}

	@Override
	public int getDefaultProjectileRange() {
		return DEFAULT_RANGE;
	}
}
