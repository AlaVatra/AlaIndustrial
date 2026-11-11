package dev.alaindustrial.core.forge;

import dev.alaindustrial.core.item.ItemHostRedirect;
import dev.alaindustrial.core.item.ItemLookup;
import dev.alaindustrial.core.item.ItemPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jspecify.annotations.Nullable;

/**
 * {@link ItemLookup} on MinecraftForge. Our own containers go through {@link ForgeCapabilities#ITEM_HANDLER} like
 * everyone else's, as on NeoForge, so our pipes see exactly what other mods see.
 *
 * <p>Two kinds of block have no block entity to carry a capability on Forge, and are read here directly: a block
 * that lends another block's inventory ({@link ItemHostRedirect}, the quarry's socket; other mods do not see it on
 * Forge) and a vanilla {@link WorldlyContainerHolder} such as the composter, which Fabric and NeoForge publish as a
 * block capability and which Forge's own hopper reads the same way.
 */
public final class ForgeItemLookup implements ItemLookup {

	@Override
	public ItemPort find(Level level, BlockPos pos, Direction side) {
		return ForgeItemPort.of(handler(level, pos, side), level, pos);
	}

	/** The item handler of the block at {@code pos} seen from {@code side}, or {@code null} for none. */
	static @Nullable IItemHandler handler(Level level, BlockPos pos, @Nullable Direction side) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ItemHostRedirect) {
			WorldlyContainer lent = ItemHostRedirect.lent(level, pos, state, side);
			return lent == null ? null : wrap(lent, side);
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null) {
			return state.getBlock() instanceof WorldlyContainerHolder holder
					? wrap(holder.getContainer(state, level, pos), side)
					: null;
		}
		return be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null);
	}

	/**
	 * A container as an item handler from {@code side}: sided when it has faces and a face is named, whole
	 * otherwise. Never a {@link SidedInvWrapper} for {@code null}: it would ask {@code getSlotsForFace(null)}, and
	 * our containers read the face there.
	 */
	static IItemHandler wrap(Container container, @Nullable Direction side) {
		return container instanceof WorldlyContainer worldly && side != null
				? new SidedInvWrapper(worldly, side)
				: new InvWrapper(container);
	}
}
