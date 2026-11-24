package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.ElevatorShaftBlock;
import dev.alaindustrial.block.ElevatorStationBlock;
import dev.alaindustrial.block.ElevatorWinchBlock;
import dev.alaindustrial.block.entity.ElevatorWinchBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Zone <b>elevator</b> (MOD-769, x = 25, z = 16): a working two-floor elevator between the loss lane and the item
 * pipes, the glass shaft of variant A. Stations at y = 1..2 and y = 4..5 make floors at y = 1 (on the stand's floor)
 * and y = 4, shaft frames fill y = 3 and y = 6, and the winch closes the tube at y = 7. The stations face north, so a
 * visitor walks up to the closed doors at z = 15, calls the cabin with the panel on the right and rides. The winch's
 * buffer is filled, so the cabin runs without a cable.
 *
 * <p>Every cell is written with its final state: the stand writes blocks one by one, so neither the station's upper
 * half (placed by {@code setPlacedBy} in play) nor the frames' joins (worked out on placement) come by themselves.
 *
 * <p>Domain (coding standard, section 1): Storage (the elevator is declared with the logistics blocks).
 */
final class ElevatorZone implements DemoZone {

	private static final int X = 25;
	private static final int Z = 16;
	private static final int WINCH_Y = 7;

	@Override
	public void build(StandWriter w) {
		for (int floor : new int[] {1, 4}) {
			BlockState station = ModContent.ELEVATOR_STATION.get().defaultBlockState()
					.setValue(ElevatorStationBlock.FACING, Direction.NORTH);
			w.place(w.origin().offset(X, floor, Z), station);
			w.place(w.origin().offset(X, floor + 1, Z),
					station.setValue(ElevatorStationBlock.HALF, DoubleBlockHalf.UPPER));
		}
		for (int y : new int[] {3, 6}) {
			w.place(w.origin().offset(X, y, Z), ModContent.ELEVATOR_SHAFT.get().defaultBlockState()
					.setValue(ElevatorShaftBlock.UP, true).setValue(ElevatorShaftBlock.DOWN, true));
		}
		w.place(w.origin().offset(X, WINCH_Y, Z), ModContent.ELEVATOR_WINCH.get().defaultBlockState()
				.setValue(ElevatorWinchBlock.FACING, Direction.NORTH));
		w.configure(X, WINCH_Y, Z, ElevatorWinchBlockEntity.class, "elevator winch to charge",
				winch -> winch.getEnergyStorage().setAmountUntracked(winch.getEnergyStorage().getCapacity()));
	}
}
