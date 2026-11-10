package dev.alaindustrial.loot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Every loot pool the mod adds to a table it does not own (MOD-962, step 2c): one row per injection, the
 * single declaration each loader replays. A row says "add one pool to {@code target} that rolls the mod
 * sub-table {@code table} once"; the sub-table holds the items and their balance, so all loaders read one
 * number.
 *
 * <ul>
 *   <li><b>Fabric</b> replays this list in code, from one {@code LootTableEvents.MODIFY} handler in
 *       {@code IndustrializationFabric}.</li>
 *   <li><b>NeoForge</b> reads one {@code neoforge:add_table} global loot modifier per row,
 *       {@code data/alaindustrial/loot_modifiers/<id>.json}; those files are written from this list by the
 *       command in {@code LootModifierTableTest}, which fails while a file differs.</li>
 *
 *   <li><b>Forge</b> replays this list from one global loot modifier, {@code InjectionLootModifier}.</li>
 * </ul>
 *
 * <p><b>The one rule on all three loaders.</b> A row adds its pool to every table with the target id,
 * whatever its source: the built-in table, or one a datapack or another mod replaced. A global loot modifier
 * matches by id alone and cannot tell those apart, so Fabric does not ask either. The bonus-chest gate
 * ({@code Config.bonusChestEnabled}) is read when the chest is rolled, through the
 * {@code alaindustrial:bonus_chest_enabled} loot condition ({@link BonusChestEnabledCondition}), so a config
 * change applies without a {@code /reload} on every loader.
 */
public final class LootInjections {

	/**
	 * One injected pool.
	 *
	 * @param id             the NeoForge modifier file stem ({@code loot_modifiers/<id>.json})
	 * @param target         the table that receives the pool
	 * @param table          the mod sub-table the pool rolls once
	 * @param bonusChestGate whether {@code Config.bonusChestEnabled} switches it off
	 */
	public record Injection(String id, ResourceKey<LootTable> target, ResourceKey<LootTable> table,
			boolean bonusChestGate) {
	}

	/**
	 * Every injection, sorted by {@link Injection#id()} so the order never depends on a {@code Set} or
	 * {@code Map} iteration order.
	 *
	 * <ul>
	 *   <li>MOD-119, the bonus chest: gated by {@code Config.bonusChestEnabled} at roll time.</li>
	 *   <li>MOD-534, the drill's smithing template in vanilla's four bastion chests: never gated — it is the
	 *       only route to the drill's top tier.</li>
	 *   <li>MOD-280, cotton seeds from short and tall grass: tall grass has its own sub-table because its
	 *       table is rolled once per half ({@link Trellis#SEEDS_FROM_TALL_GRASS}).</li>
	 *   <li>MOD-942, structure chests: the config switch is a loot condition inside the sub-table, read at
	 *       roll time.</li>
	 * </ul>
	 */
	public static final List<Injection> INJECTIONS = build();

	private static List<Injection> build() {
		List<Injection> rows = new ArrayList<>();
		rows.add(new Injection("bonus_chest_inject", BuiltInLootTables.SPAWN_BONUS_CHEST, BonusChest.INJECT_TABLE,
				true));
		for (ResourceKey<LootTable> bastion : DrillTemplateLoot.BASTION_TABLES) {
			rows.add(new Injection(afterSlash(bastion) + "_drill_template", bastion, DrillTemplateLoot.INJECT_TABLE,
					false));
		}
		for (Map.Entry<ResourceKey<LootTable>, ResourceKey<LootTable>> grass : Trellis.GRASS_TABLES.entrySet()) {
			rows.add(new Injection("cotton_seeds_from_" + afterSlash(grass.getKey()), grass.getKey(),
					grass.getValue(), false));
		}
		for (Map.Entry<ResourceKey<LootTable>, ResourceKey<LootTable>> chest : StructureLoot.INJECTIONS.entrySet()) {
			rows.add(new Injection("structure_loot_" + afterSlash(chest.getValue()), chest.getKey(), chest.getValue(),
					false));
		}
		rows.sort(Comparator.comparing(Injection::id));
		return List.copyOf(rows);
	}

	/** The last path segment of a table id: {@code chests/bastion_other} becomes {@code bastion_other}. */
	private static String afterSlash(ResourceKey<LootTable> key) {
		String path = key.identifier().getPath();
		return path.substring(path.lastIndexOf('/') + 1);
	}

	private LootInjections() {
	}
}
