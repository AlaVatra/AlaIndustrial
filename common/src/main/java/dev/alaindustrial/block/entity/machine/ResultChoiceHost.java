package dev.alaindustrial.block.entity.machine;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A furnace with a result button (MOD-1018): the electric furnace and the iron furnace. Its menu reads the options
 * through this and forwards a press of the button to {@link #cycleResult}.
 */
public interface ResultChoiceHost {

	/** The furnace's chooser — its stored choice and the options of its input. */
	ResultChooser resultChooser();

	/** The stack in the input slot, whose results the button lists. */
	ItemStack resultChoiceInput();

	/** The server level the furnace stands in, or {@code null} on the client or before it is placed. */
	@Nullable ServerLevel resultChoiceLevel();

	/**
	 * One press of the button: step to the next result and, when the result the furnace runs changed, restart the
	 * operation in progress and wake the furnace.
	 */
	void cycleResult();
}
