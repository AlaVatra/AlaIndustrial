package dev.alaindustrial.core.escalator;

/**
 * The escalator's arithmetic, free of Minecraft so the L1 lane covers it (MOD-969): the physical steps of a
 * section, the draw of a running band and the velocity the band gives a rider.
 *
 * <p><b>Physical steps.</b> A section is one block of a 1:1 slope cut into {@link #SUBSTEPS} steps 2 px apart; the
 * top of step {@code k} is {@code 2k + 1} px. The highest is 15 px, so a rider's feet stay inside the section's own
 * cell (where {@code entityInside} reaches them) and the first step is 1 px off the floor, the landing 1 px above
 * the last. The drawn steps are larger (two per block) and lie on the same slope.
 *
 * <p><b>Velocity.</b> The band raises the rider's speed along its run to its own and never adds to it: a constant
 * push would be scaled by the friction of the next tick, which is 0.91 in the air and 0.546 on the ground, and the
 * steady speed would differ tenfold between the two. The side speed is kept, so the rider can step off sideways.
 * The vertical speed follows the 1:1 slope.
 */
public final class EscalatorRules {

	/** Physical steps per section. */
	public static final int SUBSTEPS = 8;

	/** Drawn steps per section (owner decision 2026-11-22: smaller than a block, two per block). */
	public static final int DRAWN_STEPS = 2;

	private EscalatorRules() {
	}

	/** Top of physical step {@code k} (0 at the low end), in pixels above the section's floor. */
	public static int stepTopPixels(int k) {
		if (k < 0 || k >= SUBSTEPS) {
			throw new IllegalArgumentException("step " + k + " outside 0.." + (SUBSTEPS - 1));
		}
		return 2 * k + 1;
	}

	/** Draw of a running band in thousandths of an EU per tick: the drive's fixed part plus every section's. */
	public static long milliEuPerTick(int baseEuPerTick, int milliEuPerSection, int sections) {
		return Math.max(0L, (long) baseEuPerTick * 1000L + (long) milliEuPerSection * Math.max(0, sections));
	}

	/**
	 * The velocity a running band gives a rider.
	 *
	 * @param mx    the rider's current velocity, X
	 * @param my    the rider's current velocity, Y
	 * @param mz    the rider's current velocity, Z
	 * @param fx    X of the section's facing (the climbing direction), -1, 0 or 1
	 * @param fz    Z of the section's facing
	 * @param down  whether the band runs down (towards {@code -facing})
	 * @param speed the band speed along the run, blocks per tick
	 * @return the new velocity {x, y, z}
	 */
	public static double[] carry(double mx, double my, double mz, int fx, int fz, boolean down, double speed) {
		double dx = down ? -fx : fx;
		double dz = down ? -fz : fz;
		double along = mx * dx + mz * dz;
		double sideX = mx - along * dx;
		double sideZ = mz - along * dz;
		double a = Math.max(along, speed);
		double vy = down ? Math.min(my, -a) : Math.max(my, a);
		return new double[] {sideX + a * dx, vy, sideZ + a * dz};
	}
}
