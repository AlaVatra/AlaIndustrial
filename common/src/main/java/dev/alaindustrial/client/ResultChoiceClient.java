package dev.alaindustrial.client;

import dev.alaindustrial.menu.ResultChoiceMenu;
import dev.alaindustrial.network.ResultChoicePayload;
import net.minecraft.client.Minecraft;

/**
 * Client landing point for {@link ResultChoicePayload} (MOD-1018): hands the furnace's options to the open menu,
 * whose id check drops a packet that arrives after the player moved on to another screen.
 */
public final class ResultChoiceClient {

	private ResultChoiceClient() {
	}

	public static void receive(ResultChoicePayload payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.containerMenu instanceof ResultChoiceMenu menu) {
			menu.acceptResultChoices(payload);
		}
	}
}
