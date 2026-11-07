package dev.alaindustrial.client.tooltip;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.TooltipLayout;
import dev.alaindustrial.core.tooltip.TooltipRole;
import dev.alaindustrial.item.misc.TooltipKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * An item's own tooltip in the owner's style "09b" (MOD-958): the item names its lines by role — state,
 * figures, tier, details, modules, keys, warnings (the purpose line comes from {@link MachineTooltips}) — and
 * {@link #emit} lays them out by the same {@link TooltipLayout} the blocks use, with the hold-[Shift] hint when
 * [Shift] would show more. An item's
 * {@code appendHoverText} builds one and emits it; it writes no colour of its own.
 *
 * <p>[Shift] is read through {@link TooltipKeys} (common code may not ask the keyboard), the always-detailed
 * option from {@link AlaClientConfig}. Long lines break through {@link TooltipWrap#wrapStyled}, colours kept.
 */
public final class ItemTooltip {

	/** The label of a module line: one argument, the module's name. */
	public static final String MODULE_KEY = "tooltip.alaindustrial.module";
	/** Labels every item shares: a charge {@code (eu, capacity)}, a reach, a price per item and per orb. */
	public static final String STAT_CHARGE = "tooltip.alaindustrial.stat.charge";
	public static final String STAT_RANGE = "tooltip.alaindustrial.range";
	public static final String STAT_PER_ITEM = "tooltip.alaindustrial.stat.per_item";
	public static final String STAT_PER_ORB = "tooltip.alaindustrial.stat.per_orb";

	private final List<TooltipLayout.Placed<Component>> lines = new ArrayList<>();

	/** A two-state line: {@code onKey} with a green tick, or {@code offKey} with a grey cross. */
	public ItemTooltip state(boolean on, String onKey, String offKey) {
		return add(TooltipLayout.Slot.STATE, on
				? TooltipRender.roleLine(TooltipRole.STATE_ON, onKey, null)
				: TooltipRender.roleLine(TooltipRole.STATE_OFF, offKey, null));
	}

	/** A state that is off or spent ("Empty, needs charging"): a grey cross. */
	public ItemTooltip off(String key, Object... values) {
		return add(TooltipLayout.Slot.STATE, TooltipRender.roleLine(TooltipRole.STATE_OFF, key, null, values));
	}

	/** A line of words shown without [Shift]: the info icon, grey. */
	public ItemTooltip info(String key, Object... values) {
		return add(TooltipLayout.Slot.STAT, TooltipRender.roleLine(TooltipRole.INFO, key, null, values));
	}

	/** A figure shown without [Shift]: {@code <icon> <label(values)> <unit>}. */
	public ItemTooltip stat(TooltipRole role, String key, @Nullable String unitKey, Object... values) {
		return add(TooltipLayout.Slot.STAT, TooltipRender.roleLine(role, key, unitKey, values));
	}

	/** A line a description declares ({@code MachineTooltipSpec.Line}), in its role's slot. */
	public ItemTooltip described(MachineTooltipSpec.Line line) {
		return add(line.role().defaultSlot(), TooltipRender.line(line));
	}

	/** A figure shown under [Shift]. */
	public ItemTooltip detail(TooltipRole role, String key, @Nullable String unitKey, Object... values) {
		return add(TooltipLayout.Slot.DETAIL, TooltipRender.roleLine(role, key, unitKey, values));
	}

	/** The tier line, under [Shift]. */
	public ItemTooltip tier(MachineTooltipSpec.Tier tier) {
		return add(TooltipLayout.Slot.TIER, TooltipRender.line(tier.line()));
	}

	/** A fitted module, named in the module colour, under [Shift]. */
	public ItemTooltip module(Component name) {
		Component shown = TooltipRender.inRoleColour(TooltipRole.MODULE, name);
		return add(TooltipLayout.Slot.MODULE, TooltipRender.roleLine(TooltipRole.MODULE, MODULE_KEY, null, shown));
	}

	/** A key action ("Right-click: open the screen"), under [Shift] unless it is all the item has to say. */
	public ItemTooltip key(String key, Object... values) {
		return add(TooltipLayout.Slot.KEY, TooltipRender.roleLine(TooltipRole.KEY, key, null, values));
	}

	/** A warning: shown in every mode. */
	public ItemTooltip warning(String key, Object... values) {
		return add(TooltipLayout.Slot.WARNING, TooltipRender.roleLine(TooltipRole.WARNING, key, null, values));
	}

	/** A remark: shown in every mode. */
	public ItemTooltip note(String key, Object... values) {
		return add(TooltipLayout.Slot.NOTE, TooltipRender.roleLine(TooltipRole.NOTE, key, null, values));
	}

	private ItemTooltip add(TooltipLayout.Slot slot, Component line) {
		lines.add(new TooltipLayout.Placed<>(slot, line));
		return this;
	}

	/** Hands the lines a player sees now to {@code adder}, the hint last when [Shift] would show more. */
	public void emit(Consumer<Component> adder) {
		emit(adder, TooltipKeys.shiftDown(), AlaClientConfig.alwaysDetailedTooltips);
	}

	/** {@link #emit(Consumer)} for a given [Shift] state and always-detailed option (tests). */
	public void emit(Consumer<Component> adder, boolean shiftHeld, boolean alwaysDetailed) {
		List<Component> collapsed = TooltipLayout.arrange(lines, false);
		List<Component> expanded = TooltipLayout.arrange(lines, true);
		for (Component line : shiftHeld || alwaysDetailed ? expanded : collapsed) {
			TooltipWrap.wrapStyled(line).forEach(adder);
		}
		TooltipLayout.Hint hint = TooltipLayout.hint(collapsed.size(), expanded.size(), shiftHeld, alwaysDetailed);
		if (hint != TooltipLayout.Hint.NONE) {
			adder.accept(TooltipRender.hint(hint == TooltipLayout.Hint.KEY_HELD));
		}
	}
}
