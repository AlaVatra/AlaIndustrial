package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.gametest.PoweredItemCatalog;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.wearable.EnergyPackItem;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import org.jspecify.annotations.Nullable;

/**
 * The Forge half of the cross-mod item energy bridge (MOD-084, suite TC-XMOD-001), the twin of the NeoForge
 * lane's {@code ItemEnergyCapabilityScenarios} with the same test ids: the mod's powered items exposed through
 * {@link ForgeCapabilities#ENERGY} on the stack ({@code ItemStackCapabilitiesForge}).
 *
 * <p>Forge has no transactions (ADR-052), so each call is one real {@code receiveEnergy}/{@code extractEnergy}.
 * Forge's stack capability writes the stack in place; the charge is read back from the slot anyway, as the other
 * lanes do. The {@code @implements} tags live on the Fabric twin (one tag per case).
 */
public final class ItemEnergyCapabilityScenariosForge {

	/** The pouch's per-operation input ceiling (LV tier: 32 EU) — what a foreign charger may push per call. */
	private static final int POUCH_INPUT_RATE = 32;

	private ItemEnergyCapabilityScenariosForge() {
	}

	private static @Nullable IEnergyStorage storageInSlotZero(Player player) {
		return player.getInventory().getItem(0).getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
	}

	/** A foreign charger fills a carried pouch, clamped to the pouch's own input rate. */
	public static void fun01ForeignChargerFillsPouch(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(ModContent.BATTERY_POUCH.get()));
		IEnergyStorage storage = storageInSlotZero(player);
		if (storage == null) {
			helper.fail("the pouch must expose ForgeCapabilities.ENERGY so other mods' chargers can fill it");
			return;
		}
		int inserted = storage.receiveEnergy(1000, false);
		if (inserted != POUCH_INPUT_RATE) {
			helper.fail("a charger offering 1000 FE must be clamped to the pouch's input rate ("
					+ POUCH_INPUT_RATE + "), inserted " + inserted);
		}
		if (ItemEnergy.get(player.getInventory().getItem(0)) != POUCH_INPUT_RATE) {
			helper.fail("the inserted FE must land in the pouch's own pouch_energy component as EU (1:1)");
		}
		helper.succeed();
	}

	/** A worn pack charges another mod's powered item ({@link ForeignEnergyItemStandIn}). */
	public static void fun02PackChargesForeignItem(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = new ItemStack(ModContent.ENERGY_PACK.get());
		ItemEnergy.set(pack, ToolConfig.energyPackBuffer);
		player.getInventory().setItem(0, new ItemStack(Items.APPLE));

		long moved = EnergyPackItem.chargeStep(pack, player);

		long budget = ToolConfig.energyPackOutputRate * 20L;
		if (moved != budget) {
			helper.fail("the pack must hand its whole step budget (" + budget
					+ " EU) to a foreign energy item, moved " + moved);
		}
		if (ForeignEnergyItemStandIn.storedEnergy(player.getInventory().getItem(0)) != budget) {
			helper.fail("the EU must land in the foreign item's own energy storage");
		}
		if (ItemEnergy.get(pack) != ToolConfig.energyPackBuffer - budget) {
			helper.fail("the pack must be debited exactly what the foreign item took");
		}
		helper.succeed();
	}

	/**
	 * EVERY powered item of the mod is reachable through {@code ForgeCapabilities.ENERGY} and takes a first insert
	 * into its own {@code pouch_energy} (MOD-372); the expected set comes from {@link PoweredItemCatalog}.
	 */
	public static void reg01EveryPoweredItemExposesCapability(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		List<Item> powered = PoweredItemCatalog.poweredItems();
		if (powered.size() < PoweredItemCatalog.MIN_POWERED_ITEMS) {
			helper.fail("the powered-item roster collapsed to " + powered.size() + " item(s) — expected at"
					+ " least " + PoweredItemCatalog.MIN_POWERED_ITEMS + "; a guard over a shrunken set"
					+ " proves nothing. Found: " + PoweredItemCatalog.idsOf(powered));
			return;
		}
		List<String> broken = new ArrayList<>(PoweredItemCatalog.rosterItemsWithoutBuffer());
		for (Item item : powered) {
			player.getInventory().setItem(0, new ItemStack(item));
			IEnergyStorage storage = storageInSlotZero(player);
			if (storage == null) {
				broken.add(PoweredItemCatalog.idOf(item) + " (no ForgeCapabilities.ENERGY)");
				continue;
			}
			long expectedEu = PoweredItemCatalog.expectedFirstInsert(item);
			if (expectedEu <= 0L) {
				broken.add(PoweredItemCatalog.idOf(item) + " (input rate 0 — nothing can charge it)");
				continue;
			}
			int expected = (int) Math.min(expectedEu, Integer.MAX_VALUE);
			int offered = (int) Math.min(expectedEu * 100L, Integer.MAX_VALUE);
			int inserted = storage.receiveEnergy(offered, false);
			long charge = ItemEnergy.get(player.getInventory().getItem(0));
			if (inserted != expected || charge != expected) {
				broken.add(PoweredItemCatalog.idOf(item) + " (inserted " + inserted + ", charge " + charge
						+ ", expected " + expected + ")");
			}
		}
		if (!broken.isEmpty()) {
			helper.fail("every powered item must be chargeable by a foreign mod (MOD-084) — broken: "
					+ String.join(", ", broken));
		}
		helper.succeed();
	}

	/** The capability is insert-only: a foreign machine cannot drain the mod's items. */
	public static void neg01ForeignMachineCannotDrain(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pouch = new ItemStack(ModContent.BATTERY_POUCH.get());
		ItemEnergy.set(pouch, POUCH_INPUT_RATE);
		player.getInventory().setItem(0, pouch);
		IEnergyStorage storage = storageInSlotZero(player);
		if (storage == null) {
			helper.fail("the pouch must expose ForgeCapabilities.ENERGY");
			return;
		}
		int extracted = storage.extractEnergy(POUCH_INPUT_RATE, false);
		if (extracted != 0 || ItemEnergy.get(player.getInventory().getItem(0)) != POUCH_INPUT_RATE) {
			helper.fail("a foreign machine must not be able to drain a pouch, extracted " + extracted);
		}
		helper.succeed();
	}

	/**
	 * A foreign charger fills a crystal blank to the top through {@code receiveEnergy}; Forge's capability cannot
	 * swap the stack, so it is still a full blank — and one inventory tick of the player holding it makes it the
	 * crystal (MOD-962, {@code CrystalBlankItem.inventoryTick}).
	 */
	public static void crystalBlankFilledByForeignChargerFinishesInInventory(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(ModContent.ENERGY_CRYSTAL_BLANK.get()));
		IEnergyStorage storage = storageInSlotZero(player);
		if (storage == null) {
			helper.fail("the crystal blank must expose ForgeCapabilities.ENERGY so other mods' chargers can fill it");
			return;
		}
		long capacity = ItemEnergy.capacity(player.getInventory().getItem(0));
		int calls = 0;
		while (storage.receiveEnergy(Integer.MAX_VALUE, false) > 0) {
			if (++calls > 100_000) {
				helper.fail("the blank kept accepting energy past " + calls + " calls");
				return;
			}
		}
		ItemStack charged = player.getInventory().getItem(0);
		if (!charged.is(ModContent.ENERGY_CRYSTAL_BLANK.get()) || ItemEnergy.get(charged) != capacity) {
			helper.fail("a foreign charger must leave a full blank in the slot (" + capacity + " EU), found "
					+ charged.getItem() + " with " + ItemEnergy.get(charged) + " EU");
			return;
		}

		player.getInventory().tick();

		ItemStack after = player.getInventory().getItem(0);
		if (!after.is(ModContent.ENERGY_CRYSTAL.get())) {
			helper.fail("one inventory tick of the holding player must turn the full blank into the crystal,"
					+ " the slot holds " + after.getItem());
			return;
		}
		helper.succeed();
	}
}
