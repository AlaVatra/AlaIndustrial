package dev.alaindustrial.registry.forge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import org.jspecify.annotations.Nullable;

/**
 * Which {@link FluidType} each of the mod's fluids has (MOD-962, the Forge twin of {@code NeoForgeFluidTypes}):
 * recorded by {@code ModFluidsForge} when it builds a fluid, read by {@code FluidTypeLookupMixin} when Forge first
 * asks the fluid for its type.
 *
 * <p>A class of its own, with no other static state, because the mixin reaches it from
 * {@code Fluid.getFluidType()} — which vanilla fluids answer long before the mod's registers exist — and
 * initialising it must not pull those registers in.
 */
public final class ForgeFluidTypes {
	private static final Map<Fluid, Supplier<FluidType>> TYPE_OF = new ConcurrentHashMap<>();

	private ForgeFluidTypes() {
	}

	/**
	 * Records that {@code fluid} has the type {@code type} resolves to. Lazily: Forge fires the FLUID registry
	 * event before its own {@code forge:fluid_type} registry, so the type does not exist yet when the fluid is
	 * built; {@code type} throws, naming the fluid, if it is asked too early.
	 */
	static void record(Fluid fluid, Supplier<FluidType> type) {
		TYPE_OF.put(fluid, type);
	}

	/** The type of a fluid the mod registered, or {@code null} for any other fluid. */
	public static @Nullable FluidType typeOf(Fluid fluid) {
		Supplier<FluidType> type = TYPE_OF.get(fluid);
		return type == null ? null : type.get();
	}
}
