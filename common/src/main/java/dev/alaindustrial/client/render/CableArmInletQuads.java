package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alaindustrial.block.CableArmInlet;
import dev.alaindustrial.block.CableBlock;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A cable's straight arm carried on past its cell edge into a {@link CableArmInlet} neighbour (MOD-948), drawn in the
 * cable's own texture by {@link CableAccessoryBlockEntityRenderer}, as the dropped arm of MOD-609 is.
 *
 * <p>The box is built in the NORTH frame the arm models are written in — the arm's section, {@code 5..11} on both
 * cross axes, running from the cell edge ({@code z = 0}) {@code depth} pixels out ({@code z < 0}) — and turned to the
 * arm's direction, up and down included. Only its four sides are drawn: the end at the edge would sit inside the
 * cable's baked arm, and the far end meets the neighbour's own surface.
 */
final class CableArmInletQuads {
	private static final float LO = (float) CableBlock.SLEEVE_MIN;
	private static final float HI = (float) CableBlock.SLEEVE_MAX;
	private static final Direction[] SIDES = {Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};

	private CableArmInletQuads() {
	}

	/**
	 * Fills {@code depths} (indexed by {@link Direction#ordinal()}) with how far each joined, straight arm of the cable
	 * at {@code pos} runs into an inlet neighbour; returns whether any does. Only joined arms read the world.
	 */
	static boolean extract(BlockGetter level, BlockPos pos, BlockState cable, float[] depths) {
		boolean any = false;
		for (Direction dir : Direction.values()) {
			depths[dir.ordinal()] = 0.0F;
			BooleanProperty joined = PipeBlock.PROPERTY_BY_DIRECTION.get(dir);
			if (!cable.hasProperty(joined) || !cable.getValue(joined)) {
				continue;
			}
			BooleanProperty low = CableBlock.lowFlagFor(dir);
			if (low != null && cable.hasProperty(low) && cable.getValue(low)) {
				continue; // a dropped arm continues by CableArmReach, not here
			}
			BlockState neighbour = level.getBlockState(pos.relative(dir));
			if (neighbour.getBlock() instanceof CableArmInlet inlet) {
				float depth = inlet.cableArmInlet(neighbour, dir.getOpposite());
				if (depth > 0.0F) {
					depths[dir.ordinal()] = depth;
					any = true;
				}
			}
		}
		return any;
	}

	/** Draws every arm of {@code depths} with {@code sprite}, tinted and shaded as the baked arm is. */
	static void draw(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, int tint, int light,
			CardinalLighting shading, float[] depths) {
		for (Direction toward : Direction.values()) {
			float depth = depths[toward.ordinal()];
			if (depth <= 0.0F) {
				continue;
			}
			for (Direction face : SIDES) {
				face(pose, consumer, sprite, tint, light, shading, toward, face, depth);
			}
		}
	}

	private static void face(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, int tint,
			int light, CardinalLighting shading, Direction toward, Direction face, float depth) {
		float[] n = turn(toward, 0.5F + face.getStepX() * 0.5F, 0.5F + face.getStepY() * 0.5F,
				0.5F + face.getStepZ() * 0.5F);
		float nx = n[0] - 0.5F;
		float ny = n[1] - 0.5F;
		float nz = n[2] - 0.5F;
		Direction world = Direction.getApproximateNearest(nx, ny, nz);
		float shade = shading.byFace(world);
		int color = 0xFF000000
				| Math.min(255, Math.round(((tint >> 16) & 0xFF) * shade)) << 16
				| Math.min(255, Math.round(((tint >> 8) & 0xFF) * shade)) << 8
				| Math.min(255, Math.round((tint & 0xFF) * shade));
		// The baked arm's UVs (*_cable_arm.json: sides [0, 5, 5, 11], top and bottom [5, 11, 11, 16]) continued
		// past the edge: the strip along the arm takes the next pixels of the same rows, so the pattern runs on.
		float[] uv = switch (face) {
			case WEST -> new float[] {16.0F - depth, LO, 16.0F, HI};
			case EAST -> new float[] {5.0F, LO, 5.0F + depth, HI};
			default -> new float[] {LO, 11.0F - depth, HI, 11.0F};
		};
		QuadEmitter quads = new QuadEmitter(pose, consumer, light).sprite(sprite).color(color);
		FaceInfo info = FaceInfo.fromFacing(face);
		float z0 = -depth;
		for (int i = 0; i < 4; i++) {
			// Vanilla's own corner layout for a model element's face, so winding and UV match the baked arm.
			FaceInfo.VertexInfo corner = info.getVertexInfo(i);
			float x = corner.xFace().select(LO, LO, z0, HI, HI, 0.0F);
			float y = corner.yFace().select(LO, LO, z0, HI, HI, 0.0F);
			float z = corner.zFace().select(LO, LO, z0, HI, HI, 0.0F);
			float[] p = turn(toward, x / 16.0F, y / 16.0F, z / 16.0F);
			float u = i == 0 || i == 1 ? uv[0] : uv[2];
			float v = i == 0 || i == 3 ? uv[1] : uv[3];
			quads.vertex(p[0], p[1], p[2], u / 16.0F, v / 16.0F, nx * 2.0F, ny * 2.0F, nz * 2.0F);
		}
	}

	/**
	 * A point of the NORTH frame (block units) turned so the arm points {@code toward}: a quarter turn clockwise seen
	 * from above per horizontal step, as a blockstate {@code y} turns it, and a tilt about the x axis for up and down.
	 */
	static float[] turn(Direction toward, float x, float y, float z) {
		return switch (toward) {
			case EAST -> new float[] {1.0F - z, y, x};
			case SOUTH -> new float[] {1.0F - x, y, 1.0F - z};
			case WEST -> new float[] {z, y, 1.0F - x};
			case UP -> new float[] {x, 1.0F - z, y};
			case DOWN -> new float[] {x, z, 1.0F - y};
			default -> new float[] {x, y, z};
		};
	}
}
