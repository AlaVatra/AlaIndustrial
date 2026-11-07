package dev.alaindustrial.client.render;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * The bumblebee apiary drone (MOD-923) as the station's renderer draws it: the owner's pick of 2026-11-07, the box list
 * of {@code bee_parts()} in {@code tools/model_sources/apiary/build_apiary.py} moved onto its feet (the origin is
 * between the feet, the head at -z) and its moving parts hung on their own pivots, so each turns in place.
 *
 * <p>Two roots, one per render type: {@code opaque} (body, both rotors, the sting, the honey in the belly) is drawn
 * cut out; {@code clear} (the glass belly and the two rotor discs) translucent, after it. A third root,
 * {@code column}, is the station's honey: the window of the casing and the column of the hive. The texture is
 * {@code textures/entity/apiary_drone.png}: one 64 x 16 tile per material, wide enough for the box UV of every part.
 *
 * <p>The poses are set in {@code setupAnim} from a per-drone state, not on the shared parts at submit time: the
 * collector draws later, so a part posed for the first drone would be drawn with the fourth drone's pose.
 */
public final class ApiaryDroneModel {

	/** Where the sting hinges: the tail end of the belly, in model pixels from the feet. */
	public static final float STING_PIVOT_Y = 4.5F;
	public static final float STING_PIVOT_Z = 7.1F;
	/** The sting's length tucked; docked it telescopes to twice this. */
	public static final float STING_LENGTH = 2.5F;
	/** The rotors' hubs: x of each, their height and z. */
	public static final float ROTOR_X = 6.0F;
	public static final float ROTOR_Y = 8.5F;
	public static final float ROTOR_Z = -2.0F;

	// The material tiles of the texture, (u, v) of each.
	private static final int[] FUZZ = {0, 0};
	private static final int[] BLACK = {64, 0};
	private static final int[] EYE = {0, 16};
	private static final int[] COPPER = {64, 16};
	private static final int[] STEEL = {0, 32};
	private static final int[] ROTOR = {64, 32};
	private static final int[] HONEY = {0, 48};
	private static final int[] GLASS = {64, 48};
	private static final int[] BLUR = {0, 64};
	private static final int[] YELLOW = {64, 64};

	private ApiaryDroneModel() {
	}

	private static CubeListBuilder box(int[] tile, float x, float y, float z, float w, float h, float d) {
		return CubeListBuilder.create().texOffs(tile[0], tile[1]).addBox(x, y, z, w, h, d);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition opaque = root.addOrReplaceChild("opaque", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition clear = root.addOrReplaceChild("clear", CubeListBuilder.create(), PartPose.ZERO);

		PartDefinition body = opaque.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		body.addOrReplaceChild("thorax", box(FUZZ, -3.0F, 2.0F, -5.0F, 6.0F, 5.0F, 6.0F), PartPose.ZERO);
		body.addOrReplaceChild("collar_l", box(BLACK, -3.1F, 4.0F, -2.5F, 0.1F, 1.0F, 1.0F), PartPose.ZERO);
		body.addOrReplaceChild("collar_r", box(BLACK, 3.0F, 4.0F, -2.5F, 0.1F, 1.0F, 1.0F), PartPose.ZERO);
		body.addOrReplaceChild("head", box(BLACK, -2.0F, 2.5F, -7.0F, 4.0F, 4.0F, 2.0F), PartPose.ZERO);
		body.addOrReplaceChild("eye_l", box(EYE, -2.2F, 4.2F, -7.2F, 1.5F, 1.6F, 0.2F), PartPose.ZERO);
		body.addOrReplaceChild("eye_r", box(EYE, 0.7F, 4.2F, -7.2F, 1.5F, 1.6F, 0.2F), PartPose.ZERO);
		body.addOrReplaceChild("ant_l", box(COPPER, -1.4F, 6.5F, -6.4F, 0.5F, 2.0F, 0.5F), PartPose.ZERO);
		body.addOrReplaceChild("ant_r", box(COPPER, 0.9F, 6.5F, -6.4F, 0.5F, 2.0F, 0.5F), PartPose.ZERO);
		body.addOrReplaceChild("arm", box(STEEL, -7.5F, 7.0F, -2.6F, 15.0F, 0.8F, 1.2F), PartPose.ZERO);
		for (int ring = 0; ring < 2; ring++) {
			float z = 2.5F + 2.0F * ring;
			// 0.1 px off the belly's top and floor: on its plane they z-fought in flight (owner, 2026-11-07).
			body.addOrReplaceChild("ring_top_" + ring, box(BLACK, -2.7F, 7.1F, z, 5.4F, 0.3F, 0.8F), PartPose.ZERO);
			body.addOrReplaceChild("ring_bottom_" + ring, box(BLACK, -2.7F, 1.6F, z, 5.4F, 0.3F, 0.8F),
					PartPose.ZERO);
		}
		for (int side = 0; side < 2; side++) {
			float x = side == 0 ? -ROTOR_X : ROTOR_X;
			body.addOrReplaceChild("mast_" + side, box(YELLOW, x - 0.5F, 7.8F, -2.5F, 1.0F, 0.6F, 1.0F),
					PartPose.ZERO);
			// Blades authored around the hub: a cross that turns on its own axis when the part gets a yRot.
			opaque.addOrReplaceChild("rotor_" + side, CubeListBuilder.create().texOffs(ROTOR[0], ROTOR[1])
					.addBox(-3.6F, -0.1F, -0.35F, 7.2F, 0.3F, 0.7F)
					.addBox(-0.35F, -0.1F, -3.6F, 0.7F, 0.3F, 3.25F)
					.addBox(-0.35F, -0.1F, 0.35F, 0.7F, 0.3F, 3.25F), PartPose.offset(x, ROTOR_Y, ROTOR_Z));
			// The disc a fast rotor reads as, hung on the same hub just above the blades.
			clear.addOrReplaceChild("blur_" + side, box(BLUR, -4.0F, 0.25F, -4.0F, 8.0F, 0.1F, 8.0F),
					PartPose.offset(x, ROTOR_Y, ROTOR_Z));
		}
		for (int leg = 0; leg < 4; leg++) {
			float x = leg % 2 == 0 ? -2.4F : 1.8F;
			float z = leg < 2 ? -4.0F : -0.5F;
			body.addOrReplaceChild("leg_" + leg, box(BLACK, x, 0.0F, z, 0.6F, 2.0F, 0.6F), PartPose.ZERO);
		}
		// The sting hangs on the tail; a positive xRot swings it down (y up, +z back), a zScale telescopes it.
		opaque.addOrReplaceChild("sting", box(COPPER, -0.6F, -0.6F, 0.0F, 1.2F, 1.2F, STING_LENGTH),
				PartPose.offset(0.0F, STING_PIVOT_Y, STING_PIVOT_Z));
		// The honey in the belly fills from its floor: yScale 0..1 on a pivot at the bottom, 0.5 px off the glass.
		opaque.addOrReplaceChild("honey", box(HONEY, -2.0F, 0.0F, 1.5F, 4.0F, 4.0F, 5.0F),
				PartPose.offset(0.0F, 2.5F, 0.0F));
		// 0.1 px gaps at the thorax and at the sting's hinge: no two faces share a plane (they flickered).
		clear.addOrReplaceChild("belly", box(GLASS, -2.5F, 2.0F, 1.1F, 5.0F, 5.0F, 5.9F), PartPose.ZERO);

		// The station's honey, around the block's axis with the window at north (z 0.6..0.85 of the casing, between
		// its glass and its black back) and the column of the hive above (0.5 px off its glass); both fill from the
		// floor through a yScale, and the floors sit 0.02 px up so no face lies on the model's.
		PartDefinition column = root.addOrReplaceChild("column", CubeListBuilder.create(), PartPose.ZERO);
		column.addOrReplaceChild("window", box(HONEY, -2.0F, 0.0F, -7.4F, 4.0F, WINDOW_HEIGHT, 0.25F),
				PartPose.offset(0.0F, 2.02F, 0.0F));
		column.addOrReplaceChild("upper", box(HONEY, -1.5F, 0.0F, -1.5F, 3.0F, UPPER_HEIGHT, 3.0F),
				PartPose.offset(0.0F, 18.02F, 0.0F));
		return LayerDefinition.create(mesh, 128, 80);
	}

	/** Honey heights of the casing's window and of the hive's column, in pixels: one tank shown in two windows. */
	public static final float WINDOW_HEIGHT = 12.98F;
	public static final float UPPER_HEIGHT = 10.48F;

	/** How full the window and the column are for a tank {@code fill} 0..1: the window first, then the column. */
	public static float[] columnFills(float fill) {
		float px = Math.clamp(fill, 0.0F, 1.0F) * (WINDOW_HEIGHT + UPPER_HEIGHT);
		return new float[] {Math.min(1.0F, px / WINDOW_HEIGHT),
				Math.clamp((px - WINDOW_HEIGHT) / UPPER_HEIGHT, 0.0F, 1.0F)};
	}

	/** One drone's moving parts at one frame. */
	public record Pose(float rotor, float sting, float belly, boolean blur) {
	}

	/** The cut-out half of a drone: body, rotors, the sting and the honey in the belly. */
	public static final class Opaque extends Model<Pose> {
		private final ModelPart rotorLeft;
		private final ModelPart rotorRight;
		private final ModelPart sting;
		private final ModelPart honey;

		public Opaque(ModelPart root) {
			super(root.getChild("opaque"), RenderTypes::entityCutout);
			ModelPart opaque = root.getChild("opaque");
			this.rotorLeft = opaque.getChild("rotor_0");
			this.rotorRight = opaque.getChild("rotor_1");
			this.sting = opaque.getChild("sting");
			this.honey = opaque.getChild("honey");
		}

		@Override
		public void setupAnim(Pose pose) {
			resetPose();
			// Opposite directions, as a real twin-rotor craft cancels its torque.
			rotorLeft.yRot = pose.rotor();
			rotorRight.yRot = -pose.rotor();
			sting.xRot = pose.sting() * (float) (Math.PI / 2.0);
			sting.zScale = 1.0F + pose.sting();
			honey.visible = pose.belly() > 0.001F;
			honey.yScale = Math.max(0.001F, pose.belly());
		}
	}

	/** The translucent half of a drone: the glass belly and, while it flies, the rotor discs. */
	public static final class Clear extends Model<Pose> {
		private final ModelPart blurLeft;
		private final ModelPart blurRight;

		public Clear(ModelPart root) {
			super(root.getChild("clear"), RenderTypes::entityTranslucent);
			ModelPart clear = root.getChild("clear");
			this.blurLeft = clear.getChild("blur_0");
			this.blurRight = clear.getChild("blur_1");
		}

		@Override
		public void setupAnim(Pose pose) {
			resetPose();
			blurLeft.visible = pose.blur();
			blurRight.visible = pose.blur();
		}
	}

	/** The station's honey: the window's and the column's fill, 0..1 each. */
	public static final class Column extends Model<float[]> {
		private final ModelPart window;
		private final ModelPart upper;

		public Column(ModelPart root) {
			super(root.getChild("column"), RenderTypes::entityCutout);
			ModelPart column = root.getChild("column");
			this.window = column.getChild("window");
			this.upper = column.getChild("upper");
		}

		@Override
		public void setupAnim(float[] fills) {
			resetPose();
			window.visible = fills[0] > 0.001F;
			window.yScale = Math.max(0.001F, fills[0]);
			upper.visible = fills[1] > 0.001F;
			upper.yScale = Math.max(0.001F, fills[1]);
		}
	}


}
