package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.Industrialization;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;

/**
 * Collects the JSON documents of the Forge roster datapack ({@link RosterPack}, ADR-053): one
 * {@code minecraft:function} test instance per test, and one empty environment per environment id the tests name.
 *
 * <p>Every field of an instance is read from the {@link TestData} that {@code GameTestData.of} built, so the
 * values come from the same Java as on the other lanes (ADR-038, point 5); only the environment is written by id.
 * Naming it by id is deliberate: environments decoded by value would be equal records, and the game batches tests
 * by environment holder, so {@code config_overrides} and {@code config_overrides_2} would collapse into one batch.
 */
final class RosterPackWriter {

	/** What {@code data/<ns>/test_environment/<path>.json} holds: the empty {@code all_of}, as on Fabric. */
	private static final String EMPTY_ENVIRONMENT = "{\"type\":\"minecraft:all_of\",\"definitions\":[]}";

	private final Map<Identifier, byte[]> files = new LinkedHashMap<>();
	private final Map<Identifier, String> sources = new LinkedHashMap<>();
	private final Set<Identifier> environments = new LinkedHashSet<>();
	private int instances;

	/** One test instance {@code id} running the body registered under the same id in {@code TEST_FUNCTION}. */
	void functionTest(Identifier id, TestData<?> data, Identifier environment, String source) {
		String previous = sources.putIfAbsent(id, source);
		if (previous != null) {
			throw new IllegalStateException("Duplicate Forge gametest id " + id + ": declared by " + previous
					+ " and by " + source);
		}
		String json = "{"
				+ "\"type\":\"minecraft:function\","
				+ "\"function\":\"" + id + "\","
				+ "\"environment\":\"" + environment + "\","
				+ "\"dimension\":\"" + data.dimension().identifier() + "\","
				+ "\"structure\":\"" + data.structure() + "\","
				+ "\"max_ticks\":" + data.maxTicks() + ","
				+ "\"setup_ticks\":" + data.setupTicks() + ","
				+ "\"required\":" + data.required() + ","
				+ "\"rotation\":\"" + data.rotation().getSerializedName() + "\","
				+ "\"manual_only\":" + data.manualOnly() + ","
				+ "\"max_attempts\":" + data.maxAttempts() + ","
				+ "\"required_successes\":" + data.requiredSuccesses() + ","
				+ "\"sky_access\":" + data.skyAccess() + ","
				+ "\"padding\":" + data.padding()
				+ "}";
		put(Identifier.fromNamespaceAndPath(id.getNamespace(), "test_instance/" + id.getPath() + ".json"), json);
		environments.add(environment);
		instances++;
	}

	/**
	 * Writes an empty environment for every id in our namespace, the NeoForge rule ("registers an empty one under
	 * the id"). An id in another namespace must exist in data ({@code minecraft:default} does in vanilla).
	 */
	void writeEnvironments() {
		for (Identifier environment : environments) {
			if (Industrialization.MOD_ID.equals(environment.getNamespace())) {
				put(Identifier.fromNamespaceAndPath(environment.getNamespace(),
						"test_environment/" + environment.getPath() + ".json"), EMPTY_ENVIRONMENT);
			}
		}
	}

	private void put(Identifier path, String json) {
		files.put(path, json.getBytes(StandardCharsets.UTF_8));
	}

	Map<Identifier, byte[]> files() {
		return files;
	}

	int instances() {
		return instances;
	}

	int environments() {
		return environments.size();
	}
}
