package dev.alaindustrial.client.forge;

import dev.alaindustrial.client.ElectricBowFirstPerson;
import java.util.function.Predicate;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderHandEvent;

/**
 * The Electric Bow's first-person draw on Forge (MOD-363, MOD-961): the other hand hidden and the bow's zoom, through
 * the loader's own events instead of the two Fabric mixins. Forge fires {@link RenderHandEvent} right before each
 * {@code submitArmWithItem} call and {@link ComputeFovModifierEvent} with the modifier vanilla is about to
 * interpolate, so both reach the shared {@link ElectricBowFirstPerson} at the same place as on Fabric and NeoForge.
 */
public final class ForgeElectricBowHooks {

	private ForgeElectricBowHooks() {
	}

	/** Adds both listeners (client only). A hand listener that returns true cancels vanilla's drawing of the hand. */
	public static void register() {
		RenderHandEvent.BUS.addListener((Predicate<RenderHandEvent>) event ->
				ElectricBowFirstPerson.skipsHand(event.getHand()));
		ComputeFovModifierEvent.BUS.addListener(event ->
				event.setNewFovModifier(ElectricBowFirstPerson.newFovModifier(event.getPlayer(),
						event.getFovModifier(), event.getScale(), event.getNewFovModifier())));
	}
}
