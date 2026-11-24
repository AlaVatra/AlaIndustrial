package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.EscalatorChain;
import dev.alaindustrial.block.EscalatorMode;
import dev.alaindustrial.block.EscalatorSectionBlock;
import dev.alaindustrial.block.entity.EscalatorDriveBlockEntity;
import dev.alaindustrial.compat.client.Poses;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The band of an escalator (MOD-969): the treads and the arrow lights on them, drawn by the drive for its whole
 * chain whenever the band is ready or running, standing or moving.
 *
 * <p>A ready or running section's stair model keeps only its side walls and the closed shell under the band
 * ({@code escalator_section_running.json}); a stopped one carries still treads of its own. Ready and running share
 * that model, so a band that starts or stops swaps no model: before, the swap came a frame or two after this
 * renderer had already changed, and the band jumped (owner, 2026-11-24). The handrail is part of the balustrade's
 * model in every mode. The numbers here are the generator's ({@code tools/model_sources/escalator/build_escalator.py}):
 * two treads per block between the walls (x 2..14 px), 8 px run and rise, their tops on the slope line at the tread
 * middle; one arrow per tread (x 5..11 px). The band rests only where those treads stand
 * ({@link dev.alaindustrial.core.escalator.EscalatorBandMotion}), so this band at rest is the still model.
 *
 * <p><b>No gaps.</b> Every riser reaches down to the tread before it, also where that is below the floor of the
 * section it stands in: cutting it at the floor left a 3 px slit at every seam. Only the drive's top cuts a riser.
 * Treads and risers are drawn from both sides (their backs in the underside texture), so the band shows no hole
 * from below either; the shell closes the rest.
 *
 * <p><b>Frame.</b> The pose moves to the bottom section's cell and turns canonical NORTH onto the chain's facing; in
 * that frame the band climbs towards -Z, and a point {@code r} blocks along the run at height {@code y} is
 * {@code (x, y, 1 - r)}. Treads that run past the top end shrink into the landing's comb and grow back out of the
 * floor at the bottom, as on a real escalator.
 *
 * <p><b>Travel</b> is the drive's client value ({@link EscalatorDriveBlockEntity#travel}); the arrows ride on the
 * treads, so they run the way the band goes.
 */
public final class EscalatorDriveRenderer
		implements BlockEntityRenderer<EscalatorDriveBlockEntity, EscalatorDriveRenderer.State>,
			RenderBoxProvider<EscalatorDriveBlockEntity> {

	private static final float PX = 1.0F / 16.0F;
	private static final float TREAD_RUN = 0.5F;
	private static final float TREAD_TOP_OFFSET = 5 * PX;
	private static final float TREAD_X0 = 2 * PX;
	private static final float TREAD_X1 = 14 * PX;
	private static final float[] ARROW_X = {5 * PX, 11 * PX};

	private static final SpriteId TREAD = Sheets.BLOCKS_MAPPER.apply(Industrialization.id("escalator_tread"));
	private static final SpriteId RISER = Sheets.BLOCKS_MAPPER.apply(Industrialization.id("escalator_riser"));
	private static final SpriteId UNDERSIDE = Sheets.BLOCKS_MAPPER.apply(Industrialization.id("escalator_underside"));
	private static final SpriteId ARROW = Sheets.BLOCKS_MAPPER.apply(Industrialization.id("escalator_arrow"));
	/** Every sprite is in the block atlas, so one cutout render type serves all of them. */
	private static final RenderType TYPE = TREAD.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	private final SpriteGetter sprites;

	public EscalatorDriveRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	/** Render state: only primitives, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		boolean skip = true;
		float yaw;
		float offsetX;
		float offsetY;
		float offsetZ;
		int sections;
		float travel;
		boolean down;
		int[] lights = new int[0];
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(EscalatorDriveBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		EscalatorChain chain = entity.clientChain();
		Level level = entity.getLevel();
		BlockPos bottom = chain.bottom();
		state.skip = true;
		// The server writes ready or running only onto a whole chain; trust that, not this client's view of the
		// landing, which may stand in a chunk it has not loaded: a ready band with no treads drawn would be hollow.
		if (level == null || bottom == null || chain.sections() <= 0) {
			return;
		}
		BlockState first = level.getBlockState(bottom);
		if (!(first.getBlock() instanceof EscalatorSectionBlock)
				|| first.getValue(EscalatorSectionBlock.MODE) == EscalatorMode.OFF) {
			return;
		}
		state.skip = false;
		BlockPos drive = entity.getBlockPos();
		state.offsetX = bottom.getX() - drive.getX();
		state.offsetY = bottom.getY() - drive.getY();
		state.offsetZ = bottom.getZ() - drive.getZ();
		state.yaw = yawOf(chain.facing());
		state.sections = chain.sections();
		state.down = first.getValue(EscalatorSectionBlock.REVERSED);
		state.travel = entity.travel(partialTicks);
		if (state.lights.length != state.sections) {
			state.lights = new int[state.sections];
		}
		for (int i = 0; i < state.sections; i++) {
			state.lights[i] = LightCoordsUtil.getLightCoords(level, EscalatorChain.section(bottom, chain.facing(), i));
		}
	}

	/** Canonical NORTH turned onto {@code facing}, right-handed about +Y (+90 takes north to west). */
	static float yawOf(Direction facing) {
		return switch (facing) {
			case WEST -> 90.0F;
			case SOUTH -> 180.0F;
			case EAST -> 270.0F;
			default -> 0.0F;
		};
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.skip || state.sections <= 0) {
			return;
		}
		TextureAtlasSprite tread = this.sprites.get(TREAD);
		TextureAtlasSprite riser = this.sprites.get(RISER);
		TextureAtlasSprite under = this.sprites.get(UNDERSIDE);
		TextureAtlasSprite arrow = this.sprites.get(ARROW);
		poseStack.pushPose();
		poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
		poseStack.translate(0.5F, 0.0F, 0.5F);
		Poses.rotate(poseStack, Axis.YP.rotationDegrees(state.yaw));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		collector.submitCustomGeometry(poseStack, TYPE, (pose, consumer) -> {
			QuadEmitter out = new QuadEmitter(pose, consumer, state.lights[0]);
			drawTreads(out, state, tread, riser, under, arrow);
		});
		poseStack.popPose();
	}

	private static void drawTreads(QuadEmitter out, State state, TextureAtlasSprite tread, TextureAtlasSprite riser,
			TextureAtlasSprite under, TextureAtlasSprite arrow) {
		float length = state.sections;
		int count = state.sections * 2;
		for (int j = 0; j < count; j++) {
			float s = floorMod(j * TREAD_RUN + state.travel, length);
			float end = s + TREAD_RUN;
			int light = state.lights[Math.min(state.sections - 1, (int) (s + 0.25F))];
			if (end <= length) {
				float top = s + TREAD_TOP_OFFSET;
				treadBox(out.light(light), tread, riser, under, s, end, top);
				out.light(LightCoordsUtil.FULL_BRIGHT).sprite(arrow).color(-1);
				arrowPad(out, ARROW_X[0], ARROW_X[1], s + PX, s + 7 * PX, top + 0.5F * PX, state.down);
			} else {
				// Past the top: what is left sinks under the landing's comb; the rest comes out of the floor below.
				treadBox(out.light(light), tread, riser, under, s, length,
						Math.min(s + TREAD_TOP_OFFSET, length - 0.5F * PX));
				float back = end - length;
				treadBox(out.light(state.lights[0]), tread, riser, under, 0.0F, back,
						back - TREAD_RUN + TREAD_TOP_OFFSET);
			}
		}
	}

	/**
	 * One tread between runs {@code r0..r1} with its top at {@code top}: the tread face and its front riser, which
	 * reaches down to the tread before (8 px), cut only at the drive's top (y 0); nothing when the cut leaves no
	 * height. Both faces are two-sided, their backs in the underside texture. No back riser: the next tread's riser
	 * and the section's shell cover that side.
	 */
	private static void treadBox(QuadEmitter out, TextureAtlasSprite tread, TextureAtlasSprite riser,
			TextureAtlasSprite under, float r0, float r1, float top) {
		if (r1 - r0 < 1.0E-3F) {
			return;
		}
		float z0 = 1.0F - r1;
		float z1 = 1.0F - r0;
		float front = Math.max(top - TREAD_RUN, 0.0F);
		if (top - front < 1.0E-3F) {
			return;
		}
		out.color(-1).sprite(tread);
		out.cubeFace(Direction.UP, TREAD_X0, top, z0, TREAD_X1, top, z1, 2 * PX, 0.5F, 14 * PX, 1.0F);
		out.sprite(under);
		out.cubeFace(Direction.DOWN, TREAD_X0, top, z0, TREAD_X1, top, z1, 2 * PX, 0.5F, 14 * PX, 1.0F);
		// Riser rows from the yellow nose down; a riser cut at the drive's top shows its upper rows only.
		out.sprite(riser);
		out.cubeFace(Direction.SOUTH, TREAD_X0, front, z1, TREAD_X1, top, z1, 2 * PX, 0.0F, 14 * PX, top - front);
		out.sprite(under);
		out.cubeFace(Direction.NORTH, TREAD_X0, front, z1, TREAD_X1, top, z1, 2 * PX, 0.0F, 14 * PX, top - front);
	}

	/** An arrow light on a tread, pointing the way the band goes (the texture points to its top, v = 0). */
	private static void arrowPad(QuadEmitter out, float x0, float x1, float r0, float r1, float y, boolean down) {
		out.cubeFace(Direction.UP, x0, y - 0.5F * PX, 1.0F - r1, x1, y, 1.0F - r0, 0.0F, down ? 1.0F : 0.0F, 1.0F,
				down ? 0.0F : 1.0F);
	}

	private static float floorMod(float value, float modulus) {
		float m = value % modulus;
		return m < 0.0F ? m + modulus : m;
	}

	/** The band reaches up to sixteen blocks from the drive. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	/**
	 * The box NeoForge and Forge test against the view frustum before they draw this renderer (MOD-633): the whole
	 * chain from the bottom section to the landing. A drive with no chain draws nothing.
	 *
	 * <p>No {@code @Override}: only NeoForge's {@code BlockEntityRenderer} declares this method.
	 */
	public AABB getRenderBoundingBox(EscalatorDriveBlockEntity blockEntity) {
		BlockPos drive = blockEntity.getBlockPos();
		EscalatorChain chain = blockEntity.clientChain();
		BlockPos bottom = chain.bottom();
		if (bottom == null || chain.sections() <= 0) {
			return new AABB(drive);
		}
		BlockPos top = EscalatorChain.landingPos(bottom, chain.facing(), chain.sections());
		return new AABB(Vec3.atLowerCornerOf(bottom), Vec3.atLowerCornerOf(top)).expandTowards(1, 3, 1)
				.minmax(new AABB(drive)).inflate(1.0);
	}

	/**
	 * As far as a beacon beam: a ready band's treads exist only in this renderer (its model has none), so a shorter
	 * distance would show a hollow escalator from where its walls are still drawn.
	 */
	@Override
	public int getViewDistance() {
		return 256;
	}
}
