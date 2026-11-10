package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge data-component registration: a replay of the shared {@link ModDataComponents#COMPONENTS} list; the
 * twin of {@code ModDataComponentsNeoForge} (MOD-962). What is left here is the Forge registration MECHANISM:
 * the {@link DeferredRegister}, its lazy {@code register}, and the binding of each resulting
 * {@link RegistryObject} (itself a {@code Supplier<DataComponentType<T>>}) into the entry's handle.
 */
public final class ModDataComponentsForge {
	public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
			DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Industrialization.MOD_ID);

	/** Every shared entry, queued and bound the moment this class loads. */
	private static final List<RegistryObject<?>> REGISTERED = registerAll();

	private static List<RegistryObject<?>> registerAll() {
		List<RegistryObject<?>> registered = new ArrayList<>();
		for (ModDataComponents.ComponentDef<?> def : ModDataComponents.COMPONENTS) {
			registered.add(register(def));
		}
		return List.copyOf(registered);
	}

	/** One entry. Separate from {@link #registerAll()} only to capture the entry's type parameter. */
	private static <T> RegistryObject<DataComponentType<T>> register(ModDataComponents.ComponentDef<T> def) {
		RegistryObject<DataComponentType<T>> holder =
				DATA_COMPONENTS.register(def.id().getPath(), def.factory());
		def.bind().accept(holder);
		return holder;
	}

	/** Class-load trigger for the {@code @Mod} ctor; also checks the replay covered the whole list. */
	public static void init() {
		if (REGISTERED.size() != ModDataComponents.COMPONENTS.size()) {
			throw new IllegalStateException("ModDataComponentsForge registered " + REGISTERED.size()
					+ " of " + ModDataComponents.COMPONENTS.size() + " shared data components");
		}
	}

	private ModDataComponentsForge() {
	}
}
