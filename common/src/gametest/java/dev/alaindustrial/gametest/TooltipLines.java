package dev.alaindustrial.gametest;

import dev.alaindustrial.client.tooltip.TooltipRender;
import dev.alaindustrial.item.misc.TooltipKeys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * An item's OWN tooltip lines as the scenarios read them (MOD-958): one translatable per line, the one the line
 * was built from.
 *
 * <p>A style-09b line starts with its icon and keeps its label, its values and its unit in parts, so reading the
 * root of each line (what the scenarios did before) sees a literal and loses the line. {@link TooltipRender#lineKey}
 * finds the label instead. {@code Item#appendHoverText} is soft-deprecated by vanilla but is the only hook that
 * yields what the item computes for itself (MOD-498, the reasoning on {@code AssemblerScenarios}).
 */
final class TooltipLines {

	/** Prefixes of the style keys every item shares: a line under one of them borrows no other item's text. */
	private static final List<String> SHARED = List.of("tooltip.alaindustrial.hold_shift",
			"tooltip.alaindustrial.tier_", "tooltip.alaindustrial.stat.", "tooltip.alaindustrial.key.",
			"tooltip.alaindustrial.module", "tooltip.alaindustrial.unit.", "tooltip.alaindustrial.number.");

	private TooltipLines() {
	}

	/**
	 * The lines of {@code stack}, read as if [Shift] were {@code shift}. The [Shift] hook is installed for the
	 * length of the read and restored in a {@code finally}; scenarios run on the server thread one at a time, so
	 * no other read observes it.
	 */
	@SuppressWarnings("deprecation")
	static List<TranslatableContents> of(ItemStack stack, boolean shift) {
		List<Component> raw = new ArrayList<>();
		TooltipKeys.ClientHook previous = TooltipKeys.CLIENT;
		try {
			TooltipKeys.CLIENT = () -> shift;
			stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT, raw::add,
					TooltipFlag.NORMAL);
		} finally {
			TooltipKeys.CLIENT = previous;
		}
		List<TranslatableContents> out = new ArrayList<>();
		for (Component line : raw) {
			TranslatableContents contents = TooltipRender.lineContents(line);
			if (contents != null) {
				out.add(contents);
			}
		}
		return out;
	}

	/** Whether {@code key} is one of the style keys every item shares (the hint, a tier, a shared label). */
	static boolean isShared(String key) {
		// A stat label every block shares ("tooltip.alaindustrial.range", MOD-958 StatStyles) has no item part.
		String tooltip = "tooltip.alaindustrial.";
		boolean generic = key.startsWith(tooltip) && key.indexOf('.', tooltip.length()) < 0;
		return generic || SHARED.stream().anyMatch(key::startsWith);
	}

	/** Argument {@code index} of {@code line} as the player reads it: a value component's text, or the value. */
	static String arg(TranslatableContents line, int index) {
		Object[] args = line.getArgs();
		if (index >= args.length) {
			return "<none>";
		}
		return args[index] instanceof Component component ? component.getString() : String.valueOf(args[index]);
	}
}
