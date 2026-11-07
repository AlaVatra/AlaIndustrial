package dev.alaindustrial.gametest;

import dev.alaindustrial.entity.TorchProjectile;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.tool.SlingshotItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The slingshot and its torch (MOD-871): where a fired torch lands, when it drops instead, what a mob takes, and
 * that a shot spends exactly one torch. Every landing case flies a real {@link TorchProjectile} a block or two
 * into its target, so the path under test is the projectile's own hit, not a call into the placement code.
 */
public final class SlingshotScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(SlingshotScenarios::topFaceStandsAVanillaTorch, "slingshot_top_face_standing_torch")
						.ticks(40),
				RosterEntry.of(SlingshotScenarios::topFaceStandsAUraniumTorch, "slingshot_top_face_uranium_torch")
						.ticks(40),
				RosterEntry.of(SlingshotScenarios::sideFaceHangsAVanillaTorch, "slingshot_side_face_wall_torch")
						.ticks(40),
				RosterEntry.of(SlingshotScenarios::sideFaceHangsAUraniumTorch, "slingshot_side_face_uranium_wall_torch")
						.ticks(40),
				RosterEntry.of(SlingshotScenarios::ceilingDropsTheTorch, "slingshot_ceiling_drops_torch").ticks(40),
				RosterEntry.of(SlingshotScenarios::vanillaTorchInWaterDrops, "slingshot_water_vanilla_torch_drops")
						.ticks(60),
				RosterEntry.of(SlingshotScenarios::uraniumTorchInWaterIsLogged, "slingshot_water_uranium_torch_logged")
						.ticks(60),
				RosterEntry.of(SlingshotScenarios::mobTakesDamageAndBurns, "slingshot_mob_damage_fire_drop").ticks(40),
				RosterEntry.of(SlingshotScenarios::torchStillFlyingDropsAfterTimeout, "slingshot_timeout_drops_torch")
						.ticks(260),
				RosterEntry.of(SlingshotScenarios::offlineShooterDropsTheTorch, "slingshot_offline_shooter_drops")
						.ticks(40),
				RosterEntry.of(SlingshotScenarios::adventureModeDropsTheTorch, "slingshot_adventure_mode_drops")
						.ticks(40),
				RosterEntry.of(SlingshotScenarios::shotSpendsExactlyOneTorch, "slingshot_shot_spends_one_torch"),
				RosterEntry.of(SlingshotScenarios::creativeShotSpendsNothing, "slingshot_creative_spends_nothing"),
				RosterEntry.of(SlingshotScenarios::noTorchNoShot, "slingshot_no_torch_no_shot"),
				RosterEntry.of(SlingshotScenarios::everyTorchItemIsAmmo, "slingshot_every_torch_is_ammo"));

		private Roster() {}
	}

	private SlingshotScenarios() {}

	/** The block every landing case aims at. */
	private static final BlockPos TARGET = new BlockPos(3, 2, 3);
	/** Speed of the test shots: a block or two of flight, so gravity bends nothing. */
	private static final double SPEED = 0.6;

	// ── where it lands ────────────────────────────────────────────────────────────────────────────

	/**
	 * A torch that hits the top of a block stands on it.
	 *
	 * @implements MOD-871 — top face: standing torch.
	 */
	public static void topFaceStandsAVanillaTorch(GameTestHelper helper) {
		landsOnTop(helper, new ItemStack(Items.TORCH), Blocks.TORCH);
	}

	/**
	 * The enriched uranium torch lands by the same rule, as its own block.
	 *
	 * @implements MOD-871 — top face: standing torch, our torch too.
	 */
	public static void topFaceStandsAUraniumTorch(GameTestHelper helper) {
		landsOnTop(helper, new ItemStack(ModContent.ENRICHED_URANIUM_TORCH_ITEM.get()),
				ModContent.ENRICHED_URANIUM_TORCH.get());
	}

	/**
	 * A torch that hits the side of a block hangs on that side — even with a floor under the cell, where a placement
	 * that asked "down" first (vanilla {@code DirectionalPlaceContext}) would have stood it on the floor.
	 *
	 * @implements MOD-871 — side face: wall torch, by the hit face and not by the shooter's look.
	 */
	public static void sideFaceHangsAVanillaTorch(GameTestHelper helper) {
		landsOnSide(helper, new ItemStack(Items.TORCH), Blocks.WALL_TORCH);
	}

	/** @implements MOD-871 — side face: wall torch, our torch too. */
	public static void sideFaceHangsAUraniumTorch(GameTestHelper helper) {
		landsOnSide(helper, new ItemStack(ModContent.ENRICHED_URANIUM_TORCH_ITEM.get()),
				ModContent.ENRICHED_URANIUM_WALL_TORCH.get());
	}

	/**
	 * No torch hangs from a ceiling: a hit on the bottom face drops the torch and leaves the cell empty.
	 *
	 * @implements MOD-871 — no support: the torch drops as an item.
	 */
	public static void ceilingDropsTheTorch(GameTestHelper helper) {
		BlockPos ceiling = TARGET.above(2);
		helper.setBlock(ceiling, Blocks.STONE);
		BlockPos below = ceiling.below();
		fire(helper, AlaGameTestHelper.survivalPlayer(helper), new ItemStack(Items.TORCH),
				Vec3.atCenterOf(below.below()), new Vec3(0, SPEED, 0));
		expectDropped(helper, below, Items.TORCH);
	}

	/**
	 * A vanilla torch would put still water out, so it does not land there: it drops, and the water stays.
	 *
	 * @implements MOD-871 — a vanilla torch that lands in water drops.
	 */
	public static void vanillaTorchInWaterDrops(GameTestHelper helper) {
		BlockPos pool = waterPool(helper);
		fire(helper, AlaGameTestHelper.survivalPlayer(helper), new ItemStack(Items.TORCH),
				Vec3.atCenterOf(pool.above()), new Vec3(0, -SPEED, 0));
		helper.succeedWhen(() -> {
			assertNoProjectile(helper);
			helper.assertTrue(helper.getBlockState(pool).is(Blocks.WATER),
					"the water cell holds " + helper.getBlockState(pool) + ", expected the untouched water");
			assertOneDrop(helper, pool, Items.TORCH);
		});
	}

	/**
	 * The enriched uranium torch lands in still water, logged with it, as when placed by hand.
	 *
	 * @implements MOD-871 — our torch lands in water by its own block's rules (logged).
	 */
	public static void uraniumTorchInWaterIsLogged(GameTestHelper helper) {
		BlockPos pool = waterPool(helper);
		fire(helper, AlaGameTestHelper.survivalPlayer(helper),
				new ItemStack(ModContent.ENRICHED_URANIUM_TORCH_ITEM.get()), Vec3.atCenterOf(pool.above()),
				new Vec3(0, -SPEED, 0));
		helper.succeedWhen(() -> {
			assertNoProjectile(helper);
			BlockState state = helper.getBlockState(pool);
			helper.assertTrue(state.is(ModContent.ENRICHED_URANIUM_TORCH.get())
					&& state.getValue(BlockStateProperties.WATERLOGGED),
					"the water cell holds " + state + ", expected a waterlogged enriched uranium torch");
			assertNoDrop(helper, pool);
		});
	}

	// ── what a mob takes ──────────────────────────────────────────────────────────────────────────

	/**
	 * A mob hit by the torch takes the configured damage from the thrown torch and burns; the torch drops. The damage
	 * is read in the first ticks after the hit, while the hurt cooldown still swallows the first fire damage, and
	 * its source is checked too: with no hit damage at all, the fire alone would also cost the pig one point.
	 *
	 * @implements MOD-871 — a mob hit: slingshotHitDamage, ignition for slingshotIgniteSeconds, the torch drops.
	 */
	public static void mobTakesDamageAndBurns(GameTestHelper helper) {
		floor(helper);
		BlockPos at = new BlockPos(3, 1, 4);
		Pig pig = helper.spawn(EntityTypes.PIG, at);
		pig.setNoAi(true);
		float full = pig.getHealth();
		fire(helper, AlaGameTestHelper.survivalPlayer(helper), new ItemStack(Items.TORCH),
				new Vec3(3.5, 1.4, 2.0), new Vec3(0, 0, SPEED));
		helper.succeedWhen(() -> {
			assertNoProjectile(helper);
			float expected = full - (float) ToolConfig.slingshotHitDamage;
			helper.assertTrue(Math.abs(pig.getHealth() - expected) < 1.0E-3F,
					"the pig has " + pig.getHealth() + " health, expected " + expected);
			DamageSource last = pig.getLastDamageSource();
			helper.assertTrue(last != null && last.is(DamageTypes.THROWN)
					&& last.getDirectEntity() instanceof TorchProjectile,
					"the pig's last damage is " + last + ", expected a thrown torch");
			helper.assertTrue(pig.getRemainingFireTicks() > 0, "the pig does not burn");
			helper.assertTrue(helper.getBlockState(at).isAir(), "a torch was placed in the pig's cell");
			assertOneDrop(helper, at, Items.TORCH);
		});
	}

	// ── when it drops ─────────────────────────────────────────────────────────────────────────────

	/**
	 * A torch that never hits anything drops where it is once its flight time is up.
	 *
	 * @implements MOD-871 — timeout in the air: the torch drops.
	 */
	public static void torchStillFlyingDropsAfterTimeout(GameTestHelper helper) {
		BlockPos at = new BlockPos(3, 4, 3);
		TorchProjectile torch = fire(helper, AlaGameTestHelper.survivalPlayer(helper), new ItemStack(Items.TORCH),
				Vec3.atCenterOf(at), Vec3.ZERO);
		torch.setNoGravity(true);
		helper.runAtTickTime(TorchProjectile.MAX_FLIGHT_TICKS - 20, () -> helper.assertTrue(torch.isAlive(),
				"the torch is gone before its flight time is up"));
		helper.runAtTickTime(TorchProjectile.MAX_FLIGHT_TICKS + 20, () -> {
			assertNoProjectile(helper);
			// The drop falls from mid-air, so it is looked for in the whole column under the cell.
			assertOneDrop(helper, at.below(2), Items.TORCH);
			helper.succeed();
		});
	}

	/**
	 * A shooter who left the game before the torch landed cannot be asked whether he may build there: it drops.
	 *
	 * @implements MOD-871 — shooter not online: the torch drops.
	 */
	public static void offlineShooterDropsTheTorch(GameTestHelper helper) {
		helper.setBlock(TARGET, Blocks.STONE);
		ServerPlayer shooter = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		TorchProjectile torch = fire(helper, shooter, new ItemStack(Items.TORCH),
				Vec3.atCenterOf(TARGET.above(2)), new Vec3(0, -SPEED, 0));
		shooter.discard();
		helper.assertTrue(torch.getOwner() == null, "the torch still finds its shooter after he left");
		expectDropped(helper, TARGET.above(), Items.TORCH);
	}

	/**
	 * A shooter in adventure mode may not place a torch by hand, so his torch does not land either.
	 *
	 * @implements MOD-871 — protection: adventure mode (Player.mayUseItemAt) is not bypassed.
	 */
	public static void adventureModeDropsTheTorch(GameTestHelper helper) {
		helper.setBlock(TARGET, Blocks.STONE);
		ServerPlayer shooter = AlaGameTestHelper.survivalPlayer(helper);
		shooter.setGameMode(GameType.ADVENTURE);
		fire(helper, shooter, new ItemStack(Items.TORCH), Vec3.atCenterOf(TARGET.above(2)), new Vec3(0, -SPEED, 0));
		expectDropped(helper, TARGET.above(), Items.TORCH);
	}

	// ── what a shot spends ────────────────────────────────────────────────────────────────────────

	/**
	 * A full draw fires one torch: the stack loses exactly one, exactly one torch flies, the slingshot wears by one.
	 *
	 * @implements MOD-871 — one torch per shot, no dupe, no loss.
	 */
	public static void shotSpendsExactlyOneTorch(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		ItemStack slingshot = new ItemStack(ModContent.SLINGSHOT.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, slingshot);
		player.getInventory().setItem(9, new ItemStack(Items.TORCH, 5));
		List<TorchProjectile> fired = fullDraw(helper, player, slingshot);
		try {
			helper.assertTrue(fired.size() == 1, "the shot fired " + fired.size() + " torches, expected 1");
			int left = player.getInventory().getItem(9).getCount();
			helper.assertTrue(left == 4, "the stack holds " + left + " torches after one shot, expected 4");
			ItemStack flying = fired.getFirst().getItem();
			helper.assertTrue(flying.is(Items.TORCH) && flying.getCount() == 1
					&& !flying.has(DataComponents.INTANGIBLE_PROJECTILE),
					"the torch in flight is " + flying + ", expected one real torch");
			helper.assertTrue(slingshot.getDamageValue() == 1,
					"the slingshot took " + slingshot.getDamageValue() + " damage, expected 1");
		} finally {
			fired.forEach(TorchProjectile::discard);
		}
		helper.succeed();
	}

	/**
	 * A creative player's shot spends no torch, and the torch in flight is marked as never spent, so it cannot
	 * become an item to pick up.
	 *
	 * @implements MOD-871 — in creative the torch is not spent.
	 */
	public static void creativeShotSpendsNothing(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		ItemStack slingshot = new ItemStack(ModContent.SLINGSHOT.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, slingshot);
		player.getInventory().setItem(9, new ItemStack(Items.TORCH, 5));
		List<TorchProjectile> fired = fullDraw(helper, player, slingshot);
		try {
			helper.assertTrue(fired.size() == 1, "the shot fired " + fired.size() + " torches, expected 1");
			int left = player.getInventory().getItem(9).getCount();
			helper.assertTrue(left == 5, "the creative stack holds " + left + " torches after a shot, expected 5");
			helper.assertTrue(fired.getFirst().getItem().has(DataComponents.INTANGIBLE_PROJECTILE),
					"the creative torch in flight is not marked intangible");
		} finally {
			fired.forEach(TorchProjectile::discard);
		}
		helper.succeed();
	}

	/**
	 * Without a torch the slingshot does not draw and does not fire — for a creative player either, whom
	 * {@code Player.getProjectile} would hand an arrow.
	 *
	 * @implements MOD-871 — no ammunition, no shot.
	 */
	public static void noTorchNoShot(GameTestHelper helper) {
		for (ServerPlayer player : List.of(AlaGameTestHelper.survivalPlayer(helper),
				AlaGameTestHelper.mockPlayerInLevel(helper))) {
			ItemStack slingshot = new ItemStack(ModContent.SLINGSHOT.get());
			player.setItemInHand(InteractionHand.MAIN_HAND, slingshot);
			player.getInventory().setItem(9, new ItemStack(Items.ARROW, 5));
			InteractionResult use = slingshot.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			helper.assertTrue(use == InteractionResult.FAIL,
					"the slingshot drew with no torch (" + use + "), creative=" + player.hasInfiniteMaterials());
			List<TorchProjectile> fired = fullDraw(helper, player, slingshot);
			fired.forEach(TorchProjectile::discard);
			helper.assertTrue(fired.isEmpty(), "the slingshot fired with no torch, creative="
					+ player.hasInfiniteMaterials());
			helper.assertTrue(player.getInventory().getItem(9).getCount() == 5, "an arrow was spent");
		}
		helper.succeed();
	}

	/**
	 * Every torch item of the game — vanilla, ours, another mod's — is slingshot ammunition: an item that places a
	 * standing {@code BaseTorchBlock} and a wall variant must be in {@code alaindustrial:slingshot_ammo}, or a new
	 * torch would silently not fire.
	 *
	 * @implements MOD-871 — the ammunition tag holds every torch.
	 */
	public static void everyTorchItemIsAmmo(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		int torches = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			if (item instanceof StandingAndWallBlockItem blockItem && blockItem.getBlock() instanceof BaseTorchBlock) {
				torches++;
				if (!new ItemStack(item).is(SlingshotItem.AMMO)) {
					missing.add(BuiltInRegistries.ITEM.getKey(item).toString());
				}
			}
		}
		helper.assertTrue(torches >= 5, "found only " + torches + " torch items; the scan itself is broken");
		helper.assertTrue(missing.isEmpty(), "torch items missing from alaindustrial:slingshot_ammo: " + missing);
		helper.succeed();
	}

	// ── helpers ───────────────────────────────────────────────────────────────────────────────────

	private static void landsOnTop(GameTestHelper helper, ItemStack torch, Block standing) {
		helper.setBlock(TARGET, Blocks.STONE);
		BlockPos cell = TARGET.above();
		fire(helper, AlaGameTestHelper.survivalPlayer(helper), torch, Vec3.atCenterOf(cell.above()),
				new Vec3(0, -SPEED, 0));
		helper.succeedWhen(() -> {
			assertNoProjectile(helper);
			helper.assertTrue(helper.getBlockState(cell).is(standing),
					"the cell above the hit holds " + helper.getBlockState(cell) + ", expected " + standing);
			assertNoDrop(helper, cell);
		});
	}

	private static void landsOnSide(GameTestHelper helper, ItemStack torch, Block wall) {
		helper.setBlock(TARGET, Blocks.STONE);
		BlockPos cell = TARGET.west();
		helper.setBlock(cell.below(), Blocks.STONE);
		fire(helper, AlaGameTestHelper.survivalPlayer(helper), torch, Vec3.atCenterOf(cell.west()),
				new Vec3(SPEED, 0, 0));
		helper.succeedWhen(() -> {
			assertNoProjectile(helper);
			BlockState state = helper.getBlockState(cell);
			helper.assertTrue(state.is(wall) && state.getValue(WallTorchBlock.FACING) == Direction.WEST,
					"the cell west of the hit holds " + state + ", expected " + wall + " facing west");
			assertNoDrop(helper, cell);
		});
	}

	/** A still water source walled in on four sides, over stone; returns the water cell. */
	private static BlockPos waterPool(GameTestHelper helper) {
		BlockPos pool = TARGET;
		helper.setBlock(pool.below(), Blocks.STONE);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			helper.setBlock(pool.relative(side), Blocks.STONE);
		}
		helper.setBlock(pool, Blocks.WATER);
		return pool;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** Spawns a torch shot by {@code shooter} at relative {@code from}, flying at {@code velocity} blocks a tick. */
	private static TorchProjectile fire(GameTestHelper helper, Player shooter, ItemStack torch, Vec3 from,
			Vec3 velocity) {
		ServerLevel level = helper.getLevel();
		shooter.setPos(helper.absoluteVec(new Vec3(7.5, 1.0, 7.5)));
		TorchProjectile projectile = new TorchProjectile(level, shooter, torch);
		projectile.setPos(helper.absoluteVec(from));
		projectile.setDeltaMovement(velocity);
		level.addFreshEntity(projectile);
		return projectile;
	}

	/** Releases the slingshot after a full draw; returns the torches the shot put in the world. */
	private static List<TorchProjectile> fullDraw(GameTestHelper helper, ServerPlayer player, ItemStack slingshot) {
		player.setPos(helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)));
		List<TorchProjectile> before = torches(helper);
		player.startUsingItem(InteractionHand.MAIN_HAND);
		slingshot.releaseUsing(helper.getLevel(), player, slingshot.getUseDuration(player) - 20);
		player.stopUsingItem();
		List<TorchProjectile> after = new ArrayList<>(torches(helper));
		after.removeAll(before);
		return after;
	}

	private static List<TorchProjectile> torches(GameTestHelper helper) {
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(64);
		return helper.getLevel().getEntitiesOfClass(TorchProjectile.class, box, TorchProjectile::isAlive);
	}

	private static void expectDropped(GameTestHelper helper, BlockPos cell, Item torch) {
		helper.succeedWhen(() -> {
			assertNoProjectile(helper);
			helper.assertTrue(helper.getBlockState(cell).isAir(),
					"the cell holds " + helper.getBlockState(cell) + ", expected nothing placed");
			assertOneDrop(helper, cell, torch);
		});
	}

	private static void assertNoProjectile(GameTestHelper helper) {
		helper.assertTrue(helper.getEntities(ModEntityTypes.TORCH_PROJECTILE.type()).isEmpty(),
				"a torch is still in flight");
	}

	private static void assertOneDrop(GameTestHelper helper, BlockPos near, Item torch) {
		List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, near, 3.0);
		int count = drops.stream().mapToInt(e -> e.getItem().is(torch) ? e.getItem().getCount() : 0).sum();
		helper.assertTrue(count == 1, count + " torches dropped near " + near + ", expected exactly 1");
	}

	private static void assertNoDrop(GameTestHelper helper, BlockPos near) {
		helper.assertTrue(helper.getEntities(EntityTypes.ITEM, near, 3.0).isEmpty(),
				"an item dropped near " + near + " although the torch landed");
	}
}
