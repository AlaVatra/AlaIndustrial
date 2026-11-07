package dev.alaindustrial.core.tooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import dev.alaindustrial.core.style.AlaPalette;
import org.jspecify.annotations.Nullable;

/**
 * A block's hover tooltip, declared by the block itself (MOD-716, ADR-040): its voltage tier, the lines shown
 * without [SHIFT] and the lines [SHIFT] adds. The client's {@code MachineTooltips} only looks the description
 * up ({@link HasMachineTooltip}) and turns {@link Line}s into chat components — it no longer keeps a list of
 * machines of its own.
 *
 * <p><b>The four modes come from the description, not from four lists.</b> {@link #lines} decides what a
 * player sees for [SHIFT] up or down and for the client's {@code showEuNumbers} on or off, from each line's
 * {@link Shown} flag:
 * <ul>
 *   <li>{@link Shown#NUMBERS} — an EU figure or another number the player switched off: shown only with
 *       numbers on;</li>
 *   <li>{@link Shown#ALWAYS} — a size or a range that is not an energy figure (a tank's millibuckets, a
 *       sprinkler's reach): shown in every mode;</li>
 *   <li>{@link Shown#LABEL} — a word, not a number (a tier, a safety warning, a hint): shown with numbers
 *       on, and with numbers off only under [SHIFT], so the plain tooltip stays the bare hint.</li>
 * </ul>
 * The visible lines are laid out by {@link TooltipLayout} (MOD-958, the owner's style "09b"): the {@code basic}
 * lines, and under [SHIFT] the tier line (when {@link #tier} is set)
 * and the visible {@code detailed} lines; a line with a {@link TooltipRole} goes to its role's slot (a key action
 * waits for [SHIFT], a warning never hides). Last comes the hold-[SHIFT] hint, only when [SHIFT] would add a
 * line, with its key white while [SHIFT] is held, and never for a player who asked for details always.
 *
 * <p>Every line has a {@link Line#role} and is drawn in style 09b: the role's icon, a grey label, white values and
 * a copper unit ({@link Line#unit}); the client's {@code TooltipRender} is the one place a role becomes a colour.
 *
 * <p>Minecraft-free on purpose: the arguments are live suppliers (the block passes {@code ServerBalance}
 * reads, so a dedicated server's balance is what the player reads), the colours are roles. That keeps the mode
 * rules above on the L1 lane ({@code MachineTooltipSpecTest}).
 *
 * @param tier     the tier line shown after the basic lines under [SHIFT], or null for a block that names its
 *                 tier elsewhere (in its basic lines) or takes no EU at all
 * @param basic    lines shown without [SHIFT]
 * @param detailed lines [SHIFT] adds after the tier line
 */
public record MachineTooltipSpec(@Nullable Tier tier, List<Line> basic, List<Line> detailed) {

	/** Translation key of the hint a non-detailed tooltip ends with. */
	public static final String HOLD_SHIFT_KEY = "tooltip.alaindustrial.hold_shift";

	/** Prefix of every stat key: {@code stat("buffer", …)} is {@code tooltip.alaindustrial.buffer}. */
	private static final String STAT_PREFIX = "tooltip.alaindustrial.";

	public MachineTooltipSpec {
		basic = List.copyOf(basic);
		detailed = List.copyOf(detailed);
	}

	/** When a line is visible — see the class javadoc. */
	public enum Shown {
		NUMBERS, ALWAYS, LABEL
	}

	/**
	 * Voltage class of the tier line: {@code "<icon> Tier LV"}, the label grey and the class name and the icon in
	 * the tier's colour (LV and MV green, HV purple, as every tooltip of the mod has always shown them). The line's
	 * one argument is the tier itself, which the client writes as its name in that colour.
	 */
	public enum Tier {
		LV("tooltip.alaindustrial.tier_lv", AlaPalette.GOOD),
		MV("tooltip.alaindustrial.tier_mv", AlaPalette.GOOD),
		HV("tooltip.alaindustrial.tier_hv", AlaPalette.TIER_HV);

		private final String key;
		private final int rgb;

		Tier(String key, int rgb) {
			this.key = key;
			this.rgb = rgb;
		}

		/** The tier's colour, {@code 0xRRGGBB}: its icon and its name on the tier line. */
		public int rgb() {
			return rgb;
		}

		/** The tier line itself, as {@link #lines} places it. */
		public Line line() {
			return new Line(key, List.of(() -> this), Shown.LABEL, null, TooltipRole.TIER, null);
		}
	}

	/**
	 * One tooltip line in style 09b (MOD-958): a full translation key with its live arguments, when it shows, and
	 * its role — the icon in the role's colour, the key's text grey, each argument white (a number grouped, a
	 * {@link TooltipUnits.Decimal} with the language's decimal mark), and the {@code unit} key, when set, as a
	 * copper word at the end. With a {@code label} the line reads {@code <icon> <label> <value> <unit>} (the
	 * incubator's per-mode durations).
	 */
	public record Line(String key, List<Supplier<?>> args, Shown shown, @Nullable String label, TooltipRole role,
			@Nullable String unit) {
		public Line {
			args = List.copyOf(args);
		}

		/** The arguments as the line shows them now. */
		public Object[] argValues() {
			Object[] values = new Object[args.size()];
			for (int i = 0; i < values.length; i++) {
				values[i] = args.get(i).get();
			}
			return values;
		}
	}

	/**
	 * A numeric line {@code tooltip.alaindustrial.<key>(value)}, hidden with EU numbers off. A key of
	 * {@link StatStyles} takes its role and unit from there (MOD-958); any other is an info line.
	 */
	public static Line stat(String key, IntSupplier value) {
		return styled(key, value::getAsInt, Shown.NUMBERS, null);
	}

	/**
	 * A numeric line whose argument is not an {@code int}: a {@code long} buffer, or a figure already
	 * formatted as text (a percentage). The value keeps its own type, as the chat component receives it.
	 */
	public static Line statValue(String key, Supplier<?> value) {
		return styled(key, value, Shown.NUMBERS, null);
	}

	private static Line styled(String key, Supplier<?> value, Shown shown, @Nullable String label) {
		StatStyles.Style style = StatStyles.of(key);
		if (style == null) {
			return new Line(STAT_PREFIX + key, List.of(value), shown, label, TooltipRole.INFO, null);
		}
		return new Line(STAT_PREFIX + key, List.of(() -> StatStyles.shown(style, value.get())), shown, label,
				style.role(), style.unit());
	}

	/** A line with two arguments, hidden with EU numbers off; its role from {@link StatStyles}, else info. */
	public static Line stat(String key, Supplier<?> first, Supplier<?> second) {
		StatStyles.Style style = StatStyles.of(key);
		return new Line(STAT_PREFIX + key, List.of(first, second), Shown.NUMBERS, null,
				style == null ? TooltipRole.INFO : style.role(), style == null ? null : style.unit());
	}

	/** A grey line that is not an energy figure (a tank size, a range): shown in every mode. */
	public static Line plain(String key, IntSupplier value) {
		return styled(key, value::getAsInt, Shown.ALWAYS, null);
	}

	/** A line of words with no argument in {@code role}, shown like a tier line ({@link Shown#LABEL}). */
	public static Line text(String fullKey, TooltipRole role) {
		return new Line(fullKey, List.of(), Shown.LABEL, null, role, null);
	}

	/** A line of words with no argument in {@code role}, shown in every mode ({@link Shown#ALWAYS}). */
	public static Line note(String fullKey, TooltipRole role) {
		return new Line(fullKey, List.of(), Shown.ALWAYS, null, role, null);
	}

	/**
	 * A figure in style 09b (MOD-958): {@code <icon> <key(value)> <unit>}, hidden with EU numbers off.
	 *
	 * @param fullKey the label's translation key, its one argument the value
	 * @param unitKey the unit's key ({@link TooltipUnits}), or null for a value that carries its own
	 */
	public static Line stat(TooltipRole role, String fullKey, @Nullable String unitKey, Supplier<?> value) {
		return new Line(fullKey, List.of(value), Shown.NUMBERS, null, role, unitKey);
	}

	/** {@link #stat(TooltipRole, String, String, Supplier)} for a figure that is not an energy one: every mode. */
	public static Line fact(TooltipRole role, String fullKey, @Nullable String unitKey, Supplier<?> value) {
		return new Line(fullKey, List.of(value), Shown.ALWAYS, null, role, unitKey);
	}

	/** A key action in style 09b ("Right-click: open the machine"): waits for [SHIFT] like every key line. */
	public static Line action(String fullKey) {
		return new Line(fullKey, List.of(), Shown.ALWAYS, null, TooltipRole.KEY, null);
	}

	/**
	 * A figure under a label of its own, hidden with EU numbers off: the incubator's per-mode durations. A key of
	 * {@link StatStyles} takes its role and unit from there; it reads {@code <icon> <label> <value> <unit>}.
	 */
	public static Line labelled(String labelKey, String key, IntSupplier value) {
		return styled(key, value::getAsInt, Shown.NUMBERS, labelKey);
	}

	/**
	 * The standard processing machine (LV), in style 09b (MOD-958): draw and the operation's length in seconds
	 * without [SHIFT]; buffer, the energy of one operation and the right-click under it. The per-operation figure
	 * is {@code euPerTick * duration}, computed live.
	 */
	public static MachineTooltipSpec processing(IntSupplier euPerTick, IntSupplier duration, IntSupplier buffer) {
		return processing(Tier.LV, euPerTick, duration, buffer);
	}

	/** {@link #processing(IntSupplier, IntSupplier, IntSupplier)} for a machine of another tier. */
	public static MachineTooltipSpec processing(Tier tier, IntSupplier euPerTick, IntSupplier duration,
			IntSupplier buffer) {
		return new MachineTooltipSpec(tier,
				List.of(stat("energy_input", euPerTick), stat("duration_ticks", duration)),
				List.of(stat("buffer", buffer), stat("energy_per_op", () -> euPerTick.getAsInt() * duration.getAsInt()),
						action(KEY_OPEN_MACHINE)));
	}

	/** Labels of the figures every machine shares (styled by {@link StatStyles}). */
	public static final String STAT_DRAW = "tooltip.alaindustrial.energy_input";
	public static final String STAT_OPERATION = "tooltip.alaindustrial.duration_ticks";
	public static final String STAT_BUFFER = "tooltip.alaindustrial.buffer";
	public static final String STAT_PER_OPERATION = "tooltip.alaindustrial.energy_per_op";
	/** The key line of a machine with a screen. */
	public static final String KEY_OPEN_MACHINE = "tooltip.alaindustrial.key.open_machine";

	/**
	 * The lines a player sees: {@code detailed} is [SHIFT] held, {@code numbers} is the client's
	 * {@code showEuNumbers}. The same as {@link #lines(boolean, boolean, boolean)} for a player who has not asked
	 * for details always.
	 */
	public List<Line> lines(boolean detailed, boolean numbers) {
		return lines(detailed, false, numbers);
	}

	/**
	 * The lines a player sees, the hold-[SHIFT] hint last when [SHIFT] would show more (see the class javadoc).
	 * The hint's one argument is whether [SHIFT] is held: the client writes the key white then, grey otherwise.
	 *
	 * @param shiftHeld      whether [SHIFT] is held
	 * @param alwaysDetailed the client's always-detailed option: everything shows and the hint never does
	 * @param numbers        the client's {@code showEuNumbers}
	 */
	public List<Line> lines(boolean shiftHeld, boolean alwaysDetailed, boolean numbers) {
		List<Line> collapsed = visible(false, numbers);
		List<Line> expanded = visible(true, numbers);
		List<Line> out = new ArrayList<>(shiftHeld || alwaysDetailed ? expanded : collapsed);
		TooltipLayout.Hint hint = TooltipLayout.hint(collapsed.size(), expanded.size(), shiftHeld, alwaysDetailed);
		if (hint != TooltipLayout.Hint.NONE) {
			boolean held = hint == TooltipLayout.Hint.KEY_HELD;
			out.add(new Line(HOLD_SHIFT_KEY, List.of(() -> held), Shown.ALWAYS, null, TooltipRole.HINT, null));
		}
		return out;
	}

	private List<Line> visible(boolean detailed, boolean numbers) {
		List<TooltipLayout.Placed<Line>> placed = new ArrayList<>();
		for (Line line : basic) {
			if (isVisible(line, detailed, numbers)) {
				placed.add(new TooltipLayout.Placed<>(line.role().defaultSlot(), line));
			}
		}
		if (tier != null) {
			placed.add(new TooltipLayout.Placed<>(TooltipLayout.Slot.TIER, tier.line()));
		}
		for (Line line : this.detailed) {
			if (isVisible(line, true, numbers)) {
				placed.add(new TooltipLayout.Placed<>(detailSlot(line), line));
			}
		}
		return TooltipLayout.arrange(placed, detailed);
	}

	/** A [SHIFT] line keeps a slot of its own (a key, a module, a warning); every other one is a detail. */
	private static TooltipLayout.Slot detailSlot(Line line) {
		TooltipLayout.Slot slot = line.role().defaultSlot();
		return slot.underShift() || slot == TooltipLayout.Slot.WARNING || slot == TooltipLayout.Slot.NOTE
				? slot : TooltipLayout.Slot.DETAIL;
	}

	private static boolean isVisible(Line line, boolean detailed, boolean numbers) {
		return switch (line.shown()) {
			case NUMBERS -> numbers;
			case ALWAYS -> true;
			case LABEL -> numbers || detailed;
		};
	}
}
