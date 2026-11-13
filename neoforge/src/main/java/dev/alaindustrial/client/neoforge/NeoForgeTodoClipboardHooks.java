package dev.alaindustrial.client.neoforge;

import dev.alaindustrial.client.TodoClipboardFirstPerson;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The to-do clipboard held like a map on NeoForge (MOD-905), through the loader's own hand event instead of the
 * Fabric mixin. NeoForge fires {@link RenderHandEvent} right before each {@code submitArmWithItem} call, so it reaches
 * the shared {@link TodoClipboardFirstPerson} at the same place and with the same arguments as Fabric does.
 */
public final class NeoForgeTodoClipboardHooks {

	private NeoForgeTodoClipboardHooks() {
	}

	/** Adds the listener to the game bus (client only). */
	public static void register() {
		NeoForge.EVENT_BUS.addListener((RenderHandEvent event) -> {
			if (TodoClipboardFirstPerson.submitInsteadOfHand(event.getHand(), event.getItemStack(),
					event.getPoseStack(), event.getSubmitNodeCollector(), event.getPackedLight(),
					event.getInterpolatedPitch(), event.getEquipProgress(), event.getSwingProgress())) {
				event.setCanceled(true);
			}
		});
	}
}
