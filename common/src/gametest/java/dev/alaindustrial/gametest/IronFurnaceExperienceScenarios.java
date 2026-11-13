package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.IronFurnaceBlockEntity;
import dev.alaindustrial.mixin.ExperienceOrbAccessor;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Smelting experience of the iron furnace (MOD-971), run by every loader from the roster.
 *
 * <p>Raw gold is smelted because its vanilla recipe is worth exactly 1.0 experience, so every expected total is a
 * whole number and no random roll is involved. The furnace is ticked synchronously and the orbs are counted in the
 * same tick, before any of them can move or merge with an orb of another test; an orb's points are its value
 * times its merge count. The player is a real survival mock taking through the stock furnace menu the iron furnace
 * opens, so the take goes through the result slot exactly as a click in the game does.
 */
public final class IronFurnaceExperienceScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(IronFurnaceExperienceScenarios::playerPickupPaysVanillaExperience,
						"iron_furnace_player_pickup_pays_vanilla_xp"),
				RosterEntry.of(IronFurnaceExperienceScenarios::shiftClickPaysOnce,
						"iron_furnace_shift_click_pays_once"),
				RosterEntry.of(IronFurnaceExperienceScenarios::automationTakePaysNothingAndKeepsTheBank,
						"iron_furnace_automation_take_pays_nothing_and_keeps_xp"),
				RosterEntry.of(IronFurnaceExperienceScenarios::breakingPopsTheBank,
						"iron_furnace_break_pops_stored_xp"),
				RosterEntry.of(IronFurnaceExperienceScenarios::bankSurvivesSaveAndOldSavesLoad,
						"iron_furnace_xp_survives_save_and_old_saves_load"));

		private Roster() {}
	}

	private static final BlockPos FURNACE = new BlockPos(1, 2, 1);
	private static final BlockPos RELOADED = new BlockPos(3, 2, 1);
	private static final BlockPos OLD_SAVE = new BlockPos(1, 2, 3);
	/** Where the player stands, inside the test's own cells (the mock spawns at the shared world spawn). */
	private static final Vec3 PLAYER = new Vec3(3.5, 2.0, 3.5);
	private static final int RESULT = IronFurnaceBlockEntity.RESULT_SLOT;

	private IronFurnaceExperienceScenarios() {
	}

	/**
	 * A player taking three smelted ingots gets three points; one more smelt and a second take bring the total to
	 * four, not seven: the take empties the bank.
	 *
	 * @implements MOD-971 — a player taking the result is paid the vanilla smelting experience, once.
	 */
	public static void playerPickupPaysVanillaExperience(GameTestHelper helper) {
		IronFurnaceBlockEntity furnace = loadedFurnace(helper, FURNACE);
		smeltUntil(helper, FURNACE, furnace, 3);
		ServerPlayer player = player(helper);
		FurnaceMenu menu = menu(furnace, player);

		menu.clicked(RESULT, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(menu.getCarried().is(Items.GOLD_INGOT) && menu.getCarried().getCount() == 3,
				"the click must take the three ingots, carried " + menu.getCarried());
		assertPoints(helper, 3, "three raw gold smelts taken by a player");

		menu.setCarried(ItemStack.EMPTY);
		smeltUntil(helper, FURNACE, furnace, 1);
		menu.clicked(RESULT, 0, ContainerInput.PICKUP, player);
		assertPoints(helper, 4, "one more smelt taken after the first take (the bank must have been emptied)");
		helper.succeed();
	}

	/**
	 * A shift-click pays exactly once: the slot reports the take twice on this path, and the second report must
	 * find the bank empty.
	 *
	 * @implements MOD-971 — a shift-click take pays the smelting experience once.
	 */
	public static void shiftClickPaysOnce(GameTestHelper helper) {
		IronFurnaceBlockEntity furnace = loadedFurnace(helper, FURNACE);
		smeltUntil(helper, FURNACE, furnace, 2);
		ServerPlayer player = player(helper);
		FurnaceMenu menu = menu(furnace, player);

		menu.clicked(RESULT, 0, ContainerInput.QUICK_MOVE, player);
		helper.assertTrue(furnace.getItem(RESULT).isEmpty()
						&& player.getInventory().countItem(Items.GOLD_INGOT) == 2,
				"the shift-click must move both ingots into the inventory");
		assertPoints(helper, 2, "two smelts taken by a shift-click");
		helper.succeed();
	}

	/**
	 * Automation takes the result the way a hopper does, through the container with no slot: no experience, and
	 * the bank stays for the next player take, which is paid for every smelt.
	 *
	 * @implements MOD-971 — automation taking the result pays no experience and keeps the bank.
	 */
	public static void automationTakePaysNothingAndKeepsTheBank(GameTestHelper helper) {
		IronFurnaceBlockEntity furnace = loadedFurnace(helper, FURNACE);
		smeltUntil(helper, FURNACE, furnace, 2);

		ItemStack pulled = furnace.removeItem(RESULT, 64);
		helper.assertTrue(pulled.getCount() == 2, "the automated take must pull both ingots, got " + pulled);
		assertPoints(helper, 0, "an automated take");

		smeltUntil(helper, FURNACE, furnace, 1);
		ServerPlayer player = player(helper);
		FurnaceMenu menu = menu(furnace, player);
		menu.clicked(RESULT, 0, ContainerInput.PICKUP, player);
		assertPoints(helper, 3, "a player take after an automated one (two banked smelts plus one)");
		helper.succeed();
	}

	/**
	 * Breaking the furnace pops what it banked at the block and still drops its contents.
	 *
	 * @implements MOD-971 — breaking the iron furnace pops its banked smelting experience.
	 */
	public static void breakingPopsTheBank(GameTestHelper helper) {
		IronFurnaceBlockEntity furnace = loadedFurnace(helper, FURNACE);
		smeltUntil(helper, FURNACE, furnace, 2);

		helper.getLevel().destroyBlock(helper.absolutePos(FURNACE), false);
		assertPoints(helper, 2, "breaking a furnace with two banked smelts");
		int ingots = 0;
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper))) {
			if (item.getItem().is(Items.GOLD_INGOT)) {
				ingots += item.getItem().getCount();
			}
		}
		helper.assertTrue(ingots == 2, "the broken furnace must still drop its two ingots, dropped " + ingots);
		helper.succeed();
	}

	/**
	 * The bank survives a save and load; a furnace saved before MOD-971 (no {@code recipes_used} key) loads with its
	 * items and an empty bank.
	 *
	 * @implements MOD-971 — banked smelting experience survives a reload, and old saves load unchanged.
	 */
	public static void bankSurvivesSaveAndOldSavesLoad(GameTestHelper helper) {
		IronFurnaceBlockEntity furnace = loadedFurnace(helper, FURNACE);
		smeltUntil(helper, FURNACE, furnace, 2);
		ServerLevel level = helper.getLevel();
		CompoundTag tag = furnace.saveCustomOnly(level.registryAccess());
		helper.assertTrue(tag.contains("recipes_used"), "the save must carry the bank, got " + tag);

		ServerPlayer player = player(helper);
		IronFurnaceBlockEntity reloaded = emptyFurnace(helper, RELOADED);
		reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		helper.assertTrue(reloaded.getItem(RESULT).getCount() == 2, "the reloaded furnace must keep its ingots");
		menu(reloaded, player).clicked(RESULT, 0, ContainerInput.PICKUP, player);
		assertPoints(helper, 2, "a take from the reloaded furnace");

		CompoundTag oldSave = tag.copy();
		oldSave.remove("recipes_used");
		IronFurnaceBlockEntity old = emptyFurnace(helper, OLD_SAVE);
		old.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), oldSave));
		helper.assertTrue(old.getItem(RESULT).getCount() == 2, "a pre-MOD-971 save must load with its ingots");
		menu(old, player).clicked(RESULT, 0, ContainerInput.PICKUP, player);
		assertPoints(helper, 2, "a take from a pre-MOD-971 save (nothing banked, total unchanged)");
		helper.succeed();
	}

	// --- helpers ---

	/** An iron furnace with raw gold and enough coal for every smelt of a scenario. */
	private static IronFurnaceBlockEntity loadedFurnace(GameTestHelper helper, BlockPos pos) {
		IronFurnaceBlockEntity furnace = emptyFurnace(helper, pos);
		furnace.setItem(IronFurnaceBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_GOLD, 8));
		furnace.setItem(IronFurnaceBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 4));
		return furnace;
	}

	private static IronFurnaceBlockEntity emptyFurnace(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModContent.IRON_FURNACE.get());
		IronFurnaceBlockEntity furnace = helper.getBlockEntity(pos, IronFurnaceBlockEntity.class);
		helper.assertTrue(furnace != null, "fixture error: no iron furnace block entity at " + pos);
		return furnace;
	}

	/** Ticks the furnace by hand until its result slot holds {@code count} items, bounded by the cook time. */
	private static void smeltUntil(GameTestHelper helper, BlockPos pos, IronFurnaceBlockEntity furnace, int count) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		int bound = count * Math.max(1, Config.ironFurnaceCookTime) + 10;
		for (int tick = 0; tick < bound && furnace.getItem(RESULT).getCount() < count; tick++) {
			IronFurnaceBlockEntity.serverTick(level, abs, level.getBlockState(abs), furnace);
		}
		helper.assertTrue(furnace.getItem(RESULT).getCount() == count,
				"fixture error: the furnace must smelt " + count + " raw gold, holds " + furnace.getItem(RESULT));
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		Vec3 at = helper.absoluteVec(PLAYER);
		player.setPos(at.x, at.y, at.z);
		return player;
	}

	private static FurnaceMenu menu(IronFurnaceBlockEntity furnace, ServerPlayer player) {
		return (FurnaceMenu) furnace.createMenu(1, player.getInventory(), player);
	}

	/** The experience points lying in this test's cells, an orb counted as its value times its merge count. */
	private static void assertPoints(GameTestHelper helper, int expected, String what) {
		int points = 0;
		for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, area(helper))) {
			points += orb.getValue() * ((ExperienceOrbAccessor) orb).alaindustrial$getCount();
		}
		helper.assertTrue(points == expected, what + ": expected " + expected + " experience points in total, found "
				+ points);
	}

	private static AABB area(GameTestHelper helper) {
		return new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(5, 5, 5)));
	}
}
