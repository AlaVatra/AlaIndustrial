package dev.alaindustrial.gametest.forge;

/**
 * Forge registrations of the scenarios that exist on ONE Minecraft line only (MOD-705), the twin of the NeoForge
 * class of the same name: {@link ForgeGameTests} calls {@link #register} on both lines, so that call stays
 * byte-equal; the content differs per line. On 26.2 the only line-only scenarios are loader-only ones: the
 * {@code ToolAction} answers of the right-click tools (MOD-962), whose bodies are in
 * {@link ElectricToolAbilityForgeScenarios} — the same ids, ticks and flags as NeoForge's {@code ItemAbility} pair.
 */
final class LineOnlyRegistrations {

	private LineOnlyRegistrations() {}

	static void register(ForgeTestRegistrar event) {
		// The ToolAction half of the four right-click tools — a Forge 26.2 mechanism only.
		ForgeGameTests.registerTest(event, "right_click_tools_ability_answers", 40, true,
				ElectricToolAbilityForgeScenarios::abilitiesAnswerAsTheVanillaTools);
		ForgeGameTests.registerTest(event, "right_click_tools_simulated_probe_writes_nothing", 40, true,
				ElectricToolAbilityForgeScenarios::simulatedProbeAnswersAndWritesNothing);
	}
}
