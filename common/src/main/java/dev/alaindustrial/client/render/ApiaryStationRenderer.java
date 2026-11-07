package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.ApiaryDroneHiveBlock;
import dev.alaindustrial.block.HiveAttachmentBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.ApiaryDroneStationBlockEntity;
import dev.alaindustrial.block.entity.machine.DroneBay;
import dev.alaindustrial.block.entity.machine.DroneFlight;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.compat.client.ModelSubmit;
import dev.alaindustrial.compat.client.Poses;
import dev.alaindustrial.core.apiary.ApiaryRules;
import dev.alaindustrial.core.drone.DroneFlightPath;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the apiary station's four bumblebee drones and its honey (MOD-923), from the casing's block entity.
 *
 * <p>The drones are not entities: each is drawn from its {@link DroneBay}, whose leg the server sent once. Parked, a
 * drone stands on its pad (feet 0.02 above it, so no face lies on the model's) head to the column, rotors idling and
 * rocking a little. In flight it arcs ({@link ApiaryRules#arcHeight}), leans into its way, turns over the leg from its
 * pad's heading to the one it docks with, and its rotors read as discs. Docked, it hovers over the jar's funnel with
 * its sting swung down into it and telescoped, its glass belly filling while the jar steps down; home again it pours,
 * the belly emptying as the honey column catches up.
 *
 * <p>The box NeoForge culls by is the station and every leg in the air ({@link #getRenderBoundingBox}); Fabric never
 * culls a block entity, so {@link #shouldRenderOffScreen} is what keeps a far drone drawn there.
 */
public final class ApiaryStationRenderer
		implements BlockEntityRenderer<ApiaryDroneStationBlockEntity, ApiaryStationRenderer.State> {

	public static final ModelLayerLocation DRONE_LAYER =
			new ModelLayerLocation(Industrialization.id("apiary_drone"), "main");

	private static final Identifier TEXTURE = Industrialization.id("textures/entity/apiary_drone.png");

	/** The drone's size in the world: 0.55 of its authored pixels, about 10 px across (owner's pick). */
	private static final float SCALE = 0.55F;
	/** How far the feet stand above the pad, in blocks. */
	private static final float FEET_CLEARANCE = 0.02F;
	/** How far the sting reaches into the funnel's mouth, in blocks. */
	private static final float STING_INTO_FUNNEL = 0.4F / 16.0F;
	/** The sting's tip docked: from the feet, in authored pixels — under the hinge by its telescoped length. */
	private static final float TIP_Y = ApiaryDroneModel.STING_PIVOT_Y - 2.0F * ApiaryDroneModel.STING_LENGTH;
	private static final float TIP_Z = ApiaryDroneModel.STING_PIVOT_Z;

	/** Where the drone's feet ride in an open cell of its route: low enough that its rotors stay in the cell. */
	private static final float ROUTE_FEET = 0.3F;

	/** The share of a routed leg at each end over which the drone turns from its pad's or to its dock's heading. */
	private static final float TURN_SPAN = 0.15F;

	private static final float ROTOR_FAST = 1.1F;
	private static final float ROTOR_IDLE = 0.15F;
	/** Lean into the flight at the middle of a leg, radians (14 degrees). */
	private static final float MAX_LEAN = 0.244F;
	/** The parked drone's rock, radians. */
	private static final float ROCK = 0.03F;

	/** Where the funnel's mouth is in a side jar's cell, mounted EAST (the hive to the west), in pixels. */
	private static final float[] SIDE_FUNNEL = {3.5F, 13.3F, 8.0F};
	/** And in a jar on the hive's top. */
	private static final float[] TOP_FUNNEL = {8.5F, 12.3F, 8.0F};

	private final ApiaryDroneModel.Opaque opaque;
	private final ApiaryDroneModel.Clear clear;
	private final ApiaryDroneModel.Column column;

	public ApiaryStationRenderer(BlockEntityRendererProvider.Context context) {
		ModelPart root = context.bakeLayer(DRONE_LAYER);
		this.opaque = new ApiaryDroneModel.Opaque(root);
		this.clear = new ApiaryDroneModel.Clear(root);
		this.column = new ApiaryDroneModel.Column(root);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ApiaryDroneStationBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		Level level = entity.getLevel();
		if (level == null) {
			return;
		}
		BlockPos station = entity.getBlockPos();
		float time = level.getGameTime() + partialTicks;
		BlockState casing = entity.getBlockState();
		Direction front = casing.hasProperty(HorizontalMachineBlock.FACING)
				? casing.getValue(HorizontalMachineBlock.FACING) : Direction.NORTH;
		state.frontQuarter = (front.get2DDataValue() + 2) % 4;
		state.hive = ApiaryDroneHiveBlock.isHive(level.getBlockState(station.above()));
		float[] fills = ApiaryDroneModel.columnFills(entity.shownTankFill(time));
		state.fills = state.hive ? fills : new float[] {fills[0], 0.0F};
		state.windowLight = LightCoordsUtil.getLightCoords(level, station.relative(front));
		state.columnLight = LightCoordsUtil.getLightCoords(level, station.above());
		for (int i = 0; i < ApiaryRules.BAYS; i++) {
			state.drones[i] = state.hive && entity.hasDrone(i) ? drone(level, station, entity.bay(i), i, time) : null;
		}
	}

	/**
	 * Where bay {@code bay}'s drone is and how it is posed at {@code time}.
	 *
	 * <p>size-justified: one switch over the five phases of a round, each a few lines of the same pose; split up, the
	 * dock point and the headings it shares with every phase would travel through four parameter lists.
	 */
	private static Drone drone(Level level, BlockPos station, DroneBay bay, int i, float time) {
		double[] pad = ApiaryRules.padSpot(i);
		Vec3 home = new Vec3(pad[0], pad[1] + FEET_CLEARANCE, pad[2]);
		float padYaw = (float) Math.toRadians(ApiaryRules.padYawDegrees(i));
		Drone drone = new Drone();
		drone.pos = home;
		drone.yaw = padYaw;
		int dockTicks = Math.max(5, ServerBalance.apiaryDockTicks());
		BlockPos jar = bay.jar();
		Vec3 dock = home;
		float dockYaw = padYaw;
		if (jar != null) {
			Vec3 funnel = funnel(level.getBlockState(jar), jar, station);
			Vec3 toward = funnel.subtract(home);
			dockYaw = dockYaw(level.getBlockState(jar), toward);
			double sin = Math.sin(dockYaw);
			double cos = Math.cos(dockYaw);
			// The sting's tip, turned with the drone: tail at +z, so it lies at (sin, cos) of the heading.
			Vec3 tip = new Vec3(TIP_Z * sin, TIP_Y, TIP_Z * cos).scale(SCALE / 16.0F);
			dock = funnel.subtract(tip).subtract(0.0, STING_INTO_FUNNEL, 0.0);
		}
		switch (bay.phase()) {
			case OUTBOUND, RETURNING -> {
				DroneFlight flight = bay.flight();
				float raw = flight.progress(time);
				float eased = DroneFlight.accelerate(raw);
				float travel = flight.returning() ? 1.0F - eased : eased;
				float arc = (float) Math.sin(Math.PI * travel);
				int[] corners = flight.path();
				Vec3 way = dock.subtract(home);
				if (corners.length >= 6) {
					// Round trees and walls along the server's route (owner, in-game round of 2026-11-07).
					DroneFlightPath path = new DroneFlightPath(routePoints(home, dock, corners, station));
					float[] now = new float[3];
					float[] ahead = new float[3];
					path.rounded(travel, now);
					path.rounded(Math.min(1.0F, travel + 0.03F), ahead);
					drone.pos = new Vec3(now[0], now[1], now[2]);
					Vec3 heading = new Vec3(ahead[0] - now[0], 0.0, ahead[2] - now[2]);
					if (heading.lengthSqr() > 1.0E-6) {
						// Forward along the route; the lean below turns it round on the way home.
						way = heading;
					}
					// Nose first along the route; the pad's and the dock's headings blend in near each end.
					float motionYaw = flight.returning() ? (float) Math.atan2(way.x, way.z)
							: (float) Math.atan2(-way.x, -way.z);
					float yaw = turn(padYaw, motionYaw, Math.clamp(travel / TURN_SPAN, 0.0F, 1.0F));
					drone.yaw = turn(yaw, dockYaw, Math.clamp((travel - (1.0F - TURN_SPAN)) / TURN_SPAN, 0.0F, 1.0F));
				} else {
					drone.pos = home.lerp(dock, travel).add(0.0, arc * bay.arc(), 0.0);
					drone.yaw = turn(padYaw, dockYaw, travel);
				}
				double len = Math.sqrt(way.x * way.x + way.z * way.z);
				if (len > 1.0E-4) {
					float dir = flight.returning() ? -1.0F : 1.0F;
					drone.tiltX = (float) (way.z / len) * dir * MAX_LEAN * arc;
					drone.tiltZ = (float) (-way.x / len) * dir * MAX_LEAN * arc;
				}
				drone.pose = new ApiaryDroneModel.Pose(time * ROTOR_FAST, 0.0F,
						flight.returning() && bay.honey() > 0 ? 1.0F : 0.0F, raw > 0.1F && raw < 0.9F);
			}
			case DOCKING -> {
				float elapsed = Math.min(dockTicks, bay.elapsedAt(time));
				drone.pos = dock;
				drone.yaw = dockYaw;
				drone.pose = new ApiaryDroneModel.Pose(time * ROTOR_FAST, ApiaryRules.stingPose(elapsed, dockTicks),
						ApiaryRules.bellyWhileDocking(elapsed, dockTicks), true);
			}
			case POURING -> {
				float poured = Math.clamp(bay.elapsedAt(time) / Math.max(1, ServerBalance.apiaryPourTicks()), 0.0F,
						1.0F);
				drone.pose = new ApiaryDroneModel.Pose(time * ROTOR_IDLE, 0.0F, 1.0F - poured, false);
			}
			default -> drone.pose = new ApiaryDroneModel.Pose(time * ROTOR_IDLE, 0.0F, 0.0F, false);
		}
		if (bay.phase() == DroneBay.Phase.PARKED || bay.phase() == DroneBay.Phase.POURING) {
			drone.tiltZ = ROCK * (float) Math.sin(time * 0.15F + i * 1.7F);
		}
		BlockPos cell = BlockPos.containing(station.getX() + drone.pos.x, station.getY() + drone.pos.y,
				station.getZ() + drone.pos.z);
		drone.light = LightCoordsUtil.max(LightCoordsUtil.getLightCoords(level, cell),
				LightCoordsUtil.getLightCoords(level, cell.above()));
		return drone;
	}

	/** The heading {@code t} (0..1, smoothed) of the shorter way from {@code from} to {@code to}, radians. */
	private static float turn(float from, float to, float t) {
		float s = t * t * (3.0F - 2.0F * t);
		return from + Mth.wrapDegrees((float) Math.toDegrees(to - from)) * Mth.DEG_TO_RAD * s;
	}

	/**
	 * The route's polyline relative to the station's corner, the drone's feet: the pad, the corners of the server's
	 * route between the hive and the jar (the hive's own cell left out, as the pad is in it), then the dock point.
	 */
	static float[] routePoints(Vec3 home, Vec3 dock, int[] corners, BlockPos station) {
		int inner = corners.length / 3 - 1;
		float[] xyz = new float[(inner + 2) * 3];
		xyz[0] = (float) home.x;
		xyz[1] = (float) home.y;
		xyz[2] = (float) home.z;
		for (int c = 1; c <= inner; c++) {
			xyz[c * 3] = corners[c * 3] - station.getX() + 0.5F;
			xyz[c * 3 + 1] = corners[c * 3 + 1] - station.getY() + ROUTE_FEET;
			xyz[c * 3 + 2] = corners[c * 3 + 2] - station.getZ() + 0.5F;
		}
		int last = (inner + 1) * 3;
		xyz[last] = (float) dock.x;
		xyz[last + 1] = (float) dock.y;
		xyz[last + 2] = (float) dock.z;
		return xyz;
	}

	/**
	 * The funnel's mouth of the jar at {@code jar}, relative to the station's corner, in blocks: the EAST mount's point
	 * turned as the blockstate turns the model (SOUTH a quarter, WEST a half, NORTH three quarters); the top mount's
	 * stands upright. A cell without a jar any more gives its centre.
	 */
	static Vec3 funnel(BlockState jarState, BlockPos jar, BlockPos station) {
		float x = 8.0F;
		float y = 13.0F;
		float z = 8.0F;
		if (jarState.getBlock() instanceof HiveAttachmentBlock) {
			Direction facing = jarState.getValue(HiveAttachmentBlock.FACING);
			float[] p = facing.getAxis().isVertical() ? TOP_FUNNEL : SIDE_FUNNEL;
			x = p[0];
			y = p[1];
			z = p[2];
			int quarters = switch (facing) {
				case SOUTH -> 1;
				case WEST -> 2;
				case NORTH -> 3;
				default -> 0;
			};
			for (int q = 0; q < quarters; q++) {
				float turned = 16.0F - z;
				z = x;
				x = turned;
			}
		}
		return new Vec3(jar.getX() - station.getX() + x / 16.0, jar.getY() - station.getY() + y / 16.0,
				jar.getZ() - station.getZ() + z / 16.0);
	}

	/**
	 * The heading a drone docks with, radians: at a side jar the nose points away from the hive, so the body hangs
	 * clear of it and the sting reaches back into the funnel; at a top jar it keeps the heading it flew in with.
	 */
	private static float dockYaw(BlockState jarState, Vec3 toward) {
		if (jarState.getBlock() instanceof HiveAttachmentBlock) {
			Direction facing = jarState.getValue(HiveAttachmentBlock.FACING);
			if (facing.getAxis().isHorizontal()) {
				return (float) Math.atan2(-facing.getStepX(), -facing.getStepZ());
			}
		}
		return (float) Math.atan2(-toward.x, -toward.z);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		// The honey: the casing's window turned to its front, and the hive's column.
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		Poses.rotate(poseStack, Axis.YP.rotationDegrees(-90.0F * state.frontQuarter));
		ModelSubmit.typed(collector, column, new float[] {state.fills[0], 0.0F}, poseStack,
				RenderTypes.entityCutout(TEXTURE), state.windowLight, OverlayTexture.NO_OVERLAY, 0);
		ModelSubmit.typed(collector, column, new float[] {0.0F, state.fills[1]}, poseStack,
				RenderTypes.entityCutout(TEXTURE), state.columnLight, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
		for (Drone drone : state.drones) {
			if (drone == null) {
				continue;
			}
			poseStack.pushPose();
			poseStack.translate(drone.pos.x, drone.pos.y, drone.pos.z);
			Poses.rotate(poseStack, Axis.XP.rotation(drone.tiltX));
			Poses.rotate(poseStack, Axis.ZP.rotation(drone.tiltZ));
			Poses.rotate(poseStack, Axis.YP.rotation(drone.yaw));
			poseStack.scale(SCALE, SCALE, SCALE);
			ModelSubmit.typed(collector, opaque, drone.pose, poseStack, RenderTypes.entityCutout(TEXTURE),
					drone.light, OverlayTexture.NO_OVERLAY, 0);
			// The glass after the body, as the oil slime's shell: what is inside is drawn before it is covered.
			ModelSubmit.typed(collector.order(1), clear, drone.pose, poseStack, RenderTypes.entityTranslucent(TEXTURE),
					drone.light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	/**
	 * The box NeoForge tests against the frustum before drawing (MOD-633): both halves of the station and, for every
	 * drone out, the leg from the station to its jar, widened by a block and raised by the leg's arc and a block more.
	 * Read from the legs themselves, so it follows the configured range. No {@code @Override}: only NeoForge's
	 * interface declares it; {@code OffScreenRendererBoxTest} fails if it stops overriding there.
	 */
	public AABB getRenderBoundingBox(ApiaryDroneStationBlockEntity blockEntity) {
		BlockPos pos = blockEntity.getBlockPos();
		AABB box = new AABB(pos).minmax(new AABB(pos.above())).inflate(0.25, 0.0, 0.25);
		for (int i = 0; i < ApiaryRules.BAYS; i++) {
			DroneBay bay = blockEntity.bay(i);
			if (bay.busy() && bay.jar() != null) {
				int[] corners = bay.flight().path();
				for (int c = 0; c + 2 < corners.length; c += 3) {
					box = box.minmax(new AABB(new BlockPos(corners[c], corners[c + 1], corners[c + 2])).inflate(1.0));
				}
				box = box.minmax(bay.flight().renderBox(pos, 1.0, 0.0)
						.minmax(new AABB(bay.jar()).inflate(1.0, 0.0, 1.0))
						.expandTowards(0.0, bay.arc() + 2.0, 0.0));
			}
		}
		return box;
	}

	@Override
	public int getViewDistance() {
		return 96;
	}

	/** One drone at one frame. */
	private static final class Drone {
		private Vec3 pos = Vec3.ZERO;
		private float yaw;
		private float tiltX;
		private float tiltZ;
		private int light = LightCoordsUtil.FULL_BRIGHT;
		private ApiaryDroneModel.Pose pose = new ApiaryDroneModel.Pose(0.0F, 0.0F, 0.0F, false);
	}

	public static final class State extends BlockEntityRenderState {
		private final @Nullable Drone[] drones = new Drone[ApiaryRules.BAYS];
		private float[] fills = {0.0F, 0.0F};
		private int frontQuarter;
		private boolean hive;
		private int windowLight;
		private int columnLight;
	}
}
