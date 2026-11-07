package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.ChestBoatVariants;
import dev.alaindustrial.entity.ModChestBoat;
import dev.alaindustrial.entity.QuarryDrillBody;
import dev.alaindustrial.entity.StockDisplayFrameEntity;
import dev.alaindustrial.entity.TorchProjectile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The mod's entity types that are not mobs, declared once (MOD-959). Each is one {@link EntityDef}: its id,
 * factory, category and the vanilla builder calls (size, eye height, tracking range, update interval, …).
 * Fabric ({@code ModEntities}) and NeoForge ({@code ModEntitiesNeoForge}) replay {@link #ENTITIES} and build
 * every type through {@link EntityDef#build()}, so the two loaders cannot drift; the renderer is the client half,
 * {@code ClientContentManifest.ENTITY_RENDERERS}. The mobs, which also carry attributes and spawn rules, are
 * {@link ModMobs#MOBS}.
 *
 * <p>Code that needs the typed handle asks {@link EntityDef#type()}, which reads the registry by id — the same
 * idiom {@link ModMobs.MobDef#type()} uses, so nothing closes over a loader handle.
 *
 * <p><b>Order is registration order</b> on both loaders: the frame, the drill body, then the chest boats in
 * {@link ChestBoatVariants#ALL} order — the order the per-loader lists had before this list existed — and the
 * entities added since, at the end.
 */
public final class ModEntityTypes {

	private ModEntityTypes() {
	}

	/**
	 * One entity type: its registry id, factory and category, and the vanilla builder calls applied on top of
	 * {@code EntityType.Builder.of(factory, category)}.
	 */
	public record EntityDef<T extends Entity>(String id, EntityType.EntityFactory<T> factory, MobCategory category,
			UnaryOperator<EntityType.Builder<T>> builder) {

		public Identifier key() {
			return Industrialization.id(id);
		}

		public ResourceKey<EntityType<?>> resourceKey() {
			return ResourceKey.create(Registries.ENTITY_TYPE, key());
		}

		/** Builds the type; both loaders register what this returns, Fabric eagerly, NeoForge in its supplier. */
		public EntityType<T> build() {
			return builder.apply(EntityType.Builder.of(factory, category)).build(resourceKey());
		}

		/** The registered type; throws when asked before the loader registered it. */
		@SuppressWarnings("unchecked")
		public EntityType<T> type() {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(key());
			if (type == null || !BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(key())) {
				throw new IllegalStateException("entity type '" + key() + "' is not registered (yet)");
			}
			return (EntityType<T>) type;
		}
	}

	/**
	 * The Stock Display Frame (MOD-066). Builder values mirror vanilla {@code EntityTypes.ITEM_FRAME}:
	 * {@code MobCategory.MISC}, {@code sized(0.5, 0.5)}, {@code eyeHeight(0)}, {@code clientTrackingRange(10)},
	 * {@code updateInterval(Integer.MAX_VALUE)} — the frame never moves, and dirty {@code SynchedEntityData} (the
	 * stock count) syncs immediately regardless of the update interval.
	 */
	public static final EntityDef<StockDisplayFrameEntity> STOCK_DISPLAY_FRAME = new EntityDef<>(
			"stock_display_frame", StockDisplayFrameEntity::new, MobCategory.MISC,
			b -> b.noLootTable().sized(0.5F, 0.5F).eyeHeight(0.0F).clientTrackingRange(10)
					.updateInterval(Integer.MAX_VALUE));

	/**
	 * The quarry's drill body (MOD-952): an invisible solid box the quarry moves with its drill every tick, never
	 * saved, never summoned, sized by its synced data; updated every tick so the box follows the drawn drill.
	 */
	public static final EntityDef<QuarryDrillBody> QUARRY_DRILL_BODY = new EntityDef<>(
			QuarryDrillBody.ID.getPath(), QuarryDrillBody::new, MobCategory.MISC,
			b -> b.noLootTable().noSummon().noSave().fireImmune().sized(1.0F, 1.0F).clientTrackingRange(10)
					.updateInterval(1));

	/**
	 * The chest boats (MOD-785), one entity type per wood × chest pair of {@link ChestBoatVariants#ALL}, in table
	 * order. Builder values are vanilla {@code EntityTypes.OAK_CHEST_BOAT}'s (the chest raft's are the same): size
	 * 1.375 × 0.5625, eye height 0.5625, tracking 10.
	 */
	public static final List<EntityDef<ModChestBoat>> CHEST_BOATS = chestBoats();

	/**
	 * A torch fired from the slingshot (MOD-871). Builder values are vanilla {@code EntityTypes.SNOWBALL}'s: size
	 * 0.25 × 0.25, tracking 4, update interval 10 — it flies and lands like a snowball.
	 */
	public static final EntityDef<TorchProjectile> TORCH_PROJECTILE = new EntityDef<>(
			"torch_projectile", TorchProjectile::new, MobCategory.MISC,
			b -> b.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));

	private static final Map<String, EntityDef<ModChestBoat>> CHEST_BOATS_BY_ID = byId(CHEST_BOATS);

	/** Every non-mob entity type of the mod, in registration order; both loaders replay it. */
	public static final List<EntityDef<?>> ENTITIES = entities();

	/** The chest boat of one wood × chest pair. */
	public static EntityDef<ModChestBoat> chestBoat(ChestBoatVariants.Variant variant) {
		EntityDef<ModChestBoat> def = CHEST_BOATS_BY_ID.get(variant.id());
		if (def == null) {
			throw new IllegalArgumentException("ModEntityTypes: no chest boat for the pair '" + variant.id() + "'");
		}
		return def;
	}

	private static List<EntityDef<ModChestBoat>> chestBoats() {
		List<EntityDef<ModChestBoat>> boats = new ArrayList<>();
		for (ChestBoatVariants.Variant variant : ChestBoatVariants.ALL) {
			boats.add(new EntityDef<>(variant.id(), variant::create, MobCategory.MISC,
					b -> b.noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10)));
		}
		return Collections.unmodifiableList(boats);
	}

	private static Map<String, EntityDef<ModChestBoat>> byId(List<EntityDef<ModChestBoat>> defs) {
		Map<String, EntityDef<ModChestBoat>> out = new LinkedHashMap<>();
		for (EntityDef<ModChestBoat> def : defs) {
			out.put(def.id(), def);
		}
		return Collections.unmodifiableMap(out);
	}

	private static List<EntityDef<?>> entities() {
		List<EntityDef<?>> all = new ArrayList<>();
		all.add(STOCK_DISPLAY_FRAME);
		all.add(QUARRY_DRILL_BODY);
		all.addAll(CHEST_BOATS);
		all.add(TORCH_PROJECTILE);
		return Collections.unmodifiableList(all);
	}
}
