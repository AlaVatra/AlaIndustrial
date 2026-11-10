package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.gametest.LineOnlyScenarios;

/**
 * Forge registrations of the scenarios that exist on the 26.3 Minecraft line only (MOD-705), the same six lines as
 * the NeoForge class of the same name. Forge exists only on 26.3, so no byte-equality with another line is owed.
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
