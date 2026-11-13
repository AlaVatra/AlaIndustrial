package dev.alaindustrial.client.forge;

import dev.alaindustrial.client.SlingshotFirstPerson;
import java.util.function.Predicate;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderHandEvent;

/**
 * The slingshot's first-person draw on Forge (MOD-961): the pouch hand and the bow's zoom, through the loader's own
 * events instead of the two Fabric mixins. Forge fires {@link RenderHandEvent} right before each
 * {@code submitArmWithItem} call and {@link ComputeFovModifierEvent} with the modifier vanilla is about to
 * interpolate, so both reach the shared {@link SlingshotFirstPerson} at the same place and with the same arguments
 * as Fabric and NeoForge do.
 */
public final class ForgeSlingshotHooks {

	private ForgeSlingshotHooks() {
	}

	/** Adds both listeners (client only). A hand listener that returns true cancels vanilla's drawing of the hand. */
	public static void register() {
		RenderHandEvent.BUS.addListener((Predicate<RenderHandEvent>) event ->
				SlingshotFirstPerson.submitInsteadOfHand(event.getHand(), event.getPoseStack(),
						event.getNodeCollector(), event.getPackedLight(), event.getEquipProgress()));
		ComputeFovModifierEvent.BUS.addListener(event ->
				event.setNewFovModifier(SlingshotFirstPerson.newFovModifier(event.getPlayer(), event.getFovModifier(),
						event.getScale(), event.getNewFovModifier())));
	}
}
