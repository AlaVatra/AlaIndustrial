package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModParticles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge particle registration: a replay of the shared {@link ModParticles#PARTICLES} list (MOD-962), the
 * counterpart of {@code ModParticlesNeoForge}.
 *
 * <p>The particle objects are eager class-load constants in {@link ModParticles}; this class only publishes
 * those same instances to the {@code PARTICLE_TYPE} registry. There is no handle to bind.
 */
public final class ModParticlesForge {
	public static final DeferredRegister<ParticleType<?>> PARTICLES =
			DeferredRegister.create(Registries.PARTICLE_TYPE, Industrialization.MOD_ID);

	/** Every shared entry, queued the moment this class loads. */
	private static final List<RegistryObject<SimpleParticleType>> REGISTERED = registerAll();

	private static List<RegistryObject<SimpleParticleType>> registerAll() {
		List<RegistryObject<SimpleParticleType>> registered = new ArrayList<>();
		for (ModParticles.ParticleDef def : ModParticles.PARTICLES) {
			registered.add(PARTICLES.register(def.id(), def::instance));
		}
		return List.copyOf(registered);
	}

	/** Checks the replay covered the whole list, then attaches the register to the mod bus. */
	public static void register(BusGroup modBusGroup) {
		if (REGISTERED.size() != ModParticles.PARTICLES.size()) {
			throw new IllegalStateException("ModParticlesForge registered " + REGISTERED.size() + " of "
					+ ModParticles.PARTICLES.size() + " shared particle types");
		}
		PARTICLES.register(modBusGroup);
	}

	private ModParticlesForge() {
	}
}
