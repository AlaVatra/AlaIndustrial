package dev.alaindustrial.gametest;

import dev.alaindustrial.menu.BronzeLunarPanelMenu;
import dev.alaindustrial.menu.ReinforcedSolarPanelMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A right-click on a full-block solar panel (MOD-1033). The owner's check of MOD-1031 reported that the two third
 * rungs open no screen; these scenarios click them the way the server receives a player's click — through
 * {@code ServerPlayerGameMode#useItemOn}, the loader's block-use chain included — on the face a player looking down
 * hits, on the sides, with an empty hand and with something in it.
 *
 * <p>The planned rungs T4 to T8 have no block entity and no function yet (MOD-1031), so they have no screen: a click
 * on them passes, so a block in hand is placed against them as against any plain block.
 */
public final class FullSolarPanelUseScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FullSolarPanelUseScenarios::thirdRungsOpenTheirScreen,
						"mod1033_third_rung_panels_open_their_screen").ticks(20),
				RosterEntry.of(FullSolarPanelUseScenarios::plannedRungsPassTheClick,
						"mod1033_planned_panels_pass_the_click").ticks(20));

		private Roster() {}
	}

	private FullSolarPanelUseScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 1, 1);
	private static final Direction[] FACES = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH,
			Direction.WEST};

	/**
	 * Both third rungs open their own screen from every face a player can reach, empty-handed and holding a block
	 * or a chip.
	 *
	 * @implements MOD-1033-T3-SCREEN — a right-click on a T3 solar panel opens its screen
	 */
	public static void thirdRungsOpenTheirScreen(GameTestHelper helper) {
		expectScreen(helper, ModContent.REINFORCED_SOLAR_PANEL.get(), ReinforcedSolarPanelMenu.class);
		expectScreen(helper, ModContent.BRONZE_LUNAR_PANEL.get(), BronzeLunarPanelMenu.class);
		helper.succeed();
	}

	/**
	 * The planned rungs answer a click with PASS and open nothing.
	 *
	 * @implements MOD-1033-PLANNED-NO-SCREEN — T4 to T8 have no screen until they have a function
	 */
	public static void plannedRungsPassTheClick(GameTestHelper helper) {
		for (Block planned : List.of(ModContent.ELECTRUM_SOLAR_PANEL.get(), ModContent.ULTIMATE_SOLAR_PANEL.get(),
				ModContent.SILVER_LUNAR_PANEL.get(), ModContent.ULTIMATE_LUNAR_PANEL.get())) {
			helper.setBlock(POS, planned);
			ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
			InteractionResult result = click(helper, player, Direction.UP);
			helper.assertTrue(player.containerMenu == player.inventoryMenu,
					planned + " opened " + player.containerMenu.getClass().getSimpleName());
			helper.assertTrue(result == InteractionResult.PASS, planned + " answered " + result + ", not PASS");
		}
		helper.succeed();
	}

	private static void expectScreen(GameTestHelper helper, Block panel, Class<? extends AbstractContainerMenu> menu) {
		helper.setBlock(POS, panel);
		List<ItemStack> hands = List.of(ItemStack.EMPTY, new ItemStack(panel.asItem()),
				new ItemStack(ModContent.ALIGNMENT_CHIP_NIGHT.get()), new ItemStack(ModContent.MUTE_CHIP.get()));
		for (ItemStack held : hands) {
			for (Direction face : FACES) {
				ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
				player.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
				InteractionResult result = click(helper, player, face);
				String what = panel + " clicked on its " + face + " face holding "
						+ (held.isEmpty() ? "nothing" : held.getItem());
				helper.assertTrue(menu.isInstance(player.containerMenu),
						what + " opened " + player.containerMenu.getClass().getSimpleName() + " (result " + result
								+ "), expected " + menu.getSimpleName());
				helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(POS)).is(panel),
						what + " changed the block");
				player.closeContainer();
			}
		}
	}

	/** A real right-click on {@code face}, at the face itself as a player's crosshair would hit it. */
	private static InteractionResult click(GameTestHelper helper, ServerPlayer player, Direction face) {
		BlockPos abs = helper.absolutePos(POS);
		Vec3 at = Vec3.atCenterOf(abs).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		BlockHitResult hit = new BlockHitResult(at, face, abs, false);
		return player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(InteractionHand.MAIN_HAND),
				InteractionHand.MAIN_HAND, hit);
	}
}
