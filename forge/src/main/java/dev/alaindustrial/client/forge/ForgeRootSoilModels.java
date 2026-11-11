package dev.alaindustrial.client.forge;

import dev.alaindustrial.client.render.RootSoilAppearance;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelDataManager;
import net.minecraftforge.client.model.data.ModelProperty;

/**
 * The kok-sagyz root drawn as the soil it grew in (MOD-962; the Forge twin of {@code NeoForgeRootSoilModels}).
 *
 * <p>The root's block entity hands its soil to the chunk mesher as {@link ModelData} (the client mixin
 * {@code RootModelDataMixin}); the root's baked model is wrapped so that it collects the parts of that soil's
 * model. Forge's {@code BlockStateModel.collectParts} sees no level or position, only the model data, so the
 * soil travels in the data alone. A soil whose own model needs the level is drawn from its plain parts.
 */
public final class ForgeRootSoilModels {

	/** The soil block state of a root, from its block entity. */
	public static final ModelProperty<BlockState> SOIL = new ModelProperty<>();

	private ForgeRootSoilModels() {
	}

	/** Points the shared soil tint at the model data of the meshed level. */
	public static void init() {
		RootSoilAppearance.reader = ForgeRootSoilModels::soil;
	}

	private static BlockState soil(BlockAndTintGetter level, BlockPos pos) {
		ModelDataManager manager = level.getModelDataManager();
		BlockState state = manager == null ? null : manager.getAtOrEmpty(pos).get(SOIL);
		return state == null ? Blocks.DIRT.defaultBlockState() : state;
	}

	/** Wraps every root model; the replacement is read by the block-state dispatch built after this event. */
	public static void onBake(ModelEvent.ModifyBakingResult event) {
		Map<BlockState, BlockStateModel> models = event.getResults().blockStateModels();
		Map<BlockState, BlockStateModel> originals = Map.copyOf(models);
		models.replaceAll((state, model) -> state.is(ModContent.KOK_SAGYZ_ROOT.get())
				? new SoilModel(model, originals) : model);
	}

	private record SoilModel(BlockStateModel fallback, Map<BlockState, BlockStateModel> models)
			implements BlockStateModel {

		private BlockStateModel model(ModelData data) {
			BlockState soil = data.get(SOIL);
			return soil == null ? fallback : models.getOrDefault(soil, fallback);
		}

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
			fallback.collectParts(random, output);
		}

		@Override
		public Material.Baked particleMaterial() {
			return fallback.particleMaterial();
		}

		@Override
		public int materialFlags() {
			return fallback.materialFlags();
		}

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> output, ModelData data) {
			model(data).collectParts(random, output);
		}

		@Override
		public Material.Baked particleMaterial(ModelData data) {
			return model(data).particleMaterial();
		}
	}
}
