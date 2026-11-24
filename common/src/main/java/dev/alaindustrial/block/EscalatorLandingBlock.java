package dev.alaindustrial.block;

import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The top landing of an escalator (MOD-969): a full floor block one step past the last section, with a yellow and
 * black edge on the band side. It closes the chain (a chain without one does not run) and wakes a sleeping band when
 * someone steps on it, so a player at the top can ride down without first stepping onto a moving step.
 *
 * <p>{@link #FACING} is the band's climbing direction, so the edge faces back down the band. It is set when the
 * landing is placed next to a section and kept in step by the drive's scan.
 */
public class EscalatorLandingBlock extends Block implements HasMachineTooltip {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public EscalatorLandingBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** The section it closes decides the facing; with none beside it, the way the player looks. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos section = EscalatorChain.sectionUnderLanding(context.getLevel(), context.getClickedPos());
		Direction facing = section != null
				? context.getLevel().getBlockState(section).getValue(EscalatorSectionBlock.FACING)
				: context.getHorizontalDirection();
		return defaultBlockState().setValue(FACING, facing);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** Someone at the top: wake the band below, as stepping onto its first step does at the bottom. */
	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		super.stepOn(level, pos, state, entity);
		if (!(level instanceof ServerLevel server) || !EscalatorCarry.rides(entity)) {
			return;
		}
		BlockPos section = EscalatorChain.sectionUnderLanding(level, pos);
		if (section != null && level.getBlockState(section).getValue(EscalatorSectionBlock.MODE)
				== EscalatorMode.READY) {
			EscalatorSectionBlock.wakeDrive(server, section,
					level.getBlockState(section).getValue(EscalatorSectionBlock.FACING));
		}
	}

	/** An empty-hand click names the band's state above the hotbar, or that no section leads up to the landing. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			BlockPos section = EscalatorChain.sectionUnderLanding(level, pos);
			serverPlayer.sendOverlayMessage(section == null
					? Component.translatable("message.alaindustrial.escalator.landing_alone")
					: EscalatorSectionBlock.statusAt(level, section,
							level.getBlockState(section).getValue(EscalatorSectionBlock.FACING)));
		}
		return InteractionResult.SUCCESS;
	}

	/** A new landing may complete a chain: its drive looks again at once. */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		rescanBelow(level, pos, oldState);
	}

	/** A landing gone leaves its chain open: its drive looks again and stops the band. */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		if (!level.getBlockState(pos).is(this)) {
			BlockPos section = EscalatorChain.sectionUnderLanding(level, pos);
			if (section != null) {
				EscalatorSectionBlock.rescanDrive(level, section,
						level.getBlockState(section).getValue(EscalatorSectionBlock.FACING));
			}
		}
	}

	private void rescanBelow(Level level, BlockPos pos, BlockState oldState) {
		if (level instanceof ServerLevel server && !oldState.is(this)) {
			BlockPos section = EscalatorChain.sectionUnderLanding(level, pos);
			if (section != null) {
				EscalatorSectionBlock.rescanDrive(server, section,
						level.getBlockState(section).getValue(EscalatorSectionBlock.FACING));
			}
		}
	}

	/** Hover tooltip of the item (MOD-716, ADR-040): what it is for. It takes no EU, so no tier line. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(null, List.of(), List.of());
	}
}
