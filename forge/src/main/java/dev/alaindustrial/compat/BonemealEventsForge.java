package dev.alaindustrial.compat;

import dev.alaindustrial.item.misc.ReinforcedBonemeal;
import dev.alaindustrial.item.misc.ReinforcedBonemeal.Verdict;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.ForgeEventFactory;
import org.jspecify.annotations.Nullable;

/**
 * Reinforced bone meal asks Forge's {@code BonemealEvent} (MOD-962, slice 2.5), the twin of
 * {@code BonemealEventsNeoForge}: protection and moderation mods cancel it, as they do for vanilla bone meal.
 *
 * <p>Not a version facade (it is not in {@code SEAMS.md}): Forge exists only on the 26.3 line, so there is no
 * twin on another line; it sits in {@code compat} beside its NeoForge counterpart.
 *
 * <p><b>Differences from NeoForge, by Forge's own contract</b> ({@code ForgeEventFactory.onApplyBonemeal}):
 * <ul>
 *   <li>A listener answering {@code Result.ALLOW} makes Forge spend one meal itself on the server ({@code
 *       stack.shrink(1)}, creative mode included); {@code ReinforcedBoneMealItem} does not spend on
 *       {@code HANDLED_ELSEWHERE}, so one meal goes in total, as with Forge's vanilla bone meal. NeoForge spends
 *       none.</li>
 *   <li>No player (automation, a dispenser): Forge fires no event at all and answers "go on"; NeoForge fires its
 *       event with a null player. The event is not posted by hand here: Forge listeners assume a player.</li>
 * </ul>
 */
public final class BonemealEventsForge {

	private BonemealEventsForge() {
	}

	public static void install() {
		ReinforcedBonemeal.LOADER_GATE = BonemealEventsForge::check;
	}

	public static Verdict check(@Nullable Player player, Level level, BlockPos pos, BlockState state,
			ItemStack stack) {
		return verdict(ForgeEventFactory.onApplyBonemeal(player, level, pos, state, stack));
	}

	/** {@code onApplyBonemeal}: -1 the event was cancelled, 1 a listener took it over, 0 go on. */
	static Verdict verdict(int hook) {
		if (hook < 0) {
			return Verdict.DENIED;
		}
		if (hook > 0) {
			return Verdict.HANDLED;
		}
		return Verdict.PROCEED;
	}
}
