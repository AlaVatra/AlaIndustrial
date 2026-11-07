package dev.alaindustrial.item.misc;

import dev.alaindustrial.core.tooltip.TooltipRole;
import dev.alaindustrial.client.tooltip.ItemTooltip;
import dev.alaindustrial.block.entity.IncubatorMode;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.machine.MachineRates;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A mutation chip (MOD-118): the tooltip says which incubator mode the chip selects, with the
 * numbers behind Shift — the same two-layer convention the item pipe set (MOD-108). The figures are
 * the server's, read live through {@link ServerBalance} when the tooltip is drawn (MOD-695, MOD-759), so
 * a retuned server describes itself truthfully to its players; the duplicate chip quotes no chance at
 * all, because every shipped duplicate recipe carries its own and the mode default never applies to it.
 */
public class MutationChipItem extends Item {

	private final IncubatorMode mode;

	public MutationChipItem(Properties properties, IncubatorMode mode) {
		super(properties);
		this.mode = mode;
	}

	// MOD-498 — Item#appendHoverText is soft-deprecated by vanilla but is the ONLY hook an item has for
	// its own tooltip lines: ItemStack#addDetailsToTooltip calls it, and vanilla itself overrides it in
	// DiscFragmentItem, HangingEntityItem and SmithingTemplateItem. Data-component TooltipProviders cover
	// data-driven components, not lines derived per item from the chip's mode as these are.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		// Two short lines instead of one long one: tooltips are not wrapped by the game.
		// Style 09b (MOD-958): what this chip does is its purpose line; the incubator's numbers under [Shift].
		ItemTooltip tooltip = new ItemTooltip().info("item.alaindustrial.mutation_chip.hint");
		if (mode == IncubatorMode.DUPLICATE) {
			tooltip.detail(TooltipRole.INFO, "item.alaindustrial.mutation_chip.tech.chance_varies", null);
		} else {
			long percent = Math.round(mode.shownBaseChance() * 100.0);
			tooltip.detail(TooltipRole.INFO, "item.alaindustrial.mutation_chip.tech.chance", null, percent);
		}
		// Mirrors the block entity's own arithmetic so the numbers cannot drift from the machine.
		// The server's numbers (MOD-695): a tooltip runs on the client, whose Config is its own file.
		int euPerTick = MachineRates.euPerTick(ServerBalance.incubatorEuPerTick(),
				ServerBalance.globalMachineSpeedMultiplier());
		int ticks = ServerBalance.scaledDuration(mode.shownBaseDuration());
		String seconds = String.format("%.0f", ticks / 20.0);
		tooltip.detail(TooltipRole.TIME, "item.alaindustrial.mutation_chip.tech.cycle", null, seconds,
						(long) euPerTick * ticks)
				.detail(TooltipRole.INFO, "item.alaindustrial.mutation_chip.tech.miss", null)
				.emit(adder);
	}
}
