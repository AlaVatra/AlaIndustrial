package dev.alaindustrial.client.forge;

import dev.alaindustrial.block.PiezoPlateBlock;
import dev.alaindustrial.client.render.PlateCamouflage;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jspecify.annotations.Nullable;

/**
 * The piezo plate's camouflage (MOD-764, MOD-962; the Forge twin of {@code NeoForgePiezoPlateModels} and of
 * Fabric's {@code PiezoPlateModels}): the plate's baked model collects the squashed floor block
 * ({@link PlateCamouflage}) whenever the block below qualifies, and its own parts otherwise.
 *
 * <p>Forge's {@code collectParts} sees no level or position, so the floor and the pressed flag are put into the
 * {@link ModelData} by {@code getModelData(level, pos, state, data)}, which the chunk mesher calls first. Break and
 * mining particles get no such data (Forge keeps model data of block entities only, and the plate has none), so
 * {@code TerrainParticleMixin} asks {@link #floorParticle} for the floor's material directly.
 */
public final class ForgePiezoPlateModels {

	static final ModelProperty<BlockState> FLOOR = new ModelProperty<>();
	static final ModelProperty<Boolean> PRESSED = new ModelProperty<>();

	private ForgePiezoPlateModels() {
	}

	/**
	 * The particle material of the floor under a plate at {@code pos}, or {@code null} when {@code state} is not a
	 * plate, the position is unknown or the floor is not one the plate camouflages as. The plate itself may already
	 * be gone (break particles spawn after the block is removed); only the floor is read from the level.
	 */
	public static Material.@Nullable Baked floorParticle(BlockState state, @Nullable BlockPos pos) {
		ClientLevel level = Minecraft.getInstance().level;
		if (pos == null || level == null || !(state.getBlock() instanceof PiezoPlateBlock)) {
			return null;
		}
		BlockPos below = pos.below();
		BlockState floor = level.getBlockState(below);
		if (!PlateCamouflage.camouflages(floor)) {
			return null;
		}
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(floor)
				.particleMaterial(level.getModelDataManager().getAt(below));
	}

	/** Wraps every plate model; the replacement is read by the block-state dispatch built after this event. */
	public static void onBake(ModelEvent.ModifyBakingResult event) {
		Map<BlockState, BlockStateModel> models = event.getResults().blockStateModels();
		Map<BlockState, BlockStateModel> originals = Map.copyOf(models);
		PlateCamouflage camouflage = new PlateCamouflage();
		models.replaceAll((state, model) -> state.getBlock() instanceof PiezoPlateBlock
				? new CamouflagedPlate(model, originals, camouflage) : model);
	}

	private record CamouflagedPlate(BlockStateModel own, Map<BlockState, BlockStateModel> models,
			PlateCamouflage camouflage) implements BlockStateModel {

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
			own.collectParts(random, output);
		}

		@Override
		public Material.Baked particleMaterial() {
			return own.particleMaterial();
		}

		@Override
		public int materialFlags() {
			return own.materialFlags();
		}

		@Override
		public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
			return data.derive()
					.with(FLOOR, level.getBlockState(pos.below()))
					.with(PRESSED, state.getValue(PiezoPlateBlock.POWERED))
					.build();
		}

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> output, ModelData data) {
			BlockState floor = data.get(FLOOR);
			BlockStateModel source = floor != null && PlateCamouflage.camouflages(floor) ? models.get(floor) : null;
			if (source == null) {
				own.collectParts(random, output);
				return;
			}
			output.addAll(camouflage.parts(source, random, Boolean.TRUE.equals(data.get(PRESSED))));
		}
	}
}
