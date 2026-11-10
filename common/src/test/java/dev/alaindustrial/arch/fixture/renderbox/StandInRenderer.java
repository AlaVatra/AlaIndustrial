package dev.alaindustrial.arch.fixture.renderbox;

/**
 * Stand-in for {@code net.minecraft.client.renderer.blockentity.BlockEntityRenderer} (MOD-962): the real interface is
 * not on {@code :common}'s test classpath, so {@code RenderBoxRules.provideTheRenderBox} is aimed here by the
 * negative control. Its default {@code shouldRenderOffScreen()} plays vanilla's.
 */
public interface StandInRenderer {

	default boolean shouldRenderOffScreen() {
		return false;
	}
}
