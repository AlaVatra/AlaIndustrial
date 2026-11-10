package dev.alaindustrial.client;

import dev.alaindustrial.client.render.BlueprintResultItemModel;
import dev.alaindustrial.client.render.ModItemTintSources;
import dev.alaindustrial.client.sound.MachineHumClientHook;
import dev.alaindustrial.client.tooltip.TooltipKeysClientHook;
import java.util.List;

/**
 * The one-off client setup calls every loader makes once (MOD-962, step 2e): each is one {@link Step} of
 * {@link #STEPS}, and each loader replays the list in a single loop instead of naming the calls by hand.
 *
 * <p><b>Contract.</b> The steps run once, in list order, on the render thread during client init —
 * Fabric: {@code ClientModInitializer.onInitializeClient}; NeoForge: {@code FMLClientSetupEvent.enqueueWork};
 * MinecraftForge: {@code FMLConstructModEvent.enqueueWork} (the main thread, once every mod is constructed, still
 * inside {@code ClientModLoader.begin}), because Forge fires its {@code FMLClientSetupEvent} from inside the first
 * resource reload ({@code ClientModLoader.onResourceReload}), in parallel with the item-model parsing, and constructs
 * the mods themselves on parallel worker threads. Every one of those moments comes before the item models are
 * parsed, which is what the two item-model hooks need: they add a codec to a vanilla late-bound id mapper that the
 * reload reads when it parses the item models. No step reads a registry, so the earliest of them (Forge's) is safe.
 *
 * <p>Only direct calls belong here. A registration bound to a loader event (a screen opening, the client
 * tick, leaving a world, model baking) is a {@link ClientContentManifest} list instead.
 */
public final class ClientSetup {

	/** One setup call; {@code name} is the class and method it calls, for the parity gate and the logs. */
	public record Step(String name, Runnable run) {}

	public static final List<Step> STEPS = List.of(
			// Item tint sources and the blueprint's product item-model type (MOD-275): before the first reload.
			new Step("ModItemTintSources.register", ModItemTintSources::register),
			new Step("BlueprintResultItemModel.register", BlueprintResultItemModel::register),
			// MOD-248: the submerged-in-oil fog, inserted into vanilla's fog environment list.
			new Step("OilFogEnvironment.install", OilFogEnvironment::install),
			// The client-side machine-hum manager (looping ambient sound).
			new Step("MachineHumClientHook.register", MachineHumClientHook::register),
			// MOD-108: answers "is Shift held" for item tooltips (the pipe shows its numbers behind Shift).
			new Step("TooltipKeysClientHook.register", TooltipKeysClientHook::register));

	private ClientSetup() {}
}
