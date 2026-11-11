package dev.alaindustrial.item.tool.forge;

import dev.alaindustrial.compat.RightClickTransform;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/**
 * The Forge half of the right-click seam on Minecraft 26.2 (MOD-962, the twin of NeoForge's
 * {@code RightClickAbility}): what the four right-click tools declare to Forge, written once. The constants mirror
 * {@code dev.alaindustrial.compat.RightClickTransform}: {@link #HOE} and {@link #SHOVEL}.
 *
 * <h2>Why the tools need Forge subclasses on this line</h2>
 * On 26.2 {@code RightClickTransform} delegates to {@code Items.DIAMOND_HOE}/{@code DIAMOND_SHOVEL.useOn}, and
 * Forge 65 patches {@code HoeItem.useOn}/{@code ShovelItem.useOn} to ask the block
 * ({@code IForgeBlockState.getToolModifiedState} with {@code ToolActions.HOE_TILL}/{@code SHOVEL_FLATTEN}). The
 * block's default ({@code IForgeBlock.getToolModifiedState}) opens with a gate on the HELD stack,
 * {@code if (!itemStack.canPerformAction(toolAction)) return null}, which asks the item
 * ({@code IForgeItem.canPerformAction(ItemStack, ToolAction)}, {@code false} by default). The held stack is ours, so
 * the answer has to be an override on our item class — the shared {@code common/} classes cannot name a Forge type,
 * hence one thin subclass per tool, registered from {@code ModItemsForge.LOADER_ITEMS} (verified in the Forge
 * 65.1.5 sources: the {@code HoeItem}/{@code ShovelItem} patches and {@code IForgeBlock}). On 26.3 the tools use the
 * vanilla {@code minecraft:block_transformer} component, which Forge 66 does not gate, so this class and the four
 * subclasses exist on this line only.
 *
 * <p>Forge's shovel set has no douse action: the patched {@code ShovelItem.useOn} puts out a campfire without a
 * gate, so the shovel declares exactly what a vanilla shovel declares and nothing more.
 */
public enum RightClickToolAction {

	/** The vanilla hoe's set ({@code HOE_DIG}, {@code HOE_TILL}), what Forge's patched {@code HoeItem} answers. */
	HOE(ToolActions.DEFAULT_HOE_ACTIONS),

	/** The vanilla shovel's set ({@code SHOVEL_DIG}, {@code SHOVEL_FLATTEN}). */
	SHOVEL(ToolActions.DEFAULT_SHOVEL_ACTIONS);

	private final Set<ToolAction> actions;

	RightClickToolAction(Set<ToolAction> actions) {
		this.actions = actions;
	}

	/** The tool's answer to {@code canPerformAction}: the vanilla tool's own set, nothing more. */
	public boolean canPerform(ToolAction action) {
		return actions.contains(action);
	}

	/**
	 * The Forge answer to "would a hoe convert this block?" (MOD-389), asked by the hoe before its charge gate. The
	 * shared {@code RightClickTransform.wouldTill} reads vanilla's {@code HoeItem.TILLABLES}, which Forge patches out
	 * of the flow: it would miss every block a mod contributes through {@code BlockToolModificationEvent}.
	 *
	 * <p>Forge's copy of the rules in {@code IForgeBlock.getToolModifiedState} drops vanilla's DOWN-face check
	 * ({@code HoeItem.onlyIfAirAbove}), so the bare probe — and Forge's own diamond hoe — tills dirt from below.
	 * Our hoe follows the game instead (ADR-054): from below, vanilla's rules ({@code RightClickTransform.wouldTill},
	 * the map Forge keeps populated) must agree too, which leaves exactly rooted dirt — its rule has no face
	 * condition. Refusing here is enough: the hoe returns {@code PASS} before it reaches Forge's patched till.
	 *
	 * <p>{@code simulate = true}: with {@code false}, probing rooted dirt would pop a hanging root before the hoe has
	 * decided whether it can pay for the till. The call still routes through the held stack's
	 * {@code canPerformAction(HOE_TILL)} gate, so it answers for this item.
	 */
	public static boolean wouldTill(UseOnContext context) {
		if (context.getClickedFace() == Direction.DOWN && !RightClickTransform.wouldTill(context)) {
			return false;
		}
		return context.getLevel().getBlockState(context.getClickedPos())
				.getToolModifiedState(context, ToolActions.HOE_TILL, /*simulate*/ true) != null;
	}
}
