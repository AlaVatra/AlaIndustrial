package dev.alaindustrial.client;

import dev.alaindustrial.menu.TodoClipboardMenu;
import dev.alaindustrial.network.TodoClipboardSnapshotPayload;
import net.minecraft.client.Minecraft;

/**
 * Client landing point for {@link TodoClipboardSnapshotPayload} (MOD-905): the snapshot goes to the open
 * clipboard window, whose id check drops one meant for a window closed since.
 */
public final class TodoClipboardClient {

	private TodoClipboardClient() {
	}

	public static void receive(TodoClipboardSnapshotPayload payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.containerMenu instanceof TodoClipboardMenu menu) {
			menu.acceptSnapshot(payload);
		}
	}
}
