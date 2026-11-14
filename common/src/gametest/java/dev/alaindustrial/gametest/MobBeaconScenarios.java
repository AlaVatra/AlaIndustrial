package dev.alaindustrial.gametest;

import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.entity.CreativeEnergySourceBlockEntity;
import dev.alaindustrial.block.entity.MobBeaconBlockEntity;
import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.entity.GlowRenewal;
import dev.alaindustrial.entity.MobBeaconConfig;
import dev.alaindustrial.entity.MobBeaconGlow;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;

/**
 * Loader-neutral gametests of the Mob Beacon (MOD-968, suite TC-BCN-001): a powered beacon gives the mobs in its zone
 * the game's glowing effect, and nothing else does.
 *
 * <p><b>The zone is pinned to radius 2.</b> The gametest server shares one world and the rigs stand a few blocks
 * apart; a live radius-16 beacon would outline a neighbour's mobs and, worse, a neighbour's assertion could see a glow
 * this rig gave. The mob repeller's suite does the same (MOD-278).
 *
 * <p>The block entity is driven tick by tick ({@link AlaGameTestHelper#drive}): the upkeep and the search live in
 * {@code onServerTick}, which the gametest's own block tick never reaches.
 */
public final class MobBeaconScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MobBeaconScenarios::glowsMobsInRangeAndBills, "mob_beacon_glows_mobs_in_range")
						.ticks(20),
				RosterEntry.of(MobBeaconScenarios::leavesMobOutOfRangeDark, "mob_beacon_leaves_far_mob_dark")
						.ticks(20),
				RosterEntry.of(MobBeaconScenarios::unpoweredBeaconGlowsNothing, "mob_beacon_unpowered_glows_nothing")
						.ticks(20),
				RosterEntry.of(MobBeaconScenarios::redstoneSwitchesItOff, "mob_beacon_redstone_switches_off")
						.ticks(20),
				RosterEntry.of(MobBeaconScenarios::playerIsNotOutlined, "mob_beacon_player_not_outlined").ticks(20),
				RosterEntry.of(MobBeaconScenarios::longerGlowIsNotShortened, "mob_beacon_longer_glow_kept")
						.ticks(20),
				RosterEntry.of(MobBeaconScenarios::energyOnlyFromBelow, "mob_beacon_energy_only_from_below")
						.ticks(20),
				RosterEntry.of(MobBeaconScenarios::glassBodySeesThroughButStaysSolid,
						"mob_beacon_glass_body_sees_through_but_stays_solid"));

		private Roster() {}
	}

	private MobBeaconScenarios() {
	}

	private static final BlockPos BEACON = new BlockPos(1, 2, 1);
	private static final int TEST_RANGE = 2;

	private static ConfigOverrides isolatedZone() {
		return ConfigOverrides.sync().set("mobBeaconRange", TEST_RANGE);
	}

	private static MobBeaconBlockEntity place(GameTestHelper helper, long charge) {
		helper.setBlock(BEACON, ModContent.MOB_BEACON.get());
		MobBeaconBlockEntity beacon = helper.getBlockEntity(BEACON, MobBeaconBlockEntity.class);
		beacon.getEnergyStorage().setAmountUntracked(charge);
		return beacon;
	}

	private static boolean glows(LivingEntity entity) {
		return entity.hasEffect(MobEffects.GLOWING);
	}

	/**
	 * @implements TC-BCN-001-FUN01 — one powered tick lights the block, bills one tick of upkeep and gives a hostile
	 *     and a passive mob in the zone the glow, for the full length the rule names.
	 */
	public static void glowsMobsInRangeAndBills(GameTestHelper helper) {
		try (ConfigOverrides o = isolatedZone()) {
			MobBeaconBlockEntity beacon = place(helper, MobBeaconConfig.mobBeaconBuffer);
			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BEACON.above());
			Cow cow = helper.spawn(EntityTypes.COW, BEACON.east());
			long before = beacon.getEnergyStorage().getAmount();

			AlaGameTestHelper.drive(beacon, helper, 1);

			helper.assertTrue(glows(zombie), "a hostile mob in the zone must glow");
			helper.assertTrue(glows(cow), "a passive mob in the zone must glow too: the beacon shows every mob");
			int expected = GlowRenewal.glowTicks(MobBeaconConfig.mobBeaconScanIntervalTicks);
			MobEffectInstance glow = zombie.getEffect(MobEffects.GLOWING);
			helper.assertTrue(glow.getDuration() == expected,
					"the glow must last " + expected + " ticks, got " + glow.getDuration());
			long spent = before - beacon.getEnergyStorage().getAmount();
			helper.assertTrue(spent == MobBeaconConfig.mobBeaconEuPerTick,
					"one powered tick must cost " + MobBeaconConfig.mobBeaconEuPerTick + " EU, spent " + spent);
			helper.assertBlockProperty(BEACON, BlockStateProperties.LIT, true);
			helper.succeed();
		}
	}

	/**
	 * @implements TC-BCN-001-FUN02 — a mob outside the cube stays dark while a mob inside glows on the same search.
	 */
	public static void leavesMobOutOfRangeDark(GameTestHelper helper) {
		try (ConfigOverrides o = isolatedZone()) {
			MobBeaconBlockEntity beacon = place(helper, MobBeaconConfig.mobBeaconBuffer);
			Zombie near = helper.spawn(EntityTypes.ZOMBIE, BEACON.above());
			Zombie far = helper.spawn(EntityTypes.ZOMBIE, BEACON.east(TEST_RANGE + 3));

			AlaGameTestHelper.drive(beacon, helper, 1);

			helper.assertTrue(glows(near), "the mob inside the zone must glow (control)");
			helper.assertFalse(glows(far), "a mob " + (TEST_RANGE + 3) + " blocks away must not glow at radius "
					+ TEST_RANGE);
			helper.succeed();
		}
	}

	/**
	 * @implements TC-BCN-001-FUN03 — an empty beacon spends nothing, stays dark and outlines no one.
	 */
	public static void unpoweredBeaconGlowsNothing(GameTestHelper helper) {
		try (ConfigOverrides o = isolatedZone()) {
			MobBeaconBlockEntity beacon = place(helper, 0);
			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BEACON.above());

			AlaGameTestHelper.drive(beacon, helper, 1);

			helper.assertFalse(glows(zombie), "a beacon with no energy must not outline mobs");
			helper.assertTrue(beacon.getEnergyStorage().getAmount() == 0, "an empty buffer must stay empty");
			helper.assertBlockProperty(BEACON, BlockStateProperties.LIT, false);
			helper.succeed();
		}
	}

	/**
	 * @implements TC-BCN-001-FUN04 — a redstone signal on the block switches it off: dark, no upkeep, no glow.
	 */
	public static void redstoneSwitchesItOff(GameTestHelper helper) {
		try (ConfigOverrides o = isolatedZone()) {
			MobBeaconBlockEntity beacon = place(helper, MobBeaconConfig.mobBeaconBuffer);
			helper.setBlock(BEACON.west(), Blocks.REDSTONE_BLOCK);
			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BEACON.above());
			long before = beacon.getEnergyStorage().getAmount();

			AlaGameTestHelper.drive(beacon, helper, 1);

			helper.assertFalse(glows(zombie), "a switched-off beacon must not outline mobs");
			helper.assertTrue(beacon.getEnergyStorage().getAmount() == before,
					"a switched-off beacon must not pay its upkeep");
			helper.assertBlockProperty(BEACON, BlockStateProperties.LIT, false);
			helper.succeed();
		}
	}

	/**
	 * @implements TC-BCN-001-NEG01 — a player standing in the zone is never outlined; the mob beside them is.
	 */
	public static void playerIsNotOutlined(GameTestHelper helper) {
		try (ConfigOverrides o = isolatedZone()) {
			MobBeaconBlockEntity beacon = place(helper, MobBeaconConfig.mobBeaconBuffer);
			ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
			BlockPos at = helper.absolutePos(BEACON.east());
			player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BEACON.above());
			// Non-vacuous: the player really stands inside the zone the beacon searches.
			AABB zone = new AABB(helper.absolutePos(BEACON)).inflate(TEST_RANGE);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(Player.class, zone).contains(player),
					"the player must stand inside the zone for this check to mean anything");

			AlaGameTestHelper.drive(beacon, helper, 1);

			helper.assertTrue(glows(zombie), "the mob beside the player must glow (control)");
			helper.assertFalse(glows(player), "a player must never be outlined by the beacon");
			helper.succeed();
		}
	}

	/**
	 * @implements TC-BCN-001-FUN06 — power goes in through the bottom face only (owner, 2026-11-14): a creative source
	 *     under the beacon charges it, one on any side or on top charges nothing; only a cable under it draws an arm.
	 */
	public static void energyOnlyFromBelow(GameTestHelper helper) {
		for (Direction face : Direction.values()) {
			boolean open = face == Direction.DOWN;
			MobBeaconBlockEntity beacon = place(helper, 0);
			BlockPos abs = beacon.getBlockPos();
			BlockPos sourcePos = BEACON.relative(face);
			helper.setBlock(sourcePos, ModContent.CREATIVE_ENERGY_SOURCE.get());
			CreativeEnergySourceBlockEntity source = helper.getBlockEntity(sourcePos,
					CreativeEnergySourceBlockEntity.class);
			for (int t = 0; t < 3; t++) {
				EnergyScenarioSupport.tick(helper, source);
			}
			long got = beacon.getEnergyStorage().getAmount();
			helper.assertTrue((got > 0) == open, "a creative source on the " + face + " face must "
					+ (open ? "" : "not ") + "charge the beacon, it got " + got + " EU");
			helper.assertTrue((EnergyLookup.get().find(helper.getLevel(), abs, face) != null) == open,
					"face " + face + (open ? " must" : " must not") + " offer an energy port");

			// The cable goes in first and the beacon after it, as a player lays a line up to a placed block's
			// neighbour: the beacon's placement is the neighbour update that shapes the cable's arm.
			helper.setBlock(sourcePos, ModContent.COPPER_CABLE.get());
			helper.setBlock(BEACON, Blocks.AIR);
			place(helper, 0);
			helper.assertTrue(CableBlock.shouldConnectTo(helper.getLevel(), helper.absolutePos(sourcePos),
					face.getOpposite()) == open,
					"a cable on the " + face + " face must " + (open ? "" : "not ") + "draw an arm to the beacon");
			helper.setBlock(sourcePos, Blocks.AIR);
			helper.setBlock(BEACON, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BCN-001-NEG02 — a glow longer than the renewal threshold (a spectral arrow, a potion) is left as
	 *     it is, and a glow at the threshold is renewed to the full length.
	 */
	public static void longerGlowIsNotShortened(GameTestHelper helper) {
		try (ConfigOverrides o = isolatedZone()) {
			int interval = MobBeaconConfig.mobBeaconScanIntervalTicks;
			int full = GlowRenewal.glowTicks(interval);
			int foreign = full * 3;
			int expiring = GlowRenewal.renewAtTicks(interval);
			Zombie longGlow = helper.spawn(EntityTypes.ZOMBIE, BEACON.above());
			longGlow.addEffect(new MobEffectInstance(MobEffects.GLOWING, foreign, 0, false, false));
			Zombie dimming = helper.spawn(EntityTypes.ZOMBIE, BEACON.east());
			dimming.addEffect(new MobEffectInstance(MobEffects.GLOWING, expiring, 0, false, false));

			int renewed = MobBeaconGlow.sweep(helper.getLevel(), helper.absolutePos(BEACON), TEST_RANGE, interval);

			helper.assertTrue(renewed == 1, "only the expiring glow is renewed, renewed " + renewed);
			int kept = longGlow.getEffect(MobEffects.GLOWING).getDuration();
			helper.assertTrue(kept == foreign, "a longer glow must stay " + foreign + " ticks, became " + kept);
			int fresh = dimming.getEffect(MobEffects.GLOWING).getDuration();
			helper.assertTrue(fresh == full, "a glow at the threshold must be renewed to " + full + ", got " + fresh);
			helper.succeed();
		}
	}

	/**
	 * @implements TC-BCN-001-FUN07 — every state of the three beacons is a glass body in a solid box: it hides no
	 *     neighbour face (no face occlusion shape is the full block, not solid-rendered) and lets light and skylight
	 *     through with no corner shade, yet still occludes ({@code canOcclude()}), collides as the full cube and is not
	 *     a path for mobs. @covers R-PHY-05
	 */
	public static void glassBodySeesThroughButStaysSolid(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		BlockPos at = helper.absolutePos(BEACON);
		int checked = 0;
		for (Block block : List.of(ModContent.MOB_BEACON.get(), ModContent.MOB_BEACON_MV.get(),
				ModContent.MOB_BEACON_HV.get())) {
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				checked++;
				if (!state.canOcclude()) {
					problems.add(state + ": canOcclude()=false (R-PHY-05 and the registry snapshot pin true)");
				}
				if (state.isSolidRender()) {
					problems.add(state + ": isSolidRender()=true, it hides every neighbour face");
				}
				for (Direction side : Direction.values()) {
					if (Block.isShapeFullBlock(state.getFaceOcclusionShape(side))) {
						problems.add(state + ": face " + side + " occludes as the full block");
					}
				}
				if (state.getLightDampening() != 0 || !state.propagatesSkylightDown()) {
					problems.add(state + ": dampens light " + state.getLightDampening() + ", skylight "
							+ state.propagatesSkylightDown() + "; glass lets both through");
				}
				if (state.getShadeBrightness(helper.getLevel(), at) != 1.0F) {
					problems.add(state + ": casts the corner shade of a solid cube");
				}
				if (!Block.isShapeFullBlock(state.getCollisionShape(helper.getLevel(), at))) {
					problems.add(state + ": collision is not the full cube");
				}
				if (state.isPathfindable(PathComputationType.LAND)) {
					problems.add(state + ": mobs would path through it");
				}
			}
		}
		helper.assertTrue(checked == 6, "three beacons with lit on and off are six states, checked " + checked);
		helper.assertTrue(problems.isEmpty(), "MOD-968 glass body: " + problems);
		helper.succeed();
	}
}
