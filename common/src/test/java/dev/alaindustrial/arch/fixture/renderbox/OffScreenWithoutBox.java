package dev.alaindustrial.arch.fixture.renderbox;

/** Violator: says it draws off screen and gives Forge no box. */
public class OffScreenWithoutBox implements StandInRenderer {

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}
}
