package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.Industrialization;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Runs a Forge-only check that collects its findings as lines (the form the stage-2 probe used) and turns them into
 * a gametest verdict: no line passes, any line fails the test naming every line.
 */
final class ForgeChecks {

	private ForgeChecks() {
	}

	static void run(GameTestHelper helper, String what, Supplier<List<String>> check) {
		List<String> problems = check.get();
		for (String problem : problems) {
			Industrialization.LOGGER.info("[forge-check] {}: {}", what, problem);
		}
		if (!problems.isEmpty()) {
			helper.fail(what + ": " + problems.size() + " finding(s): " + String.join("; ", problems));
			return;
		}
		helper.succeed();
	}

	static void log(String format, Object... args) {
		Industrialization.LOGGER.info("[forge-check] " + format, args);
	}
}
