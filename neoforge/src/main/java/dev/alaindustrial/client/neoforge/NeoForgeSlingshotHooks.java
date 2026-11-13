package dev.alaindustrial.client.neoforge;

import dev.alaindustrial.client.SlingshotFirstPerson;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The slingshot's first-person draw on NeoForge (MOD-961): the pouch hand and the bow's zoom, through the loader's
 * own events instead of the two Fabric mixins. NeoForge fires {@link RenderHandEvent} right before each
 * {@code submitArmWithItem} call and {@link ComputeFovModifierEvent} with the modifier vanilla is about to
 * interpolate, so both reach the shared {@link SlingshotFirstPerson} at the same place and with the same arguments
 * as Fabric does.
 */
public final class NeoForgeSlingshotHooks {

	private NeoForgeSlingshotHooks() {
	}

	/** Adds both listeners to the game bus (client only). */
	public static void register() {
		NeoForge.EVENT_BUS.addListener((RenderHandEvent event) -> {
			if (SlingshotFirstPerson.submitInsteadOfHand(event.getHand(), event.getPoseStack(),
					event.getSubmitNodeCollector(), event.getPackedLight(), event.getEquipProgress())) {
				event.setCanceled(true);
			}
		});
		NeoForge.EVENT_BUS.addListener((ComputeFovModifierEvent event) ->
				event.setNewFovModifier(SlingshotFirstPerson.newFovModifier(event.getPlayer(), event.getFovModifier(),
						event.getFovScale(), event.getNewFovModifier())));
	}
}
