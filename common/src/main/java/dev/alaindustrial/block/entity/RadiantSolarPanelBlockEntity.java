package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.WithdrawnSolarBlocks;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Block entity of the withdrawn Mirror Concentrator (MOD-1031). It lives for one tick: it loads the charge, the
 * owner and the upgrade chips a pre-withdrawal save holds, and on its first server tick hands all of them to the
 * Reinforced Solar Panel that replaces it ({@link WithdrawnSolarBlocks#convertConcentrator}).
 *
 * <p>The same slots (four upgrade arms, no machine slot) and the same buffer as the panel it turns into, so
 * nothing is clamped and every chip lands in the arm it sat in.
 *
 * <p><b>Still a {@link MenuProvider}, with no menu.</b> The machine base gives the four upgrade arms only to a menu
 * provider ({@code SlotLayout}); without it a pre-withdrawal save would load into a container of size 0 and drop
 * the chips it holds. The screen itself is gone (its menu type is withdrawn), so {@link #createMenu} opens nothing.
 */
public class RadiantSolarPanelBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	private static final int MAX_EXTRACT = 20;
	/** No machine slots, as when it shipped: the save holds four upgrade arms and nothing else. */
	public static final int SLOT_COUNT = 0;

	public RadiantSolarPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.RADIANT_SOLAR_PANEL_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				GeneratorConfig.reinforcedSolarBuffer, MAX_EXTRACT);
	}

	/** As shipped, until it turns: the top face is inert, the other five give EU (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == Direction.UP ? EnergyRole.NONE : EnergyRole.OUT;
	}

	/** The first tick turns the block into its successor; this block entity is gone after it. */
	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		WithdrawnSolarBlocks.convertConcentrator(level, pos);
		return 0;
	}

	/** The concentrator's screen is withdrawn with it; nothing opens in the one tick this block lives. */
	@Override
	public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return null;
	}
}
