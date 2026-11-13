package dev.alaindustrial.client;

/**
 * The bow's first-person numbers that the slingshot and the Electric Bow borrow (MOD-961), kept free of Minecraft
 * types so an L1 test can pin them: the zoom curve, how that zoom joins a loader's field-of-view event without
 * erasing what other mods put there, and which hand a drawn bow hides.
 *
 * <p>{@link SlingshotFirstPerson} and {@link ElectricBowFirstPerson} read these on the client; the loader adapters
 * only hand them their arguments.
 */
public final class BowDrawMath {

	/** Ticks of draw after which a vanilla bow's zoom stops deepening ({@code BowItem.MAX_DRAW_DURATION}). */
	public static final float BOW_DRAW_TICKS = 20.0f;

	/** How far the view narrows at full draw: the modifier drops by this fraction, as a bow's does. */
	static final float ZOOM_DEPTH = 0.15f;

	private BowDrawMath() {
	}

	/**
	 * The factor the field-of-view modifier is multiplied by after {@code ticksUsing} ticks of a draw that is full
	 * after {@code drawTicks}: 1 at rest, falling with the square of the draw to {@code 1 - ZOOM_DEPTH}, then
	 * holding. With {@code drawTicks} = {@link #BOW_DRAW_TICKS} it is vanilla's bow curve,
	 * {@code 1 - min(t/20, 1)^2 * 0.15}.
	 */
	public static float zoomFactor(int ticksUsing, float drawTicks) {
		float draw = Math.min(Math.max(ticksUsing, 0) / drawTicks, 1.0f);
		return 1.0f - draw * draw * ZOOM_DEPTH;
	}

	/**
	 * Vanilla's last step of {@code getFieldOfViewModifier}: {@code Mth.lerp(effectScale, 1, modifier)}, written the
	 * same way so the result is the same float.
	 */
	public static float lerpByEffectScale(float effectScale, float modifier) {
		return 1.0f + effectScale * (modifier - 1.0f);
	}

	/**
	 * The new value for a loader's field-of-view event (NeoForge and Forge {@code ComputeFovModifierEvent}) when a
	 * zoom of {@code factor} applies: the value the listeners have set so far, {@code current}, scaled by what the
	 * zoom does to the interpolated modifier.
	 *
	 * <p>On a game where nobody else touched the event, {@code current} is the loader's own
	 * {@code lerp(effectScale, 1, modifier)} and the answer is exactly {@code lerp(effectScale, 1, modifier * factor)}
	 * — what Fabric gets by narrowing the modifier before vanilla interpolates it. When another mod has changed the
	 * value, its change is kept: the zoom multiplies it, so the two compose in either listener order. With the
	 * effects slider at 0 the interpolation is 1 whatever the modifier, and nothing changes, as in vanilla.
	 *
	 * @param current     the event's current new modifier
	 * @param modifier    the raw modifier the loader passed to the event, before interpolation
	 * @param effectScale the player's "FOV effects" setting, 0..1
	 * @param factor      the zoom factor, {@link #zoomFactor}
	 */
	public static float composedFovModifier(float current, float modifier, float effectScale, float factor) {
		float base = lerpByEffectScale(effectScale, modifier);
		float zoomed = lerpByEffectScale(effectScale, modifier * factor);
		if (current == base) {
			return zoomed;
		}
		if (base <= 0.0f) {
			// Nothing to scale against (a modifier of 0 at full effect scale): leave the value as the others set it.
			return current;
		}
		return current * (zoomed / base);
	}

	/**
	 * Whether the first-person hand being drawn is hidden while a bow-like weapon is drawn: as vanilla hides the
	 * other hand of a drawn bow, only the hand that does not hold the drawn weapon, and only while it is drawn.
	 */
	public static boolean hidesOtherHand(boolean usingItem, boolean usingThisWeapon, boolean drawnHandIsTheUsedOne) {
		return usingItem && usingThisWeapon && !drawnHandIsTheUsedOne;
	}
}
