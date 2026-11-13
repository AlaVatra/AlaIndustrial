package dev.alaindustrial.client;

/**
 * The numbers behind the slingshot's first-person draw (MOD-961), kept free of Minecraft types so an L1 test can
 * pin them: which hand shows the pouch arm, and which pull frame the item model shows. The zoom is the bow's,
 * {@link BowDrawMath}.
 *
 * <p>{@link SlingshotFirstPerson} reads these on the client; the loader adapters only hand it their arguments.
 */
public final class SlingshotDrawMath {

	/**
	 * Ticks of draw at which the item model switches to its second and third pull frame. They are the thresholds
	 * of the {@code range_dispatch} in {@code items/slingshot.json} divided by its scale; an L1 test reads the
	 * JSON, so a retimed model cannot leave the arm behind.
	 */
	static final int FRAME_1_TICKS = 13;
	static final int FRAME_2_TICKS = 18;

	/**
	 * How far the pouch comes back towards the eye in each pull frame, in view units: where the pouch element of the
	 * three item-model frames lands under vanilla's bow-use arm transform and the model's first-person display,
	 * measured from the first frame. Checked against L3 frames {@code hand_slingshot_draw_*} (2026-11-12).
	 */
	private static final float[] POUCH_BACK = {0.0f, 0.158f, 0.347f};

	/** How far the pouch drops in each pull frame, in view units, measured the same way. */
	private static final float[] POUCH_DROP = {0.0f, 0.046f, 0.099f};

	private SlingshotDrawMath() {
	}

	/**
	 * Whether the hand being drawn shows the pouch arm instead of its own item: only while a slingshot is being
	 * drawn, and only for the hand that does not hold it.
	 */
	public static boolean showsPouchArm(boolean usingItem, boolean usingSlingshot, int remainingTicks,
			boolean drawnHandIsTheUsedOne) {
		return usingItem && usingSlingshot && remainingTicks > 0 && !drawnHandIsTheUsedOne;
	}

	/** The pull frame (0, 1 or 2) the item model shows after {@code ticksUsing} ticks of draw. */
	public static int pullFrame(int ticksUsing) {
		if (ticksUsing >= FRAME_2_TICKS) {
			return 2;
		}
		return ticksUsing >= FRAME_1_TICKS ? 1 : 0;
	}

	/** How far the pouch hand is pulled back towards the eye after {@code ticksUsing} ticks of draw. */
	public static float pouchBack(int ticksUsing) {
		return POUCH_BACK[pullFrame(ticksUsing)];
	}

	/** How far the pouch hand drops below its first-frame place after {@code ticksUsing} ticks of draw. */
	public static float pouchDrop(int ticksUsing) {
		return POUCH_DROP[pullFrame(ticksUsing)];
	}
}
