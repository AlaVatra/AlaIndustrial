package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge status-effect registration: a replay of the shared {@link ModEffects#EFFECTS} list (MOD-962), the
 * counterpart of {@code ModEffectsNeoForge}.
 *
 * <p>NeoForge binds {@code () -> holder} because its {@code DeferredHolder} already IS a {@code Holder}. A
 * Forge {@link RegistryObject} is not one: {@link RegistryObject#getHolder()} returns the vanilla holder, and
 * only once the registry entry exists. The bound supplier therefore asks for it lazily and fails loudly,
 * naming the effect, if a caller reads it before registration.
 */
public final class ModEffectsForge {
	public static final DeferredRegister<MobEffect> EFFECTS =
			DeferredRegister.create(Registries.MOB_EFFECT, Industrialization.MOD_ID);

	/** Every shared entry, queued and bound the moment this class loads. */
	private static final List<RegistryObject<MobEffect>> REGISTERED = registerAll();

	private static List<RegistryObject<MobEffect>> registerAll() {
		List<RegistryObject<MobEffect>> registered = new ArrayList<>();
		for (ModEffects.EffectDef def : ModEffects.EFFECTS) {
			RegistryObject<MobEffect> holder = EFFECTS.register(def.id(), def.factory());
			def.bind().accept(() -> holder.getHolder().orElseThrow(() -> new IllegalStateException(
					"Status effect " + def.id() + " read before it was registered")));
			registered.add(holder);
		}
		return List.copyOf(registered);
	}

	/** Checks the replay covered the whole list, then attaches the register to the mod bus. */
	public static void register(BusGroup modBusGroup) {
		if (REGISTERED.size() != ModEffects.EFFECTS.size()) {
			throw new IllegalStateException("ModEffectsForge registered " + REGISTERED.size() + " of "
					+ ModEffects.EFFECTS.size() + " shared status effects");
		}
		EFFECTS.register(modBusGroup);
	}

	private ModEffectsForge() {
	}
}
