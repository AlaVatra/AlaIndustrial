package dev.alaindustrial.gametest.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

/**
 * The client-side mixin audit on Forge (MOD-962): Forge has no client gametest framework, so this is the one
 * automated client check. Runs only with {@code -Dalaindustrial.forgeClientAudit=true} ({@code -PforgeClientAudit},
 * which also opens a copy of the last game-test world): once the player is in a world it runs the Mixin audit for
 * the client side — every client mixin target is loaded now, so one that cannot apply fails here — and logs a
 * verdict. With {@code -Dalaindustrial.forgeClientAudit.quitAfterTicks=N} it closes the game after N ticks.
 */
final class ForgeClientAudit {

	static final String PROPERTY = "alaindustrial.forgeClientAudit";

	private static final int TICKS_IN_WORLD_BEFORE_AUDIT = 40;

	private static int ticksInWorld;
	private static boolean audited;

	private ForgeClientAudit() {
	}

	static void install() {
		if (!Boolean.getBoolean(PROPERTY)) {
			return;
		}
		TickEvent.ClientTickEvent.Post.BUS.addListener(ForgeClientAudit::onClientTick);
	}

	private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		ticksInWorld++;
		if (!audited && ticksInWorld >= TICKS_IN_WORLD_BEFORE_AUDIT) {
			audited = true;
			String verdict = "PASS";
			try {
				org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
			} catch (RuntimeException | Error e) {
				dev.alaindustrial.Industrialization.LOGGER.error("[forge-client-audit] client mixin audit threw", e);
				verdict = "FAIL " + e;
			}
			log("CLIENT in world '{}' for {} ticks, player at {}; mixin audit {}",
					minecraft.level.dimension(), ticksInWorld, minecraft.player.blockPosition(), verdict);
		}
		int quitAfter = Integer.getInteger(PROPERTY + ".quitAfterTicks", -1);
		if (quitAfter > 0 && ticksInWorld >= quitAfter) {
			log("CLIENT closing the game after {} ticks in the world", ticksInWorld);
			minecraft.stop();
		}
	}

	private static void log(String format, Object... args) {
		dev.alaindustrial.Industrialization.LOGGER.info("[forge-client-audit] " + format, args);
	}
}
