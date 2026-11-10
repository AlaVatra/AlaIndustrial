package dev.alaindustrial.registry.forge;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModLootConditions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge loot-condition registration: a replay of the shared {@link ModLootConditions#CONDITIONS} list
 * (MOD-962), the counterpart of {@code ModLootConditionsNeoForge}. There is no handle to bind: a loot table
 * names a condition by id.
 */
public final class ModLootConditionsForge {

	public static final DeferredRegister<MapCodec<? extends LootItemCondition>> LOOT_CONDITION_TYPES =
			DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Industrialization.MOD_ID);

	/** Every shared entry, queued the moment this class loads. */
	private static final List<RegistryObject<?>> REGISTERED = registerAll();

	private static List<RegistryObject<?>> registerAll() {
		List<RegistryObject<?>> registered = new ArrayList<>();
		for (ModLootConditions.ConditionDef def : ModLootConditions.CONDITIONS) {
			registered.add(LOOT_CONDITION_TYPES.register(def.id(), def::codec));
		}
		return List.copyOf(registered);
	}

	/** Checks the replay covered the whole list, then attaches the register to the mod bus. */
	public static void register(BusGroup modBusGroup) {
		if (REGISTERED.size() != ModLootConditions.CONDITIONS.size()) {
			throw new IllegalStateException("ModLootConditionsForge registered " + REGISTERED.size() + " of "
					+ ModLootConditions.CONDITIONS.size() + " shared loot-condition types");
		}
		LOOT_CONDITION_TYPES.register(modBusGroup);
	}

	private ModLootConditionsForge() {
	}
}
