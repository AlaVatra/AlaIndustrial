package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.SmartWireBlock;
import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import dev.alaindustrial.core.monitor.MonitorWire;
import dev.alaindustrial.registry.ModContent;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A smart wire segment (MOD-480). It holds no state of its own — the wire is a link, and everything
 * worth remembering lives in the monitor core at the end of the run.
 *
 * <p>It exists at all for one reason: something has to enrol the position in the network graph, and
 * enrolment has to survive a chunk load. The pipe does the same through {@code ensureRegistered}.
 */
public class SmartWireBlockEntity extends BlockEntity implements MonitorWire {

	private boolean registered;
	/** Not persisted: every load re-reads the faces once (see {@link #reconcileLinks}). */
	private boolean linksChecked;

	public SmartWireBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.SMART_WIRE_BE.get(), pos, state);
	}

	/** Join the graph if this segment has not yet done so. Safe to call repeatedly. */
	public void ensureRegistered() {
		if (registered || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		registered = true;
		MonitorNetworkManager.register(serverLevel, worldPosition);
	}

	public void serverTick() {
		if (level instanceof ServerLevel serverLevel) {
			reconcileLinks(serverLevel);
		}
		ensureRegistered();
	}

	/**
	 * Once per load, bring the faces in line with the neighbours (MOD-1014).
	 *
	 * <p>A wire saved before the faces became {@link dev.alaindustrial.block.WireLink} values had boolean
	 * faces, which load as the default {@code none}, and {@code updateShape} runs only when a neighbour
	 * changes, so an old run would stay bare. The wire faces come back here; the plugs and the fill come
	 * from the network, which is marked to refresh. Written with {@link Block#UPDATE_CLIENTS} and only when
	 * something differs, so no {@code neighborChanged} fires; the flag is set before the write, so the
	 * write cannot bring this method back.
	 */
	private void reconcileLinks(ServerLevel serverLevel) {
		if (linksChecked) {
			return;
		}
		linksChecked = true;
		BlockState live = serverLevel.getBlockState(worldPosition);
		if (!(live.getBlock() instanceof SmartWireBlock)) {
			return;
		}
		BlockState expected = SmartWireBlock.withLinksFromNeighbours(live, serverLevel, worldPosition);
		if (expected != live) {
			serverLevel.setBlock(worldPosition, expected, Block.UPDATE_CLIENTS);
		}
		MonitorNetworkManager.demandChanged(serverLevel, worldPosition);
	}

	/**
	 * Draw what the network decided (MOD-1014). Written with {@link Block#UPDATE_CLIENTS} only and only
	 * when the state differs: the network calls this from its own refresh, and a neighbour update would
	 * make it rebuild and refresh again.
	 */
	@Override
	public void showChannels(Set<Direction> plugged, int fill) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		BlockState live = serverLevel.getBlockState(worldPosition);
		if (!(live.getBlock() instanceof SmartWireBlock)) {
			return;
		}
		BlockState expected = SmartWireBlock.withChannels(live, serverLevel, worldPosition, plugged, fill);
		if (expected != live) {
			serverLevel.setBlock(worldPosition, expected, Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public void setRemoved() {
		if (registered && level instanceof ServerLevel serverLevel) {
			MonitorNetworkManager.unregister(serverLevel, worldPosition);
			registered = false;
		}
		super.setRemoved();
	}
}
