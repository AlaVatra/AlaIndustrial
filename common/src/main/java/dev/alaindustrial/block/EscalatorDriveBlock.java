package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.EscalatorDriveBlockEntity;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModSounds;
import dev.alaindustrial.sound.MachineHum;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The escalator drive (MOD-969): the bottom landing, a full block set into the floor one step down the diagonal from
 * the bottom section, that runs the whole band (owner's choice of sketch B, 2026-11-22). Its top is the floor the
 * rider steps from: the comb and hazard edge towards the band, a cyan arrow onto it, the status light. {@link #FACING}
 * is the band's climbing direction. Two tiers of one class: LV pulls up to 8 sections slowly, MV up to 16 faster; the
 * sections and the landing are shared, so upgrading swaps this one block. A cable joins it on any face.
 *
 * <p>No screen. An empty-hand click names the state above the hotbar; a wrench click flips the direction (a
 * shift-click with the wrench still unbolts the block: vanilla routes a secondary use straight to the item); a
 * redstone signal is an emergency stop.
 */
public class EscalatorDriveBlock extends AbstractMachineBlock implements MachineHumProvider, HasMachineTooltip {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<EscalatorMode> MODE = EscalatorSectionBlock.MODE;

	private final EnergyTier tier;

	public EscalatorDriveBlock(EnergyTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MODE, EscalatorMode.OFF));
	}

	public EnergyTier tier() {
		return tier;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, MODE);
	}

	/**
	 * Faces the band: the bottom section already standing one step up the diagonal, or else the way the player looks
	 * (standing at the entry, looking up the future band), as a section does.
	 */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction above = EscalatorChain.facingFromAbove(context.getLevel(), context.getClickedPos());
		return defaultBlockState().setValue(FACING, above != null ? above : context.getHorizontalDirection());
	}

	/** Someone on the bottom landing: wake the band, as stepping onto the top landing does. */
	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		super.stepOn(level, pos, state, entity);
		if (level instanceof ServerLevel server && state.getValue(MODE) == EscalatorMode.READY
				&& EscalatorCarry.rides(entity) && level.getBlockEntity(pos) instanceof EscalatorDriveBlockEntity be) {
			be.onContact(server.getGameTime());
		}
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new EscalatorDriveBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide()) {
			// The band's travel for the renderer, plus the hum loop (MOD-447, pattern C): one client ticker for both.
			MachineHum.ClientHook hook = MachineHum.CLIENT;
			return (lvl, pos, st, be) -> {
				if (be instanceof EscalatorDriveBlockEntity drive) {
					drive.clientTick(lvl, st);
				}
				if (hook != null) {
					hook.tick(lvl, pos, st);
				}
			};
		}
		return machineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.ESCALATOR_HUM;
	}

	/** Hums only while the band moves: a sleeping escalator is silent. */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return state.getValue(MODE) == EscalatorMode.RUNNING;
	}

	/** Quiet: escalators stand where people walk and talk. */
	@Override
	public float humVolume() {
		return 0.25f;
	}

	/** Empty hand: the state above the hotbar. With anything else in hand the click is the item's own. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!player.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof EscalatorDriveBlockEntity drive) {
			serverPlayer.sendOverlayMessage(drive.statusMessage());
		}
		return InteractionResult.SUCCESS;
	}

	/** A wrench click flips the direction; anything else falls through to the item. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModContent.WRENCH.get())) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof EscalatorDriveBlockEntity drive) {
			drive.reverse();
			serverPlayer.sendOverlayMessage(drive.directionMessage());
		}
		return InteractionResult.SUCCESS;
	}

	/** A redstone change reaches a sleeping drive at once, not at its next periodic check. */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EscalatorDriveBlockEntity drive) {
			drive.requestRescan();
		}
	}

	/** The drive is gone: its whole band stops, since nothing else would ever stop it. */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		if (level.getBlockState(pos).is(this)) {
			return;
		}
		Direction facing = state.getValue(FACING);
		BlockPos up = EscalatorChain.above(pos, facing);
		for (int i = 0; i < EscalatorSectionBlock.WALK_LIMIT; i++) {
			if (!EscalatorChain.isSection(level.getBlockState(up), facing)) {
				break;
			}
			EscalatorDriveBlockEntity.writeSection(level, up, EscalatorMode.OFF, null, null);
			up = EscalatorChain.above(up, facing);
		}
	}

	/** Hover tooltip of the item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		boolean mv = tier == EnergyTier.MV;
		return new MachineTooltipSpec(mv ? MachineTooltipSpec.Tier.MV : MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.plain("escalator_sections",
						mv ? ServerBalance::escalatorMaxSectionsMv : ServerBalance::escalatorMaxSectionsLv)),
				List.of(MachineTooltipSpec.stat("buffer",
						mv ? ServerBalance::escalatorBufferMv : ServerBalance::escalatorBufferLv)));
	}
}
