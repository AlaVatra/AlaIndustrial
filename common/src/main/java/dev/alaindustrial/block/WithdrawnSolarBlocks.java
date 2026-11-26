package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.machine.EvolutionHelper;
import dev.alaindustrial.registry.ModContent;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the withdrawn Mirror Concentrator and its Concentrator Section turn into (MOD-1031).
 *
 * <p>The two blocks stay registered so a world saved before the withdrawal loads, and each turns itself into its
 * successor the first time the world touches it: the concentrator becomes the day branch's third rung, the
 * Reinforced Solar Panel, with its charge, its owner and its upgrade chips. A section is refunded as one machine
 * casing: a loose one turns into the casing block in place, but one that was part of an assembled structure leaves
 * its cell empty and drops the casing as an item — the top section stood right over the core, and a casing block
 * there would shade the new panel to nothing. Nothing is lost; the T3 buffer is the concentrator's 16 000 EU.
 */
public final class WithdrawnSolarBlocks {
	private WithdrawnSolarBlocks() {
	}

	/** Replace the concentrator at {@code pos} with the Reinforced Solar Panel, keeping charge, owner and chips. */
	public static void convertConcentrator(Level level, BlockPos pos) {
		if (level.isClientSide()) {
			return;
		}
		Block target = ModContent.REINFORCED_SOLAR_PANEL.get();
		if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
			// Every slot travels to the same index: both blocks have no machine slot, so slot i is upgrade arm i
			// on each. EvolutionHelper empties the source slots first, so the swap drops nothing on the ground.
			Map<Integer, ItemStack> slots = new LinkedHashMap<>();
			for (int slot = 0; slot < machine.getContainerSize(); slot++) {
				ItemStack stack = machine.getItem(slot);
				if (!stack.isEmpty()) {
					slots.put(slot, stack.copy());
				}
			}
			EvolutionHelper.evolveInto(machine, level, pos, target, slots);
		} else {
			level.setBlockAndUpdate(pos, target.defaultBlockState());
		}
	}

	/**
	 * Refund the section at {@code pos} as one machine casing: a loose section becomes the casing block in place, a
	 * section of an assembled structure becomes air and drops the casing item there. The drop ignores the block
	 * drop game rule on purpose — it is the player's material, like the contents of a broken machine — and the
	 * loot table is not involved, so the casing is paid exactly once. A cell that is no longer a section (a second
	 * tick for the same cell) is left alone.
	 */
	public static void convertSection(Level level, BlockPos pos) {
		if (level.isClientSide()) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModContent.CONCENTRATOR_SECTION.get())) {
			return;
		}
		if (!wasAssembled(state)) {
			level.setBlockAndUpdate(pos, ModContent.MACHINE_CASING.get().defaultBlockState());
			return;
		}
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
				new ItemStack(ModContent.MACHINE_CASING_ITEM.get()));
	}

	/** Whether the section was saved as a cell of an assembled concentrator rather than a loose block. */
	static boolean wasAssembled(BlockState section) {
		return section.getValue(ConcentratorSectionBlock.PART) != ConcentratorPart.LOOSE;
	}
}
