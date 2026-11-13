package dev.alaindustrial.menu;

import dev.alaindustrial.core.todo.TodoList;
import dev.alaindustrial.core.todo.TodoListRules.Edit;
import dev.alaindustrial.core.todo.TodoListRules.Op;
import dev.alaindustrial.core.todo.TodoListRules.Outcome;
import dev.alaindustrial.menu.todo.TodoListSource;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.TodoClipboardSnapshotPayload;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The to-do clipboard's window (MOD-905): the list above, the player's inventory below and nothing else — the
 * clipboard holds no item, so the menu has no slot of its own.
 *
 * <h2>How the window learns the list</h2>
 * Nothing travels in the open packet (each loader has its own API for that). The server side holds the list's
 * {@link TodoListSource} — the hung clipboard or the one in the hand — and, from {@link #broadcastChanges} —
 * which vanilla calls every tick for an OPEN menu and never for a
 * closed one — sends a {@link TodoClipboardSnapshotPayload} whenever the list's revision moved since the last
 * one this window got. The snapshot carries this viewer's own rights, which differ between viewers of one
 * clipboard. The client side has no block entity and reads only the snapshot.
 *
 * <h2>How the window changes the list</h2>
 * Every edit arrives as a {@code TodoClipboardEditPayload} and ends in {@link #edit}, which hands it to the
 * source's rules. Shift-clicking a stack in the inventory adds a line with that item and that count
 * ({@link #quickMoveStack}); the stack stays where it is.
 */
public class TodoClipboardMenu extends AbstractContainerMenu {

	// --- layout, in screen pixels: the six-row chest's window, the list where the chest's slots are ---
	public static final int WIDTH = 176;
	public static final int HEIGHT = 222;
	public static final int INV_X = 8;
	public static final int INV_Y = 140;
	public static final int HOTBAR_Y = 198;
	public static final int STRIDE = 18;

	private final Player player;
	/** Where the list lives on the server; {@code null} on the client, which knows it only from snapshots. */
	private final @Nullable TodoListSource clipboard;
	/** Server: the revision of the last snapshot this window was sent, or -1 to send one on the next tick. */
	private int sentRevision = -1;
	/** Client: the last snapshot, or {@code null} until the first one lands. */
	private @Nullable TodoClipboardSnapshotPayload snapshot;

	/** The client's menu, built from the open packet. */
	public TodoClipboardMenu(int syncId, Inventory inventory) {
		this(syncId, inventory, null);
	}

	/** The server's menu, opened on {@code clipboard}. */
	public TodoClipboardMenu(int syncId, Inventory inventory, @Nullable TodoListSource clipboard) {
		super(ModContent.TODO_CLIPBOARD_MENU.get(), syncId);
		this.player = inventory.player;
		this.clipboard = clipboard;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * STRIDE, INV_Y + row * STRIDE));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inventory, col, INV_X + col * STRIDE, HOTBAR_Y));
		}
	}

	/** Server: the list this window edits, or {@code null} on the client. */
	public @Nullable TodoListSource clipboard() {
		return clipboard;
	}

	/**
	 * Server: apply an edit the window made against list revision {@code revision}. A refused edit sends the
	 * current list back at once, so the window shows what is really on the wall instead of a lost click.
	 */
	public Outcome edit(Edit edit, int revision) {
		if (clipboard == null) {
			return Outcome.INVALID;
		}
		Outcome outcome = clipboard.apply(player, edit, revision);
		if (outcome != Outcome.APPLIED) {
			sentRevision = -1;
		}
		return outcome;
	}

	/**
	 * Shift-click: a line with the clicked stack's item and count. Nothing moves — the stack is a sample, and
	 * returning {@code EMPTY} ends vanilla's quick-move loop.
	 */
	@Override
	public ItemStack quickMoveStack(Player who, int index) {
		if (clipboard != null && index >= 0 && index < this.slots.size()) {
			ItemStack stack = this.slots.get(index).getItem();
			if (!stack.isEmpty()) {
				edit(Edit.addItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()),
						clipboard.revision());
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player who) {
		return clipboard == null || clipboard.stillValid(who);
	}

	@Override
	public void sendAllDataToRemote() {
		super.sendAllDataToRemote();
		sentRevision = -1;
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (clipboard == null || !(player instanceof ServerPlayer serverPlayer)
				|| clipboard.revision() == sentRevision) {
			return;
		}
		sentRevision = clipboard.revision();
		// A player whose connection never agreed on the mod's channels (a gametest's mock player on NeoForge)
		// cannot be sent the snapshot; the window then simply has nothing to show.
		if (NetworkDispatcher.get().canSendToPlayer(serverPlayer, TodoClipboardSnapshotPayload.TYPE)) {
			NetworkDispatcher.get().sendToPlayer(serverPlayer, snapshotFor(serverPlayer));
		}
	}

	/** The snapshot {@code viewer} is sent: the list, and what this viewer may do with it. */
	public TodoClipboardSnapshotPayload snapshotFor(Player viewer) {
		TodoListSource source = clipboard;
		if (source == null) {
			throw new IllegalStateException("snapshotFor is server-side: the client menu has no clipboard");
		}
		String name = source.ownerName();
		if (name.length() > TodoClipboardSnapshotPayload.MAX_NAME) {
			name = name.substring(0, TodoClipboardSnapshotPayload.MAX_NAME);
		}
		return new TodoClipboardSnapshotPayload(containerId, source.revision(), source.list(),
				source.may(viewer, Op.ADD_NOTE), source.may(viewer, Op.TOGGLE_DONE), source.may(viewer, Op.SET_ACCESS),
				name);
	}

	/** Client: take a snapshot meant for this window; one for a window closed since is dropped. */
	public void acceptSnapshot(TodoClipboardSnapshotPayload payload) {
		if (payload.containerId() == containerId) {
			this.snapshot = payload;
		}
	}

	/** Client: the last snapshot, or {@code null} before the first one. */
	public @Nullable TodoClipboardSnapshotPayload snapshot() {
		return snapshot;
	}

	/** Client: the list as last shown, or a blank one before the first snapshot. */
	public TodoList shownList() {
		return snapshot == null ? TodoList.EMPTY : snapshot.list();
	}
}
