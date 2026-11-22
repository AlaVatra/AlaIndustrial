package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * S2C: the results a furnace's input can be smelted into and which one it runs (MOD-1018), for the result button
 * of the electric and iron furnace screens. Sent to ONE player — the one whose screen is open on that furnace —
 * from the menu's {@code broadcastChanges}, and only when the list or the choice changed.
 *
 * <p>A payload and not {@code ContainerData}: the vanilla container channel carries signed 16-bit numbers, and
 * the button needs item stacks and the recipe's mod id. The client has no recipes of its own on 26.x, so it
 * cannot work the list out itself.
 *
 * @param containerId the menu this list belongs to; the client drops a packet for any other menu
 * @param options     every distinct result, the default first; empty when the input smelts into nothing
 * @param selected    the position of the option the furnace runs, or -1 with no options
 */
public record ResultChoicePayload(int containerId, List<Entry> options, int selected) implements CustomPacketPayload {

	/** At most this many options travel; a furnace input with more results than this is not a real case. */
	public static final int MAX_OPTIONS = 64;

	/**
	 * One option on the wire.
	 *
	 * @param result the result of one operation
	 * @param source the namespace of the recipe: the mod it comes from
	 */
	public record Entry(ItemStack result, String source) {
		static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
				ItemStack.STREAM_CODEC, Entry::result,
				ByteBufCodecs.STRING_UTF8, Entry::source,
				Entry::new);
	}

	/** No options: what a menu shows before the first packet lands. */
	public static ResultChoicePayload none(int containerId) {
		return new ResultChoicePayload(containerId, List.of(), -1);
	}

	public static final Type<ResultChoicePayload> TYPE = new Type<>(Industrialization.id("result_choice"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ResultChoicePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ResultChoicePayload::containerId,
			Entry.CODEC.apply(ByteBufCodecs.list(MAX_OPTIONS)), ResultChoicePayload::options,
			ByteBufCodecs.VAR_INT, ResultChoicePayload::selected,
			ResultChoicePayload::new);

	/** The option the furnace runs, or {@code null} with none. */
	public @Nullable Entry selectedEntry() {
		return selected >= 0 && selected < options.size() ? options.get(selected) : null;
	}

	/** Whether the button is shown: only with a real choice, i.e. two results or more. */
	public boolean hasChoice() {
		return options.size() > 1;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
