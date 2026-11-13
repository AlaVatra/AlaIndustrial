package dev.alaindustrial.client.tooltip;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.TooltipRole;
import dev.alaindustrial.core.tooltip.TooltipUnits;
import dev.alaindustrial.item.misc.MutationGrades;
import dev.alaindustrial.mutation.MutationGrade;
import dev.alaindustrial.item.tool.AnalyzerMode;
import dev.alaindustrial.item.energy.BatteryItem;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.energy.PoweredItem;
import dev.alaindustrial.item.energy.PoweredToolTooltip;
import dev.alaindustrial.item.tool.NetworkAnalyzerItem;
import dev.alaindustrial.item.tool.NetworkScanData;
import dev.alaindustrial.item.energy.PouchItem;
import dev.alaindustrial.item.misc.ShieldingPouchItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import dev.alaindustrial.client.AlaClientConfig;
import org.jspecify.annotations.Nullable;

/**
 * Loader-neutral hover-tooltip content for the mod's block items, its powered items and the Network Analyzer
 * (MOD-022). Each loader's client hooks its own tooltip event and calls {@link #append}: Fabric via
 * {@code ItemTooltipCallback}, NeoForge via {@code ItemTooltipEvent}.
 *
 * <p><b>A block's tooltip is declared by the block</b> (MOD-716, ADR-040): a block implementing
 * {@link HasMachineTooltip} hands over its {@link MachineTooltipSpec}, and this class only renders it — it keeps
 * no list of machines. The four modes ([SHIFT] up/down x the client's {@code showEuNumbers}) are decided by the
 * description, not by four parallel lists here.
 *
 * <p>Client-side code, though it links no client-only type: it reads the player's {@link AlaClientConfig}
 * (always-detailed tooltips, EU numbers shown) and the server's balance through {@link ServerBalance}. The
 * "is shift held" check is passed in as a boolean.
 */
public final class MachineTooltips {
	private MachineTooltips() {
	}

	/**
	 * Append Ala Industrial tooltip lines for {@code stack} (machine stats or analyzer reading), then the
	 * blueprint line when the item has a blueprint (MOD-797): under the stats, above the hold-[SHIFT] hint
	 * (MOD-958), which stays the last line.
	 */
	public static void append(ItemStack stack, List<Component> lines, boolean shiftDown) {
		// The name is the first line the game hands over; a list without it (a test) takes the purpose first.
		int underName = Math.min(1, lines.size());
		appendStats(stack, lines, shiftDown);
		dev.alaindustrial.client.blueprint.BlueprintTooltip.append(stack, lines);
		insertPurpose(stack, lines, underName);
	}

	/**
	 * The purpose line of style 09b (MOD-958), right under the name (index {@code at}):
	 * {@code tooltip.alaindustrial.<id>.purpose} of every item and block of the mod, shown when the language has
	 * it. One place for blocks and items alike, so an item's own {@code appendHoverText} lines, which the game
	 * adds before this hook runs, still come after it.
	 */
	private static void insertPurpose(ItemStack stack, List<Component> lines, int at) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (!Industrialization.MOD_ID.equals(id.getNamespace())) {
			return;
		}
		String key = purposeKey(id.getPath());
		if (!Language.getInstance().has(key)) {
			return;
		}
		lines.addAll(at,
				TooltipWrap.wrapStyled(TooltipRender.roleLine(TooltipRole.PURPOSE, key, null)));
	}

	/** The translation key of an item's purpose line: {@code tooltip.alaindustrial.<path>.purpose}. */
	public static String purposeKey(String path) {
		return "tooltip.alaindustrial." + path + ".purpose";
	}

	/**
	 * The stat lines proper. size-justified: one ordered dispatch over every item family that writes its own
	 * tooltip (graded, dyed, analyzer, pouches, storage, batteries, powered tools, components, machines);
	 * the first match returns, so splitting it would only spread that order across files.
	 */
	private static void appendStats(ItemStack stack, List<Component> lines, boolean shiftDown) {
		// A mutated stack carries its grade regardless of what item it is, so this runs before the
		// mod-item branches below (a graded vanilla ingot would never reach them).
		MutationGrade grade = MutationGrades.get(stack);
		if (grade.isMarked()) {
			// MOD-498 — Rarity#color() is deprecated by NeoForge only. Vanilla's Rarity has color() and
			// nothing else; the replacement NeoForge names, getStyleModifier(), is added by its patch and is
			// absent from the vanilla class this shared body is compiled against for Fabric. color() is the
			// only accessor that exists on both loaders.
			@SuppressWarnings("deprecation")
			ChatFormatting gradeColor = MutationGrades.vanillaRarity(grade).color();
			lines.add(Component.translatable(grade.translationKey()).withStyle(gradeColor));
		}
		// MOD-666: a dyed cable names its colour with vanilla's own "Color: %s" line and colour names,
		// so the line reads the same as dyed leather in every language the game ships.
		net.minecraft.world.item.DyeColor cableColor = stack.get(dev.alaindustrial.registry.ModDataComponents.CABLE_COLOR.get());
		if (cableColor != null) {
			lines.add(Component.translatable("item.color",
					Component.translatable("color.minecraft." + cableColor.getName())).withStyle(ChatFormatting.GRAY));
		}
		boolean detailed = shiftDown || AlaClientConfig.alwaysDetailedTooltips;
		if (stack.getItem() instanceof NetworkAnalyzerItem) {
			analyzerTooltip(stack).emit(lines::add, shiftDown, AlaClientConfig.alwaysDetailedTooltips);
			return;
		}
		if (stack.getItem() instanceof PouchItem) {
			ItemTooltip tooltip = pouchTooltip(stack);
			if (stack.getItem() instanceof ShieldingPouchItem) {
				// The two lines that are the whole reason this tier exists (MOD-545).
				String shielded = "tooltip.alaindustrial.shielding_pouch.shielded";
				tooltip.state(true, shielded, shielded)
						.note("tooltip.alaindustrial.shielding_pouch.not_a_suit");
			}
			tooltip.emit(lines::add, shiftDown, AlaClientConfig.alwaysDetailedTooltips);
			return;
		}
		if (storageSlots(stack) > 0) {
			storageTooltip(stack).emit(lines::add, shiftDown, AlaClientConfig.alwaysDetailedTooltips);
			return;
		}
		if (stack.getItem() instanceof BatteryItem) {
			batteryTooltip(stack).emit(lines::add, shiftDown, AlaClientConfig.alwaysDetailedTooltips);
			return;
		}
		// A powered tool or wearable declares its own tooltip (MOD-716, ADR-040).
		if (stack.getItem() instanceof PoweredItem powered) {
			PoweredToolTooltip tool = powered.toolTooltip();
			if (tool != null) {
				poweredToolTooltip(stack, tool).emit(lines::add, shiftDown, AlaClientConfig.alwaysDetailedTooltips);
				return;
			}
		}
		// Plain-item components (not BlockItem) — the windmill rotor is the only such item with a
		// tooltip. Its line describes what it does in the wind mill, not standalone stats.
		if (stack.is(ModContent.WINDMILL_ROTOR.get())) {
			addRotorTooltip(lines, detailed);
			return;
		}
		if (stack.is(ModContent.DRILL_COLUMN_MODULE.get())) {
			boreModuleTooltip("drill_column_module").emit(lines::add, shiftDown, AlaClientConfig.alwaysDetailedTooltips);
			return;
		}
		if (stack.is(ModContent.DRILL_ROW_MODULE.get())) {
			boreModuleTooltip("drill_row_module").emit(lines::add, shiftDown, AlaClientConfig.alwaysDetailedTooltips);
			return;
		}
		if (stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof HasMachineTooltip owner) {
			for (MachineTooltipSpec.Line line : owner.machineTooltip().lines(shiftDown,
					AlaClientConfig.alwaysDetailedTooltips, AlaClientConfig.showEuNumbers)) {
				if (owner.wrapsTooltipLines()) {
					lines.addAll(TooltipWrap.wrapStyled(render(line)));
				} else {
					lines.add(render(line));
				}
			}
		}
	}

	/** One described line as a chat component, through the one renderer of roles ({@link TooltipRender}). */
	static Component render(MachineTooltipSpec.Line line) {
		return TooltipRender.line(line);
	}

	/**
	 * The Network Analyzer (MOD-016, MOD-047) in style 09b (MOD-958): the active mode (TRAVERSE / STOP_AT_STORAGE)
	 * always; the last scan, stored on the item the moment it is used, under [SHIFT], so the reading persists in
	 * the inventory after the actionbar message fades; the right-clicks as a key line.
	 */
	private static ItemTooltip analyzerTooltip(ItemStack stack) {
		AnalyzerMode mode = stack.get(ModDataComponents.NETWORK_ANALYZER_MODE.get());
		if (mode == null) {
			mode = AnalyzerMode.TRAVERSE;
		}
		ItemTooltip tooltip = new ItemTooltip().stat(TooltipRole.MODULE,
				"tooltip.alaindustrial.network_analyzer.mode_label", null,
				Component.translatable("tooltip.alaindustrial.network_analyzer.mode." + mode.getSerializedName()));
		NetworkScanData scan = stack.get(ModDataComponents.NETWORK_SCAN.get());
		if (scan != null) {
			tooltip.detail(TooltipRole.INFO, "tooltip.alaindustrial.network_analyzer.last_scan", null)
					.detail(TooltipRole.INFO, "tooltip.alaindustrial.network_analyzer.cables", null, scan.cables())
					.detail(TooltipRole.INFO, "tooltip.alaindustrial.network_analyzer.endpoints", null,
							scan.producers(), scan.consumers(), scan.storage())
					.detail(TooltipRole.ENERGY, "tooltip.alaindustrial.network_analyzer.flow", null, scan.supply(),
							scan.demand())
					.detail(TooltipRole.ENERGY, "tooltip.alaindustrial.network_analyzer.moved", null, scan.moved());
		}
		return tooltip.key("tooltip.alaindustrial.network_analyzer.usage");
	}

	/**
	 * Capacity of the mod's storage blocks (MOD-600), under [SHIFT], in style 09b (MOD-958). A chest tier tells the
	 * player nothing about its size until it is placed and opened, and the ladder runs from 36 to 108. <b>The
	 * numbers come from the block entities themselves</b>, never from a literal here: a tooltip that states a
	 * capacity the container does not have is worse than no tooltip.
	 */
	private static ItemTooltip storageTooltip(ItemStack stack) {
		int slots = storageSlots(stack);
		ItemTooltip tooltip = new ItemTooltip().detail(TooltipRole.INFO, "tooltip.alaindustrial.storage_slots", null,
				slots);
		// A module's number is misleading on its own: touching modules pool into one warehouse.
		return stack.is(ModContent.STORAGE_MODULE_ITEM.get())
				? tooltip.detail(TooltipRole.INFO, "tooltip.alaindustrial.storage_module_merges", null)
				: tooltip.detail(TooltipRole.INFO, "tooltip.alaindustrial.storage_slots_double", null, slots * 2);
	}

	/** Slots of the storage block behind {@code stack}, or 0 when it is not one of ours. */
	private static int storageSlots(ItemStack stack) {
		if (stack.is(ModContent.IRON_CHEST_ITEM.get())) {
			return dev.alaindustrial.block.entity.IronChestBlockEntity.CONTAINER_SIZE;
		}
		if (stack.is(ModContent.SILVER_CHEST_ITEM.get())) {
			return dev.alaindustrial.block.entity.SilverChestBlockEntity.CONTAINER_SIZE;
		}
		if (stack.is(ModContent.GOLD_CHEST_ITEM.get())) {
			return dev.alaindustrial.block.entity.GoldChestBlockEntity.CONTAINER_SIZE;
		}
		if (stack.is(ModContent.ELECTRUM_CHEST_ITEM.get())) {
			return dev.alaindustrial.block.entity.ElectrumChestBlockEntity.CONTAINER_SIZE;
		}
		if (stack.is(ModContent.DIAMOND_CHEST_ITEM.get())) {
			return dev.alaindustrial.block.entity.DiamondChestBlockEntity.CONTAINER_SIZE;
		}
		if (stack.is(ModContent.SHIELDING_CHEST_ITEM.get())) {
			return dev.alaindustrial.block.entity.ShieldingChestBlockEntity.CONTAINER_SIZE;
		}
		if (stack.is(ModContent.STORAGE_MODULE_ITEM.get())) {
			return dev.alaindustrial.block.entity.StorageModuleBlockEntity.CONTAINER_SIZE;
		}
		return 0;
	}

	/**
	 * The Battery Pouch (MOD-052) in style 09b (MOD-958): its charge, or the grey "depleted" (the lock state must be
	 * readable at a glance), a warning when the charge runs low, and the two right-clicks under [Shift]. No tier
	 * line: the pouch is a tier-less consumer item. The contents grid and the weight bar are visual — see
	 * {@code PouchClientTooltip}. The Shielding Pouch IS one, plus lead: its charge keys are the base pouch's on
	 * purpose (one sentence about the same buffer, not a second copy for every locale to drift on).
	 */
	private static ItemTooltip pouchTooltip(ItemStack stack) {
		ItemTooltip tooltip = new ItemTooltip();
		long eu = ItemEnergy.shownGet(stack);
		long cap = ItemEnergy.shownCapacity(stack);
		if (eu <= 0) {
			tooltip.off("tooltip.alaindustrial.battery_pouch.depleted");
		} else {
			tooltip.stat(TooltipRole.ENERGY, ItemTooltip.STAT_CHARGE, TooltipUnits.EU, eu, cap);
			if (cap > 0 && eu * 10 < cap) {
				tooltip.warning("tooltip.alaindustrial.battery_pouch.low_charge");
			}
		}
		return tooltip.key("tooltip.alaindustrial.battery_pouch.usage_insert")
				.key("tooltip.alaindustrial.battery_pouch.usage_extract");
	}

	/**
	 * The Battery (MOD-083) in style 09b (MOD-958): its charge or "empty", what the whole stack is worth when the
	 * player holds more than one, and the right-click under [Shift].
	 *
	 * <p>The stack line exists because the battery is the only powered item that stacks, and its charge is
	 * stored per item: without it, sixteen full batteries would read as "2 000 EU" and the player would
	 * have no way to see the 32 000 they are actually carrying.
	 */
	private static ItemTooltip batteryTooltip(ItemStack stack) {
		ItemTooltip tooltip = new ItemTooltip();
		long eu = ItemEnergy.shownGet(stack);
		long cap = ItemEnergy.shownCapacity(stack);
		if (eu <= 0) {
			tooltip.off("tooltip.alaindustrial.battery.depleted");
		} else {
			tooltip.stat(TooltipRole.ENERGY, ItemTooltip.STAT_CHARGE, TooltipUnits.EU, eu, cap);
			if (stack.getCount() > 1) {
				tooltip.stat(TooltipRole.ENERGY, "tooltip.alaindustrial.battery.stack_total", null,
						stack.getCount(), ItemEnergy.shownStackGet(stack));
			}
		}
		return tooltip.key("tooltip.alaindustrial.battery.usage");
	}

	/**
	 * A powered tool's tooltip (MOD-716) in style 09b (MOD-958): the price of one use (an energy figure; a tool
	 * with no price says how it is used instead), its state lines, the charge {@code eu / capacity} with the shared
	 * label, or a grey "depleted" once the item's own rule says it can no longer work. No [SHIFT] gate: the charge
	 * is what a player checks; what the tool is for is its purpose line.
	 */
	private static ItemTooltip poweredToolTooltip(ItemStack stack, PoweredToolTooltip tooltip) {
		ItemTooltip out = new ItemTooltip();
		Object[] usage = tooltip.usageValues();
		String usageKey = tooltip.usageKey();
		// No usage key: the tool says how it is used under [SHIFT], in its details (MOD-977).
		if (usageKey != null && usage.length > 0) {
			out.stat(TooltipRole.ENERGY, usageKey, null, usage);
		} else if (usageKey != null) {
			out.info(usageKey);
		}
		addStateLines(stack, tooltip.beforeCharge(), out);
		if (tooltip.depleted().test(stack)) {
			out.off(tooltip.depletedKey());
		} else {
			out.stat(TooltipRole.ENERGY, ItemTooltip.STAT_CHARGE, TooltipUnits.EU, ItemEnergy.shownGet(stack),
					ItemEnergy.shownCapacity(stack));
		}
		addStateLines(stack, tooltip.afterCharge(), out);
		for (Function<ItemStack, MachineTooltipSpec.@Nullable Line> detail : tooltip.details()) {
			MachineTooltipSpec.Line line = detail.apply(stack);
			if (line != null) {
				out.detail(line);
			}
		}
		return out;
	}

	private static void addStateLines(ItemStack stack,
			List<Function<ItemStack, MachineTooltipSpec.@Nullable Line>> states, ItemTooltip out) {
		for (Function<ItemStack, MachineTooltipSpec.@Nullable Line> state : states) {
			MachineTooltipSpec.Line line = state.apply(stack);
			if (line != null) {
				out.described(line);
			}
		}
	}

	/**
	 * Tooltip for the wooden rotor — describes its role in the wind mill: required to generate,
	 * and the base EU/t a T1 wind mill produces at full height. Numbers come from {@link ServerBalance} so
	 * the tooltip stays in sync with the server's balance knobs (the rotor itself carries no stats — it is
	 * a gate, the mill's output depends on height/weather).
	 */
	private static void addRotorTooltip(List<Component> lines, boolean detailed) {
		// What it does is its purpose line (MOD-958). Base T1 output at full height (the cap), so a player can
		// compare rotors without placing.
		lines.add(TooltipRender.roleLine(TooltipRole.ENERGY, "tooltip.alaindustrial.rotor_output", null,
				ServerBalance.windMillMaxEuPerTick()));
		if (detailed) {
			lines.add(tier());
		} else {
			lines.add(TooltipRender.hint(false));
		}
	}

	/** A drill bore module: where it fits and how it is switched, under [SHIFT] (its role is its purpose). */
	private static ItemTooltip boreModuleTooltip(String module) {
		return new ItemTooltip().detail(TooltipRole.INFO, "tooltip.alaindustrial." + module + ".fitting", null)
				.key("tooltip.alaindustrial." + module + ".toggle");
	}

	private static Component tier() {
		return TooltipRender.line(MachineTooltipSpec.Tier.LV.line());
	}
}
