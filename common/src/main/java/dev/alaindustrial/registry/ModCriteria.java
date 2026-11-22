package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.advancement.GeothermalMilestone;
import dev.alaindustrial.advancement.GeothermalMilestoneTrigger;
import dev.alaindustrial.advancement.MutationCompletedTrigger;
import dev.alaindustrial.advancement.NetworkEnergizedTrigger;
import dev.alaindustrial.advancement.ReactorMilestone;
import dev.alaindustrial.advancement.ReactorMilestoneTrigger;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.NetworkManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Central registration for Industrialization's custom advancement criteria (see task MOD-015).
 *
 * <p>MOD-022 facade: NeoForge freezes the vanilla {@code TRIGGER_TYPES} registry before mod construction,
 * so a trigger is bound lazily per loader — Fabric via the eager {@link #init()} below, NeoForge via a
 * {@code DeferredRegister} (see {@code ModCriteriaNeoForge}) — and read through a {@code Supplier}.
 *
 * <p><b>Which criteria exist is decided once, here</b> (MOD-555): {@link #TRIGGERS} is the list both
 * loaders replay, so only the registration mechanism differs between them. Each entry used to be written
 * three times — the handle and factory here, an eager registration block in {@code init()}, and a
 * {@code DeferredHolder} field plus a binding line on NeoForge.
 */
public final class ModCriteria {
	private ModCriteria() {
	}

	/**
	 * One advancement criterion: its registry path, how to build the trigger, and where to publish the
	 * registered result.
	 *
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param factory builds the trigger instance the loader registers
	 * @param bind    publishes the registered trigger into its handle above
	 */
	public record CriterionDef<T extends CriterionTrigger<?>>(String id, Supplier<T> factory,
			Consumer<Supplier<T>> bind) {
	}

	/** What a handle holds until its loader binds it: a loud failure, never a silent NPE. */
	private static <T> Supplier<T> unbound(String handle) {
		return () -> {
			throw new IllegalStateException("ModCriteria." + handle + " read before its loader bound it");
		};
	}

	public static Supplier<NetworkEnergizedTrigger> NETWORK_ENERGIZED = unbound("NETWORK_ENERGIZED");

	/** The incubator's criterion (MOD-118). */
	public static Supplier<MutationCompletedTrigger> MUTATION_COMPLETED = unbound("MUTATION_COMPLETED");

	/** The reactor branch's criterion (MOD-473). */
	public static Supplier<ReactorMilestoneTrigger> REACTOR_MILESTONE = unbound("REACTOR_MILESTONE");

	/** The geothermal chain's criterion (MOD-1001). */
	public static Supplier<GeothermalMilestoneTrigger> GEOTHERMAL_MILESTONE = unbound("GEOTHERMAL_MILESTONE");

	/** Every criterion, in one shared registration order. Both loaders replay this list. */
	public static final List<CriterionDef<?>> TRIGGERS = List.of(
			new CriterionDef<>("network_energized", NetworkEnergizedTrigger::new, t -> NETWORK_ENERGIZED = t),
			new CriterionDef<>("mutation_completed", MutationCompletedTrigger::new, t -> MUTATION_COMPLETED = t),
			new CriterionDef<>("reactor_milestone", ReactorMilestoneTrigger::new, t -> REACTOR_MILESTONE = t),
			new CriterionDef<>("geothermal_milestone", GeothermalMilestoneTrigger::new,
					t -> GEOTHERMAL_MILESTONE = t));

	/**
	 * Fabric registration: the {@code TRIGGER_TYPES} registry stays writable during init, so every entry of
	 * {@link #TRIGGERS} is registered eagerly and bound to a constant supplier. NeoForge replays the same
	 * list through a {@code DeferredRegister} (see {@code ModCriteriaNeoForge}).
	 */
	public static void init() {
		for (CriterionDef<?> def : TRIGGERS) {
			registerEagerly(def);
		}
	}

	/** One entry, the Fabric way. Separate from {@link #init()} only to capture the entry's type parameter. */
	private static <T extends CriterionTrigger<?>> void registerEagerly(CriterionDef<T> def) {
		ResourceKey<CriterionTrigger<?>> key =
				ResourceKey.create(Registries.TRIGGER_TYPE, Industrialization.id(def.id()));
		T trigger = Registry.register(BuiltInRegistries.TRIGGER_TYPES, key, def.factory().get());
		def.bind().accept(() -> trigger);
	}

	/**
	 * Credits {@code owner} with a reactor milestone, if they are online (MOD-473).
	 *
	 * <p>A reactor seals, produces and boils on its own tick, with no player in the event. The
	 * controller records its placer, so that is who the branch is written for — the alternative,
	 * awarding the nearest entity, hands somebody else's reactor to a passer-by.
	 *
	 * <p>An offline owner simply misses it: an advancement can only be granted to a loaded player, and
	 * queueing them up would mean persisting a list of pending awards per machine. Every step here is
	 * repeatable — the room is rescanned, the reactor runs again, the loop boils again — so the miss
	 * costs the player the next tick their reactor works while they are watching, not the advancement.
	 */
	public static void fireReactorMilestone(ServerLevel level, @Nullable UUID owner,
			ReactorMilestone milestone) {
		if (owner == null) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		if (player != null) {
			REACTOR_MILESTONE.get().trigger(player, milestone);
		}
	}

	/**
	 * Credits {@code owner} with a step of the geothermal chain, if they are online (MOD-1001): the same rule as
	 * {@link #fireReactorMilestone}, for the same reason. A generator burns lava on its own tick with nobody in the
	 * event, so the player who placed it is credited; an offline owner gets it the next time the generator burns
	 * while they are online.
	 */
	public static void fireGeothermalMilestone(ServerLevel level, @Nullable UUID owner,
			GeothermalMilestone milestone) {
		if (owner == null) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		if (player != null) {
			GEOTHERMAL_MILESTONE.get().trigger(player, milestone);
		}
	}

	/** The geothermal generator's id in the career statistics' per-generator breakdown (MOD-1001). */
	private static final Identifier GEOTHERMAL_GENERATOR = Industrialization.id("geothermal_generator");

	/**
	 * Credits {@code player} with the geothermal chain's million (MOD-1001) once their career EU from geothermal
	 * generators reaches {@link GeothermalMilestone#LAVA_MILLION_EU}. Called when their career statistics take in
	 * new production, so the count is the one the statistics already keep and save per player, with no counter of
	 * its own and no statistics chip; it follows that count's rules (the owner online and not in creative).
	 */
	public static void fireLavaMillionIfReached(ServerPlayer player, Map<Identifier, Long> producedByGenerator) {
		Long fromLava = producedByGenerator.get(GEOTHERMAL_GENERATOR);
		if (fromLava != null && fromLava >= GeothermalMilestone.LAVA_MILLION_EU) {
			GEOTHERMAL_MILESTONE.get().trigger(player, GeothermalMilestone.LAVA_MILLION);
		}
	}

	/**
	 * Fires {@link #NETWORK_ENERGIZED} for {@code player} if {@code pos} or any of its neighbours
	 * belong to an awake {@link EnergyNetwork}. Covers both ways a network can complete: the player
	 * placing the cable that connects the last producer/consumer pair (checked at {@code pos} itself,
	 * since a cable is a network member) and the player placing the producer/consumer machine that
	 * completes an existing cable run (checked via the neighbours, since a machine is never itself a
	 * network member — see MOD-015, MOD-016 code review).
	 */
	public static void tryFireNetworkEnergized(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (isAwakeNetworkAt(level, pos)) {
			NETWORK_ENERGIZED.get().trigger(player);
			return;
		}
		for (Direction dir : Direction.values()) {
			if (isAwakeNetworkAt(level, pos.relative(dir))) {
				NETWORK_ENERGIZED.get().trigger(player);
				return;
			}
		}
	}

	private static boolean isAwakeNetworkAt(ServerLevel level, BlockPos pos) {
		EnergyNetwork net = NetworkManager.networkAt(level, pos);
		return net != null && net.isAwake();
	}
}
