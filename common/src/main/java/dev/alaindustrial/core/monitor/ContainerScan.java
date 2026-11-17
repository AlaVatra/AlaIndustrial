package dev.alaindustrial.core.monitor;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.AbstractModChestBlock;
import dev.alaindustrial.core.energy.PosOrder;
import dev.alaindustrial.loot.PendingLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

/**
 * Counting somebody else's storage without touching it (MOD-480). The resolution order is lifted
 * from the stock display frame (MOD-066), because the same three hazards apply and each of them
 * has already cost this mod a bug:
 *
 * <ol>
 *   <li><b>Pending loot first.</b> Every read path below ends in {@code Container.getItem}, and on a
 *       container whose loot table has not been rolled yet that unpacks the table with no player in
 *       context — emptying another mod's loot chest for good (ADR-010, MOD-524). A container in that
 *       state reads as "nothing here", which is also the honest answer: until somebody opens it, it
 *       holds nothing to count.</li>
 *   <li><b>Double chests are resolved positionally, before combining.</b> {@code CompoundContainer}
 *       hides which half is which, so the pending-loot question has to be asked first.</li>
 *   <li><b>Vanilla {@code Container} is not the whole world.</b> Mod storages that expose only their
 *       loader's capability never implement it, so the fallback goes through
 *       {@link ItemViewLookup}.</li>
 *   <li><b>Only storage, never machines (MOD-1014).</b> A furnace, a hopper or a generator also exposes
 *       an inventory, and a wall that counted them would fill its channels with machines. Readable means
 *       the block is in {@link #READABLE} AND a container or an item view is found there.</li>
 * </ol>
 */
public final class ContainerScan {

	/**
	 * The blocks a monitor wall reads (MOD-1014): chests, barrels, shulker boxes and the mod's own
	 * storage, plus the conventional chest and barrel tags for other mods' chests. A datapack extends it.
	 */
	public static final TagKey<Block> READABLE = TagKey.create(Registries.BLOCK,
			Industrialization.id("monitor_readable"));

	private ContainerScan() {
	}

	/**
	 * Add the contents at {@code pos} into {@code sink}.
	 *
	 * <p>An unloaded chunk is skipped rather than loaded: forcing a synchronous chunk load once per
	 * scan per wired container is how a monitoring wall would turn into a server stall.
	 *
	 * @return whether anything readable was found there
	 */
	public static boolean tallyAt(ServerLevel level, BlockPos pos, StockTally sink) {
		if (sink.isEmpty() || !level.isLoaded(pos) || !level.getBlockState(pos).is(READABLE)) {
			return false;
		}
		Container container = resolveContainer(level, pos);
		if (container != null) {
			tallyContainer(container, sink);
			return true;
		}
		ItemView view = ItemViewLookup.get().find(level, pos, null);
		if (view == null) {
			return false;
		}
		view.tally(sink);
		return true;
	}

	/**
	 * Whether {@code pos} holds storage this system reads: a block in {@link #READABLE} with a container
	 * or an item view behind it. Decides which neighbours of a wire can take a channel.
	 */
	public static boolean isReadable(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !level.getBlockState(pos).is(READABLE)) {
			return false;
		}
		return resolveContainer(level, pos) != null
				|| ItemViewLookup.get().find(level, pos, null) != null;
	}

	/**
	 * The one position that stands for the container at {@code pos} when the wall counts its channels
	 * (MOD-1014): a double chest is ONE channel, whichever half or halves a wire touches.
	 *
	 * <p>Read from the vanilla chest-type and facing properties alone, the same partner rule vanilla and
	 * the mod's own chests apply, so any chest that pairs the vanilla way pairs here too. The partner has
	 * to point back: a half whose neighbour does not name it as its partner is a channel of its own.
	 *
	 * @return the lower of the two halves in geometric order for a double chest, otherwise {@code pos}
	 */
	public static BlockPos channelKey(ServerLevel level, BlockPos pos) {
		BlockPos partner = chestPartner(pos, level.getBlockState(pos));
		if (partner == null || !level.isLoaded(partner)) {
			return pos;
		}
		BlockState partnerState = level.getBlockState(partner);
		if (partnerState.getBlock() != level.getBlockState(pos).getBlock()
				|| !pos.equals(chestPartner(partner, partnerState))) {
			return pos;
		}
		return PosOrder.compare(pos.getX(), pos.getY(), pos.getZ(), partner.getX(), partner.getY(),
				partner.getZ()) <= 0 ? pos : partner.immutable();
	}

	private static @Nullable BlockPos chestPartner(BlockPos pos, BlockState state) {
		if (!state.hasProperty(BlockStateProperties.CHEST_TYPE)
				|| !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return null;
		}
		ChestType type = state.getValue(BlockStateProperties.CHEST_TYPE);
		if (type == ChestType.SINGLE) {
			return null;
		}
		Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		return pos.relative(type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
	}

	private static void tallyContainer(Container container, StockTally sink) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			int index = sink.indexOf(stack);
			if (index >= 0) {
				sink.add(index, stack.getCount());
			}
		}
	}

	private static @Nullable Container resolveContainer(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (PendingLoot.isPendingAt(level, pos, state)) {
			return null;
		}
		if (state.getBlock() instanceof ChestBlock chest) {
			return ChestBlock.getContainer(chest, state, level, pos, false);
		}
		if (state.getBlock() instanceof AbstractModChestBlock chest) {
			return chest.combinedContainer(state, level, pos);
		}
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof Container container ? container : null;
	}
}
