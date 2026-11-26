package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.client.blueprint.AutomationBlueprints;
import dev.alaindustrial.client.blueprint.BlueprintCatalog;
import dev.alaindustrial.client.blueprint.BlueprintDef;
import dev.alaindustrial.client.blueprint.BlueprintScreen;
import dev.alaindustrial.client.blueprint.ComponentBlueprints;
import dev.alaindustrial.client.blueprint.ComponentLineBlueprints;
import dev.alaindustrial.client.blueprint.ConstructionBlueprints;
import dev.alaindustrial.client.blueprint.EnergyBlueprints;
import dev.alaindustrial.client.blueprint.FieldToolsBlueprints;
import dev.alaindustrial.client.blueprint.GearBlueprints;
import dev.alaindustrial.client.blueprint.LogisticsBlueprints;
import dev.alaindustrial.client.blueprint.MachineBlueprints;
import dev.alaindustrial.client.blueprint.MaterialBlueprints;
import dev.alaindustrial.client.blueprint.MiscBlueprints;
import dev.alaindustrial.client.blueprint.NuclearBioBlueprints;
import dev.alaindustrial.client.blueprint.OreLineBlueprints;
import dev.alaindustrial.client.blueprint.PowerGearBlueprints;
import dev.alaindustrial.client.blueprint.ReactorBlueprints;
import dev.alaindustrial.client.blueprint.ReactorBuildBlueprints;
import dev.alaindustrial.client.blueprint.SettlementBlueprints;
import dev.alaindustrial.client.blueprint.StorageBlueprints;
import dev.alaindustrial.client.blueprint.StructureBlueprints;
import dev.alaindustrial.client.blueprint.SupplyBlueprints;
import dev.alaindustrial.client.blueprint.ToolGearBlueprints;
import dev.alaindustrial.client.blueprint.TransportBlueprints;
import dev.alaindustrial.client.blueprint.UpgradeBlueprints;
import dev.alaindustrial.client.blueprint.UtilityBlueprints;
import dev.alaindustrial.client.blueprint.WorkshopBlueprints;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MOD-934 — frames of the blueprint sheet (the I key, MOD-797): one sheet of every group file of
 * {@link BlueprintCatalog}, on its first and on its last step, plus the first "this is wrong" step of two sheets.
 * Before this stand the sheet's drawing (frame, title stamp, scene, numbered callout, step strip, the red and
 * green verdicts) was guarded by nobody's eye but the player's.
 *
 * <p>The frame is a still: {@link BlueprintScreen#holdStill} pauses the sheet with the step fully played, so the
 * late states (a generator lit once its fuel went in) are on and the idle sway is off. What the stand asserts
 * is that the sheet it asked for is the one on screen, at the step it asked for; what the picture is right
 * about is the reviewer's, by the caption of {@code gui_blueprint_sheet_} in {@code ShotFamilies} (R-GUI-16).
 */
@SuppressWarnings("UnstableApiUsage")
public final class BlueprintScreenStand {

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

	/** One sheet of each group file, in the catalogue's group order; the new groups of MOD-935 last. */
	private static final List<BlueprintDef> SHEETS = List.of(
			EnergyBlueprints.ENERGY_BASICS,
			MachineBlueprints.ORE_PROCESSING,
			LogisticsBlueprints.ITEM_PIPES,
			GearBlueprints.POWER_TOOLS,
			ReactorBlueprints.REACTOR_ROOM,
			StructureBlueprints.MOB_WHEEL,
			WorkshopBlueprints.MONITORING,
			UpgradeBlueprints.BUILDING_BLOCKS,
			PowerGearBlueprints.JETPACK_FLIGHT,
			ToolGearBlueprints.PROTECTIVE_SUITS,
			MaterialBlueprints.ALLOYS,
			ComponentBlueprints.CIRCUITS,
			NuclearBioBlueprints.URANIUM_FUEL,
			MiscBlueprints.ENGRAVED_PLATES,
			StorageBlueprints.DECOR_FORMS,
			OreLineBlueprints.TIN_LINE,
			ComponentLineBlueprints.RUBBER_MATERIALS,
			AutomationBlueprints.COLUMN_DRILL,
			ReactorBuildBlueprints.REACTOR_WATCH,
			FieldToolsBlueprints.APIARY,
			TransportBlueprints.ELEVATOR,
			SettlementBlueprints.SETTLEMENT,
			ConstructionBlueprints.QUARRY_BLOCKS,
			SupplyBlueprints.RADIATION_CARE,
			UtilityBlueprints.MOB_TRAP);

	/** Sheets whose first "this is wrong" step gets a frame of its own: the red callout and its cross. */
	private static final List<BlueprintDef> WRONG = List.of(EnergyBlueprints.ENERGY_BASICS,
			TransportBlueprints.ESCALATOR);

	private BlueprintScreenStand() {
	}

	public static void shoot(ClientGameTestContext context) {
		for (BlueprintDef def : SHEETS) {
			shot(context, def, 0, "gui_blueprint_sheet_" + def.id() + "_first");
			shot(context, def, def.steps().size() - 1, "gui_blueprint_sheet_" + def.id() + "_last");
		}
		for (BlueprintDef def : WRONG) {
			shot(context, def, firstWrong(def), "gui_blueprint_sheet_" + def.id() + "_wrong");
		}
		context.runOnClient(mc -> mc.setScreenAndShow(null));
		context.waitTicks(3);
	}

	/** Opens {@code def} on step {@code index}, held still, and takes the frame {@code name}. */
	private static void shot(ClientGameTestContext context, BlueprintDef def, int index, String name) {
		context.runOnClient(mc -> {
			BlueprintScreen screen = new BlueprintScreen(def, null);
			mc.setScreenAndShow(screen);
			screen.holdStill(index);
		});
		// A few ticks for the scene's block entities and item icons to be built and drawn once.
		context.waitTicks(4);
		String shown = context.computeOnClient(mc -> mc.gui.screen() instanceof BlueprintScreen screen
				? screen.blueprint().id() + "#" + screen.step() : String.valueOf(mc.gui.screen()));
		if (!shown.equals(def.id() + "#" + index)) {
			throw new AssertionError("[BLUEPRINT] asked for sheet " + def.id() + " at step " + (index + 1)
					+ ", the screen shows " + shown + ": the frame " + name + " would picture the wrong sheet");
		}
		LOG.info("[BLUEPRINT] {} -> {}", name, takeCleanScreenshot(context, name).toAbsolutePath());
	}

	/** The index of the first step marked "this is wrong"; a sheet in {@link #WRONG} without one is a stand bug. */
	private static int firstWrong(BlueprintDef def) {
		for (int i = 0; i < def.steps().size(); i++) {
			if (def.steps().get(i).verdict() == BlueprintDef.Verdict.BAD) {
				return i;
			}
		}
		throw new AssertionError("[BLUEPRINT] " + def.id() + " has no 'this is wrong' step to photograph");
	}
}
