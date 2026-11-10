package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.gametest.AlaGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Crops on Forge follow the game's own soil tags (MOD-962): the two mixins {@code CropSoilTagMixin} and
 * {@code CropGrowthSoilMixin} against the Forge patches that dropped them.
 *
 * <p>The lane's test datapack adds rooted dirt to {@code #minecraft:supports_crops} and to nothing else (the same
 * file the NeoForge lane uses), which is exactly the soil Forge 66.1.0 refuses: tagged for standing on, not
 * farmland, not in {@code #minecraft:grows_crops}. The garden drone's side of it is the roster scenario
 * {@code garden_drone_plants_on_tagged_soil}; here is the player's hand and the growth rate.
 */
public final class CropSoilScenariosForge {

	private static final BlockPos SOIL = new BlockPos(2, 1, 2);

	private CropSoilScenariosForge() {
	}

	/**
	 * Wheat seeds go onto tagged soil by hand and the crop lives there; the pitcher crop, patched the same way,
	 * lives there too. Plain dirt, which no tag names, still takes neither: the mixin widens by the tag only.
	 */
	public static void cropsTakeTaggedSoil(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(SOIL, Blocks.ROOTED_DIRT);
		helper.setBlock(SOIL.above(), Blocks.AIR);
		BlockPos soil = helper.absolutePos(SOIL);
		BlockPos cell = soil.above();
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 2));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(soil).add(0.0, 0.5, 0.0), Direction.UP, soil, false);
		player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);

		BlockState planted = level.getBlockState(cell);
		if (!planted.is(Blocks.WHEAT)) {
			helper.fail("wheat seeds did not go onto soil tagged #minecraft:supports_crops; found "
					+ planted.getBlock());
			return;
		}
		if (!planted.canSurvive(level, cell)) {
			helper.fail("the planted wheat does not survive on the tagged soil");
			return;
		}
		if (!Blocks.PITCHER_CROP.defaultBlockState().canSurvive(level, cell)) {
			helper.fail("the pitcher crop does not survive on the tagged soil");
			return;
		}
		helper.setBlock(SOIL.above(), Blocks.AIR);
		helper.setBlock(SOIL, Blocks.DIRT);
		if (Blocks.WHEAT.defaultBlockState().canSurvive(level, cell)) {
			helper.fail("wheat survives on plain dirt, which no crop tag names: the soil rule was opened too wide");
			return;
		}
		helper.succeed();
	}

	/**
	 * The soil bonus of the growth rate is the game's: farmland speeds a crop up, a soil that is only in
	 * {@code #minecraft:supports_crops} does not. Without {@code CropGrowthSoilMixin} the widened soil rule would
	 * give tagged soil the bonus on Forge alone. Measured against bare stone, so no number of the formula is
	 * repeated here.
	 */
	public static void soilBonusFollowsVanillaTag(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(SOIL.offset(dx, 0, dz), Blocks.STONE);
				helper.setBlock(SOIL.offset(dx, 1, dz), Blocks.AIR);
			}
		}
		BlockPos cell = helper.absolutePos(SOIL.above());
		float bare = GrowthProbe.speed(level, cell);
		helper.setBlock(SOIL, Blocks.ROOTED_DIRT);
		float tagged = GrowthProbe.speed(level, cell);
		helper.setBlock(SOIL, Blocks.FARMLAND);
		float farmland = GrowthProbe.speed(level, cell);

		if (tagged != bare) {
			helper.fail("soil that is only in #minecraft:supports_crops changes the growth rate: " + tagged
					+ " against " + bare + " on bare stone; vanilla reads the bonus from #minecraft:grows_crops");
			return;
		}
		if (farmland <= bare) {
			helper.fail("farmland gives no growth bonus: " + farmland + " against " + bare + " on bare stone");
			return;
		}
		helper.succeed();
	}

	/** Reaches {@code CropBlock.getGrowthSpeed}, protected and static, the way a subclass may; never instantiated. */
	private abstract static class GrowthProbe extends CropBlock {

		private GrowthProbe(Properties properties) {
			super(properties);
		}

		static float speed(BlockGetter level, BlockPos cropCell) {
			Block wheat = Blocks.WHEAT;
			return getGrowthSpeed(wheat, level, cropCell);
		}
	}
}
