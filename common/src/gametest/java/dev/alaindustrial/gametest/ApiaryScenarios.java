package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HiveAttachmentBlock;
import dev.alaindustrial.item.apiary.ApiaryMapItem;
import dev.alaindustrial.item.apiary.ApiaryMapPoints;
import dev.alaindustrial.menu.ApiaryMapMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The apiary, slice 1 (MOD-923): the hive attachment's placement rule, its mirror of the hive's honey level, the drain
 * steps and their self-restore, and the apiary map's editing rules. Each scenario asserts both sides of its rule — a
 * refusal next to an acceptance — so it can fail.
 */
final class ApiaryScenarios {

	private ApiaryScenarios() {}

	public static final class Roster {
		private Roster() {}

		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ApiaryScenarios::attachmentGoesOnSidesAndTopOnly, "apiary_attachment_placement_rules")
						.ticks(40),
				RosterEntry.of(ApiaryScenarios::attachmentPopsWhenItsHiveGoes, "apiary_attachment_pops_off").ticks(40),
				RosterEntry.of(ApiaryScenarios::attachmentMirrorsTheHoneyLevel, "apiary_attachment_mirrors_honey")
						.ticks(40),
				RosterEntry.of(ApiaryScenarios::drainStepsDownAndRestores, "apiary_attachment_drain_steps_restore")
						.ticks(100),
				RosterEntry.of(ApiaryScenarios::mapWritesCrossesOutAndCaps, "apiary_map_edit_rules").ticks(20),
				RosterEntry.of(ApiaryScenarios::mapScreenCrossesOutARow, "apiary_map_screen_cross_out").ticks(20));
	}

	/** The hive every scenario uses; its entrance faces north. */
	private static final BlockPos HIVE = new BlockPos(3, 2, 3);

	private static BlockState hive(Direction entrance, int honey) {
		return Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.FACING, entrance)
				.setValue(BeehiveBlock.HONEY_LEVEL, honey);
	}

	private static Player player(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		BlockPos abs = helper.absolutePos(new BlockPos(0, 2, 0));
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	/** Uses an attachment item on face {@code face} of the block at {@code target}, as a right-click does. */
	private static void place(GameTestHelper helper, Player player, BlockPos target, Direction face) {
		ItemStack stack = new ItemStack(ModContent.HIVE_ATTACHMENT_ITEM.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos abs = helper.absolutePos(target);
		Vec3 hit = Vec3.atCenterOf(abs).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, abs, false)));
	}

	private static boolean jarAt(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos).getBlock() instanceof HiveAttachmentBlock;
	}

	private static void expectPlaced(GameTestHelper helper, Player player, Direction face, boolean placed,
			String why) {
		BlockPos cell = HIVE.relative(face);
		place(helper, player, HIVE, face);
		if (jarAt(helper, cell) != placed) {
			helper.fail("a jar on the hive's " + face + " face was " + (placed ? "refused" : "placed") + ": " + why);
		}
	}

	/**
	 * Three sides and the top take a jar; the entrance face and the bottom do not; a second jar on the same hive is
	 * refused, as is a jar in front of a neighbouring hive's entrance or against a block that is no hive.
	 */
	public static void attachmentGoesOnSidesAndTopOnly(GameTestHelper helper) {
		Player player = player(helper);
		helper.setBlock(HIVE, hive(Direction.NORTH, 0));
		expectPlaced(helper, player, Direction.NORTH, false, "the entrance locks the bees in");
		expectPlaced(helper, player, Direction.DOWN, false, "the bottom face is not allowed");
		for (Direction face : new Direction[] {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.UP}) {
			expectPlaced(helper, player, face, true, "a side or the top is allowed");
			BlockState jar = helper.getBlockState(HIVE.relative(face));
			if (jar.getValue(HiveAttachmentBlock.FACING) != face) {
				helper.fail("the jar on the " + face + " face faces " + jar.getValue(HiveAttachmentBlock.FACING));
			}
			// one per hive: with this jar standing no other face takes a second one
			Direction other = face == Direction.EAST ? Direction.WEST : Direction.EAST;
			expectPlaced(helper, player, other, false, "the hive already has a jar");
			helper.setBlock(HIVE.relative(face), Blocks.AIR);
		}
		// a stone block is no hive
		BlockPos stone = new BlockPos(3, 2, 7);
		helper.setBlock(stone, Blocks.STONE);
		place(helper, player, stone, Direction.UP);
		if (jarAt(helper, stone.above())) {
			helper.fail("a jar was placed on stone");
		}
		// the cell east of the hive is right in front of a bee nest's entrance two cells east: refused
		BlockPos nest = HIVE.east(2);
		helper.setBlock(nest, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING, Direction.WEST));
		expectPlaced(helper, player, Direction.EAST, false, "the cell is in front of the bee nest's entrance");
		helper.setBlock(nest, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING, Direction.SOUTH));
		expectPlaced(helper, player, Direction.EAST, true, "the nest turned away, the cell is free again");
		helper.succeed();
	}

	/** Breaking the hive, turning its entrance at the jar, or a new hive's entrance at the jar pop the jar off. */
	public static void attachmentPopsWhenItsHiveGoes(GameTestHelper helper) {
		Player player = player(helper);
		BlockPos jar = HIVE.east();
		helper.setBlock(HIVE, hive(Direction.NORTH, 2));
		place(helper, player, HIVE, Direction.EAST);
		if (!jarAt(helper, jar)) {
			helper.fail("the jar was not placed on the hive's east face");
		}
		helper.setBlock(HIVE, Blocks.AIR);
		if (jarAt(helper, jar)) {
			helper.fail("the jar stayed after its hive was broken");
		}
		helper.assertItemEntityPresent(ModContent.HIVE_ATTACHMENT_ITEM.get(), jar, 2.0);
		helper.killAllEntities();

		helper.setBlock(HIVE, hive(Direction.NORTH, 2));
		place(helper, player, HIVE, Direction.EAST);
		helper.setBlock(HIVE, hive(Direction.EAST, 2));
		if (jarAt(helper, jar)) {
			helper.fail("the jar stayed after its hive turned its entrance at it");
		}
		helper.killAllEntities();

		helper.setBlock(HIVE, hive(Direction.NORTH, 2));
		place(helper, player, HIVE, Direction.EAST);
		if (!jarAt(helper, jar)) {
			helper.fail("the jar was not placed again");
		}
		helper.setBlock(jar.east(), Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.FACING, Direction.WEST));
		if (jarAt(helper, jar)) {
			helper.fail("the jar stayed in front of a hive placed later with its entrance at it");
		}
		helper.succeed();
	}

	/** The jar shows the hive's level 0..5, whatever writes it, vanilla's reset included. */
	public static void attachmentMirrorsTheHoneyLevel(GameTestHelper helper) {
		Player player = player(helper);
		BlockPos jar = HIVE.above();
		helper.setBlock(HIVE, hive(Direction.NORTH, 3));
		place(helper, player, HIVE, Direction.UP);
		expectLevel(helper, jar, 3, "placed on a hive at 3");
		for (int level = 0; level <= BeehiveBlock.MAX_HONEY_LEVELS; level++) {
			helper.setBlock(HIVE, hive(Direction.NORTH, level));
			expectLevel(helper, jar, level, "the hive at " + level);
		}
		BlockPos abs = helper.absolutePos(HIVE);
		BlockState full = helper.getLevel().getBlockState(abs);
		((BeehiveBlock) Blocks.BEEHIVE).resetHoneyLevel(helper.getLevel(), full, abs);
		expectLevel(helper, jar, 0, "after vanilla's resetHoneyLevel");
		helper.succeed();
	}

	private static void expectLevel(GameTestHelper helper, BlockPos jar, int level, String when) {
		BlockState state = helper.getBlockState(jar);
		if (!(state.getBlock() instanceof HiveAttachmentBlock)) {
			helper.fail("no jar at " + jar + " " + when);
		}
		if (state.getValue(HiveAttachmentBlock.HONEY_LEVEL) != level) {
			helper.fail("the jar shows " + state.getValue(HiveAttachmentBlock.HONEY_LEVEL) + ", expected " + level
					+ " " + when);
		}
	}

	/**
	 * Draining steps the jar down one level at a time and pauses the mirror (a bee's delivery mid-drain does not lift
	 * it); {@code restore} takes the hive's level at once, and an unfinished drain lets go by itself after
	 * {@link HiveAttachmentBlock#RESTORE_TICKS}.
	 */
	public static void drainStepsDownAndRestores(GameTestHelper helper) {
		Player player = player(helper);
		BlockPos jar = HIVE.west();
		BlockPos abs = helper.absolutePos(jar);
		Level level = helper.getLevel();
		helper.setBlock(HIVE, hive(Direction.NORTH, 5));
		place(helper, player, HIVE, Direction.WEST);
		expectLevel(helper, jar, 5, "on a full hive");
		HiveAttachmentBlock.stepDown(level, abs);
		HiveAttachmentBlock.stepDown(level, abs);
		expectLevel(helper, jar, 3, "after two drain steps");
		if (!helper.getBlockState(jar).getValue(HiveAttachmentBlock.DRAINING)) {
			helper.fail("a draining jar is not marked draining");
		}
		helper.setBlock(HIVE, hive(Direction.NORTH, 4));
		helper.setBlock(HIVE, hive(Direction.NORTH, 5));
		expectLevel(helper, jar, 3, "while draining, after the hive changed (a bee's delivery)");
		HiveAttachmentBlock.restore(level, abs);
		expectLevel(helper, jar, 5, "after restore");
		if (helper.getBlockState(jar).getValue(HiveAttachmentBlock.DRAINING)) {
			helper.fail("a restored jar is still marked draining");
		}
		// five steps empty the jar, a sixth has nothing to take
		for (int i = 0; i < 5; i++) {
			HiveAttachmentBlock.stepDown(level, abs);
		}
		expectLevel(helper, jar, 0, "after five drain steps");
		if (HiveAttachmentBlock.stepDown(level, abs)) {
			helper.fail("an empty jar took a sixth drain step");
		}
		helper.runAfterDelay(HiveAttachmentBlock.RESTORE_TICKS + 5, () -> {
			expectLevel(helper, jar, 5, "once the self-restore tick ran with nobody ending the drain");
			if (helper.getBlockState(jar).getValue(HiveAttachmentBlock.DRAINING)) {
				helper.fail("the self-restore left the jar marked draining");
			}
			helper.succeed();
		});
	}

	/**
	 * The map writes a jar once, refuses the ninth, crosses out only what it holds, and keeps a jar of another
	 * dimension as written.
	 */
	public static void mapWritesCrossesOutAndCaps(GameTestHelper helper) {
		ItemStack map = new ItemStack(ModContent.APIARY_MAP.get());
		BlockPos base = helper.absolutePos(HIVE);
		GlobalPos first = GlobalPos.of(Level.OVERWORLD, base);
		expectEdit(helper, map, first, false, ApiaryMapItem.Edit.WRITTEN, 1);
		expectEdit(helper, map, first, false, ApiaryMapItem.Edit.ALREADY, 1);
		GlobalPos nether = GlobalPos.of(Level.NETHER, base);
		expectEdit(helper, map, nether, false, ApiaryMapItem.Edit.WRITTEN, 2);
		for (int i = 2; i < ApiaryMapPoints.MAX; i++) {
			expectEdit(helper, map, GlobalPos.of(Level.OVERWORLD, base.east(i)), false, ApiaryMapItem.Edit.WRITTEN,
					i + 1);
		}
		expectEdit(helper, map, GlobalPos.of(Level.OVERWORLD, base.north(5)), false, ApiaryMapItem.Edit.FULL,
				ApiaryMapPoints.MAX);
		if (!ApiaryMapItem.points(map).contains(nether)) {
			helper.fail("the other dimension's jar was lost from the map");
		}
		expectEdit(helper, map, first, true, ApiaryMapItem.Edit.CROSSED_OUT, ApiaryMapPoints.MAX - 1);
		expectEdit(helper, map, first, true, ApiaryMapItem.Edit.NOT_ON_MAP, ApiaryMapPoints.MAX - 1);
		expectEdit(helper, map, GlobalPos.of(Level.OVERWORLD, base.north(5)), false, ApiaryMapItem.Edit.WRITTEN,
				ApiaryMapPoints.MAX);
		helper.succeed();
	}

	private static void expectEdit(GameTestHelper helper, ItemStack map, GlobalPos pos, boolean crossOut,
			ApiaryMapItem.Edit expected, int size) {
		ApiaryMapItem.Edit edit = ApiaryMapItem.edit(map, pos, crossOut);
		int now = ApiaryMapItem.points(map).size();
		if (edit != expected || now != size) {
			helper.fail((crossOut ? "crossing out " : "writing ") + pos + " gave " + edit + " with " + now
					+ " rows, expected " + expected + " with " + size);
		}
	}

	/** The list's button crosses out exactly its row of the map in hand; a row the map lacks is refused. */
	public static void mapScreenCrossesOutARow(GameTestHelper helper) {
		Player player = player(helper);
		ItemStack map = new ItemStack(ModContent.APIARY_MAP.get());
		BlockPos base = helper.absolutePos(HIVE);
		for (int i = 0; i < 3; i++) {
			ApiaryMapItem.edit(map, GlobalPos.of(Level.OVERWORLD, base.east(i)), false);
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, map);
		ApiaryMapMenu menu = new ApiaryMapMenu(0, player.getInventory());
		if (!menu.clickMenuButton(player, 1)) {
			helper.fail("the button of row 1 was refused");
		}
		ApiaryMapPoints left = ApiaryMapItem.points(player.getMainHandItem());
		if (left.size() != 2 || left.contains(GlobalPos.of(Level.OVERWORLD, base.east(1)))
				|| !left.contains(GlobalPos.of(Level.OVERWORLD, base.east(2)))) {
			helper.fail("crossing out row 1 left " + left.points());
		}
		if (menu.clickMenuButton(player, 5)) {
			helper.fail("a button past the last row was accepted");
		}
		if (ApiaryMapItem.points(player.getMainHandItem()).size() != 2) {
			helper.fail("a refused button still changed the map");
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		if (menu.stillValid(player)) {
			helper.fail("the list stays open with no map in hand");
		}
		helper.succeed();
	}
}
