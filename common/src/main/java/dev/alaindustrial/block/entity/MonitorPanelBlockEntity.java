package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import dev.alaindustrial.core.monitor.MonitorReadout;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.block.MonitorPanelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * One panel of a monitoring wall (MOD-480).
 *
 * <p><b>Deliberately not a {@code MachineBlockEntity}.</b> A panel owns two values — the item it
 * watches and the number it shows — and nothing else: no buffer, no progress, no upgrade slots.
 * The reason is the wire, not tidiness: {@code syncBlockEntityToClient} ships a block entity's whole
 * NBT to every player watching the chunk, so a hundred-panel wall of machines would be a hundred
 * full machine packets every time a single number moved. Two fields make that packet trivial, and it
 * is only sent when the number actually changes.
 *
 * <p>The count is NOT persisted. After a load it is unknown until the core's next scan, and showing
 * a remembered number would be showing a number that was true before the world was closed.
 */
public class MonitorPanelBlockEntity extends BlockEntity {

	private ItemStack filter = ItemStack.EMPTY;
	private MonitorReadout readout = MonitorReadout.IDLE;
	private long count;
	private boolean registered;
	/** Not persisted: every load re-checks the connector flags once (see {@link #reconcileConnectors}). */
	private boolean connectorsChecked;

	public MonitorPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.MONITOR_PANEL_BE.get(), pos, state);
	}

	public ItemStack getFilter() {
		return filter;
	}

	public MonitorReadout getReadout() {
		return readout;
	}

	public long getCount() {
		return count;
	}

	/** Set (or clear) the watched item and tell the network its demand has changed. */
	public void setFilter(ItemStack stack) {
		this.filter = stack;
		if (stack.isEmpty()) {
			this.readout = MonitorReadout.IDLE;
			this.count = 0L;
		}
		updateActiveState();
		sync();
		if (level instanceof ServerLevel serverLevel) {
			MonitorNetworkManager.demandChanged(serverLevel, worldPosition);
		}
	}

	/**
	 * The core's verdict for this panel. Called once per scan; the packet only goes out when
	 * something a player can see has actually moved.
	 */
	public void publish(MonitorReadout newReadout, long newCount) {
		if (this.readout == newReadout && this.count == newCount) {
			return;
		}
		boolean readoutMoved = this.readout != newReadout;
		this.readout = newReadout;
		this.count = newCount;
		if (readoutMoved) {
			updateActiveState();
		}
		sync();
	}

	private void sync() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	public void ensureRegistered() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		reconcileConnectors(serverLevel);
		updateActiveState();
		if (registered) {
			return;
		}
		registered = true;
		MonitorNetworkManager.register(serverLevel, worldPosition);
	}

	/**
	 * Once per load, bring the connector flags (MOD-1013) in line with the neighbours.
	 *
	 * <p>A panel saved before the flags existed loads with all of them false, and
	 * {@code MonitorPanelBlock.updateShape} only runs when a neighbour changes, so an old wall would
	 * stay bare until rebuilt. Written with {@link net.minecraft.world.level.block.Block#UPDATE_CLIENTS}
	 * and only when something differs: without the neighbour bit no {@code neighborChanged} fires, so the
	 * monitor network is not rebuilt for a purely visual change. The flag is set before the write, so
	 * the write cannot bring this method back.
	 */
	private void reconcileConnectors(ServerLevel serverLevel) {
		if (connectorsChecked) {
			return;
		}
		connectorsChecked = true;
		BlockState live = serverLevel.getBlockState(worldPosition);
		if (!(live.getBlock() instanceof MonitorPanelBlock)) {
			return;
		}
		BlockState expected = MonitorPanelBlock.withConnectors(live, serverLevel, worldPosition);
		if (expected != live) {
			serverLevel.setBlock(worldPosition, expected, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * Keep the chunk model's on/off skin in step with what the panel can actually do (MOD-1013).
	 *
	 * <p>The screen and its wires glow only while the panel shows a live number. A filled panel with no
	 * core, an unpowered core or too few capacity cards stays dark: the renderer still draws its item and
	 * the mark that names the cause, so the player sees what is set and why it is not working, while a
	 * lit screen keeps meaning "this works". Written with {@code UPDATE_CLIENTS} only: the skin is
	 * visual, and a neighbour update would make the network re-sort itself after every verdict.
	 */
	private void updateActiveState() {
		if (level == null || level.isClientSide()) {
			return;
		}
		BlockState state = getBlockState();
		boolean active = !filter.isEmpty() && readout == MonitorReadout.OK;
		if (state.hasProperty(MonitorPanelBlock.ACTIVE) && state.getValue(MonitorPanelBlock.ACTIVE) != active) {
			level.setBlock(worldPosition, state.setValue(MonitorPanelBlock.ACTIVE, active),
					net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
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

	/**
	 * Hand the watched item back when the panel is broken.
	 *
	 * <p>In {@code preRemoveSideEffects} rather than the block's {@code affectNeighborsAfterRemoval}:
	 * by the time the latter runs, 26.2 has already detached the block entity and the filter would be
	 * gone with it.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel serverLevel && !filter.isEmpty()) {
			Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					filter.copy());
			filter = ItemStack.EMPTY;
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("Filter", ItemStack.OPTIONAL_CODEC, filter);
		output.putInt("Readout", readout.ordinal());
		// Written because this same call builds the client's update tag; a stale number after a world
		// load survives at most one scan interval, and the alternative is a second sync path.
		output.putLong("Count", count);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		filter = input.read("Filter", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		readout = MonitorReadout.byOrdinal(input.getIntOr("Readout", MonitorReadout.IDLE.ordinal()));
		count = input.getLongOr("Count", 0L);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return saveWithoutMetadata(provider);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
