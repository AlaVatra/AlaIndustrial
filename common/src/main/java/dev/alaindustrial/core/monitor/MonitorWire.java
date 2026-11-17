package dev.alaindustrial.core.monitor;

import java.util.Set;
import net.minecraft.core.Direction;

/**
 * A wire segment of a monitor network, as the network sees it (MOD-1014).
 *
 * <p>The network decides which containers get a channel; the wire only shows the answer. Kept as an
 * interface in {@code core} so the network does not name the block that implements it (ADR-039).
 */
public interface MonitorWire {

	/**
	 * Show the wall's channels on this segment.
	 *
	 * @param plugged the faces toward a container the wall serves: the plug is drawn there and nowhere else
	 * @param fill    how many channels the whole wall uses, {@code 0..}{@link MonitorNetwork#WIRE_CHANNELS}
	 */
	void showChannels(Set<Direction> plugged, int fill);
}
