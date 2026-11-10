package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.gametest.PayloadRegistrationScenarios;
import dev.alaindustrial.network.forge.ForgeNetwork;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The Forge probe for {@link PayloadRegistrationScenarios} (MOD-706 on Forge, MOD-962 stage 3).
 *
 * <p>The oracle is Forge's own channel, not our bookkeeping: Forge keeps one {@code Message} per payload id in
 * {@code PayloadChannel.payloads}, with the {@code PacketFlow direction} it was added in and the handler given to
 * {@code addMain}. {@code PayloadChannel} is package-private and final, so the map is read by reflection from
 * {@link ForgeNetwork#channel()} (dev-only test code). A payload added to a flow with a handler is "handler
 * registered" in that direction; Forge requires the handler at {@code addMain}, so a type without one cannot exist.
 */
final class ForgePayloadProbe implements PayloadRegistrationScenarios.Probe {

	static final ForgePayloadProbe INSTANCE = new ForgePayloadProbe();

	private @Nullable Map<Identifier, Entry> entries;

	private ForgePayloadProbe() {
	}

	private record Entry(@Nullable PacketFlow direction, boolean handler) {}

	@Override
	public boolean typeRegistered(Identifier id, PayloadRegistrationScenarios.Direction direction) {
		Entry entry = entries().get(id);
		return entry != null && entry.direction() == flow(direction);
	}

	@Override
	public boolean serverHandlerRegistered(Identifier id) {
		Entry entry = entries().get(id);
		return entry != null && entry.direction() == PacketFlow.SERVERBOUND && entry.handler();
	}

	@Override
	public Boolean clientHandlerRegistered(Identifier id) {
		Entry entry = entries().get(id);
		return entry != null && entry.direction() == PacketFlow.CLIENTBOUND && entry.handler();
	}

	private static PacketFlow flow(PayloadRegistrationScenarios.Direction direction) {
		return direction == PayloadRegistrationScenarios.Direction.CLIENTBOUND
				? PacketFlow.CLIENTBOUND
				: PacketFlow.SERVERBOUND;
	}

	private synchronized Map<Identifier, Entry> entries() {
		if (entries == null) {
			entries = read();
		}
		return entries;
	}

	private static Map<Identifier, Entry> read() {
		Object channel = ForgeNetwork.channel();
		try {
			Field payloads = channel.getClass().getDeclaredField("payloads");
			payloads.setAccessible(true);
			Map<Identifier, Entry> out = new HashMap<>();
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) payloads.get(channel)).entrySet()) {
				Object message = entry.getValue();
				Method direction = message.getClass().getDeclaredMethod("direction");
				direction.setAccessible(true);
				Method consumer = message.getClass().getDeclaredMethod("consumer");
				consumer.setAccessible(true);
				out.put((Identifier) entry.getKey(),
						new Entry((PacketFlow) direction.invoke(message), consumer.invoke(message) != null));
			}
			return out;
		} catch (ReflectiveOperationException | RuntimeException e) {
			throw new IllegalStateException("cannot read Forge's PayloadChannel.payloads (" + channel.getClass()
					+ ") by reflection: " + e, e);
		}
	}
}
