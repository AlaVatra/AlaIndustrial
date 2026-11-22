package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.machine.ResultChoiceHost;
import dev.alaindustrial.network.ResultChoicePayload;
import dev.alaindustrial.network.ResultChoiceSync;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.crafting.RecipePropertySet;
import org.jspecify.annotations.Nullable;

/**
 * Menu of the iron furnace (MOD-1018): the vanilla furnace menu — the same three slots, the same four data values,
 * the furnace recipe book — under the mod's own menu type, so the client opens {@code IronFurnaceScreen} with its
 * result button instead of the stock furnace screen. Before MOD-1018 the furnace returned a vanilla
 * {@code FurnaceMenu}, which has no room for the button.
 *
 * <p>Built on {@link AbstractFurnaceMenu} with exactly the arguments the vanilla {@code FurnaceMenu} passes
 * ({@code RecipePropertySet.FURNACE_INPUT}, {@code RecipeBookType.FURNACE}), checked against the 26.3 bytecode,
 * so the slots, shift-click routing, the result slot's experience hook and the recipe book behave as before.
 */
public class IronFurnaceMenu extends AbstractFurnaceMenu implements ResultChoiceMenu {
	/** Server side: the furnace, for the result button; {@code null} on the client. */
	private final @Nullable ResultChoiceHost furnace;

	private final ResultChoiceSync resultSync;

	/** The player whose screen this is: the one viewer the options are sent to. */
	private final Player viewer;

	/** Client side. */
	public IronFurnaceMenu(int syncId, Inventory playerInventory) {
		super(ModContent.IRON_FURNACE_MENU.get(), RecipePropertySet.FURNACE_INPUT, RecipeBookType.FURNACE, syncId,
				playerInventory);
		this.furnace = null;
		this.resultSync = new ResultChoiceSync(syncId);
		this.viewer = playerInventory.player;
	}

	/** Server side. */
	public IronFurnaceMenu(int syncId, Inventory playerInventory, Container container, ContainerData data) {
		super(ModContent.IRON_FURNACE_MENU.get(), RecipePropertySet.FURNACE_INPUT, RecipeBookType.FURNACE, syncId,
				playerInventory, container, data);
		this.furnace = container instanceof ResultChoiceHost host ? host : null;
		this.resultSync = new ResultChoiceSync(syncId);
		this.viewer = playerInventory.player;
	}

	/** The result button press. Server side only; a forged id from a client gets nowhere. */
	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id != BUTTON_CYCLE_RESULT || furnace == null || !(player instanceof ServerPlayer)) {
			return super.clickMenuButton(player, id);
		}
		furnace.cycleResult();
		return true;
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		resultSync.broadcast(furnace, viewer);
	}

	@Override
	public ResultChoicePayload resultChoices() {
		return resultSync.received();
	}

	@Override
	public void acceptResultChoices(ResultChoicePayload payload) {
		resultSync.accept(payload);
	}
}
