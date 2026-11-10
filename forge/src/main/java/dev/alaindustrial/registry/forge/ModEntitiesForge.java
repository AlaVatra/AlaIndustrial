package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModEntityTypes;
import dev.alaindustrial.registry.ModMobs;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge replay of the mod's entity types (MOD-962), the twin of {@code ModEntitiesNeoForge}. Nothing here declares
 * an entity: the non-mob types are {@link ModEntityTypes#ENTITIES}, built lazily by their shared
 * {@code EntityDef.build()}, and the mobs are {@link ModMobs#MOBS}, replayed in the same order as on the other two
 * loaders.
 *
 * <p>Forge difference (sources of Forge 66.1.0): {@link EntityAttributeCreationEvent} and
 * {@link SpawnPlacementRegisterEvent} are not mod-bus events but global ones ({@code X.BUS}); Forge posts both from
 * {@code GameData} right after the registry events, so a listener added in the mod constructor is in time.
 */
public final class ModEntitiesForge {
	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
			DeferredRegister.create(Registries.ENTITY_TYPE, Industrialization.MOD_ID);

	// The non-mob types first, then the mobs: DeferredRegister registers in the order of these calls, the order
	// every loader registers in.
	static {
		for (ModEntityTypes.EntityDef<?> def : ModEntityTypes.ENTITIES) {
			ENTITY_TYPES.register(def.id(), def::build);
		}
	}

	/** The mod's living mobs, replayed from the shared {@link ModMobs#MOBS}: same ids and builder values. */
	public static final Map<String, RegistryObject<? extends EntityType<?>>> MOBS = registerMobs();

	private static Map<String, RegistryObject<? extends EntityType<?>>> registerMobs() {
		Map<String, RegistryObject<? extends EntityType<?>>> holders = new LinkedHashMap<>();
		for (ModMobs.MobDef<?> def : ModMobs.MOBS) {
			holders.put(def.id(), registerMob(def));
		}
		return holders;
	}

	private static <T extends Mob> RegistryObject<EntityType<T>> registerMob(ModMobs.MobDef<T> def) {
		return ENTITY_TYPES.register(def.id(),
				() -> def.builder().apply(EntityType.Builder.of(def.factory(), def.category()))
						.build(def.resourceKey()));
	}

	private ModEntitiesForge() {
	}

	/**
	 * Registers the entity types on the mod bus group and, for the mobs, the two global events that give them
	 * default attributes and spawn rules — those are events, not registers.
	 */
	public static void register(BusGroup modBusGroup) {
		ENTITY_TYPES.register(modBusGroup);
		EntityAttributeCreationEvent.BUS.addListener(ModEntitiesForge::registerAttributes);
		SpawnPlacementRegisterEvent.BUS.addListener(ModEntitiesForge::registerSpawnPlacements);
	}

	/** Default attributes of every mob. */
	public static void registerAttributes(EntityAttributeCreationEvent event) {
		for (ModMobs.MobDef<?> def : ModMobs.MOBS) {
			event.put(def.type(), def.attributes().get().build());
		}
	}

	/** Spawn placement and spawn rule of every mob. */
	public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
		for (ModMobs.MobDef<?> def : ModMobs.MOBS) {
			registerPlacement(event, def);
		}
	}

	private static <T extends Mob> void registerPlacement(SpawnPlacementRegisterEvent event, ModMobs.MobDef<T> def) {
		event.register(def.type(), def.placement(), def.heightmap(), def.spawnRule(),
				SpawnPlacementRegisterEvent.Operation.REPLACE);
	}
}
