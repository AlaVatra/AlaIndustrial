package dev.alaindustrial.item.wearable;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemBuffers;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * The two grades of jetpack (MOD-148, MOD-977): everything that differs between them, in one place, the shape of
 * {@link dev.alaindustrial.item.tool.MagnetTier}. Both grades are one {@link JetpackItem} class, so every
 * {@code instanceof JetpackItem} (the chest HUD, the client flight) covers the Mk2 with no edit.
 *
 * <p>The knobs are read live, never stored: a reloaded config must reach a tier resolved at class-init. A client
 * passes the server's numbers to the same {@link #pick}, as the magnet's tooltip does (MOD-761, MOD-791).
 */
public enum JetpackTier {

	/** The original (MOD-148): LV charging, no hover. */
	BASIC("jetpack", EnergyTier.LV),

	/** The second grade (MOD-977): four times the charge, MV charging, half again as fast, hover. */
	MK2("jetpack_mk2", EnergyTier.MV);

	private final String id;
	private final EnergyTier energyTier;
	private final ResourceKey<EquipmentAsset> litAsset;
	private final ResourceKey<EquipmentAsset> offAsset;

	JetpackTier(String id, EnergyTier energyTier) {
		this.id = id;
		this.energyTier = energyTier;
		// Built by hand for the reason given at EnergyPackItem.ENERGY_PACK_ASSET.
		this.litAsset = ResourceKey.create(EquipmentAssets.ROOT_ID, Industrialization.id(id));
		this.offAsset = ResourceKey.create(EquipmentAssets.ROOT_ID, Industrialization.id(id + "_off"));
	}

	/** This grade's value among the two its caller read: the live knobs below, or the server's numbers. */
	public int pick(int basic, int mk2) {
		return this == BASIC ? basic : mk2;
	}

	/** {@link #pick(int, int)} for a multiplier. */
	public double pick(double basic, double mk2) {
		return this == BASIC ? basic : mk2;
	}

	/** The item id's path, which is also the prefix of its tooltip keys. */
	public String id() {
		return id;
	}

	/** The colour band of its charge bar. */
	public EnergyTier energyTier() {
		return energyTier;
	}

	/** Worn look while it holds charge. */
	public ResourceKey<EquipmentAsset> litAsset() {
		return litAsset;
	}

	/** Worn look while it is empty. */
	public ResourceKey<EquipmentAsset> offAsset() {
		return offAsset;
	}

	/** Id of the armour modifier its chest slot grants; tier one keeps the id it shipped with. */
	public Identifier armorModifierId() {
		return Industrialization.id(id + "_armor");
	}

	/** EU it holds, out of {@code buffers}: the live knobs, or the server's numbers on a client drawing a bar. */
	public int buffer(ItemBuffers buffers) {
		return pick(buffers.jetpackBuffer(), buffers.jetpackMk2Buffer());
	}

	/** Max EU/tick it accepts while charging in a slot. */
	public int inputRate() {
		return pick(ToolConfig.jetpackInputRate, ToolConfig.jetpackMk2InputRate);
	}

	/** EU burned per tick of thrust. */
	public int euPerTick() {
		return pick(ToolConfig.jetpackEuPerTick, ToolConfig.jetpackMk2EuPerTick);
	}

	/** EU burned per tick of hover; tier one never hovers. */
	public int hoverEuPerTick() {
		return pick(0, ToolConfig.jetpackMk2HoverEuPerTick);
	}

	/** Whether this grade has the hover mode at all. */
	public boolean supportsHover() {
		return this == MK2;
	}
}
