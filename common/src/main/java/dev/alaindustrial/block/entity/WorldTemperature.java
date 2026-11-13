package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.environment.ThermoelectricOutput;
import dev.alaindustrial.registry.ModTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * The temperature of one world cell as the thermoelectric generator reads it (MOD-811): a signed level, hot
 * {@code +1..+4}, cold {@code -1..-4}, neutral 0 (see {@link ThermoelectricOutput}).
 *
 * <p><b>The table is data.</b> The levels come from the block and fluid tags
 * {@code alaindustrial:thermoelectric/hot_1..4} and {@code cold_1..4} ({@link ModTags}); nothing here names a
 * block, so a datapack adds a material without a rebuild and {@code /reload} picks it up.
 *
 * <p>Rules, in order: an unloaded cell is neutral; the block is read before the fluid it holds (a slab with
 * water inside is as cold as water); a block with a {@code lit} property counts only while lit (an unlit
 * campfire is neutral, though water in it still counts); within the block, or within the fluid, the highest
 * level wins and hot beats cold.
 */
public final class WorldTemperature {

	private WorldTemperature() {
	}

	/** The signed temperature level of the cell at {@code pos}. */
	public static int temperatureAt(Level level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return 0;
		}
		BlockState state = level.getBlockState(pos);
		if (!state.isAir()) {
			boolean unlit = state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT);
			if (!unlit) {
				int fromBlock = blockLevel(state);
				if (fromBlock != 0) {
					return fromBlock;
				}
			}
		}
		FluidState fluid = level.getFluidState(pos);
		return fluid.isEmpty() ? 0 : fluidLevel(fluid);
	}

	private static int blockLevel(BlockState state) {
		int hot = highest(state, ModTags.Blocks.THERMOELECTRIC_HOT);
		if (hot > 0) {
			return hot;
		}
		return -highest(state, ModTags.Blocks.THERMOELECTRIC_COLD);
	}

	private static int fluidLevel(FluidState fluid) {
		int hot = highestFluid(fluid, ModTags.Fluids.THERMOELECTRIC_HOT);
		if (hot > 0) {
			return hot;
		}
		return -highestFluid(fluid, ModTags.Fluids.THERMOELECTRIC_COLD);
	}

	/** The highest level whose tag holds {@code state}, or 0. */
	private static int highest(BlockState state, List<TagKey<Block>> levels) {
		for (int level = levels.size(); level >= 1; level--) {
			if (state.is(levels.get(level - 1))) {
				return level;
			}
		}
		return 0;
	}

	private static int highestFluid(FluidState fluid, List<TagKey<Fluid>> levels) {
		for (int level = levels.size(); level >= 1; level--) {
			if (fluid.is(levels.get(level - 1))) {
				return level;
			}
		}
		return 0;
	}
}
