package dev.alaindustrial.lifecycle;

import dev.alaindustrial.item.fluid.OilBucketDispenseBehavior;
import dev.alaindustrial.item.wearable.SuitDispenseBehavior;
import java.util.List;

/**
 * The one-off common setup calls every loader makes once (MOD-962, step 2e): each is one {@link Step} of
 * {@link #STEPS}, and each loader replays the list in a single loop instead of naming the calls by hand.
 *
 * <p><b>Contract.</b> The steps run once, in list order, on the main thread, after every registry is
 * populated and {@code ModContent.verifyAllBound()} passed, before the first level loads — Fabric: at the
 * end of {@code onInitialize}; NeoForge/Forge: inside {@code FMLCommonSetupEvent.enqueueWork}. The
 * thread matters: {@code DispenserBlock.registerBehavior} writes a plain, unsynchronised map, and
 * NeoForge runs mod setup in parallel unless the work is enqueued.
 *
 * <p>Only direct calls belong here. A call bound to a loader event (a tick, a command registration, a
 * player joining) is a {@link ServerHookRoster} entry instead.
 */
public final class CommonSetup {

	/** One setup call; {@code name} is the class and method it calls, for the parity gate and the logs. */
	public record Step(String name, Runnable run) {}

	public static final List<Step> STEPS = List.of(
			// MOD-238: dispenser support for the filled oil bucket (emptying is registered per item).
			new Step("OilBucketDispenseBehavior.register", OilBucketDispenseBehavior::register),
			// MOD-535: forgiving dispenser equipping for the shielding suit.
			new Step("SuitDispenseBehavior.register", SuitDispenseBehavior::register));

	private CommonSetup() {}
}
