package dev.alaindustrial.gametest.forge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.compat.FeatureTypes;
import dev.alaindustrial.registry.ModFluidsManifest;
import dev.alaindustrial.registry.ModLootConditions;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.ModWorldgenTypes;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Forge-only gametests of what Forge itself loads (MOD-962 stage 3): the mod's datapack, our entries in the
 * registries the registry snapshot does not hold, the server-side mixin audit, the fluid types against
 * {@link ModFluidsManifest} and where the {@code forge/biome_modifier} files put the mod's placed features. The
 * first three came from the stage-2 probe; the last two are the Forge twins of the NeoForge L1.5 tests
 * {@code NeoForgeFluidTypeSnapshotTest#fluidTypesFollowTheManifest} and
 * {@code NeoForgeBiomeModifierSnapshotTest}, which need a live server and Forge has no JUnit-with-server lane.
 *
 * <p>Not ported, and why: {@code NeoForgeRecipeSyncTest} (recipe sync to the client is deferred on Forge by owner
 * decision, task.md 2026-11-09); {@code NeoForgeTxnCacheTest} (NeoForge's transaction cache; Forge has no
 * transactions, {@code ForgeTxnTest} covers our journal); {@code NeoForgeRegistrationTest} (superseded by the
 * roster's {@code RegistrySnapshotScenarios}); {@code NeoForgeAttachmentDefinitionTest} ({@code
 * PlayerAttachmentContainerTest} and {@code forge_attachments_survive_save_and_clone}); the NeoForge energy and
 * fluid runtime tests (the roster's energy and fluid scenarios and {@code forge_tank_snapshot_and_lying_foreign_tank});
 * the reflective property snapshot of NeoForge's {@code FluidType} (Forge's class has other fields: the manifest
 * check below is the shared contract).
 */
final class ForgeLoaderScenarios {

	private ForgeLoaderScenarios() {
	}

	/**
	 * The mod's datapack is part of the world, so "no datapack errors in the log" is about our data and not a pass
	 * by absence: our pack is selected and our recipes and advancements are loaded.
	 */
	static void datapackLoaded(GameTestHelper helper) {
		ForgeChecks.run(helper, "datapack", () -> {
			MinecraftServer server = helper.getLevel().getServer();
			List<String> failures = new ArrayList<>();
			boolean selected = server.getPackRepository().getSelectedIds().contains("mod:" + Industrialization.MOD_ID);
			boolean roster = server.getPackRepository().getSelectedIds().contains(RosterPack.ID);
			long recipes = server.getRecipeManager().getRecipes().stream()
					.filter(holder -> Industrialization.MOD_ID.equals(holder.id().identifier().getNamespace())).count();
			long advancements = server.getAdvancements().getAllAdvancements().stream()
					.filter(holder -> Industrialization.MOD_ID.equals(holder.id().getNamespace())).count();
			ForgeChecks.log("datapack: packs {}; {} of our recipes, {} of our advancements",
					server.getPackRepository().getSelectedIds(), recipes, advancements);
			if (!selected || !roster || recipes == 0 || advancements == 0) {
				failures.add("our datapacks are not loaded (mod pack selected=" + selected + ", roster pack selected="
						+ roster + ", recipes=" + recipes + ", advancements=" + advancements + ")");
			}
			return failures;
		});
	}

	/** Our entries in the registries the snapshot does not hold, against the shared list each one replays. */
	static void uncoveredRegistriesHoldOurs(GameTestHelper helper) {
		ForgeChecks.run(helper, "uncovered registries", () -> {
			MinecraftServer server = helper.getLevel().getServer();
			List<String> failures = new ArrayList<>();
			long recipeTypes = ModRecipes.families().stream().filter(f -> f.createType().isPresent()).count();
			expect(server, failures, Registries.RECIPE_TYPE, recipeTypes);
			expect(server, failures, Registries.RECIPE_SERIALIZER, ModRecipes.families().size());
			expect(server, failures, Registries.LOOT_CONDITION_TYPE, ModLootConditions.CONDITIONS.size());
			expect(server, failures, Registries.PLACEMENT_MODIFIER_TYPE, ModWorldgenTypes.PLACEMENT_MODIFIERS.size());
			expect(server, failures, FeatureTypes.registry(), ModWorldgenTypes.FEATURE_TYPES.size());
			expect(server, failures, Registries.STRUCTURE_TYPE, ModWorldgenTypes.STRUCTURE_TYPES.size());
			expect(server, failures, Registries.STRUCTURE_PIECE, ModWorldgenTypes.STRUCTURE_PIECES.size());
			expect(server, failures, Registries.POINT_OF_INTEREST_TYPE, 1);
			expect(server, failures, Registries.VILLAGER_PROFESSION, 1);
			expect(server, failures, Registries.CREATIVE_MODE_TAB, 1);
			expect(server, failures, ForgeRegistries.Keys.FLUID_TYPES, ModFluidsManifest.FLUIDS.size());
			expect(server, failures, ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, 1);
			return failures;
		});
	}

	private static <T> void expect(MinecraftServer server, List<String> failures,
			ResourceKey<? extends Registry<? extends T>> key, long expected) {
		long actual = server.registryAccess().<T>lookup(key)
				.map(registry -> registry.keySet().stream()
						.filter(id -> Industrialization.MOD_ID.equals(id.getNamespace())).count())
				.orElse(-1L);
		ForgeChecks.log("registry {}: {} of ours (expected {})", key.identifier(), actual, expected);
		if (actual != expected) {
			failures.add(key.identifier() + " holds " + actual + " of ours, expected " + expected);
		}
	}

	/** Loads every mixin target of the server side now, so a mixin that cannot apply fails here, not on first use. */
	static void mixinAuditPasses(GameTestHelper helper) {
		ForgeChecks.run(helper, "mixin audit", () -> {
			try {
				org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
				return List.of();
			} catch (RuntimeException | Error e) {
				Industrialization.LOGGER.error("[forge-check] mixin audit threw", e);
				return List.of("the server-side mixin audit threw " + e);
			}
		});
	}

	/**
	 * Every form of every {@link ModFluidsManifest} entry reports, through its Forge fluid type, the entry's
	 * viscosity, temperature, lighter-than-air sign and name key — and the type is ours, under the entry's id.
	 */
	static void fluidTypesFollowTheManifest(GameTestHelper helper) {
		ForgeChecks.run(helper, "fluid types", () -> {
			List<String> wrong = new ArrayList<>();
			for (ModFluidsManifest.FluidDef<?> def : ModFluidsManifest.FLUIDS) {
				List<String> forms = def.hasFlowing() ? List.of(def.id(), def.flowingId()) : List.of(def.id());
				for (String path : forms) {
					Fluid fluid = BuiltInRegistries.FLUID.getValue(Industrialization.id(path));
					FluidType type = fluid.getFluidType();
					Identifier typeId = ForgeRegistries.FLUID_TYPES.get().getKey(type);
					String expected = Industrialization.id(def.id()) + " " + def.physics().viscosity() + "/"
							+ def.physics().temperature() + "/" + def.physics().lighterThanAir() + "/"
							+ def.descriptionId();
					String actual = typeId + " " + type.getViscosity() + "/" + type.getTemperature() + "/"
							+ type.isLighterThanAir() + "/" + type.getDescriptionId();
					if (!expected.equals(actual)) {
						wrong.add(path + ": expected " + expected + ", got " + actual);
					}
				}
			}
			return wrong;
		});
	}

	/** The mob spawn file is pinned by {@code BiomeModifierTableTest}, not here (as on NeoForge). */
	private static final String ADD_SPAWNS = "forge:add_spawns";

	/** Every feature biome modifier file of this mod on Forge, by name, its keys sorted, values as compact JSON. */
	static final List<String> FILES = List.of(
			"abandoned_labs biomes=\"#alaindustrial:has_abandoned_labs\" features=[\"alaindustrial:abandoned_lab\"]"
					+ " step=\"surface_structures\" type=\"forge:add_features\"",
			"kok_sagyz biomes=\"#alaindustrial:has_kok_sagyz\" features=[\"alaindustrial:kok_sagyz\"]"
					+ " step=\"vegetal_decoration\" type=\"forge:add_features\"",
			"nether_ores biomes=\"#alaindustrial:has_palladium_ore\" features=[\"alaindustrial:palladium_ore\"]"
					+ " step=\"underground_ores\" type=\"forge:add_features\"",
			"oil_geysers biomes=\"#alaindustrial:has_oil_geysers\" features=[\"alaindustrial:oil_geyser\"]"
					+ " step=\"lakes\" type=\"forge:add_features\"",
			"oil_lakes_deep biomes=\"#alaindustrial:has_underground_oil_lakes\""
					+ " features=[\"alaindustrial:oil_lake_deep\"] step=\"lakes\" type=\"forge:add_features\"",
			"oil_lakes_surface biomes=\"#alaindustrial:has_surface_oil_lakes\""
					+ " features=[\"alaindustrial:oil_lake_surface\"] step=\"lakes\" type=\"forge:add_features\"",
			"oil_lakes_underground biomes=\"#alaindustrial:has_underground_oil_lakes\""
					+ " features=[\"alaindustrial:oil_lake_underground\"] step=\"lakes\""
					+ " type=\"forge:add_features\"",
			"ores biomes=\"#minecraft:is_overworld\" features=[\"alaindustrial:tin_ore\",\"alaindustrial:silver_ore\","
					+ "\"alaindustrial:nickel_ore\",\"alaindustrial:sulfur_ore\",\"alaindustrial:uranium_ore\"]"
					+ " step=\"underground_ores\" type=\"forge:add_features\"");

	private static final String LAKES =
			"lakes=[oil_geyser, oil_lake_deep, oil_lake_surface, oil_lake_underground]";
	private static final String ORES = "underground_ores=[tin_ore, silver_ore, nickel_ore, sulfur_ore, uranium_ore]";

	/** The NeoForge reference, one line per probe biome: the Forge files must land the features the same way. */
	static final List<String> BIOMES = List.of(
			"minecraft:plains " + LAKES + " surface_structures=[abandoned_lab] " + ORES,
			"minecraft:jungle " + LAKES + " surface_structures=[abandoned_lab] " + ORES,
			"minecraft:savanna " + LAKES + " surface_structures=[abandoned_lab] " + ORES
					+ " vegetal_decoration=[kok_sagyz]",
			"minecraft:nether_wastes underground_ores=[palladium_ore]",
			"minecraft:the_end -");

	private static final List<ResourceKey<Biome>> PROBES =
			List.of(Biomes.PLAINS, Biomes.JUNGLE, Biomes.SAVANNA, Biomes.NETHER_WASTES, Biomes.THE_END);

	/**
	 * Forge loads the same eight feature biome modifier files as NeoForge (Forge's type names), and they put the
	 * mod's placed features into the same probe biomes, steps and order as on NeoForge.
	 */
	static void biomeModifiersPlaceFeatures(GameTestHelper helper) {
		ForgeChecks.run(helper, "biome modifiers", () -> {
			MinecraftServer server = helper.getLevel().getServer();
			List<String> failures = new ArrayList<>();
			List<String> files = new ArrayList<>();
			for (Map.Entry<String, Resource> file : biomeModifierFiles(server).entrySet()) {
				JsonObject json = JsonParser.parseString(read(file.getValue())).getAsJsonObject();
				if (ADD_SPAWNS.equals(json.get("type").getAsString())) {
					continue;
				}
				StringBuilder line = new StringBuilder(file.getKey());
				for (String key : new TreeSet<>(json.keySet())) {
					JsonElement value = json.get(key);
					line.append(' ').append(key).append('=').append(value);
				}
				files.add(line.toString());
			}
			if (!files.equals(FILES)) {
				failures.add("the forge/biome_modifier files differ from the reference:\n" + String.join("\n", files));
			}
			List<String> biomes = new ArrayList<>();
			for (ResourceKey<Biome> biome : PROBES) {
				biomes.add(line(server, biome));
			}
			if (!biomes.equals(BIOMES)) {
				failures.add("the mod's placed features land in other biomes, steps or order on Forge:\n"
						+ String.join("\n", biomes));
			}
			return failures;
		});
	}

	private static Map<String, Resource> biomeModifierFiles(MinecraftServer server) {
		Map<String, Resource> files = new TreeMap<>();
		for (Map.Entry<Identifier, Resource> entry : server.getResourceManager()
				.listResources("forge/biome_modifier", id -> id.getPath().endsWith(".json")).entrySet()) {
			Identifier id = entry.getKey();
			if (Industrialization.MOD_ID.equals(id.getNamespace())) {
				String path = id.getPath();
				files.put(path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length()),
						entry.getValue());
			}
		}
		return files;
	}

	private static String read(Resource resource) {
		try (InputStream in = resource.open()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("cannot read " + resource.sourcePackId(), e);
		}
	}

	/** {@code <biome> <step>=[<mod feature paths in list order>] …}, steps in generation order; "-" if none. */
	private static String line(MinecraftServer server, ResourceKey<Biome> biome) {
		List<HolderSet<PlacedFeature>> steps = server.registryAccess().lookupOrThrow(Registries.BIOME)
				.getOrThrow(biome).value().getGenerationSettings().features();
		StringBuilder out = new StringBuilder(biome.identifier().toString());
		boolean any = false;
		for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
			if (step.ordinal() >= steps.size()) {
				break;
			}
			List<String> mod = new ArrayList<>();
			for (Holder<PlacedFeature> holder : steps.get(step.ordinal())) {
				holder.unwrapKey().map(ResourceKey::identifier)
						.filter(id -> Industrialization.MOD_ID.equals(id.getNamespace()))
						.map(Identifier::getPath).ifPresent(mod::add);
			}
			if (!mod.isEmpty()) {
				out.append(' ').append(step.getSerializedName()).append('=').append(mod);
				any = true;
			}
		}
		return any ? out.toString() : out.append(" -").toString();
	}
}
