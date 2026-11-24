package dev.alaindustrial.gametest;

import dev.alaindustrial.block.EscalatorCarry;
import dev.alaindustrial.block.EscalatorChain;
import dev.alaindustrial.block.EscalatorDriveBlock;
import dev.alaindustrial.block.EscalatorMode;
import dev.alaindustrial.block.EscalatorSectionBlock;
import dev.alaindustrial.block.entity.EscalatorDriveBlockEntity;
import dev.alaindustrial.core.escalator.EscalatorConfig;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Loader-neutral gametests of the escalator (MOD-969): it carries riders up and down a built chain, stands without
 * power, leaves a sneaking player alone, pushes an entity once per tick on a seam, keeps to its tier's length, draws
 * energy only while carrying and stops the sections above a removed one.
 *
 * <p>The rig climbs east: the drive is the bottom landing, set into the floor one step down the diagonal from the
 * bottom section {@link #BOTTOM}; the sections follow the diagonal (each puts up its balustrade half by itself) and
 * the landing closes the chain. Armor stands are the riders: a mock player does not tick, and a mob
 * with {@code NoAI} never travels ({@code Mob.isEffectiveAi}), while an armor stand falls and is moved by physics
 * ({@code ArmorStand.travel} under {@code hasPhysics}).
 */
public final class EscalatorScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(EscalatorScenarios::carriesArmorStandUp, "escalator_carries_armor_stand_up").ticks(200),
				RosterEntry.of(EscalatorScenarios::carriesArmorStandDown, "escalator_carries_armor_stand_down")
						.ticks(200),
				RosterEntry.of(EscalatorScenarios::carriesItemUp, "escalator_carries_item_up").ticks(400),
				RosterEntry.of(EscalatorScenarios::unpoweredBandStandsStill, "escalator_unpowered_band_stands_still")
						.ticks(80),
				RosterEntry.of(EscalatorScenarios::sneakingPlayerIsNotCarried, "escalator_sneaking_player_not_carried"),
				RosterEntry.of(EscalatorScenarios::seamPushesOnce, "escalator_seam_pushes_once"),
				RosterEntry.of(EscalatorScenarios::chainLongerThanTierDoesNotRun, "escalator_chain_longer_than_tier"),
				RosterEntry.of(EscalatorScenarios::drawsOnlyWhileCarrying, "escalator_draws_only_while_carrying"),
				RosterEntry.of(EscalatorScenarios::removedSectionStopsTheRestAbove,
						"escalator_removed_section_stops_rest_above"),
				RosterEntry.of(EscalatorScenarios::stepsRiseTowardsFacing, "escalator_steps_rise_towards_facing"),
				RosterEntry.of(EscalatorScenarios::halvesStandAndFallTogether, "escalator_halves_stand_and_fall"),
				RosterEntry.of(EscalatorScenarios::sectionTakesFacingFromBelow, "escalator_section_facing_from_below"),
				RosterEntry.of(EscalatorScenarios::driveLandingWakesTheBand, "escalator_drive_landing_wakes_band")
						.ticks(100),
				RosterEntry.of(EscalatorScenarios::loneSectionNamesMissingDrive, "escalator_lone_section_names_drive"),
				RosterEntry.of(EscalatorScenarios::driveBesideBottomDoesNotRun, "escalator_drive_beside_bottom_idle"),
				RosterEntry.of(EscalatorScenarios::endsFollowTheBand, "escalator_ends_follow_the_band"));

		private Roster() {}
	}

	private EscalatorScenarios() {
	}

	private static final Direction FACING = Direction.EAST;
	private static final BlockPos BOTTOM = new BlockPos(2, 2, 1);
	/** The bottom landing with the drive: in the floor, one step down the diagonal from the bottom section. */
	private static final BlockPos DRIVE = BOTTOM.relative(FACING.getOpposite()).below();
	private static final int SECTIONS = 4;

	/** Floor, drive, {@code sections} sections and the landing; returns the drive, charged with {@code charge}. */
	private static EscalatorDriveBlockEntity build(GameTestHelper helper, int sections, long charge) {
		floor(helper);
		helper.setBlock(DRIVE, ModContent.ESCALATOR_DRIVE.get().defaultBlockState()
				.setValue(EscalatorDriveBlock.FACING, FACING));
		for (int i = 0; i < sections; i++) {
			helper.setBlock(EscalatorChain.section(BOTTOM, FACING, i),
					ModContent.ESCALATOR_SECTION.get().defaultBlockState()
							.setValue(EscalatorSectionBlock.FACING, FACING));
		}
		helper.setBlock(EscalatorChain.landingPos(BOTTOM, FACING, sections), ModContent.ESCALATOR_LANDING.get());
		EscalatorDriveBlockEntity drive = helper.getBlockEntity(DRIVE, EscalatorDriveBlockEntity.class);
		drive.getEnergyStorage().setAmountUntracked(charge);
		return drive;
	}

	/** The stone floor in front of the band at y = 1. */
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 2; x++) {
			for (int z = 0; z <= 2; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
	}

	private static long full() {
		return EscalatorConfig.escalatorBufferLv;
	}

	private static EscalatorMode modeAt(GameTestHelper helper, BlockPos rel) {
		return helper.getBlockState(rel).getValue(EscalatorSectionBlock.MODE);
	}

	private static String f(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	/** An armor stand standing on the middle of section {@code i}, just above its step. */
	private static ArmorStand standOn(GameTestHelper helper, int i) {
		BlockPos cell = EscalatorChain.section(BOTTOM, FACING, i);
		return helper.spawn(EntityTypes.ARMOR_STAND,
				new Vec3(cell.getX() + 0.5, cell.getY() + 0.62, cell.getZ() + 0.5));
	}

	/**
	 * @implements MOD-969-FUN01 — an armor stand on the bottom step of a powered four-section band wakes it and is
	 *     carried onto the top landing, four blocks up.
	 */
	public static void carriesArmorStandUp(GameTestHelper helper) {
		build(helper, SECTIONS, full());
		ArmorStand stand = standOn(helper, 0);
		BlockPos landing = EscalatorChain.landingPos(BOTTOM, FACING, SECTIONS);
		helper.succeedWhen(() -> {
			Vec3 at = helper.relativeVec(stand.position());
			if (at.x < landing.getX() || at.y < landing.getY() + 0.95) {
				helper.fail("the stand must reach the landing at x>=" + landing.getX() + " y>=" + (landing.getY() + 1)
						+ ", it is at x=" + f(at.x) + " y=" + f(at.y) + debug(helper, stand));
			}
		});
	}

	private static String debug(GameTestHelper helper, net.minecraft.world.entity.Entity e) {
		BlockPos owner = EscalatorCarry.ownerCell(helper.getLevel(), e);
		return " [mode " + modeAt(helper, BOTTOM) + ", support " + e.mainSupportingBlockPos.map(helper::relativePos)
				+ ", owner " + (owner == null ? null : helper.relativePos(owner)) + ", v " + e.getDeltaMovement()
				+ ", ground " + e.onGround() + "]";
	}

	/** @implements MOD-969-FUN02 — reversed, the band carries a stand from the top section down onto the floor. */
	public static void carriesArmorStandDown(GameTestHelper helper) {
		EscalatorDriveBlockEntity drive = build(helper, SECTIONS, full());
		drive.reverse();
		ArmorStand stand = standOn(helper, SECTIONS - 1);
		helper.succeedWhen(() -> {
			Vec3 at = helper.relativeVec(stand.position());
			if (at.x >= BOTTOM.getX() || at.y > BOTTOM.getY() + 0.05) {
				helper.fail("the stand must come down onto the floor before x=" + BOTTOM.getX() + ", it is at x="
						+ f(at.x) + " y=" + f(at.y));
			}
		});
	}

	/** @implements MOD-969-FUN03 — a dropped item on the bottom step rides up onto the landing too. */
	public static void carriesItemUp(GameTestHelper helper) {
		build(helper, SECTIONS, full());
		BlockPos cell = BOTTOM;
		ItemEntity item = helper.spawnItem(Items.IRON_INGOT,
				new Vec3(cell.getX() + 0.5, cell.getY() + 0.62, cell.getZ() + 0.5));
		item.setNeverPickUp();
		BlockPos landing = EscalatorChain.landingPos(BOTTOM, FACING, SECTIONS);
		helper.succeedWhen(() -> {
			Vec3 at = helper.relativeVec(item.position());
			if (at.x < landing.getX() || at.y < landing.getY() + 0.95) {
				helper.fail("the item must reach the landing, it is at x=" + f(at.x) + " y=" + f(at.y));
			}
		});
	}

	/** @implements MOD-969-FUN04 — with an empty drive the band stays red and the stand does not travel. */
	public static void unpoweredBandStandsStill(GameTestHelper helper) {
		build(helper, SECTIONS, 0);
		ArmorStand stand = standOn(helper, 0);
		double startX = stand.getX();
		helper.runAtTickTime(60, () -> {
			double moved = stand.getX() - startX;
			if (Math.abs(moved) > 0.3) {
				helper.fail("an unpowered band must not carry anyone, the stand moved " + f(moved));
				return;
			}
			if (modeAt(helper, BOTTOM) != EscalatorMode.OFF) {
				helper.fail("an unpowered band must show OFF, shows " + modeAt(helper, BOTTOM));
				return;
			}
			helper.succeed();
		});
	}

	/** Force the band into RUNNING on the sections without waiting for the drive (direct seam tests). */
	private static void runBand(GameTestHelper helper, int sections) {
		for (int i = 0; i < sections; i++) {
			BlockPos at = EscalatorChain.section(BOTTOM, FACING, i);
			helper.setBlock(at, helper.getBlockState(at).setValue(EscalatorSectionBlock.MODE, EscalatorMode.RUNNING));
		}
	}

	/**
	 * @implements MOD-969-FUN05 — a sneaking player on a running step is left alone; the same player standing up is
	 *     pushed along the run (the control).
	 */
	public static void sneakingPlayerIsNotCarried(GameTestHelper helper) {
		build(helper, SECTIONS, full());
		runBand(helper, SECTIONS);
		ServerLevel level = helper.getLevel();
		BlockPos cell = helper.absolutePos(BOTTOM);
		BlockState section = level.getBlockState(cell);
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		player.setPos(cell.getX() + 0.5, cell.getY() + 0.6, cell.getZ() + 0.5);
		player.setDeltaMovement(Vec3.ZERO);

		player.setShiftKeyDown(true);
		boolean pushedSneaking = EscalatorCarry.carry(level, cell, section, player);
		helper.assertTrue(!pushedSneaking && player.getDeltaMovement().lengthSqr() == 0.0,
				"a sneaking player must not be carried, velocity " + player.getDeltaMovement());

		player.setShiftKeyDown(false);
		boolean pushed = EscalatorCarry.carry(level, cell, section, player);
		helper.assertTrue(pushed && player.getDeltaMovement().x > 0.05,
				"a standing player must be pushed east along the run, velocity " + player.getDeltaMovement());
		helper.succeed();
	}

	/**
	 * @implements MOD-969-FUN06 — an entity on the seam of two sections touches both cells but is pushed by one
	 *     only: the owner cell (the next section, whose column its feet are already in).
	 */
	public static void seamPushesOnce(GameTestHelper helper) {
		build(helper, SECTIONS, full());
		runBand(helper, SECTIONS);
		ServerLevel level = helper.getLevel();
		ArmorStand stand = standOn(helper, 0);
		BlockPos first = helper.absolutePos(BOTTOM);
		BlockPos second = helper.absolutePos(EscalatorChain.section(BOTTOM, FACING, 1));
		// Feet over the second section's column, still below its cell: 15/16 up the first one.
		stand.setPos(second.getX() + 0.05, first.getY() + 0.95, first.getZ() + 0.5);
		helper.assertTrue(stand.getBoundingBox().intersects(new AABB(first)), "the rig must touch the first cell");
		int pushes = 0;
		for (BlockPos cell : List.of(first, second)) {
			stand.setDeltaMovement(Vec3.ZERO);
			if (EscalatorCarry.carry(level, cell, level.getBlockState(cell), stand)) {
				pushes++;
			}
		}
		helper.assertTrue(pushes == 1, "an entity on a seam must be pushed once a tick, got " + pushes);
		helper.succeed();
	}

	/**
	 * @implements MOD-969-FUN07 — a chain one section longer than the drive's limit does not run; at the limit it
	 *     does.
	 */
	public static void chainLongerThanTierDoesNotRun(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync().set("escalatorMaxSectionsLv", SECTIONS - 1)) {
			EscalatorDriveBlockEntity drive = build(helper, SECTIONS, full());
			drive.requestRescan();
			AlaGameTestHelper.drive(drive, helper, 1);
			helper.assertTrue(drive.chain().problem() == EscalatorChain.Problem.TOO_LONG,
					"a chain of " + SECTIONS + " must be too long for a limit of " + (SECTIONS - 1) + ", got "
							+ drive.chain().problem());
			helper.assertTrue(modeAt(helper, BOTTOM) == EscalatorMode.OFF, "a too long chain must stay OFF");
		}
		try (ConfigOverrides o = ConfigOverrides.sync().set("escalatorMaxSectionsLv", SECTIONS)) {
			EscalatorDriveBlockEntity drive = helper.getBlockEntity(DRIVE, EscalatorDriveBlockEntity.class);
			drive.requestRescan();
			AlaGameTestHelper.drive(drive, helper, 1);
			helper.assertTrue(drive.chain().formed(), "at the limit the chain must be built, got "
					+ drive.chain().problem());
			helper.assertTrue(modeAt(helper, BOTTOM) == EscalatorMode.READY, "a built, powered, empty band is READY");
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-969-FUN08 — an empty band sleeps and spends nothing; a rider runs it at the drive's fixed part
	 *     plus the per-section part; a rider gone longer than the idle time puts it back to sleep.
	 */
	public static void drawsOnlyWhileCarrying(GameTestHelper helper) {
		EscalatorDriveBlockEntity drive = build(helper, SECTIONS, full());
		long now = helper.getLevel().getGameTime();
		drive.requestRescan();
		AlaGameTestHelper.drive(drive, helper, 1);
		long before = drive.getEnergyStorage().getAmount();
		helper.assertTrue(drive.mode() == EscalatorMode.READY, "an empty band must be READY, got " + drive.mode());
		helper.assertTrue(before == full(), "a READY band must spend nothing, spent " + (full() - before));

		drive.onContact(now);
		AlaGameTestHelper.drive(drive, helper, 10);
		long perTick = (EscalatorConfig.escalatorEuPerTickLv * 1000L
				+ (long) EscalatorConfig.escalatorMilliEuPerSection * SECTIONS) / 1000L;
		long spent = before - drive.getEnergyStorage().getAmount();
		helper.assertTrue(drive.mode() == EscalatorMode.RUNNING, "a ridden band must run, got " + drive.mode());
		helper.assertTrue(spent == perTick * 10, "ten running ticks must cost " + perTick * 10 + " EU, spent " + spent);
		helper.assertTrue(modeAt(helper, EscalatorChain.section(BOTTOM, FACING, SECTIONS - 1)) == EscalatorMode.RUNNING,
				"every section must show RUNNING");

		drive.onContact(now - EscalatorConfig.escalatorIdleTicks - 1);
		long beforeSleep = drive.getEnergyStorage().getAmount();
		AlaGameTestHelper.drive(drive, helper, 1);
		helper.assertTrue(drive.mode() == EscalatorMode.READY, "a band empty for longer than the idle time sleeps");
		helper.assertTrue(drive.getEnergyStorage().getAmount() == beforeSleep, "a sleeping band must spend nothing");
		helper.succeed();
	}

	/** @implements MOD-969-FUN09 — removing a section stops every section above the gap at once. */
	public static void removedSectionStopsTheRestAbove(GameTestHelper helper) {
		EscalatorDriveBlockEntity drive = build(helper, SECTIONS, full());
		drive.onContact(helper.getLevel().getGameTime());
		drive.requestRescan();
		AlaGameTestHelper.drive(drive, helper, 1);
		helper.assertTrue(modeAt(helper, EscalatorChain.section(BOTTOM, FACING, 3)) == EscalatorMode.RUNNING,
				"the rig must run first");
		helper.setBlock(EscalatorChain.section(BOTTOM, FACING, 1), Blocks.AIR);
		for (int i = 2; i < SECTIONS; i++) {
			EscalatorMode mode = modeAt(helper, EscalatorChain.section(BOTTOM, FACING, i));
			helper.assertTrue(mode == EscalatorMode.OFF, "section " + i + " above the gap must be OFF, is " + mode);
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-969-FUN10 — a section's steps rise towards its facing: low (1 px) at the entry, high (15 px)
	 *     at the far end, inside the cell (so a rider's feet stay in it).
	 */
	public static void stepsRiseTowardsFacing(GameTestHelper helper) {
		helper.setBlock(BOTTOM, ModContent.ESCALATOR_SECTION.get().defaultBlockState()
				.setValue(EscalatorSectionBlock.FACING, Direction.NORTH));
		BlockPos at = helper.absolutePos(BOTTOM);
		VoxelShape shape = helper.getLevel().getBlockState(at).getCollisionShape(helper.getLevel(), at);
		double southTop = topAt(shape, 8 / 16.0, 15.5 / 16.0);
		double northTop = topAt(shape, 8 / 16.0, 0.5 / 16.0);
		helper.assertTrue(Math.abs(topAt(shape, 0.5 / 16.0, 8 / 16.0) - 1.0) < 1e-6,
				"the side wall must stand the full block high");
		helper.assertTrue(Math.abs(southTop - 1 / 16.0) < 1e-6, "the entry (south) step must be 1 px, is " + southTop);
		helper.assertTrue(Math.abs(northTop - 15 / 16.0) < 1e-6, "the far (north) step must be 15 px, is " + northTop);
		helper.succeed();
	}

	/** Top of the shape over the point {@code (x, z)} of the cell. */
	private static double topAt(VoxelShape shape, double x, double z) {
		double top = 0;
		for (AABB box : shape.toAabbs()) {
			if (box.minZ <= z && z <= box.maxZ && box.minX <= x && x <= box.maxX) {
				top = Math.max(top, box.maxY);
			}
		}
		return top;
	}

	/**
	 * @implements MOD-969-FUN11 — a section put down by any means stands two blocks tall: the stair and, above it,
	 *     the balustrade half; taking either half away takes the other with it.
	 */
	public static void halvesStandAndFallTogether(GameTestHelper helper) {
		build(helper, 2, full());
		BlockPos second = EscalatorChain.section(BOTTOM, FACING, 1);
		for (BlockPos lower : List.of(BOTTOM, second)) {
			BlockState upper = helper.getBlockState(lower.above());
			helper.assertTrue(upper.is(ModContent.ESCALATOR_SECTION.get())
					&& upper.getValue(EscalatorSectionBlock.HALF) == DoubleBlockHalf.UPPER
					&& upper.getValue(EscalatorSectionBlock.FACING) == FACING,
					"a section must put up its balustrade half, facing its way, above " + lower + ", found " + upper);
		}
		helper.setBlock(BOTTOM.above(), Blocks.AIR);
		helper.assertTrue(helper.getBlockState(BOTTOM).isAir(), "the stair half must go with its balustrade");
		helper.setBlock(second, Blocks.AIR);
		helper.assertTrue(helper.getBlockState(second.above()).isAir(), "the balustrade half must go with its stair");
		helper.succeed();
	}

	/**
	 * @implements MOD-969-FUN12 — a section placed one step up the diagonal from the drive or from another section
	 *     takes their facing, whatever way the player looks; with nothing below there is no facing to take.
	 */
	public static void sectionTakesFacingFromBelow(GameTestHelper helper) {
		build(helper, 2, full());
		ServerLevel level = helper.getLevel();
		Direction overDrive = EscalatorChain.facingFromBelow(level, helper.absolutePos(BOTTOM));
		Direction overSection = EscalatorChain.facingFromBelow(level,
				helper.absolutePos(EscalatorChain.section(BOTTOM, FACING, 2)));
		Direction alone = EscalatorChain.facingFromBelow(level, helper.absolutePos(new BlockPos(0, 6, 6)));
		helper.assertTrue(overDrive == FACING, "over the drive a section must face " + FACING + ", got " + overDrive);
		helper.assertTrue(overSection == FACING, "over a section it must face " + FACING + ", got " + overSection);
		helper.assertTrue(alone == null, "with nothing below there is no facing to take, got " + alone);
		helper.succeed();
	}

	/** @implements MOD-969-FUN13 — someone stepping onto the drive's landing wakes a sleeping band. */
	public static void driveLandingWakesTheBand(GameTestHelper helper) {
		build(helper, SECTIONS, full());
		helper.runAtTickTime(10, () -> {
			helper.assertTrue(modeAt(helper, BOTTOM) == EscalatorMode.READY, "the empty band must sleep first");
			helper.spawn(EntityTypes.ARMOR_STAND,
					new Vec3(DRIVE.getX() + 0.5, DRIVE.getY() + 1.4, DRIVE.getZ() + 0.5));
		});
		helper.succeedWhen(() -> helper.assertTrue(modeAt(helper, BOTTOM) == EscalatorMode.RUNNING,
				"a rider on the drive's landing must wake the band, it shows " + modeAt(helper, BOTTOM)));
	}

	/** @implements MOD-969-FUN14 — a click on a section with no drive below its band names what is missing. */
	public static void loneSectionNamesMissingDrive(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(BOTTOM, ModContent.ESCALATOR_SECTION.get().defaultBlockState()
				.setValue(EscalatorSectionBlock.FACING, FACING));
		Component line = EscalatorSectionBlock.statusAt(helper.getLevel(), helper.absolutePos(BOTTOM), FACING);
		helper.assertTrue(line.getContents() instanceof TranslatableContents t
				&& t.getKey().equals("message.alaindustrial.escalator.no_drive"),
				"a lone section must name the missing drive, said " + line.getString());
		helper.succeed();
	}

	/**
	 * @implements MOD-969-FUN15 — the drive runs only the band it lies under: beside the bottom section (where the
	 *     first slice put it) it finds no section and the band stays red.
	 */
	public static void driveBesideBottomDoesNotRun(GameTestHelper helper) {
		floor(helper);
		BlockPos beside = BOTTOM.south();
		helper.setBlock(beside, ModContent.ESCALATOR_DRIVE.get().defaultBlockState()
				.setValue(EscalatorDriveBlock.FACING, FACING));
		for (int i = 0; i < 2; i++) {
			helper.setBlock(EscalatorChain.section(BOTTOM, FACING, i), ModContent.ESCALATOR_SECTION.get()
					.defaultBlockState().setValue(EscalatorSectionBlock.FACING, FACING));
		}
		helper.setBlock(EscalatorChain.landingPos(BOTTOM, FACING, 2), ModContent.ESCALATOR_LANDING.get());
		EscalatorDriveBlockEntity drive = helper.getBlockEntity(beside, EscalatorDriveBlockEntity.class);
		drive.getEnergyStorage().setAmountUntracked(full());
		drive.requestRescan();
		AlaGameTestHelper.drive(drive, helper, 1);
		helper.assertTrue(drive.chain().problem() == EscalatorChain.Problem.NO_SECTION,
				"a drive beside the bottom section must find no section, got " + drive.chain().problem());
		helper.assertTrue(modeAt(helper, BOTTOM) == EscalatorMode.OFF, "its band must stay OFF");
		helper.succeed();
	}

	/**
	 * @implements MOD-969-FUN16 — only the first and the last section of a band end it (the handrail's newel caps,
	 *     owner 2026-11-24): both halves of every section say whether a section continues it down and up; a half
	 *     written whole with the default ends (a command, the demo stand) reads them again, and taking the last
	 *     section away makes the one below it the last.
	 */
	public static void endsFollowTheBand(GameTestHelper helper) {
		build(helper, SECTIONS, full());
		assertEnds(helper, SECTIONS, "a built band");
		BlockPos second = EscalatorChain.section(BOTTOM, FACING, 1);
		helper.setBlock(second.above(), ModContent.ESCALATOR_SECTION.get().defaultBlockState()
				.setValue(EscalatorSectionBlock.FACING, FACING).setValue(EscalatorSectionBlock.HALF,
						DoubleBlockHalf.UPPER));
		assertEnds(helper, SECTIONS, "a balustrade half written with the default ends");
		helper.setBlock(EscalatorChain.section(BOTTOM, FACING, SECTIONS - 1), Blocks.AIR);
		assertEnds(helper, SECTIONS - 1, "the band without its last section");
		helper.succeed();
	}

	private static void assertEnds(GameTestHelper helper, int sections, String what) {
		for (int i = 0; i < sections; i++) {
			BlockPos lower = EscalatorChain.section(BOTTOM, FACING, i);
			for (BlockPos at : List.of(lower, lower.above())) {
				BlockState state = helper.getBlockState(at);
				boolean bottom = state.getValue(EscalatorSectionBlock.BOTTOM);
				boolean top = state.getValue(EscalatorSectionBlock.TOP);
				helper.assertTrue(bottom == (i == 0) && top == (i == sections - 1),
						what + ": section " + i + " (" + state.getValue(EscalatorSectionBlock.HALF)
								+ ") must be bottom=" + (i == 0) + " top=" + (i == sections - 1)
								+ ", is bottom=" + bottom + " top=" + top);
			}
		}
	}
}
