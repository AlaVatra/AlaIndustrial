package dev.alaindustrial.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block a cable joins by carrying its own straight arm on past its cell edge into this block's cell (MOD-948): the
 * reactor antenna's boom, where the arm runs from the cable's side of the cell to the copper collar round the port.
 *
 * <p>The cable draws that piece, not this block, for the reason {@link CableArmReach} gives: only the cable knows its
 * grade, and an arm in the block's own model can only be one texture, so a gold or insulated cable used to end in a
 * copper stub. Decided from this block's state alone, like the rest of a cable's shape.
 */
public interface CableArmInlet {

	/**
	 * How far, in block pixels, a cable that joins {@code side} of this block (the world face it touches) carries its
	 * arm on into this cell; zero for none. Under half a cell, which the cable's render box allows for.
	 */
	float cableArmInlet(BlockState state, Direction side);
}
