package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModFluidsManifest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge fluid registration (MOD-962): a replay of the shared {@link ModFluidsManifest#FLUIDS} list through two
 * {@link DeferredRegister}s — the vanilla FLUID registry and Forge's {@code forge:fluid_type}, one
 * {@link FluidType} per entry, under the entry's id. The same shape as {@code ModFluidsNeoForge}.
 *
 * <p><b>No subclass per fluid.</b> Forge patches {@code Fluid.getFluidType()} to throw for a fluid it does not
 * know ({@code ForgeHooks.getVanillaFluidType}). The common fluid instances are registered as they are and
 * recorded with their type's holder ({@link ForgeFluidTypes}); {@code FluidTypeLookupMixin} answers
 * {@code getFluidType()} from that record the first time Forge asks.
 *
 * <p><b>Event order:</b> Forge fires the vanilla registries first, in vanilla order (FLUID before BLOCK), then the
 * modded ones sorted by name, so {@code forge:fluid_type} registers AFTER the fluids and the liquid blocks. That
 * is safe because nothing asks a fluid for its type while it is built: Forge's {@code LiquidBlock} constructor
 * reads no {@code FluidType}, and the type resolves lazily on first use. If something does ask too early, the
 * record throws an error naming the fluid instead of a bare "Registry Object not present".
 */
public final class ModFluidsForge {
	public static final DeferredRegister<Fluid> FLUIDS =
			DeferredRegister.create(Registries.FLUID, Industrialization.MOD_ID);
	public static final DeferredRegister<FluidType> FLUID_TYPES =
			DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, Industrialization.MOD_ID);

	/** The {@link ModContent} bindings, run by {@link #init()}. */
	private static final List<Runnable> BINDERS = new ArrayList<>();

	static {
		for (ModFluidsManifest.FluidDef<?> def : ModFluidsManifest.FLUIDS) {
			register(def);
		}
	}

	private ModFluidsForge() {
	}

	/** One entry: its fluid type, then its still and flowing fluids, each recorded with that type. */
	private static <T extends Fluid> void register(ModFluidsManifest.FluidDef<T> def) {
		RegistryObject<FluidType> type = FLUID_TYPES.register(def.id(), () -> new FluidType(typeProperties(def)));
		RegistryObject<T> source = FLUIDS.register(def.id(), () -> withType(def.source().get(), def.id(), type));
		BINDERS.add(() -> def.bindSource().accept(source));
		if (def.hasFlowing()) {
			RegistryObject<T> flowing =
					FLUIDS.register(def.flowingId(), () -> withType(def.flowing().get(), def.flowingId(), type));
			BINDERS.add(() -> def.bindFlowing().accept(flowing));
		}
	}

	private static <T extends Fluid> T withType(T fluid, String fluidId, RegistryObject<FluidType> type) {
		ForgeFluidTypes.record(fluid, lazyType(fluidId, type));
		return fluid;
	}

	/** The registered type, or an error naming the fluid when asked before {@code forge:fluid_type} fired. */
	private static Supplier<FluidType> lazyType(String fluidId, RegistryObject<FluidType> type) {
		return () -> {
			if (!type.isPresent()) {
				throw new IllegalStateException("fluid " + Industrialization.id(fluidId) + " was asked for its "
						+ "FluidType before the forge:fluid_type registry event registered " + type.getId());
			}
			return type.get();
		};
	}

	/**
	 * The {@link FluidType.Properties} of an entry, the same values as {@code ModFluidsNeoForge.typeProperties}.
	 * Every entity-physics field is set explicitly (MOD-238 audit, MOD-495): Forge's defaults are those of water
	 * (swim, drown, push, {@code motionScale=0.014}, {@code fallDistanceModifier=0.5F}), while the common fluid
	 * classes carry the shared immersion physics ({@code FluidImmersion}) on every loader.
	 */
	public static FluidType.Properties typeProperties(ModFluidsManifest.FluidDef<?> def) {
		return FluidType.Properties.create()
				.descriptionId(def.descriptionId())
				.density(def.physics().density())
				.temperature(def.physics().temperature())
				.viscosity(def.physics().viscosity())
				.canConvertToSource(false)
				.canSwim(false)
				.canDrown(false)
				.canPushEntity(false)
				.motionScale(0.0D)
				.fallDistanceModifier(1.0F)
				.supportsBoating(false);
	}

	/** Registers both registers on the mod bus group, the types first as on NeoForge. */
	public static void register(BusGroup modBusGroup) {
		FLUID_TYPES.register(modBusGroup);
		FLUIDS.register(modBusGroup);
	}

	/**
	 * Binds the fluid {@link RegistryObject}s into the loader-neutral {@link ModContent} facade. Run after
	 * {@link #register(BusGroup)}; the holders resolve lazily after the FLUID registry event.
	 */
	public static void init() {
		for (Runnable binder : BINDERS) {
			binder.run();
		}
	}
}
