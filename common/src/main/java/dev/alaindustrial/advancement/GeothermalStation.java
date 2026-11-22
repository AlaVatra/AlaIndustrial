package dev.alaindustrial.advancement;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Which of a player's geothermal generators are running right now, for the "Station" step (MOD-1001).
 *
 * <p><b>The definition.</b> A generator is running when it burnt lava into EU within the last
 * {@link GeothermalMilestone#STATION_WINDOW_TICKS} game ticks, and it is still the same placed block: a generator
 * that was broken stops counting at once, whatever it burnt a moment before, and one whose chunk unloaded burns no
 * more and drops out within the window. So five
 * generators running at once means five different blocks, owned by the same player, each of which burnt lava in
 * the last second. Moving one generator to a new spot does not make two of it: the old block is gone.
 *
 * <p>No Minecraft imports, so the L1 lane pins the window and the liveness rule; the block entity hands in itself
 * as the key and a predicate that tells whether it is still in the world. Keys are held weakly, so a generator
 * nobody holds any more costs nothing; nothing here is saved, since a station that is running proves itself again
 * within a second of a restart.
 *
 * @param <K> the generator: its block entity in the game, anything with identity in the tests
 */
public final class GeothermalStation<K> {

	/** Owner, then each of their generators and the game tick it last burnt lava on. */
	private final Map<UUID, Map<K, Long>> lastBurn = new LinkedHashMap<>();

	/**
	 * {@code generator}, owned by {@code owner}, burnt lava on {@code gameTime}. Returns how many of the owner's
	 * generators are running at that time, this one included, counting no further than {@code enough}. A generator
	 * that is no longer {@code live}, or whose last burn is outside the window (or after {@code gameTime}: a game
	 * whose clock started again), is forgotten on the way.
	 */
	public int burnt(UUID owner, K generator, long gameTime, Predicate<K> live, int enough) {
		Map<K, Long> generators = lastBurn.computeIfAbsent(owner, k -> new WeakHashMap<>());
		generators.put(generator, gameTime);
		int running = 0;
		Iterator<Map.Entry<K, Long>> it = generators.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<K, Long> entry = it.next();
			long age = gameTime - entry.getValue();
			if (age < 0L || age >= GeothermalMilestone.STATION_WINDOW_TICKS || !live.test(entry.getKey())) {
				it.remove();
			} else if (running < enough) {
				running++;
			}
		}
		return running;
	}

	/** Forget every owner. */
	public void clear() {
		lastBurn.clear();
	}
}
