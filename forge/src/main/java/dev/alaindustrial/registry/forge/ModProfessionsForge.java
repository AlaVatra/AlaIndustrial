package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModProfessions;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge registration of the Industrialist villager profession + its POI (MOD-062, MOD-962), the counterpart
 * of {@code ModProfessionsNeoForge}, through two {@link DeferredRegister}s.
 *
 * <p>The POI matching states are pulled from the shared job-site block ({@link ModProfessions#POI_BLOCK}),
 * looked up by its id inside the deferred supplier (the block registry is filled before POI registration
 * fires). Forge fills the blockstate-to-POI map itself through its registry callback
 * ({@code GameData.PoiTypeCallbacks}, verified in the Forge 66.1.0 sources), so no helper is needed beyond
 * the plain registration.
 */
public final class ModProfessionsForge {
	private ModProfessionsForge() {
	}

	public static final DeferredRegister<PoiType> POI_TYPES =
			DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<VillagerProfession> PROFESSIONS =
			DeferredRegister.create(Registries.VILLAGER_PROFESSION, Industrialization.MOD_ID);

	public static final RegistryObject<PoiType> INDUSTRIALIST_POI =
			POI_TYPES.register("industrialist", () -> new PoiType(
					Set.copyOf(BuiltInRegistries.BLOCK.getOptional(Industrialization.id(ModProfessions.POI_BLOCK.id()))
							.orElseThrow().getStateDefinition().getPossibleStates()),
					ModProfessions.POI_MAX_TICKETS,
					ModProfessions.POI_VALID_RANGE));

	public static final RegistryObject<VillagerProfession> INDUSTRIALIST =
			PROFESSIONS.register("industrialist", ModProfessions::createIndustrialist);

	/** Attaches both registers to the mod bus. */
	public static void register(BusGroup modBusGroup) {
		POI_TYPES.register(modBusGroup);
		PROFESSIONS.register(modBusGroup);
	}
}
