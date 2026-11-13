package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.energy.CableNode;
import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.FloorPortLender;
import dev.alaindustrial.core.environment.GeneratorConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * The piezo path (MOD-764): horizontally touching piezo plates are one {@link SharedStoreGroup} — the plates share
 * one store, the sum of their own buffers, and any plate with a receiver beside it or under its floor block is an
 * outlet that hands the group's energy on. This class holds only what is the plates' own: which faces join, the
 * caps from {@link GeneratorConfig}, and where a plate's receivers are.
 */
public final class PiezoPlateGroup {
	/** Bit of a plate's outlet mask for "a receiver two blocks down, under the floor block". */
	public static final int OUTLET_FLOOR = 1 << 4;

	/** Plates join across their four sides; up to {@link GeneratorConfig#piezoGroupMaxPlates} in one group. */
	public static final SharedStoreGroup.Rules RULES = new SharedStoreGroup.Rules(
			List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST), PiezoPlateBlockEntity.class,
			() -> GeneratorConfig.piezoGroupMaxPlates, () -> GeneratorConfig.piezoGroupOutputPerTick,
			PiezoPlateGroup::outletMask);

	private PiezoPlateGroup() {
	}

	/** The fresh group {@code plate} belongs to, walking the world again only when the cached one went stale. */
	public static SharedStoreGroup of(Level level, PiezoPlateBlockEntity plate) {
		return SharedStoreGroup.of(level, plate, RULES);
	}

	/**
	 * Which of the plate's sides lead to a receiver: bits 0-3 for the horizontal faces in
	 * {@link Direction#get2DDataValue()} order, {@link #OUTLET_FLOOR} for a receiver two blocks down. Another
	 * piezo plate is never a receiver — the group already shares its store.
	 */
	static int outletMask(Level level, BlockPos pos) {
		EnergyLookup lookup = EnergyLookup.get();
		int mask = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos next = pos.relative(dir);
			if (!level.isLoaded(next) || level.getBlockEntity(next) instanceof PiezoPlateBlockEntity) {
				continue;
			}
			EnergyPort port = lookup.find(level, next, dir.getOpposite());
			if (port != null && port.supportsInsertion()) {
				mask |= 1 << dir.get2DDataValue();
			}
		}
		BlockPos receiver = floorReceiverPos(level, pos);
		if (receiver != null) {
			EnergyPort port = lookup.find(level, receiver, Direction.UP);
			if (port != null && port.supportsInsertion()) {
				mask |= OUTLET_FLOOR;
			}
		}
		return mask;
	}

	/**
	 * The block two below the plate, when the floor between is a plain block that lends the plate's port
	 * ({@link FloorPortLender}); {@code null} when the floor is a block with its own block entity.
	 */
	static BlockPos floorReceiverPos(Level level, BlockPos pos) {
		BlockPos floor = pos.below();
		BlockPos receiver = floor.below();
		if (!level.isLoaded(receiver) || level.getBlockState(floor).hasBlockEntity()) {
			return null;
		}
		return receiver;
	}

	/** True when {@code pos} holds a cable — the network pulls from an outlet there, so it is not pushed. */
	static boolean isCable(Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof CableNode;
	}
}
