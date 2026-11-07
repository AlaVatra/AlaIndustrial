package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.ApiaryDroneStationBlockEntity;
import dev.alaindustrial.block.entity.machine.DroneBay;
import dev.alaindustrial.block.entity.machine.DroneFlight;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.apiary.ApiaryRules;
import dev.alaindustrial.core.drone.DroneFlightPath;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.TooltipRole;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The apiary station's lower half (MOD-923): a full machine casing with the honey tank, the slots and every port.
 * The upper half, {@link ApiaryDroneHiveBlock}, is a block of its own placed on top; this one decides whether the
 * pair works ({@link ApiaryDroneStationBlockEntity}), and the drones are drawn by its block entity's renderer.
 *
 * <p>Every face takes a cable, a honey pipe and an item pipe — the front included (owner, round of 2026-10-31,
 * item 2), so the inherited "front face is inert" rule is turned off here and in the block entity alike.
 */
public class ApiaryDroneStationBlock extends HorizontalMachineBlock implements MachineHumProvider, HasMachineTooltip {

	/** The casing and the hive above it, as one outline (owner, in-game round of 2026-11-07); assembled once. */
	private static final VoxelShape PAIR_OUTLINE = Shapes.or(Shapes.block(),
			ApiaryDroneHiveBlock.SHAPE.move(0.0, 1.0, 0.0));

	public ApiaryDroneStationBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return ApiaryDroneHiveBlock.isHive(level.getBlockState(pos.above())) ? PAIR_OUTLINE : Shapes.block();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return Shapes.block();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ApiaryDroneStationBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return humMachineTicker(level);
	}

	/**
	 * The upper half came or went: the station looks at once, so drones in the air come home the tick the hive is
	 * broken rather than on the next idle check.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ApiaryDroneStationBlockEntity station) {
			station.wake();
		}
	}

	/** Every face, the front included, is a port; the mirror of the block entity's energy role. */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return isCableConnectable();
	}

	/** A bumblebee drone buzzes as a vanilla bee does (owner, in-game round of 2026-11-07). */
	@Override
	public Supplier<SoundEvent> humSound() {
		return () -> SoundEvents.BEE_LOOP;
	}

	/** About as loud as a bee: the loop follows the drone in the air ({@link #humPosition}). */
	@Override
	public float humVolume() {
		return 0.6f;
	}

	/**
	 * Where the buzz is heard from: the first drone in the air, along the same route or arc the renderer draws, at
	 * the station's clock. Core types only, so this stays loadable on a server; {@code null} while none flies.
	 */
	@Override
	@Nullable
	public Vec3 humPosition(Level level, BlockPos pos, BlockState state) {
		if (!(level.getBlockEntity(pos) instanceof ApiaryDroneStationBlockEntity station)) {
			return null;
		}
		for (int i = 0; i < ApiaryRules.BAYS; i++) {
			DroneBay bay = station.bay(i);
			BlockPos jar = bay.jar();
			if (jar == null || bay.phase() != DroneBay.Phase.OUTBOUND && bay.phase() != DroneBay.Phase.RETURNING) {
				continue;
			}
			DroneFlight flight = bay.flight();
			float eased = DroneFlight.accelerate(flight.progress(level.getGameTime()));
			float travel = flight.returning() ? 1.0F - eased : eased;
			double[] pad = ApiaryRules.padSpot(i);
			int[] corners = flight.path();
			int inner = corners.length >= 6 ? corners.length / 3 - 1 : 0;
			float[] xyz = new float[(inner + 2) * 3];
			xyz[0] = (float) (pos.getX() + pad[0]);
			xyz[1] = (float) (pos.getY() + pad[1]);
			xyz[2] = (float) (pos.getZ() + pad[2]);
			for (int c = 1; c <= inner; c++) {
				xyz[c * 3] = corners[c * 3] + 0.5F;
				xyz[c * 3 + 1] = corners[c * 3 + 1] + 0.5F;
				xyz[c * 3 + 2] = corners[c * 3 + 2] + 0.5F;
			}
			int last = (inner + 1) * 3;
			xyz[last] = jar.getX() + 0.5F;
			xyz[last + 1] = jar.getY() + 1.0F;
			xyz[last + 2] = jar.getZ() + 0.5F;
			float[] at = new float[3];
			new DroneFlightPath(xyz).point(travel, at);
			double lift = inner == 0 ? Math.sin(Math.PI * travel) * bay.arc() : 0.0;
			return new Vec3(at[0], at[1] + lift, at[2]);
		}
		return null;
	}

	/** Working means airborne: a drone flying out or home. Docked or pouring, the rotors idle. */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return level.getBlockEntity(pos) instanceof ApiaryDroneStationBlockEntity station && station.anyInFlight();
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.stat("apiary_drone_flight_cost", ServerBalance::apiaryDroneLaunchEu,
						ServerBalance::apiaryDroneEuPerBlock)),
				List.of(MachineTooltipSpec.plain("apiary_station_range", ServerBalance::apiaryStationRange),
						MachineTooltipSpec.stat("capacity", ServerBalance::apiaryStationBuffer),
						MachineTooltipSpec.note("tooltip.alaindustrial.apiary_drone_station.needs_hive",
								TooltipRole.NOTE)));
	}
}
