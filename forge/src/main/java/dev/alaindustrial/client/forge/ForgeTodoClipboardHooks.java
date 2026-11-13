package dev.alaindustrial.client.forge;

import dev.alaindustrial.client.TodoClipboardFirstPerson;
import java.util.function.Predicate;
import net.minecraftforge.client.event.RenderHandEvent;

/**
 * The to-do clipboard held like a map on Forge (MOD-905), through the loader's own hand event instead of the Fabric
 * mixin. Forge fires {@link RenderHandEvent} right before each {@code submitArmWithItem} call, so it reaches the
 * shared {@link TodoClipboardFirstPerson} at the same place and with the same arguments as Fabric and NeoForge do.
 */
public final class ForgeTodoClipboardHooks {

	private ForgeTodoClipboardHooks() {
	}

	/** Adds the listener (client only). A hand listener that returns true cancels vanilla's drawing of the hand. */
	public static void register() {
		RenderHandEvent.BUS.addListener((Predicate<RenderHandEvent>) event ->
				TodoClipboardFirstPerson.submitInsteadOfHand(event.getHand(), event.getItemStack(),
						event.getPoseStack(), event.getNodeCollector(), event.getPackedLight(),
						event.getInterpolatedPitch(), event.getEquipProgress(), event.getSwingProgress()));
	}
}
