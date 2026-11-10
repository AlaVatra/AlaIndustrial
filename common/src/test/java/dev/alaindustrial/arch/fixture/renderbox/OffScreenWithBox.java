package dev.alaindustrial.arch.fixture.renderbox;

/** Clean twin: draws off screen and provides its box. */
public class OffScreenWithBox implements StandInRenderer, StandInRenderBox {

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}
}
