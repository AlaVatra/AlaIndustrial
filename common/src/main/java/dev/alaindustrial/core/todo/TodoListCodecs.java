package dev.alaindustrial.core.todo;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How a to-do list is saved and sent (MOD-905): the {@code alaindustrial:todo_list} component, the clipboard
 * block entity's NBT and the window's snapshot packet all use these two codecs.
 *
 * <p>Kept apart from {@link TodoList} so the list and its rules stay Minecraft-free for the L1 test.
 *
 * <p><b>Lenient on disk, bounded on the wire.</b> The disk codec reads any length and hands the result to
 * {@link TodoListRules#fitWire}, so a list edited outside the game loads cut to the wire limits instead of
 * failing; the stream codec then never meets a list it cannot send.
 *
 * <p><b>The access step reads old saves softly.</b> Lists saved before the three-step ladder carried a
 * {@code shared} switch; with no {@code access} field it is read through {@link TodoAccess#fromShared} and never
 * written again. Only {@code access} is written.
 */
public final class TodoListCodecs {

	private TodoListCodecs() {
	}

	public static final Codec<TodoEntry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("item", "").forGetter(TodoEntry::item),
			Codec.INT.optionalFieldOf("count", 0).forGetter(TodoEntry::count),
			Codec.STRING.optionalFieldOf("note", "").forGetter(TodoEntry::note),
			Codec.BOOL.optionalFieldOf("done", false).forGetter(TodoEntry::done)).apply(i, TodoEntry::new));

	public static final Codec<TodoAccess> ACCESS_CODEC = Codec.STRING.xmap(TodoAccess::byName,
			TodoAccess::serialName);

	public static final Codec<TodoList> CODEC = RecordCodecBuilder.<TodoList>create(i -> i.group(
			ENTRY_CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(TodoList::entries),
			ACCESS_CODEC.optionalFieldOf("access").forGetter(list -> Optional.of(list.access())),
			Codec.BOOL.optionalFieldOf("shared").forGetter(list -> Optional.<Boolean>empty()))
			.apply(i, (entries, access, shared) -> new TodoList(entries,
					access.orElseGet(() -> TodoAccess.fromShared(shared.orElse(false))))))
			.xmap(TodoListRules::fitWire, list -> list);

	/**
	 * The wire limits are counted in code points, a string on the wire in UTF-16 units: one code point is at
	 * most two units, so the stream limit is twice the rule's.
	 */
	private static final int UNITS_PER_CODE_POINT = 2;

	public static final StreamCodec<RegistryFriendlyByteBuf, TodoEntry> ENTRY_STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(TodoListRules.WIRE_ID * UNITS_PER_CODE_POINT), TodoEntry::item,
			ByteBufCodecs.VAR_INT, TodoEntry::count,
			ByteBufCodecs.stringUtf8(TodoListRules.WIRE_NOTE * UNITS_PER_CODE_POINT), TodoEntry::note,
			ByteBufCodecs.BOOL, TodoEntry::done,
			TodoEntry::new);

	/** An access number this build does not know reads as the safe end, {@link TodoAccess#OWNER_ONLY}. */
	private static final StreamCodec<ByteBuf, TodoAccess> ACCESS_STREAM_CODEC =
			ByteBufCodecs.VAR_INT.map(id -> {
				TodoAccess access = TodoAccess.byId(id);
				return access == null ? TodoAccess.OWNER_ONLY : access;
			}, TodoAccess::id);

	public static final StreamCodec<RegistryFriendlyByteBuf, TodoList> STREAM_CODEC = StreamCodec.composite(
			ENTRY_STREAM_CODEC.apply(ByteBufCodecs.list(TodoListRules.WIRE_ENTRIES)), TodoList::entries,
			ACCESS_STREAM_CODEC, TodoList::access,
			TodoList::new);
}
