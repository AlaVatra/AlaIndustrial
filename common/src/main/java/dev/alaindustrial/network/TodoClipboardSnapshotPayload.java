package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.todo.TodoList;
import dev.alaindustrial.core.todo.TodoListCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: the list of the to-do clipboard a player has open, for that player's window only (MOD-905).
 *
 * <p>Sent from {@code TodoClipboardMenu.broadcastChanges()} when the list's revision moved — once on opening,
 * then only after a change — so a window nobody has open costs nothing. It carries the viewer's own rights:
 * on one clipboard the owner sees the access switch and a visitor may see only the tick boxes, or no buttons.
 *
 * @param containerId the window it belongs to; the client drops it for any other
 * @param revision    the list revision the window's next edit must name
 * @param list        the list
 * @param canEdit     whether this viewer may add, remove and rewrite lines
 * @param canTick     whether this viewer may tick and untick lines
 * @param isOwner     whether this viewer may move the access step
 * @param ownerName   the owner's name, or {@code ""} for a clipboard nobody owns
 */
public record TodoClipboardSnapshotPayload(int containerId, int revision, TodoList list, boolean canEdit,
		boolean canTick, boolean isOwner, String ownerName) implements CustomPacketPayload {

	public static final Type<TodoClipboardSnapshotPayload> TYPE =
			new Type<>(Industrialization.id("todo_clipboard_snapshot"));

	/** A player name is at most 16 characters; the bound only keeps a broken save from breaking the packet. */
	public static final int MAX_NAME = 64;

	public static final StreamCodec<RegistryFriendlyByteBuf, TodoClipboardSnapshotPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, TodoClipboardSnapshotPayload::containerId,
					ByteBufCodecs.VAR_INT, TodoClipboardSnapshotPayload::revision,
					TodoListCodecs.STREAM_CODEC, TodoClipboardSnapshotPayload::list,
					ByteBufCodecs.BOOL, TodoClipboardSnapshotPayload::canEdit,
					ByteBufCodecs.BOOL, TodoClipboardSnapshotPayload::canTick,
					ByteBufCodecs.BOOL, TodoClipboardSnapshotPayload::isOwner,
					ByteBufCodecs.stringUtf8(MAX_NAME), TodoClipboardSnapshotPayload::ownerName,
					TodoClipboardSnapshotPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
