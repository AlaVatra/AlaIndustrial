package dev.alaindustrial.item.misc;

import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.client.tooltip.ItemTooltip;
import dev.alaindustrial.core.tooltip.TooltipRole;
import dev.alaindustrial.core.tooltip.TooltipUnits;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * An overclocker chip of a fixed tier (MOD-393). Three separate items rather than one stacking chip:
 * the tier is then visible on the item itself — its icon, its name, its recipe — instead of having to
 * be counted inside a slot, and each tier can be gated behind a different energy clot.
 *
 * <p>The tier a machine actually runs at is still clamped by what its voltage tier can feed
 * ({@code MachineBlockEntity.overclockerCap()}), so a chip too strong for the machine simply does not
 * fit its panel.
 */
public class OverclockerChipItem extends Item {

	private final int tier;

	public OverclockerChipItem(Properties properties, int tier) {
		super(properties);
		this.tier = tier;
	}

	// MOD-498 — Item#appendHoverText is soft-deprecated by Mojang ("internal"), but it is the only hook an
	// item has for its own tooltip lines: ItemStack#addDetailsToTooltip calls it, and vanilla itself
	// overrides it in DiscFragmentItem, HangingEntityItem and SmithingTemplateItem.
	/**
	 * Style 09b (MOD-958): what the chip does, then the two factors one machine takes from it — the operation's
	 * length and the draw, each the per-step factor raised to this chip's steps, read from the server's balance
	 * (MOD-761), so chip II says "x0.64" and "x4" without a line of its own.
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		new ItemTooltip()
				.stat(TooltipRole.SPEED, "tooltip.alaindustrial.overclocker_chip.time", null,
						TooltipUnits.factor(Math.pow(ServerBalance.overclockerSpeedFactor(), tier)))
				.stat(TooltipRole.ENERGY, "tooltip.alaindustrial.overclocker_chip.draw", TooltipUnits.EU_PER_TICK,
						TooltipUnits.factor(Math.pow(ServerBalance.overclockerEuFactor(), tier)))
				.emit(adder);
	}

	/** Steps of overclocking this chip grants: 1, 2 or 3. */
	public int tier() {
		return tier;
	}

	/** The tier of the chip in {@code stack}, or 0 when it is not an overclocker at all. */
	public static int tierOf(ItemStack stack) {
		return stack.getItem() instanceof OverclockerChipItem chip ? chip.tier() : 0;
	}
}
