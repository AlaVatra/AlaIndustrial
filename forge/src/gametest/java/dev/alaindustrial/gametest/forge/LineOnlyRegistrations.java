package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.gametest.LineOnlyScenarios;

/**
 * Forge registrations of the scenarios that exist on ONE Minecraft line only (MOD-705), the twin of the NeoForge
 * class of the same name: {@link ForgeGameTests} calls {@link #register} on both lines, so that call stays
 * byte-equal; the content differs per line. On 26.3 they are the same six lines as NeoForge's, bodies in
 * {@link LineOnlyScenarios}.
 */
final class LineOnlyRegistrations {

	private LineOnlyRegistrations() {}

	static void register(ForgeTestRegistrar event) {
		ForgeGameTests.registerTest(event, "persistence_mod645_mc262_blockstate_tags_still_load", 40, true,
				LineOnlyScenarios::mod645_mc262BlockStateTagsStillLoad);
		ForgeGameTests.registerTest(event, "capsule_saved_by_262_heals_fuel_on_load", 40, true,
				LineOnlyScenarios::fun14CapsuleSavedBy262HealsFuelOnLoad);
		ForgeGameTests.registerTest(event, "hoe_without_transformer_passes_free", 40, true,
				LineOnlyScenarios::fun14HoeWithoutTransformerPassesFree);
		ForgeGameTests.registerTest(event, "hoe_tills_rooted_dirt_to_dirt", 40, true,
				LineOnlyScenarios::fun15TillsRootedDirtToDirt);
		ForgeGameTests.registerTest(event, "right_click_roster_declares_transformer", 40, true,
				LineOnlyScenarios::fun06RightClickRosterDeclaresTransformer);
		ForgeGameTests.registerTest(event, "shovel_diamond_tip_douses_lit_campfire", 40, true,
				LineOnlyScenarios::fun15DiamondTipDousesLitCampfire);
	}
}
