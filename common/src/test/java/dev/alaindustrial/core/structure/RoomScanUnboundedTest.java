package dev.alaindustrial.core.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.core.structure.RoomScan.Result;
import dev.alaindustrial.core.structure.RoomScan.ShellKind;
import dev.alaindustrial.core.structure.RoomScan.Status;
import org.junit.jupiter.api.Test;

/**
 * A reactor room with one whole wall missing (MOD-468): each of the six sides must be reported as unbounded on
 * its own, at the point one step past the reach on that side, so the player is sent to the right wall.
 * {@link RoomScanTest} only ever opens the far side.
 *
 * @implements MOD-976 the mutation gate kills the six unbounded-wall answers
 */
class RoomScanUnboundedTest {

	/** A 3³ interior at 1..3 on every axis, its shell at 0 and 4, the controller at (0,1,1) facing +X. */
	private static RoomScan.ShellProbe roomWithout(int axis, int face) {
		return (x, y, z) -> {
			int[] p = {x, y, z};
			if (p[axis] == face) {
				return ShellKind.OTHER;
			}
			boolean perimeter = (x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4)
					&& x >= 0 && x <= 4 && y >= 0 && y <= 4 && z >= 0 && z <= 4;
			if (!perimeter) {
				return ShellKind.OTHER;
			}
			return x == 0 && y == 1 && z == 1 ? ShellKind.CONTROLLER : ShellKind.CASING;
		};
	}

	private static void assertUnbounded(int axis, int face, int ex, int ey, int ez) {
		Result r = RoomScan.scan(roomWithout(axis, face), 0, 1, 1, 1, 0, 0);
		assertEquals(Status.ROOM_UNBOUNDED, r.status(), "axis " + axis + " face " + face);
		assertEquals(ex, r.x(), "x");
		assertEquals(ey, r.y(), "y");
		assertEquals(ez, r.z(), "z");
	}

	@Test
	void eachMissingWallIsNamedOneStepPastTheReach() {
		int reach = RoomScan.DEFAULT_MAX_INNER + 1;
		// the seed is the cell the controller faces, (1,1,1)
		assertUnbounded(0, 4, 1 + reach, 1, 1);
		assertUnbounded(1, 0, 1, 1 - reach, 1);
		assertUnbounded(1, 4, 1, 1 + reach, 1);
		assertUnbounded(2, 0, 1, 1, 1 - reach);
		assertUnbounded(2, 4, 1, 1, 1 + reach);
	}
}
