package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModCriteria;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge advancement-criterion registration: a replay of the shared {@link ModCriteria#TRIGGERS} list
 * (MOD-962), the counterpart of {@code ModCriteriaNeoForge}.
 */
public final class ModCriteriaForge {
	public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
			DeferredRegister.create(Registries.TRIGGER_TYPE, Industrialization.MOD_ID);

	/** Every shared entry, queued and bound the moment this class loads. */
	private static final List<RegistryObject<?>> REGISTERED = registerAll();

	private static List<RegistryObject<?>> registerAll() {
		List<RegistryObject<?>> registered = new ArrayList<>();
		for (ModCriteria.CriterionDef<?> def : ModCriteria.TRIGGERS) {
			registered.add(register(def));
		}
		return List.copyOf(registered);
	}

	/** One entry. Separate from {@link #registerAll()} only to capture the entry type parameter. */
	private static <T extends CriterionTrigger<?>> RegistryObject<T> register(ModCriteria.CriterionDef<T> def) {
		RegistryObject<T> holder = TRIGGERS.register(def.id(), def.factory());
		def.bind().accept(holder::get);
		return holder;
	}

	/** Checks the replay covered the whole list, then attaches the register to the mod bus. */
	public static void register(BusGroup modBusGroup) {
		if (REGISTERED.size() != ModCriteria.TRIGGERS.size()) {
			throw new IllegalStateException("ModCriteriaForge registered " + REGISTERED.size() + " of "
					+ ModCriteria.TRIGGERS.size() + " shared advancement criteria");
		}
		TRIGGERS.register(modBusGroup);
	}

	private ModCriteriaForge() {
	}
}
