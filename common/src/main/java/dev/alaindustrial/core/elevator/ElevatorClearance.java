package dev.alaindustrial.core.elevator;

/**
 * How much room a body has in the elevator's glass tube (MOD-769), and the two rules that keep it out of the walls:
 * the guide that lines a body up with an open station's doorway, and the clamp that keeps a rider of a moving cabin
 * clear of the tube's walls. The walls of a shaft frame and of a station collide {@link #WALL_PX} thick on each side
 * of the cell, and an open doorway is exactly as wide as the inside of the tube, so one width, {@link #OPENING},
 * says both whether a body fits and how far it may stand off the middle.
 *
 * <p>Distances are in blocks; a lateral offset is a body centre's distance from the middle line of the cell, along
 * one horizontal axis. Minecraft-free, so the arithmetic is tested on the L1 lane.
 */
public final class ElevatorClearance {

	/** The walls of the tube (shaft frames, stations, the jambs of an open door): pixels thick on each side. */
	public static final double WALL_PX = 2.0D;
	/** The floor plate an open station lends the standing cabin: pixels thick. */
	public static final double FLOOR_PX = 1.0D;
	/** The walls, in blocks. */
	public static final double WALL = WALL_PX / 16.0D;
	/** The cabin floor's top over the floor's block height: a rider's feet stand there. */
	public static final double FLOOR_TOP = FLOOR_PX / 16.0D;
	/** The free width inside the tube and through an open doorway. */
	public static final double OPENING = 1.0D - 2.0D * WALL;
	/** How far a body is kept off a wall: a box exactly touching a wall still collides with it on some paths. */
	public static final double SAFETY = 0.01D;
	/** How far in front of the doorway a body heading for it is lined up. */
	public static final double APPROACH = 0.6D;
	/** Within this gap of the doorway a body is lined up at once, not over several ticks. */
	public static final double SNAP_GAP = 0.3D;
	/** The guide moves a body at least this far sideways per tick while it still has to move. */
	static final double MIN_STEP = 0.1D;

	private ElevatorClearance() {
	}

	/** How far off the middle a body {@code width} wide may stand inside the tube; negative when it does not fit. */
	public static double slack(double width) {
		return (OPENING - width) / 2.0D - SAFETY;
	}

	/** Whether a body {@code width} wide fits through an open doorway and inside the tube. */
	public static boolean fits(double width) {
		return slack(width) >= 0.0D;
	}

	/**
	 * The lateral offset that keeps a body {@code width} wide clear of the tube's walls: {@code offset} itself when it
	 * already is, the nearest offset that is otherwise. A body that does not fit at all is left where it is.
	 */
	public static double inside(double offset, double width) {
		double slack = slack(width);
		if (slack < 0.0D) {
			return offset;
		}
		return Math.max(-slack, Math.min(slack, offset));
	}

	/**
	 * One tick of the doorway guide: the lateral offset of a body {@code width} wide that heads for an open doorway
	 * and whose leading edge is {@code gap} blocks in front of the doorway's plane. Far out it is drawn toward the
	 * line it fits through by half the way (at least {@link #MIN_STEP}); within {@link #SNAP_GAP} it is put on it, so
	 * it never meets a jamb.
	 */
	public static double guideStep(double offset, double width, double gap) {
		double target = inside(offset, width);
		double delta = target - offset;
		if (delta == 0.0D || gap <= SNAP_GAP) {
			return target;
		}
		double step = Math.min(Math.abs(delta), Math.max(MIN_STEP, Math.abs(delta) / 2.0D));
		return offset + Math.copySign(step, delta);
	}
}
