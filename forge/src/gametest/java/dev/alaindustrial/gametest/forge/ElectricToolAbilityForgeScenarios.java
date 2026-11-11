package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.gametest.AlaGameTestHelper;
import dev.alaindustrial.item.tool.forge.RightClickToolAction;
import dev.alaindustrial.registry.ModContent;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/**
 * Forge-only characterization of the four right-click tools on Minecraft 26.2 (MOD-962), the twin of
 * {@code ElectricToolAbilityNeoForgeScenarios}: the {@code ToolAction} answers their Forge subclasses give other
 * mods, and the side-effect-free probe the hoe asks before its charge gate. The bodies use Forge's own API
 * ({@code ToolAction}, {@code getToolModifiedState}), which common code cannot import, so they live in this source
 * set and are registered by {@code LineOnlyRegistrations} of this line only: on 26.3 the mechanism does not exist.
 */
public final class ElectricToolAbilityForgeScenarios {

	private ElectricToolAbilityForgeScenarios() {}

	private static final List<Supplier<Item>> HOES = List.of(ModContent.ELECTRIC_HOE,
			ModContent.ELECTRIC_HOE_DIAMOND_TIP);
	private static final List<Supplier<Item>> SHOVELS = List.of(ModContent.ELECTRIC_SHOVEL,
			ModContent.ELECTRIC_SHOVEL_DIAMOND_TIP);

	/** The plot every probe looks at, with air forced above it. */
	private static final BlockPos PLOT = new BlockPos(1, 2, 1);

	/** Fewer registered actions than this means the sweep below is reading an empty registry. */
	private static final int MIN_ACTIONS = 10;

	/**
	 * On Forge 26.2, for every registered {@code ToolAction}, each electric hoe answers
	 *     {@code canPerformAction} exactly as a vanilla diamond hoe does and exactly {@code DEFAULT_HOE_ACTIONS}
	 *     membership; each electric shovel exactly as a vanilla diamond shovel and {@code DEFAULT_SHOVEL_ACTIONS}
	 *     membership — so other mods asking about our tools get the vanilla tools' answers.
	 */
	public static void abilitiesAnswerAsTheVanillaTools(GameTestHelper helper) {
		// ToolAction.get registers on first use: touching ToolActions first fills the registry with every stock
		// action, so the sweep below sees all of them and not only those someone asked for already.
		Set<ToolAction> hoeActions = ToolActions.DEFAULT_HOE_ACTIONS;
		Collection<ToolAction> actions = List.copyOf(ToolAction.getActions());
		if (actions.size() < MIN_ACTIONS) {
			helper.fail("only " + actions.size() + " tool actions registered — the sweep would prove nothing");
			return;
		}
		String problem = sweep(actions, HOES, new ItemStack(Items.DIAMOND_HOE), hoeActions);
		if (problem == null) {
			problem = sweep(actions, SHOVELS, new ItemStack(Items.DIAMOND_SHOVEL), ToolActions.DEFAULT_SHOVEL_ACTIONS);
		}
		if (problem != null) {
			helper.fail(problem);
			return;
		}
		helper.succeed();
	}

	private static String sweep(Collection<ToolAction> actions, List<Supplier<Item>> tools, ItemStack vanilla,
			Set<ToolAction> declared) {
		for (Supplier<Item> tool : tools) {
			ItemStack stack = new ItemStack(tool.get());
			for (ToolAction action : actions) {
				boolean ours = stack.canPerformAction(action);
				if (ours != declared.contains(action) || ours != vanilla.canPerformAction(action)) {
					return stack.getItem() + ".canPerformAction(" + action.name() + ") = " + ours
							+ ", the declared set says " + declared.contains(action) + ", a vanilla "
							+ vanilla.getItem() + " says " + vanilla.canPerformAction(action);
				}
			}
		}
		return null;
	}

	/**
	 * Puts {@code state} on the plot (air above) and asks it, through {@code stack} in hand, what it becomes. A till
	 * is asked the way the hoe asks before its charge gate, through {@link RightClickToolAction#wouldTill}, which adds
	 * vanilla's DOWN-face rule to Forge's simulated modification.
	 */
	private static BlockState probe(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockState state,
			Direction face, ToolAction action) {
		helper.setBlock(PLOT, state);
		helper.setBlock(PLOT.above(), Blocks.AIR);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos abs = helper.absolutePos(PLOT);
		UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false));
		BlockState simulated = helper.getLevel().getBlockState(abs).getToolModifiedState(context, action,
				/*simulate*/ true);
		return action == ToolActions.HOE_TILL && !RightClickToolAction.wouldTill(context) ? null : simulated;
	}

	/**
	 * On Forge 26.2 the simulated tool modification through a held electric tool (the probe
	 *     the hoe asks before its charge gate) answers farmland for dirt from UP and nothing from DOWN (vanilla's
	 *     rules refuse the bottom face; Forge's {@code HOE_TILL} copy has no face check, and the hoe adds it back,
	 *     ADR-054), dirt for rooted dirt from UP and from DOWN (its rule has no face condition), nothing for stone,
	 *     and a path for grass under both shovels — and writes nothing: the block stays and no hanging root is
	 *     dropped. Forge has no douse action (its shovel puts out a campfire without a gate), so there is no
	 *     campfire case here.
	 */
	public static void simulatedProbeAnswersAndWritesNothing(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		record Case(Supplier<Item> tool, BlockState state, Direction face, ToolAction action, BlockState expected) {}
		List<Case> cases = List.of(
				new Case(ModContent.ELECTRIC_HOE, Blocks.DIRT.defaultBlockState(), Direction.UP, ToolActions.HOE_TILL,
						Blocks.FARMLAND.defaultBlockState()),
				new Case(ModContent.ELECTRIC_HOE, Blocks.DIRT.defaultBlockState(), Direction.DOWN,
						ToolActions.HOE_TILL, null),
				new Case(ModContent.ELECTRIC_HOE_DIAMOND_TIP, Blocks.DIRT.defaultBlockState(), Direction.DOWN,
						ToolActions.HOE_TILL, null),
				new Case(ModContent.ELECTRIC_HOE, Blocks.ROOTED_DIRT.defaultBlockState(), Direction.DOWN,
						ToolActions.HOE_TILL, Blocks.DIRT.defaultBlockState()),
				new Case(ModContent.ELECTRIC_HOE_DIAMOND_TIP, Blocks.ROOTED_DIRT.defaultBlockState(), Direction.UP,
						ToolActions.HOE_TILL, Blocks.DIRT.defaultBlockState()),
				new Case(ModContent.ELECTRIC_HOE, Blocks.STONE.defaultBlockState(), Direction.UP,
						ToolActions.HOE_TILL, null),
				new Case(ModContent.ELECTRIC_SHOVEL, Blocks.GRASS_BLOCK.defaultBlockState(), Direction.UP,
						ToolActions.SHOVEL_FLATTEN, Blocks.DIRT_PATH.defaultBlockState()),
				new Case(ModContent.ELECTRIC_SHOVEL_DIAMOND_TIP, Blocks.GRASS_BLOCK.defaultBlockState(), Direction.UP,
						ToolActions.SHOVEL_FLATTEN, Blocks.DIRT_PATH.defaultBlockState()));
		for (Case c : cases) {
			BlockState answer = probe(helper, player, new ItemStack(c.tool().get()), c.state(), c.face(), c.action());
			String cell = c.tool().get() + " " + c.action().name() + " on " + c.state() + " from " + c.face();
			if (answer != c.expected()) {
				helper.fail(cell + " answered " + answer + ", expected " + c.expected());
				return;
			}
			if (helper.getBlockState(PLOT) != c.state()) {
				helper.fail(cell + " is a simulation but changed the block to " + helper.getBlockState(PLOT));
				return;
			}
		}
		helper.assertItemEntityNotPresent(Items.HANGING_ROOTS, PLOT, 3.0);
		helper.succeed();
	}
}
