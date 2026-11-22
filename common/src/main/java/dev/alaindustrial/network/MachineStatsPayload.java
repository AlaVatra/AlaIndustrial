package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.environment.FuelRuntime;
import dev.alaindustrial.core.environment.FuelTally;
import dev.alaindustrial.core.environment.LavaBurnTally;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: one machine's career statistics for the panel behind the GUI's statistics tab (MOD-125).
 * Sent to exactly ONE player — the one whose screen is open on that machine.
 *
 * <p><b>Why a payload and not {@code ContainerData}.</b> The vanilla container channel serialises each
 * value as a signed 16-bit short ({@code ClientboundContainerSetDataPacket}, verified against the 26.2
 * bytecode), so anything past 32767 arrives negative. Lifetime EU passes that within minutes of play —
 * the counters here are exactly the numbers that cannot travel that way.
 *
 * <p><b>Why it still costs almost nothing.</b> It rides {@code MachineMenu.broadcastChanges()}, which
 * vanilla already calls once per tick for an OPEN menu and never for a closed one, throttled there to
 * one packet every {@code StatsWindow.INTERVAL_TICKS}. No block scans the player list, and a machine
 * nobody is watching produces no traffic at all.
 *
 * <p>{@code containerId} guards against the packet landing on the wrong screen: a player who closes one
 * machine and opens another within the flight time would otherwise see the previous block's numbers for
 * one frame. The client drops any packet whose id is not its current menu's.
 *
 * @param containerId the menu this snapshot belongs to ({@code AbstractContainerMenu.containerId})
 * @param activeTicks ticks the block spent actually working — NOT its age. A machine that has never run
 *     reports zero, which is the honest answer; an age counter under a "working time" label climbs on a
 *     block that has never done anything
 * @param energyIn EU delivered into the block from outside, lifetime
 * @param energyOut EU drawn out of the block by the network, lifetime
 * @param energyGenerated EU the block produced itself, lifetime (exceeds {@code energyOut} by whatever a
 *     full buffer discarded — the use-it-or-lose-it loss worth showing the player)
 * @param energyConsumed EU the block spent on its own work, lifetime
 * @param euOverWindow EU produced plus consumed over the window just elapsed ({@code StatsWindow}), not an
 *     instantaneous tick — a generator that delivers in bursts is unreadable otherwise. Raw EU rather than
 *     EU/t, so an average under 1 EU/t survives the trip: the client divides by the window (MOD-722)
 * @param windowTicks game ticks the window really spanned — the divisor, so a late or early packet stays honest
 * @param peakEuRate highest per-tick rate since the block entity loaded (session, never persisted)
 * @param connections direct-face connections, sources in the low 16 bits and sinks in the next 16
 * @param itemsProcessed completed operations, lifetime (carried now, drawn when the machines task lands)
 * @param fuel a fuel generator's span and load (MOD-990): how long the fuel lasts at the line's real draw, and
 *     that draw against the full output; empty for every other block. Carried here and not on a GUI channel
 *     because the span in ticks passes 32767 with two pieces of coal
 * @param fuelBurnt a fuel generator's burnt fuel by kind (MOD-992): the kinds burnt most, most first, by item
 *     id, and every other piece as one figure; empty for every other block and before anything burnt
 * @param lavaBurnt a geothermal generator's burnt lava in whole mB and the EU it gave (MOD-999); empty for every
 *     other block and before any lava burnt
 */
public record MachineStatsPayload(
		int containerId,
		long activeTicks,
		long energyIn,
		long energyOut,
		long energyGenerated,
		long energyConsumed,
		long euOverWindow,
		int windowTicks,
		int peakEuRate,
		int connections,
		long itemsProcessed,
		Optional<FuelRuntime.Readout> fuel,
		Optional<FuelTally.Top> fuelBurnt,
		Optional<LavaBurnTally.Shown> lavaBurnt) implements CustomPacketPayload {

	/** One burnt kind on the wire: the item id and the pieces. */
	private static final StreamCodec<RegistryFriendlyByteBuf, FuelTally.Entry> FUEL_KIND_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.STRING_UTF8, FuelTally.Entry::id,
					ByteBufCodecs.VAR_LONG, FuelTally.Entry::count,
					FuelTally.Entry::new);

	/** {@link FuelTally.Top} on the wire, never more than {@link FuelTally#SHOWN_KINDS} kinds. */
	private static final StreamCodec<RegistryFriendlyByteBuf, FuelTally.Top> FUEL_BURNT_CODEC = StreamCodec.composite(
			FUEL_KIND_CODEC.apply(ByteBufCodecs.list(FuelTally.SHOWN_KINDS)), FuelTally.Top::kinds,
			ByteBufCodecs.VAR_LONG, FuelTally.Top::rest,
			FuelTally.Top::new);

	/** What a generator burns (MOD-1000), as its ordinal. */
	private static final StreamCodec<ByteBuf, FuelRuntime.Kind> FUEL_KIND_ORDINAL =
			ByteBufCodecs.VAR_INT.map(FuelRuntime.Kind::byOrdinal, FuelRuntime.Kind::ordinal);

	/** {@link FuelRuntime.Readout} on the wire; the span's two negative sentinels travel as they are. */
	private static final StreamCodec<RegistryFriendlyByteBuf, FuelRuntime.Readout> FUEL_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, FuelRuntime.Readout::ticksLeft,
			ByteBufCodecs.VAR_LONG, FuelRuntime.Readout::drawEu,
			ByteBufCodecs.VAR_INT, FuelRuntime.Readout::drawTicks,
			ByteBufCodecs.VAR_INT, FuelRuntime.Readout::euPerTick,
			FUEL_KIND_ORDINAL, FuelRuntime.Readout::kind,
			FuelRuntime.Readout::new);

	/** {@link LavaBurnTally.Shown} on the wire. */
	private static final StreamCodec<RegistryFriendlyByteBuf, LavaBurnTally.Shown> LAVA_BURNT_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_LONG, LavaBurnTally.Shown::mb,
					ByteBufCodecs.VAR_LONG, LavaBurnTally.Shown::eu,
					LavaBurnTally.Shown::new);

	public static final Type<MachineStatsPayload> TYPE = new Type<>(Industrialization.id("machine_stats"));

	private static final StreamCodec<RegistryFriendlyByteBuf, Optional<FuelRuntime.Readout>> FUEL_FIELD =
			ByteBufCodecs.optional(FUEL_CODEC);

	private static final StreamCodec<RegistryFriendlyByteBuf, Optional<FuelTally.Top>> FUEL_BURNT_FIELD =
			ByteBufCodecs.optional(FUEL_BURNT_CODEC);

	private static final StreamCodec<RegistryFriendlyByteBuf, Optional<LavaBurnTally.Shown>> LAVA_BURNT_FIELD =
			ByteBufCodecs.optional(LAVA_BURNT_CODEC);

	/**
	 * Written field by field rather than with {@code StreamCodec.composite}: fourteen fields pass the twelve
	 * that 26.2's {@code composite} takes, and one body keeps this file the same on both lines.
	 */
	public static final StreamCodec<RegistryFriendlyByteBuf, MachineStatsPayload> CODEC =
			StreamCodec.ofMember(MachineStatsPayload::write, MachineStatsPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		ByteBufCodecs.VAR_INT.encode(buf, containerId);
		ByteBufCodecs.VAR_LONG.encode(buf, activeTicks);
		ByteBufCodecs.VAR_LONG.encode(buf, energyIn);
		ByteBufCodecs.VAR_LONG.encode(buf, energyOut);
		ByteBufCodecs.VAR_LONG.encode(buf, energyGenerated);
		ByteBufCodecs.VAR_LONG.encode(buf, energyConsumed);
		ByteBufCodecs.VAR_LONG.encode(buf, euOverWindow);
		ByteBufCodecs.VAR_INT.encode(buf, windowTicks);
		ByteBufCodecs.VAR_INT.encode(buf, peakEuRate);
		ByteBufCodecs.VAR_INT.encode(buf, connections);
		ByteBufCodecs.VAR_LONG.encode(buf, itemsProcessed);
		FUEL_FIELD.encode(buf, fuel);
		FUEL_BURNT_FIELD.encode(buf, fuelBurnt);
		LAVA_BURNT_FIELD.encode(buf, lavaBurnt);
	}

	private static MachineStatsPayload read(RegistryFriendlyByteBuf buf) {
		return new MachineStatsPayload(
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_LONG.decode(buf),
				ByteBufCodecs.VAR_LONG.decode(buf),
				ByteBufCodecs.VAR_LONG.decode(buf),
				ByteBufCodecs.VAR_LONG.decode(buf),
				ByteBufCodecs.VAR_LONG.decode(buf),
				ByteBufCodecs.VAR_LONG.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_LONG.decode(buf),
				FUEL_FIELD.decode(buf),
				FUEL_BURNT_FIELD.decode(buf),
				LAVA_BURNT_FIELD.decode(buf));
	}

	/** Sources among the six direct faces (unpacked from {@link #connections}). */
	public int sources() {
		return connections & 0xFFFF;
	}

	/** Sinks among the six direct faces (unpacked from {@link #connections}). */
	public int sinks() {
		return (connections >>> 16) & 0xFFFF;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
