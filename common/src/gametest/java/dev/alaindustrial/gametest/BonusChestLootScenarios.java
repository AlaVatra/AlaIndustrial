package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's pool in the vanilla bonus chest (MOD-119) follows {@code bonusChestEnabled} at the moment the chest
 * is rolled, on every loader (MOD-962, ADR-054). The table is taken from the server's loot registry ONCE and
 * rolled for fixed seeds with the switch on, then off — no datapack reload in between. A loader that read the
 * switch when the table was built (Fabric before MOD-962) still hands out mod items with it off, and fails
 * here; one that reads it at roll time (the NeoForge {@code add_table} condition, the Forge global modifier,
 * and now the Fabric pool condition) passes.
 */
public final class BonusChestLootScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(BonusChestLootScenarios::bonusChestSwitchIsReadAtRollTime,
						"bonus_chest_switch_is_read_at_roll_time"));

		private Roster() {}
	}

	/** Seeds rolled per state; about 97 % of rolls carry a mod item, so a miss on all of them is no fluke. */
	private static final int SEEDS = 64;

	private BonusChestLootScenarios() {}

	/**
	 * @implements MOD-962-LOOT02 — turning {@code bonusChestEnabled} off without a reload removes the mod items
	 *     from the next bonus chest, and leaves its vanilla part as it was
	 */
	private static void bonusChestSwitchIsReadAtRollTime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SPAWN_BONUS_CHEST);
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
				.create(LootContextParamSets.CHEST);

		List<List<ItemStack>> enabled = new ArrayList<>();
		List<List<ItemStack>> disabled = new ArrayList<>();
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("bonusChestEnabled", true);
			for (int seed = 0; seed < SEEDS; seed++) {
				enabled.add(table.getRandomItems(params, RandomSource.create(seed)));
			}
			o.set("bonusChestEnabled", false);
			for (int seed = 0; seed < SEEDS; seed++) {
				disabled.add(table.getRandomItems(params, RandomSource.create(seed)));
			}
		}

		int seedsWithModItem = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			List<ItemStack> vanillaPart = new ArrayList<>();
			boolean found = false;
			for (ItemStack stack : enabled.get(seed)) {
				if (isModItem(stack)) {
					found = true;
				} else {
					vanillaPart.add(stack);
				}
			}
			if (found) {
				seedsWithModItem++;
			}
			for (ItemStack stack : disabled.get(seed)) {
				if (isModItem(stack)) {
					helper.fail("MOD-962 bonus chest seed " + seed + " gave " + BuiltInRegistries.ITEM.getKey(
							stack.getItem()) + " with bonusChestEnabled=false and no reload - the switch is not"
							+ " read when the chest is rolled");
					return;
				}
			}
			if (!sameStacks(vanillaPart, disabled.get(seed))) {
				helper.fail("MOD-962 bonus chest seed " + seed + ": the vanilla part changed with the switch - "
						+ disabled.get(seed) + " off, " + vanillaPart + " on");
				return;
			}
		}
		if (seedsWithModItem == 0) {
			helper.fail("MOD-962 bonus chest: no mod item in " + SEEDS + " seeds with bonusChestEnabled=true"
					+ " - the pool is not injected");
			return;
		}
		helper.succeed();
	}

	private static boolean isModItem(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id.getNamespace().equals(Industrialization.MOD_ID);
	}

	private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!ItemStack.matches(a.get(i), b.get(i))) {
				return false;
			}
		}
		return true;
	}
}
