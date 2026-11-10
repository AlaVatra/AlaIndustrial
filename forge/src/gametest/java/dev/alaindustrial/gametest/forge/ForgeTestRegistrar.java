package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.compat.GameTestData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/**
 * The hand-wired Forge tests as they are declared ({@link ForgeGameTests#registerTest}): id, body and test data.
 * The bodies go into {@code TEST_FUNCTION}, the instances into the roster pack, from this one list.
 */
final class ForgeTestRegistrar {

	/**
	 * Stands in for the environment holder inside {@link TestData}: the pack names the environment by id, so this
	 * holder is never serialised or run.
	 */
	static final Holder<TestEnvironmentDefinition<?>> PLACEHOLDER_ENVIRONMENT =
			Holder.direct(new TestEnvironmentDefinition.AllOf(List.of()));

	/** One hand-wired test. */
	record Test(Identifier id, Consumer<GameTestHelper> body, TestData<Holder<TestEnvironmentDefinition<?>>> data,
			Identifier environment) {}

	private final List<Test> tests = new ArrayList<>();

	/** A test on the rig, in {@code environment}, no sky, not rotated. */
	void add(String name, int maxTicks, boolean required, Identifier environment, Consumer<GameTestHelper> body) {
		tests.add(new Test(Industrialization.id(name), body,
				GameTestData.of(PLACEHOLDER_ENVIRONMENT, ScenarioRosterForge.DEFAULT_STRUCTURE, maxTicks, required,
						Rotation.NONE, false, ScenarioRosterForge.PADDING),
				environment));
	}

	List<Test> tests() {
		return List.copyOf(tests);
	}
}
