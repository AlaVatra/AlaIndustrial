package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MobBeaconBlockEntity;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Mob Beacon (MOD-968): a machine that, while powered, makes every mob in a cube around it glow. One
 * class for the three tiers; the tier is a constructor argument, and the block entity reads it from its block.
 *
 * <p>No window: there is nothing to set (every mob glows, redstone is the off switch), so the block entity is not a
 * menu provider and a right click falls through to placement ({@link AbstractMachineBlock#useWithoutItem}). The
 * {@code lit} state shows that it runs and turns its light on. Like the repeller it has no {@code FACING}: the zone is
 * radial. Power and a cable arm go through the bottom face only.
 *
 * <p>It looks like the game's beacon in a metal frame: a glass shell over a crystal, so the player sees through it.
 * The collision box stays the full cube (so the block still occludes, R-PHY-05, and mobs path round it as round any
 * solid block), but it hides none of its neighbours' faces and lets light through, as glass does.
 */
public class MobBeaconBlock extends AbstractMachineBlock implements HasMachineTooltip {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	private final MobBeaconTier tier;

	public MobBeaconBlock(MobBeaconTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
		registerDefaultState(defaultBlockState().setValue(LIT, false));
	}

	/** Which of the three beacons this is. */
	public MobBeaconTier tier() {
		return tier;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	/**
	 * What the beacon hides of its neighbours' faces: nothing. Through the glass the player sees the faces of the
	 * blocks behind it; a full-cube occlusion shape would drop them and show the void. {@code noOcclusion()} is not
	 * the answer (R-PHY-05, MOD-776): the block still occludes, it only stops culling the faces next to it.
	 */
	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	/** Light passes through, as through the game's beacon: the crystal and plate are lit by the cell they sit in. */
	@Override
	protected int getLightDampening(BlockState state) {
		return 0;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	/** No ambient-occlusion corner shadow on the neighbours, which a solid cube would cast and glass does not. */
	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}

	/** A cable joins only the bottom face, the one face that takes power ({@link MobBeaconBlockEntity}). */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return side == Direction.DOWN;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MobBeaconBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040): upkeep, buffer and radius of its own tier. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		IntSupplier upkeep;
		IntSupplier buffer;
		IntSupplier range;
		MachineTooltipSpec.Tier tooltipTier;
		switch (tier) {
			case MV -> {
				upkeep = ServerBalance::mobBeaconEuPerTickMv;
				buffer = ServerBalance::mobBeaconBufferMv;
				range = ServerBalance::mobBeaconRangeMv;
				tooltipTier = MachineTooltipSpec.Tier.MV;
			}
			case HV -> {
				upkeep = ServerBalance::mobBeaconEuPerTickHv;
				buffer = ServerBalance::mobBeaconBufferHv;
				range = ServerBalance::mobBeaconRangeHv;
				tooltipTier = MachineTooltipSpec.Tier.HV;
			}
			default -> {
				upkeep = ServerBalance::mobBeaconEuPerTick;
				buffer = ServerBalance::mobBeaconBuffer;
				range = ServerBalance::mobBeaconRange;
				tooltipTier = MachineTooltipSpec.Tier.LV;
			}
		}
		return new MachineTooltipSpec(tooltipTier,
				List.of(MachineTooltipSpec.stat("energy_input", upkeep)),
				List.of(MachineTooltipSpec.stat("buffer", buffer), MachineTooltipSpec.plain("range", range)));
	}
}
