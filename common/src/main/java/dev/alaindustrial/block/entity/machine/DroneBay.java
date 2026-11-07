package dev.alaindustrial.block.entity.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * One bay of the apiary station (MOD-923, ADR-042): the state of the drone that lives in it — where it is in its
 * round, the jar it serves, what it carries back and the EU held for its flight. The drone's item lives in the
 * station's inventory; this component is everything else, so the station holds four of them and its tick reads
 * like one bay's story told four times.
 *
 * <p>The legs are a {@link DroneFlight} as the garden drone's: the counter is the authority, the client
 * interpolates the leg it got once. The docking and the pour are counted here by {@link #elapsed}, which the
 * client also reads from the update tag, so the sting, the belly and the honey column move on the same tick numbers
 * the server acts on.
 *
 * <p>Saved under a child of its own ({@code Bay0}..{@code Bay3}); the flight inside it uses the garden drone's keys,
 * which collide with nothing there.
 */
public final class DroneBay {

	/** Where the drone is in its round. */
	public enum Phase {
		/** On its pad, free to fly. */
		PARKED,
		/** Flying to its jar. */
		OUTBOUND,
		/** Docked at its jar: sting down, pumping, sting up. */
		DOCKING,
		/** Flying home, with or without honey. */
		RETURNING,
		/** On its pad, pouring its belly into the tank. */
		POURING;

		static Phase byOrdinal(int ordinal) {
			Phase[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : PARKED;
		}
	}

	private final DroneFlight flight = new DroneFlight();
	private Phase phase = Phase.PARKED;
	/** Ticks spent in the docking or the pour so far; 0 in every other phase. */
	private int elapsed;
	/** Game time the docking or the pour began, for a client to draw it from its own clock. */
	private long since;
	/** The jar the drone serves on this round; {@code null} while parked. */
	@Nullable
	private BlockPos jar;
	/** Honey in the belly, in mB. */
	private long honey;
	/** Whether the drone carries a comb back. */
	private boolean comb;
	/** EU held for this flight until it docks (or gives up); 0 once paid or released. */
	private long reservedEu;
	/** Height of the flight's arc in blocks, decided at launch from the distance. */
	private float arc;

	public Phase phase() {
		return phase;
	}

	public DroneFlight flight() {
		return flight;
	}

	public int elapsed() {
		return elapsed;
	}

	/**
	 * Ticks into the docking or the pour at a client's {@code gameTimeWithPartials}, from the time it began; the
	 * server counts {@link #elapsed} itself.
	 */
	public float elapsedAt(float gameTimeWithPartials) {
		return Math.max(0.0F, gameTimeWithPartials - since);
	}

	@Nullable
	public BlockPos jar() {
		return jar;
	}

	public long honey() {
		return honey;
	}

	public boolean comb() {
		return comb;
	}

	public long reservedEu() {
		return reservedEu;
	}

	public float arc() {
		return arc;
	}

	/** Whether the drone is anywhere but parked on its pad. */
	public boolean busy() {
		return phase != Phase.PARKED;
	}

	/** Whether the drone may still bring honey into the tank: out, docked, or flying home, not yet poured. */
	public boolean honeyStillOut() {
		return phase == Phase.OUTBOUND || phase == Phase.DOCKING || phase == Phase.RETURNING;
	}

	/** Sends the parked drone to {@code to}: a leg of {@code ticks}, the flight's price held in reserve. */
	public void launch(BlockPos to, BlockPos target, int ticks, long gameTime, long price, float arcHeight) {
		launch(to, target, new int[0], ticks, gameTime, price, arcHeight);
	}

	/**
	 * Sends the parked drone to {@code to} along {@code route} (x, y, z corners of open cells, the hive first); an
	 * empty route flies the plain arc of {@code arcHeight}.
	 */
	public void launch(BlockPos to, BlockPos target, int[] route, int ticks, long gameTime, long price,
			float arcHeight) {
		jar = to.immutable();
		flight.launch(target, route, ticks, gameTime);
		phase = Phase.OUTBOUND;
		elapsed = 0;
		honey = 0L;
		comb = false;
		reservedEu = price;
		arc = arcHeight;
	}

	/** The outbound leg ended: the drone docks. */
	public void dock(long gameTime) {
		phase = Phase.DOCKING;
		elapsed = 0;
		since = gameTime;
	}

	/** One tick of the docking or the pour; returns the new count. */
	public int count() {
		return ++elapsed;
	}

	/** Leaves the jar with what it got (perhaps nothing) and flies home along the same leg. */
	public void returnHome(long gameTime, long carried, boolean withComb) {
		honey = carried;
		comb = withComb;
		reservedEu = 0L;
		elapsed = 0;
		phase = Phase.RETURNING;
		flight.turnBack(gameTime, 0);
	}

	/** The drone landed on its pad: it pours if it carries anything, otherwise parks at once. */
	public void land(long gameTime) {
		flight.park();
		elapsed = 0;
		since = gameTime;
		phase = honey > 0 || comb ? Phase.POURING : Phase.PARKED;
		if (phase == Phase.PARKED) {
			jar = null;
		}
	}

	/** The belly is empty and the comb handed over: the drone is free again. */
	public void park() {
		flight.park();
		phase = Phase.PARKED;
		elapsed = 0;
		jar = null;
		honey = 0L;
		comb = false;
		reservedEu = 0L;
	}

	public void save(ValueOutput output) {
		output.putInt("Phase", phase.ordinal());
		output.putInt("Elapsed", elapsed);
		output.putLong("Since", since);
		output.storeNullable("Jar", BlockPos.CODEC, jar);
		output.putLong("Honey", honey);
		output.putBoolean("Comb", comb);
		output.putLong("Reserved", reservedEu);
		output.putFloat("Arc", arc);
		flight.save(output);
	}

	public void load(ValueInput input) {
		phase = Phase.byOrdinal(input.getIntOr("Phase", 0));
		elapsed = input.getIntOr("Elapsed", 0);
		since = input.getLongOr("Since", 0L);
		jar = input.read("Jar", BlockPos.CODEC).orElse(null);
		honey = input.getLongOr("Honey", 0L);
		comb = input.getBooleanOr("Comb", false);
		reservedEu = input.getLongOr("Reserved", 0L);
		arc = input.getFloatOr("Arc", 0.0F);
		flight.load(input);
		if (phase != Phase.PARKED && jar == null) {
			park();
		}
	}
}
