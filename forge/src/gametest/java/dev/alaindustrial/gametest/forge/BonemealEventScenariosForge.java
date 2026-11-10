package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.gametest.AlaGameTestHelper;
import dev.alaindustrial.registry.ModContent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.entity.player.BonemealEvent;

/**
 * Reinforced bone meal against Forge's {@link BonemealEvent} (MOD-906 on Forge, MOD-962 stage 3), the Forge twin of
 * the NeoForge lane's {@code BonemealEventScenarios}, with the same test ids.
 *
 * <p><b>The documented Forge difference.</b> {@code ForgeEventFactory.onApplyBonemeal}: a cancelled event is
 * DENIED and spends nothing; a listener answering {@link Result#ALLOW} makes Forge itself spend one meal
 * ({@code stack.shrink(1)}) and our item spends nothing more ({@code HANDLED_ELSEWHERE}). So the handled case leaves
 * {@code STACK - 1} meals here, where NeoForge leaves {@code STACK}: one meal in total, as with Forge's vanilla bone
 * meal ({@code BonemealEventsForge}).
 *
 * <p>The event bus cannot drop a lambda listener, so one listener is installed for the whole run and acts only on
 * the cells a scenario arms; every other test's bone meal goes through untouched.
 */
public final class BonemealEventScenariosForge {

	private static final Map<BlockPos, Boolean> ARMED = new ConcurrentHashMap<>();
	private static final AtomicBoolean LISTENING = new AtomicBoolean();
	private static final int STACK = 16;

	private BonemealEventScenariosForge() {
	}

	/** A mod takes the event over ({@code Result.ALLOW}): nothing grows here, Forge spent one meal, we none. */
	public static void handledByAnotherModSpendsNothing(GameTestHelper helper) {
		run(helper, true, STACK - 1);
	}

	/** A mod cancels the event (a claim refusing the use): nothing grows, nothing is spent. */
	public static void deniedByAnotherModSpendsNothing(GameTestHelper helper) {
		run(helper, false, STACK);
	}

	private static void run(GameTestHelper helper, boolean handled, int expectedLeft) {
		listen();
		BlockPos rel = new BlockPos(2, 2, 2);
		helper.setBlock(rel.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
		helper.setBlock(rel, Blocks.WHEAT);
		BlockPos abs = helper.absolutePos(rel);
		BlockState before = helper.getBlockState(rel);
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.REINFORCED_BONE_MEAL.get(), STACK));
		ARMED.put(abs.immutable(), handled);
		try {
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
			player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
					hit);
		} finally {
			ARMED.remove(abs);
		}
		if (helper.getBlockState(rel) != before) {
			helper.fail("the meal grew the wheat although another mod took or cancelled the event: "
					+ helper.getBlockState(rel));
			return;
		}
		int count = player.getMainHandItem().getCount();
		if (count != expectedLeft) {
			helper.fail("the stack holds " + count + ", expected " + expectedLeft + " (handled=" + handled
					+ ": Forge spends one meal on ALLOW, nothing on cancel; our item must spend nothing)");
			return;
		}
		helper.succeed();
	}

	private static void listen() {
		if (LISTENING.compareAndSet(false, true)) {
			BonemealEvent.BUS.addListener((BonemealEvent event) -> {
				Boolean handled = ARMED.get(event.getPos());
				if (handled == null) {
					return false;
				}
				if (handled) {
					event.setResult(Result.ALLOW);
					return false;
				}
				return true;
			});
		}
	}
}
