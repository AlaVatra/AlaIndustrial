package dev.alaindustrial.client.neoforge;

import dev.alaindustrial.client.ElectricBowFirstPerson;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Electric Bow's first-person draw on NeoForge (MOD-363, MOD-961): the other hand hidden and the bow's zoom,
 * through the loader's own events instead of the two Fabric mixins. NeoForge fires {@link RenderHandEvent} right
 * before each {@code submitArmWithItem} call and {@link ComputeFovModifierEvent} with the modifier vanilla is about to
 * interpolate, so both reach the shared {@link ElectricBowFirstPerson} at the same place as on Fabric.
 */
public final class NeoForgeElectricBowHooks {

	private NeoForgeElectricBowHooks() {
	}

	/** Adds both listeners to the game bus (client only). */
	public static void register() {
		NeoForge.EVENT_BUS.addListener((RenderHandEvent event) -> {
			if (ElectricBowFirstPerson.skipsHand(event.getHand())) {
				event.setCanceled(true);
			}
		});
		NeoForge.EVENT_BUS.addListener((ComputeFovModifierEvent event) ->
				event.setNewFovModifier(ElectricBowFirstPerson.newFovModifier(event.getPlayer(),
						event.getFovModifier(), event.getFovScale(), event.getNewFovModifier())));
	}
}
