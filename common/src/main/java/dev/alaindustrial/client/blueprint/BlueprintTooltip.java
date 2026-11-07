package dev.alaindustrial.client.blueprint;

import dev.alaindustrial.client.ModKeyMappings;
import dev.alaindustrial.client.tooltip.TooltipRender;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.TooltipRole;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The tooltip line that leads to a blueprint (MOD-797), and the memory of which item's tooltip is on
 * screen right now. Every tooltip — inventory, creative tab, JEI, REI — goes through the loaders' tooltip
 * hooks into {@code MachineTooltips.append}, so remembering the last item seen there is what lets the
 * blueprint key work over any of them without one hook per recipe viewer.
 */
public final class BlueprintTooltip {

	/** A tooltip counts as "on screen" for this long after it was last built, in milliseconds. */
	private static final long FRESH_MS = 250;
	/** Segments of the hold-to-open bar. */
	private static final int BAR = 12;

	private static @Nullable BlueprintDef hovered;
	private static long hoveredAt;

	private BlueprintTooltip() {
	}

	/**
	 * Add the blueprint line when {@code stack} has a blueprint, in the owner's style "09b" (MOD-958): the
	 * blueprint icon, "Hold [I]: blueprint, steps: 4" in grey with a NEW stamp until the sheet has been opened,
	 * and a filling bar while the key is being held. The line goes above the hold-[SHIFT] hint, which stays last.
	 */
	public static void append(ItemStack stack, List<Component> lines) {
		Optional<BlueprintDef> found = forStack(stack);
		// No client (a server-side test of the tooltip text): there is no key to show and no sheet to open.
		if (found.isEmpty() || Minecraft.getInstance() == null) {
			return;
		}
		BlueprintDef def = found.get();
		if (!(Minecraft.getInstance().gui.screen() instanceof BlueprintScreen)) {
			hovered = def;
			hoveredAt = System.currentTimeMillis();
		}
		float held = BlueprintHold.progress(def);
		lines.add(hintIndex(lines), held > 0f ? progressLine(held) : line(def));
	}

	/** "Hold [I]: blueprint, steps: 4", the key grey, with the NEW stamp until the sheet has been opened. */
	private static Component line(BlueprintDef def) {
		MutableComponent line = TooltipRender.roleLine(TooltipRole.BLUEPRINT, "blueprint.alaindustrial.tooltip", null,
				TooltipRender.keyName(ModKeyMappings.OPEN_BLUEPRINT.getTranslatedKeyMessage(), false),
				def.steps().size());
		if (!BlueprintProgress.isViewed(def.id())) {
			line.append(" ").append(TooltipRender.stamp(Component.translatable("blueprint.alaindustrial.tooltip.new")));
		}
		return line;
	}

	/** The hold-to-open bar: "Opening blueprint" and a bar filling as the key is held. */
	private static Component progressLine(float held) {
		return TooltipRender.roleLine(TooltipRole.BLUEPRINT, "blueprint.alaindustrial.tooltip.opening", null)
				.append(" ").append(TooltipRender.bar(Math.round(held * BAR), BAR));
	}

	/** Where the blueprint line goes: just above the hold-[SHIFT] hint, or at the end when there is none. */
	private static int hintIndex(List<Component> lines) {
		for (int i = lines.size() - 1; i >= 0; i--) {
			if (MachineTooltipSpec.HOLD_SHIFT_KEY.equals(TooltipRender.lineKey(lines.get(i)))) {
				return i;
			}
		}
		return lines.size();
	}

	/** The blueprint of the item whose tooltip is on screen, if one was built just now. */
	static Optional<BlueprintDef> hoveredNow() {
		if (hovered == null || System.currentTimeMillis() - hoveredAt > FRESH_MS) {
			return Optional.empty();
		}
		return Optional.of(hovered);
	}

	/** The blueprint {@code stack}'s item leads to, if any. */
	public static Optional<BlueprintDef> forStack(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		return BlueprintCatalog.forSubject(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
	}
}
