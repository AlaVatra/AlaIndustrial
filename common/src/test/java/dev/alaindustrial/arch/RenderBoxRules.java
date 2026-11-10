package dev.alaindustrial.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;

/**
 * A block entity renderer that draws beyond its own block implements {@code RenderBoxProvider} (MOD-962) — against
 * the compiled bytecode, like {@link ArchitectureRules}, of which it is the render-box chapter (kept in its own class:
 * {@code ArchitectureRules} is at the size ceiling of coding.md §10). Same import as there.
 *
 * <p><b>Why.</b> Both Forge loaders cull a block entity against the view frustum BEFORE they read
 * {@code shouldRenderOffScreen}; vanilla, and so Fabric, never does. NeoForge asks the renderer for its box by the
 * method name, MinecraftForge asks the block entity, and its client wraps that call to ask the renderer through
 * {@code RenderBoxProvider} instead ({@code ForgeRenderBoxes}). A renderer that draws past its block without the
 * interface keeps its box on NeoForge (the name still matches) and loses it on Forge — no compiler, no Fabric run
 * and no NeoForge test sees that. This rule is the loader-neutral half; NeoForge keeps its own bytecode test of the
 * bridge it calls ({@code OffScreenRendererBoxTest} in {@code neoforge/src/test}).
 *
 * <p><b>What counts as "draws beyond its block".</b> A renderer that overrides {@code shouldRenderOffScreen()}
 * (it says so itself), or one named in {@link #DRAWS_PAST_ITS_CELL} (nothing in its bytecode says how far it draws:
 * MOD-805). The same two inputs as the NeoForge test, whose list this one mirrors.
 *
 * <p><b>This lane has no Minecraft jar.</b> {@code BlockEntityRenderer} is read by name from the class files of
 * the renderers, never loaded, so the negative control aims the condition at stand-ins
 * ({@code fixture.renderbox}) and its floor proves the production import still finds {@link #KNOWN_OFF_SCREEN}.
 *
 * <p><b>Naming.</b> No {@code Test} suffix, like {@link ArchitectureRules}: nothing here to mutate.
 */
@AnalyzeClasses(packages = "dev.alaindustrial", importOptions = ImportOption.DoNotIncludeTests.class)
public class RenderBoxRules {

	static final String RENDERER = "net.minecraft.client.renderer.blockentity.BlockEntityRenderer";
	static final String PROVIDER = "dev.alaindustrial.client.render.RenderBoxProvider";
	static final String OFF_SCREEN = "shouldRenderOffScreen";

	private static final String RENDER = "dev.alaindustrial.client.render.";

	/**
	 * Renderers known to override {@code shouldRenderOffScreen}. Not what the rule checks — it checks every renderer
	 * it finds — but its floor: if one of these is not detected, the import or the detection no longer reaches the
	 * code the rule exists for, and a green run means nothing ({@code ArchitectureRulesNegativeControl}).
	 */
	static final List<String> KNOWN_OFF_SCREEN = List.of(
			RENDER + "ApiaryStationRenderer",
			RENDER + "ConstructionDroneRenderer",
			RENDER + "GardenDroneBlockEntityRenderer",
			RENDER + "HallSchematicRenderer",
			RENDER + "IncubatorBlockEntityRenderer",
			RENDER + "MobWheelBlockEntityRenderer",
			RENDER + "RadiantSolarPanelBlockEntityRenderer",
			RENDER + "TeleporterCapsuleDoorRenderer",
			RENDER + "WaterMillWheelBlockEntityRenderer",
			RENDER + "WindMillRotorBlockEntityRenderer");

	/**
	 * Renderers that draw past their block WITHOUT overriding {@code shouldRenderOffScreen} (MOD-805). Keep in step
	 * with {@code DRAWS_PAST_ITS_CELL} of the NeoForge {@code OffScreenRendererBoxTest}.
	 */
	static final Set<String> DRAWS_PAST_ITS_CELL = Set.of(RENDER + "CableAccessoryBlockEntityRenderer");

	/** A renderer drawing beyond its block implements {@code RenderBoxProvider}, so Forge can ask it for its box. */
	@ArchTest
	static final ArchRule renderersDrawingPastTheirBlockProvideTheirRenderBox = classes()
			.should(provideTheRenderBox(RENDERER, PROVIDER, DRAWS_PAST_ITS_CELL))
			.because("MinecraftForge culls a block entity by the box of its block entity, and its client asks the "
					+ "renderer only through RenderBoxProvider (ForgeRenderBoxes): without the interface whatever the "
					+ "renderer draws outside its block vanishes on Forge as that block leaves the view");

	/**
	 * A renderer (a class assignable to {@code renderer}) that overrides {@code shouldRenderOffScreen()} — in itself
	 * or a superclass, never only through an interface default — or is named in {@code pastCell}.
	 */
	static boolean drawsPastItsBlock(JavaClass type, String renderer, Set<String> pastCell) {
		if (type.isInterface() || !type.isAssignableTo(renderer)) {
			return false;
		}
		return pastCell.contains(type.getName()) || type.getAllMethods().stream()
				.anyMatch(method -> !method.getOwner().isInterface() && method.getName().equals(OFF_SCREEN)
						&& method.getRawParameterTypes().isEmpty());
	}

	/** Violated by a renderer that {@link #drawsPastItsBlock draws past its block} and is not a {@code provider}. */
	static ArchCondition<JavaClass> provideTheRenderBox(String renderer, String provider, Set<String> pastCell) {
		return new ArchCondition<>("implement " + provider + " when drawing past the block") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				if (drawsPastItsBlock(item, renderer, pastCell) && !item.isAssignableTo(provider)) {
					events.add(SimpleConditionEvent.violated(item, item.getName() + " draws past its block ("
							+ (pastCell.contains(item.getName()) ? "named in DRAWS_PAST_ITS_CELL" : "overrides "
									+ OFF_SCREEN) + ") but does not implement " + provider
							+ " — add `implements RenderBoxProvider<its block entity>`; its getRenderBoundingBox "
							+ "already serves NeoForge by name"));
				}
			}
		};
	}
}
