package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge sound registration: a replay of the shared {@link ModSounds#SOUNDS} list (MOD-962), the counterpart of
 * {@code ModSoundsNeoForge}.
 *
 * <p>Forge fills the vanilla {@code SOUND_EVENT} registry from {@code RegisterEvent}, so registration goes
 * through a {@link DeferredRegister} on the mod bus. A {@link RegistryObject} is a {@code Supplier} that
 * resolves lazily, so each entry is bound into its handle as soon as it is queued, before the event fires.
 */
public final class ModSoundsForge {
	public static final DeferredRegister<SoundEvent> SOUNDS =
			DeferredRegister.create(Registries.SOUND_EVENT, Industrialization.MOD_ID);

	/** Every shared entry, queued and bound the moment this class loads. */
	private static final List<RegistryObject<SoundEvent>> REGISTERED = registerAll();

	private static List<RegistryObject<SoundEvent>> registerAll() {
		List<RegistryObject<SoundEvent>> registered = new ArrayList<>();
		for (ModSounds.SoundDef def : ModSounds.SOUNDS) {
			RegistryObject<SoundEvent> holder = SOUNDS.register(def.id(), def.factory());
			def.bind().accept(holder);
			registered.add(holder);
		}
		return List.copyOf(registered);
	}

	/** Checks the replay covered the whole list, then attaches the register to the mod bus. */
	public static void register(BusGroup modBusGroup) {
		if (REGISTERED.size() != ModSounds.SOUNDS.size()) {
			throw new IllegalStateException("ModSoundsForge registered " + REGISTERED.size() + " of "
					+ ModSounds.SOUNDS.size() + " shared sound events");
		}
		SOUNDS.register(modBusGroup);
	}

	private ModSoundsForge() {
	}
}
