package dev.alaindustrial.core.monitor;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.SmartWireBlock;
import dev.alaindustrial.block.entity.MonitorCoreBlockEntity;
import dev.alaindustrial.block.entity.MonitorPanelBlockEntity;
import dev.alaindustrial.block.entity.SmartWireBlockEntity;
import dev.alaindustrial.core.energy.PosOrder;
import dev.alaindustrial.core.net.GraphNetwork;
import dev.alaindustrial.core.net.NodeSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * One connected monitoring system (MOD-480): wires, a core, and the panels hanging off it.
 *
 * <p><b>Panels never scan.</b> The core collects what its panels are watching, walks the wired
 * containers ONCE, and hands each panel its number. The cost of a scan is therefore one pass over
 * the containers regardless of how many panels read the result — the naive shape, where every panel
 * looks for itself, multiplies the same work by the size of the wall.
 */
public final class MonitorNetwork implements GraphNetwork<MonitorNetwork, BlockPos> {

	/**
	 * How many containers one wall reads (MOD-1014). Not a config knob: the wire's blockstate and its
	 * models are built for exactly {@code 0..8} lit cores of the ribbon.
	 */
	public static final int WIRE_CHANNELS = 8;

	private static final Comparator<BlockPos> POS_ORDER =
			(a, b) -> PosOrder.compare(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());

	private final ServerLevel level;
	/** The nodes and the "sort them into roles again" flag (MOD-715, batch 11). */
	private final NodeSet<BlockPos> nodeSet = new NodeSet<>();
	private final Set<BlockPos> nodes = nodeSet.nodes();

	private final List<BlockPos> cores = new ArrayList<>();
	private final List<BlockPos> panels = new ArrayList<>();
	private final List<BlockPos> containers = new ArrayList<>();
	/** The wire segments, in geometric order, as the last refresh found them. */
	private final List<BlockPos> wires = new ArrayList<>();
	/** The channel key of every container a wire touches, served or not. */
	private final Map<BlockPos, BlockPos> keyOf = new LinkedHashMap<>();
	/** The channel keys that got a channel. */
	private final Set<BlockPos> servedKeys = new LinkedHashSet<>();

	private int cooldown;
	/**
	 * Whether the wall runs: exactly one core, and it paid its upkeep on the last scan. The wire lights its
	 * cores only while this holds, so it goes dark together with the panels (MOD-1014).
	 */
	private boolean working;
	/** The wires do not show the current channels and {@link #working} yet. */
	private boolean wiresStale;

	public MonitorNetwork(ServerLevel level) {
		this.level = level;
	}

	@Override
	public Set<BlockPos> nodes() {
		return nodes;
	}

	@Override
	public void addNode(BlockPos pos) {
		nodeSet.add(pos.immutable());
		markDirty();
	}

	@Override
	public void removeNode(BlockPos pos) {
		nodeSet.remove(pos);
		markDirty();
	}

	@Override
	public void absorb(MonitorNetwork other) {
		nodeSet.absorb(other.nodeSet);
		markDirty();
	}

	/** Re-sort the nodes on the next tick and scan at once rather than at the end of the interval. */
	@Override
	public void markDirty() {
		nodeSet.markDirty();
		cooldown = 0;
	}

	@Override
	public boolean isAwake() {
		return !nodes.isEmpty();
	}

	/**
	 * Sort the nodes into roles, find the containers the wires touch and give at most
	 * {@link #WIRE_CHANNELS} of them a channel.
	 *
	 * <p>Everything here is cached until the topology changes: this walks every node and asks the
	 * world for its block entity, which is exactly the work that must not happen per tick on a wall
	 * of several hundred panels.
	 *
	 * <p><b>Channels (MOD-1014).</b> A channel is one container as the player sees it: both halves of a
	 * double chest share one ({@link ContainerScan#channelKey}). The first {@link #WIRE_CHANNELS} channels
	 * in the stable geometric order get served, the same order that decides which panels go dark when the
	 * cards run out (ADR-006), so the answer is the same after a reload. A container left without a
	 * channel is not read at all. The answer is drawn on the wires at the end of the tick
	 * ({@link #showChannelsIfStale}): a plug on each face toward a served container and, while the wall
	 * runs, the number of used channels on every segment.
	 */
	private void refreshIfDirty() {
		if (!nodeSet.isDirty()) {
			return;
		}
		nodeSet.clearDirty();
		cores.clear();
		panels.clear();
		containers.clear();
		wires.clear();
		keyOf.clear();
		servedKeys.clear();
		Set<BlockPos> touched = new LinkedHashSet<>();
		for (BlockPos pos : nodes) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof MonitorCoreBlockEntity) {
				cores.add(pos);
			} else if (be instanceof MonitorPanelBlockEntity) {
				panels.add(pos);
			} else if (be instanceof MonitorWire) {
				wires.add(pos);
				for (Direction dir : Direction.values()) {
					BlockPos target = pos.relative(dir).immutable();
					if (!nodes.contains(target) && !touched.contains(target)
							&& ContainerScan.isReadable(level, target)) {
						touched.add(target);
					}
				}
			}
		}
		// A stable geometric order, not hash order: it decides which panels go dark when the fitted
		// cards run out and which containers get a channel, and both answers must be the same after a
		// reload (ADR-006).
		cores.sort(POS_ORDER);
		panels.sort(POS_ORDER);
		wires.sort(POS_ORDER);
		List<BlockPos> candidates = new ArrayList<>(touched);
		candidates.sort(POS_ORDER);
		Map<BlockPos, BlockPos> channels = new LinkedHashMap<>();
		for (BlockPos pos : candidates) {
			BlockPos key = ContainerScan.channelKey(level, pos);
			keyOf.put(pos, key);
			if (!channels.containsKey(key) && channels.size() < WIRE_CHANNELS) {
				channels.put(key, pos);
			}
		}
		// One position per channel: a double chest touched at both halves is read once, not twice.
		containers.addAll(channels.values());
		servedKeys.addAll(channels.keySet());
		wiresStale = true;
	}

	/**
	 * The end of every tick: record whether the wall runs and bring the wires up to date. A change of
	 * {@link #working} relights or darkens every wire even when the topology did not move.
	 *
	 * @return 0, the tick's telemetry: nothing is transported
	 */
	private long settle(boolean now) {
		if (working != now) {
			working = now;
			wiresStale = true;
		}
		showChannelsIfStale();
		return 0L;
	}

	/**
	 * Draw the served channels on every loaded wire segment, once per change of the channels or of
	 * {@link #working}, never per tick. The plugs show where the wire is joined whatever the power; the
	 * fill is the number of channels only while the wall runs, and {@code 0} otherwise. Each segment
	 * writes only what changed.
	 */
	private void showChannelsIfStale() {
		if (!wiresStale) {
			return;
		}
		wiresStale = false;
		int fill = working ? servedKeys.size() : 0;
		for (BlockPos pos : wires) {
			if (!(level.getBlockEntity(pos) instanceof MonitorWire wire)) {
				continue;
			}
			Set<Direction> plugged = EnumSet.noneOf(Direction.class);
			for (Direction dir : Direction.values()) {
				BlockPos key = keyOf.get(pos.relative(dir));
				if (key != null && servedKeys.contains(key)) {
					plugged.add(dir);
				}
			}
			wire.showChannels(plugged, fill);
		}
	}

	/**
	 * Run one scan if the interval has elapsed, then bring the wires up to date
	 * ({@link #settle}, on every way out). A wall with no panels pays no upkeep and so does
	 * not run: its wire stays dark until the first panel is hung (MOD-1014). Nothing is transported, so the
	 * frame's telemetry gets 0.
	 */
	@Override
	public long tick() {
		refreshIfDirty();
		if (panels.isEmpty()) {
			return settle(false);
		}
		if (--cooldown > 0) {
			return settle(working);
		}
		cooldown = Math.max(1, Config.monitorScanIntervalTicks);

		// Exactly one core, deliberately: two cores in one wall would each bill their own upkeep and
		// each claim the same panels, so the wall refuses to run rather than working twice over.
		if (cores.size() != 1
				|| !(level.getBlockEntity(cores.getFirst()) instanceof MonitorCoreBlockEntity core)) {
			publishAll(MonitorReadout.NO_CORE);
			return settle(false);
		}

		// The watched types, deduplicated: two panels showing the same item are one demand on the
		// cards, not two — the allowance is about types, not about screens.
		List<ItemStack> watched = new ArrayList<>();
		List<MonitorPanelBlockEntity> livePanels = new ArrayList<>();
		List<Integer> panelType = new ArrayList<>();
		for (BlockPos pos : panels) {
			if (!(level.getBlockEntity(pos) instanceof MonitorPanelBlockEntity panel)) {
				continue;
			}
			livePanels.add(panel);
			ItemStack filter = panel.getFilter();
			if (filter.isEmpty()) {
				panelType.add(-1);
				continue;
			}
			int index = indexOf(watched, filter);
			if (index < 0) {
				watched.add(filter);
				index = watched.size() - 1;
			}
			panelType.add(index);
		}

		int allowance = core.trackableTypes();
		int servedTypes = Math.min(allowance, watched.size());
		List<ItemStack> served = watched.subList(0, servedTypes);

		int servedPanels = 0;
		for (int type : panelType) {
			if (type >= 0 && type < servedTypes) {
				servedPanels++;
			}
		}

		boolean powered = core.payUpkeep(level, servedPanels);
		// The screen reads these from the core; the network is the only thing that knows them.
		core.publishStats(watched.size(), powered);

		StockTally tally = new StockTally(served);
		if (powered && !served.isEmpty()) {
			for (BlockPos container : containers) {
				ContainerScan.tallyAt(level, container, tally);
			}
		}

		for (int i = 0; i < livePanels.size(); i++) {
			MonitorPanelBlockEntity panel = livePanels.get(i);
			int type = panelType.get(i);
			if (type < 0) {
				panel.publish(MonitorReadout.IDLE, 0L);
			} else if (type >= servedTypes) {
				panel.publish(MonitorReadout.NO_CAPACITY, 0L);
			} else if (!powered) {
				panel.publish(MonitorReadout.NO_POWER, 0L);
			} else {
				panel.publish(MonitorReadout.OK, tally.total(type));
			}
		}
		return settle(powered);
	}

	/**
	 * What the wall can currently do, for the blocks' own feedback messages.
	 *
	 * @param hasCore      whether this side of the wall still has its brain
	 * @param allowance    how many types the fitted cards permit
	 * @param watchedTypes how many DIFFERENT types the panels are asking for
	 */
	public record Capacity(boolean hasCore, int allowance, int watchedTypes) {
	}

	/** Answers "why is my panel showing a cross" without waiting for the next scan. */
	public Capacity capacity() {
		refreshIfDirty();
		if (cores.size() != 1
				|| !(level.getBlockEntity(cores.getFirst()) instanceof MonitorCoreBlockEntity core)) {
			return new Capacity(false, 0, watched().size());
		}
		return new Capacity(true, core.trackableTypes(), watched().size());
	}

	/** The distinct item types the panels of this network are asking for, in panel order. */
	private List<ItemStack> watched() {
		List<ItemStack> result = new ArrayList<>();
		for (BlockPos pos : panels) {
			if (!(level.getBlockEntity(pos) instanceof MonitorPanelBlockEntity panel)) {
				continue;
			}
			ItemStack filter = panel.getFilter();
			if (!filter.isEmpty() && indexOf(result, filter) < 0) {
				result.add(filter);
			}
		}
		return result;
	}

	private void publishAll(MonitorReadout readout) {
		for (BlockPos pos : panels) {
			if (level.getBlockEntity(pos) instanceof MonitorPanelBlockEntity panel) {
				panel.publish(panel.getFilter().isEmpty() ? MonitorReadout.IDLE : readout, 0L);
			}
		}
	}

	private static int indexOf(List<ItemStack> samples, ItemStack stack) {
		for (int i = 0; i < samples.size(); i++) {
			if (ItemStack.isSameItemSameComponents(samples.get(i), stack)) {
				return i;
			}
		}
		return -1;
	}

	/** Grid connectivity for this system: wire to wire or core, core to panel, panel to panel. */
	public static boolean connects(ServerLevel level, BlockPos from, BlockPos to) {
		BlockEntity a = level.getBlockEntity(from);
		BlockEntity b = level.getBlockEntity(to);
		if (a == null || b == null) {
			return false;
		}
		boolean aWire = a instanceof SmartWireBlockEntity;
		boolean bWire = b instanceof SmartWireBlockEntity;
		boolean aCore = a instanceof MonitorCoreBlockEntity;
		boolean bCore = b instanceof MonitorCoreBlockEntity;
		boolean aPanel = a instanceof MonitorPanelBlockEntity;
		boolean bPanel = b instanceof MonitorPanelBlockEntity;
		if (aWire && (bWire || bCore)) {
			return SmartWireBlock.connectsTo(level, to);
		}
		if (aCore && (bWire || bPanel)) {
			return true;
		}
		if (aPanel && (bPanel || bCore)) {
			return true;
		}
		return false;
	}
}
