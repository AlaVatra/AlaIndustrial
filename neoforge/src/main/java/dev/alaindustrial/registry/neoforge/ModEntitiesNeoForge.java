package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModEntityTypes;
import dev.alaindustrial.registry.ModMobs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge replay of the mod's entity types (MOD-022 registration facade). Nothing here declares an entity: the
 * non-mob types are {@link ModEntityTypes#ENTITIES} (MOD-959), built lazily by their shared
 * {@code EntityDef.build()}, and the mobs are {@link ModMobs#MOBS}; the Fabric {@code ModEntities} replays the same
 * two lists in the same order. NeoForge's {@code RegisterEvent} for ENTITY_TYPE fires before ITEM, and the items that
 * place an entity resolve the registered type by id inside their own factory (MOD-554), so no ordering assumption is
 * load-bearing.
 */
public final class ModEntitiesNeoForge {
	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
			DeferredRegister.create(Registries.ENTITY_TYPE, Industrialization.MOD_ID);

	// The non-mob types first, then the mobs: DeferredRegister registers in the order of these calls, the order
	// both loaders register in.
	static {
		for (ModEntityTypes.EntityDef<?> def : ModEntityTypes.ENTITIES) {
			ENTITY_TYPES.register(def.id(), def::build);
		}
	}

	/**
	 * The mod's living mobs (MOD-767), replayed from the shared {@link ModMobs#MOBS} — same ids and builder
	 * values as the Fabric {@code ModEntities}. Attributes and spawn placements come from the two mod-bus
	 * events below; the natural spawn is the add_spawns biome modifier JSON ({@code BiomeModifierTableTest}).
	 */
	public static final Map<String, DeferredHolder<EntityType<?>, ? extends EntityType<?>>> MOBS = registerMobs();

	private static Map<String, DeferredHolder<EntityType<?>, ? extends EntityType<?>>> registerMobs() {
		Map<String, DeferredHolder<EntityType<?>, ? extends EntityType<?>>> holders = new LinkedHashMap<>();
		for (ModMobs.MobDef<?> def : ModMobs.MOBS) {
			holders.put(def.id(), registerMob(def));
		}
		return holders;
	}

	private static <T extends Mob> DeferredHolder<EntityType<?>, EntityType<T>> registerMob(ModMobs.MobDef<T> def) {
		return ENTITY_TYPES.register(def.id(),
				() -> def.builder().apply(EntityType.Builder.of(def.factory(), def.category()))
						.build(def.resourceKey()));
	}

	/**
	 * Registers the entity types and, for the mobs (MOD-767), the two mod-bus events that give them default
	 * attributes and spawn rules — those are events, not registers.
	 */
	public static void register(IEventBus modBus) {
		ENTITY_TYPES.register(modBus);
		modBus.addListener(ModEntitiesNeoForge::registerAttributes);
		modBus.addListener(ModEntitiesNeoForge::registerSpawnPlacements);
	}

	/** Default attributes of every mob (mod bus). */
	public static void registerAttributes(EntityAttributeCreationEvent event) {
		for (ModMobs.MobDef<?> def : ModMobs.MOBS) {
			event.put(def.type(), def.attributes().get().build());
		}
	}

	/** Spawn placement and spawn rule of every mob (mod bus). */
	public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
		for (ModMobs.MobDef<?> def : ModMobs.MOBS) {
			registerPlacement(event, def);
		}
	}

	private static <T extends Mob> void registerPlacement(RegisterSpawnPlacementsEvent event, ModMobs.MobDef<T> def) {
		event.register(def.type(), def.placement(), def.heightmap(), def.spawnRule(),
				RegisterSpawnPlacementsEvent.Operation.REPLACE);
	}

	private ModEntitiesNeoForge() {
	}
}
