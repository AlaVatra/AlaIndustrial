package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fabric replay of the mod's entity types (eager — same idiom as {@code ModBlockEntities}/{@code ModMenus}). Nothing
 * here declares an entity: the non-mob types are {@link ModEntityTypes#ENTITIES} (MOD-959), built by their shared
 * {@code EntityDef.build()}, and the mobs are {@link ModMobs#MOBS}; NeoForge's {@code ModEntitiesNeoForge} replays
 * the same two lists in the same order.
 */
public final class ModEntities {
	private ModEntities() {
	}

	// The non-mob types first, then the mobs: static initialisers run in source order, and this is the order both
	// loaders register in.
	static {
		for (ModEntityTypes.EntityDef<?> def : ModEntityTypes.ENTITIES) {
			Registry.register(BuiltInRegistries.ENTITY_TYPE, def.resourceKey(), def.build());
		}
	}

	/**
	 * The mod's living mobs (MOD-767), replayed from the shared {@link ModMobs#MOBS}: Fabric's mob builder
	 * carries the spawn placement and the default attributes, the shared definition adds the vanilla builder
	 * calls. Keyed by id in declaration order.
	 */
	public static final Map<String, EntityType<?>> MOBS = registerMobs();

	private static Map<String, EntityType<?>> registerMobs() {
		Map<String, EntityType<?>> types = new LinkedHashMap<>();
		for (ModMobs.MobDef<?> def : ModMobs.MOBS) {
			types.put(def.id(), registerMob(def));
		}
		return Map.copyOf(types);
	}

	private static <T extends Mob> EntityType<T> registerMob(ModMobs.MobDef<T> def) {
		EntityType.Builder<T> builder = FabricEntityType.Builder.createMob(def.factory(), def.category(),
				mob -> mob.spawnPlacement(def.placement(), def.heightmap(), def.spawnRule())
						.defaultAttributes(def.attributes()));
		return register(def.id(), def.builder().apply(builder));
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(
			String path, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	/** Set by the first {@link #init()}: the spawns it adds must be added once. */
	private static boolean initialised;

	/**
	 * Register the types (the class load does it) and add the mobs' natural spawns.
	 *
	 * <p>Idempotent (MOD-840): the entrypoint calls it, and so does {@code ModItems} as a class-load trigger
	 * before it builds the spawn eggs. {@code BiomeModifications.addSpawn} appends without deduplication, so a
	 * second pass used to list every mob twice in its biomes and double its natural spawn weight.
	 */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		// Natural spawns of the mobs (MOD-767): the NeoForge counterpart is the add_spawns biome modifier JSON. A mob
		// without one (the shift worker, MOD-832) never appears on its own.
		for (ModMobs.MobDef<?> def : ModMobs.MOBS) {
			ModMobs.NaturalSpawn spawn = def.naturalSpawn();
			if (spawn != null) {
				BiomeModifications.addSpawn(BiomeSelectors.tag(spawn.biomes()), def.category(), MOBS.get(def.id()),
						spawn.weight(), spawn.minGroup(), spawn.maxGroup());
			}
		}
	}
}
