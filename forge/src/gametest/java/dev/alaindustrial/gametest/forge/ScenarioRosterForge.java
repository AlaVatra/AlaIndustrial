package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.RosterEntry;
import dev.alaindustrial.gametest.ScenarioRoster;
import dev.alaindustrial.gametest.compat.GameTestData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Forge lane's replay of {@link ScenarioRoster#ALL} (MOD-962 stage 3, ADR-053; the other lanes: ADR-038):
 * <ol>
 *   <li>each body goes into {@code minecraft:test_function} from {@link RegisterEvent} — Forge posts it for every
 *       key of {@code BuiltInRegistries.REGISTRY}, and for a registry Forge does not wrap {@code register} falls
 *       through to the vanilla registry ({@code GameData.postRegisterEvents});</li>
 *   <li>a vanilla {@code minecraft:function} test instance pointing at that key is written as JSON into the
 *       in-memory datapack {@link RosterPack}, which the game-test server loads like any datapack.</li>
 * </ol>
 * Forge runs the NeoForge lane's parameters ({@link RosterEntry#params(RosterEntry.Lane)}): same rig, same empty
 * environments, same ids.
 */
final class ScenarioRosterForge {

	private static final Logger LOGGER = LoggerFactory.getLogger("alaindustrial/scenario-roster");

	/** Lane default structure: the 8x8x8 rig, shared with NeoForge's gametest data. */
	static final Identifier DEFAULT_STRUCTURE = Industrialization.id("gametest_rig");

	/** Lane default environment: the one every hand-wired test uses. */
	static final Identifier DEFAULT_ENVIRONMENT = Industrialization.id("empty_env");

	/** Lane padding, as on NeoForge. */
	static final int PADDING = 1;

	private ScenarioRosterForge() {}

	/** Wired from {@link ForgeGameTestBootstrap#init}. */
	static void init(BusGroup modBus) {
		RegisterEvent.getBus(modBus).addListener(ScenarioRosterForge::registerFunctions);
	}

	private static void registerFunctions(RegisterEvent event) {
		event.<Consumer<GameTestHelper>>register(Registries.TEST_FUNCTION, helper -> {
			for (RosterEntry entry : ScenarioRoster.ALL) {
				helper.register(entry.testId(RosterEntry.Lane.FORGE), entry.body());
			}
			LOGGER.info("[scenario roster] forge: {} test functions registered", ScenarioRoster.ALL.size());
		});
	}

	/**
	 * Roster scenarios whose check meets a behaviour of Forge itself, not of the port, by entry name, with the reason.
	 * They still run on this lane, as optional tests ({@code required=false}): a failure is reported, but does not
	 * fail the lane, and the floor ({@code roster_is_replayed_on_this_lane}) still finds every entry. A name that is
	 * no longer in the roster throws when the pack is built; an entry that PASSES turns the lane red
	 * ({@code :forge:runGameTests} reads the names from {@link #OPTIONAL_TESTS_FILE_PROPERTY}), so a Forge update
	 * that fixes the behaviour cannot leave its line here forever.
	 */
	static final Map<String, String> NOT_ON_FORGE = Map.of();

	/**
	 * System property naming the file the ids of the {@link #NOT_ON_FORGE} tests are written to, one per line; the
	 * report check of {@code :forge:runGameTests} fails on such a test that did not fail.
	 */
	static final String OPTIONAL_TESTS_FILE_PROPERTY = "alaindustrial.forgeOptionalTestsFile";

	private static void writeOptionalTestIds() {
		String to = System.getProperty(OPTIONAL_TESTS_FILE_PROPERTY);
		if (to == null || to.isBlank()) {
			return;
		}
		StringBuilder out = new StringBuilder();
		new TreeSet<>(NOT_ON_FORGE.keySet()).forEach(name -> out.append(Industrialization.id(name)).append('\n'));
		try {
			Path file = Path.of(to);
			Files.createDirectories(file.getParent());
			Files.writeString(file, out, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("could not write the optional Forge test ids to " + to, e);
		}
	}

	/** Writes one test instance per roster entry into the roster pack. */
	static void registerInstances(RosterPackWriter writer) {
		Set<String> unused = new HashSet<>(NOT_ON_FORGE.keySet());
		for (RosterEntry entry : ScenarioRoster.ALL) {
			boolean notOnForge = unused.remove(entry.name());
			boolean required = entry.required() && !notOnForge;
			RosterEntry.LaneParams params = entry.params(RosterEntry.Lane.FORGE);
			Identifier id = entry.testId(RosterEntry.Lane.FORGE);
			Identifier environment = entry.environment() != null ? entry.environment() : DEFAULT_ENVIRONMENT;
			Identifier structure = params.structure() != null ? params.structure() : DEFAULT_STRUCTURE;
			writer.functionTest(id, GameTestData.of(ForgeTestRegistrar.PLACEHOLDER_ENVIRONMENT, structure,
					params.maxTicks(), required, entry.rotation(), params.skyAccess(), PADDING),
					environment, "ScenarioRoster");
		}
		if (!unused.isEmpty()) {
			throw new IllegalStateException("ScenarioRosterForge.NOT_ON_FORGE names entries the roster no longer has: "
					+ unused);
		}
		writeOptionalTestIds();
		LOGGER.info("[scenario roster] forge: {} roster test instances written", ScenarioRoster.ALL.size());
	}
}
