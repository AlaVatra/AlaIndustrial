package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.todo.TodoListRules;
import dev.alaindustrial.core.todo.TodoListRules.Edit;
import dev.alaindustrial.menu.TodoClipboardMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S: one edit of the to-do clipboard a player has open (MOD-905). A container button carries a single number,
 * and a note is text, so the window needs a payload of its own.
 *
 * <p><b>Nothing is trusted.</b> The server applies it only to the clipboard window the player has open — the
 * same window id, still valid — and only through {@code TodoListSource.apply}, which checks the
 * revision, the player's rights, the item id against the registry and every limit of
 * {@link TodoListRules}. An op number this build does not know is ignored. The string bounds of the codec sit
 * well above the rules' limits: they only stop a client from sending a megabyte.
 *
 * @param containerId the window the edit was made in
 * @param revision    the list revision the window showed
 * @param op          {@link TodoListRules.Op#id()}
 * @param index       the line it acts on, where the op takes one
 * @param itemId      the item id, where the op takes one
 * @param count       the count, or the switch value
 * @param text        the note text, where the op takes one
 */
public record TodoClipboardEditPayload(int containerId, int revision, int op, int index, String itemId, int count,
		String text) implements CustomPacketPayload {

	public static final Type<TodoClipboardEditPayload> TYPE = new Type<>(Industrialization.id("todo_clipboard_edit"));

	private static final int MAX_ID = 512;
	private static final int MAX_TEXT = 512;

	public static final StreamCodec<RegistryFriendlyByteBuf, TodoClipboardEditPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TodoClipboardEditPayload::containerId,
			ByteBufCodecs.VAR_INT, TodoClipboardEditPayload::revision,
			ByteBufCodecs.VAR_INT, TodoClipboardEditPayload::op,
			ByteBufCodecs.VAR_INT, TodoClipboardEditPayload::index,
			ByteBufCodecs.stringUtf8(MAX_ID), TodoClipboardEditPayload::itemId,
			ByteBufCodecs.VAR_INT, TodoClipboardEditPayload::count,
			ByteBufCodecs.stringUtf8(MAX_TEXT), TodoClipboardEditPayload::text,
			TodoClipboardEditPayload::new);

	/** The payload for {@code edit} made in window {@code containerId} against list {@code revision}. */
	public static TodoClipboardEditPayload of(int containerId, int revision, Edit edit) {
		return new TodoClipboardEditPayload(containerId, revision, edit.op().id(), edit.index(), edit.item(),
				edit.count(), edit.text());
	}

	public static void handle(TodoClipboardEditPayload payload, ServerPlayer player) {
		TodoListRules.Op op = TodoListRules.Op.byId(payload.op());
		if (op != null && player.containerMenu instanceof TodoClipboardMenu menu
				&& menu.containerId == payload.containerId() && menu.stillValid(player)) {
			menu.edit(new Edit(op, payload.index(), payload.itemId(), payload.count(), payload.text()),
					payload.revision());
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
