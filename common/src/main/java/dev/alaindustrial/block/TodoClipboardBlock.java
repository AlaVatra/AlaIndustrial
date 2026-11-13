package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.TodoClipboardBlockEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The to-do clipboard (MOD-905): a board with a clip and a sheet, hung on any face of a block the way a lever
 * is — on a wall, on the floor or under a ceiling ({@link #FACE} and {@link #FACING}). It has no collision, like
 * a painting, and the vanilla attach rule of its parent pops it off as an item, list and all, when the block
 * holding it goes.
 *
 * <p>A right click with any hand opens the list's window (the held item falls through to
 * {@link #useWithoutItem}); the list, its owner and the edit rules live in {@link TodoClipboardBlockEntity}.
 */
public class TodoClipboardBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {

	/**
	 * The outline of a clipboard hung on a wall facing north: 12 px wide, the full 16 px tall, 2 px off the
	 * wall (board, sheet and clip). One table for every face and facing, built once at class load (ADR-023).
	 */
	private static final Map<AttachFace, Map<Direction, VoxelShape>> SHAPES =
			Shapes.rotateAttachFace(Block.boxZ(12.0, 16.0, 14.0, 16.0));

	/**
	 * The blockstate's model rotation {@code {x, y}} in degrees for {@code state}: the model is drawn lying on
	 * the floor with its clip at +Z, and these turn it onto its face. The same table is generated into
	 * {@code blockstates/todo_clipboard.json} by {@code tools/model_sources/todo_clipboard/build_todo_clipboard.py}
	 * ({@code BLOCKSTATE_ROTATIONS}); the renderer writes the list in that frame, so the two change together.
	 */
	public static int[] modelRotation(BlockState state) {
		int facingY = switch (state.getValue(FACING)) {
			case EAST -> 90;
			case SOUTH -> 180;
			case WEST -> 270;
			default -> 0;
		};
		return switch (state.getValue(FACE)) {
			case WALL -> new int[] {90, facingY};
			// On a floor or a ceiling the page's top points away from whoever hung it.
			case FLOOR -> new int[] {0, (facingY + 180) % 360};
			case CEILING -> new int[] {180, facingY};
		};
	}

	public TodoClipboardBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.WALL));
	}

	/**
	 * The codec Minecraft 26.2 asks every block for (abstract on its {@code FaceAttachedHorizontalDirectionalBlock};
	 * 26.3 removed block codecs). No {@code @Override} on purpose, as in {@code MonitorPanelBlock}: on 26.2 it
	 * overrides, on 26.3 it is a method nothing calls, so the source is the same on both lines.
	 */
	protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
		return MapCodec.unit(this);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACE, FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACE)).get(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof TodoClipboardBlockEntity clipboard)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			player.openMenu(clipboard);
		}
		return InteractionResult.SUCCESS;
	}

	/** The player who hangs the clipboard becomes its owner; one placed by a dispenser or a command has none. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && placer instanceof Player player
				&& level.getBlockEntity(pos) instanceof TodoClipboardBlockEntity clipboard) {
			clipboard.setOwner(player.getUUID(), player.getName().getString());
		}
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TodoClipboardBlockEntity(pos, state);
	}
}
