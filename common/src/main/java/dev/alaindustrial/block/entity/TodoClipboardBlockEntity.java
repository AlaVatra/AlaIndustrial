package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.todo.TodoList;
import dev.alaindustrial.core.todo.TodoListCodecs;
import dev.alaindustrial.core.todo.TodoListRules;
import dev.alaindustrial.menu.TodoClipboardMenu;
import dev.alaindustrial.menu.todo.TodoListSource;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The to-do clipboard's list and owner (MOD-905).
 *
 * <p><b>A plain block entity, not a machine.</b> It holds a list, an owner and nothing else: no buffer, no
 * slots, no tick — like {@code MonitorPanelBlockEntity}. It is not a {@code Container}, so no item capability is
 * derived for it; a line names an item, it never holds one.
 *
 * <p><b>Every change goes through {@link TodoListSource#apply}</b>, which checks, in order: the revision the
 * player's window showed, the player's rights and the list rules ({@link TodoListRules}). The revision is a
 * counter of this session, never saved. Nothing here sends a mod packet — the open window learns of a change
 * from its menu
 * ({@link TodoClipboardMenu#broadcastChanges}), the world from the vanilla block-entity update — so a gametest
 * may edit through a mock player on every loader.
 *
 * <p><b>The list survives the item.</b> {@link #collectImplicitComponents} writes it onto the drop (the loot
 * table copies {@code alaindustrial:todo_list} from the block entity) and {@link #applyImplicitComponents} reads
 * it back when the clipboard is hung again. The owner is not carried: whoever hangs it owns it.
 */
public class TodoClipboardBlockEntity extends BlockEntity implements MenuProvider, TodoListSource {

	private TodoList list = TodoList.EMPTY;
	private @Nullable UUID owner;
	private String ownerName = "";
	/** Bumped on every change this session; the window's edits must name the one they were made against. */
	private int revision;

	public TodoClipboardBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.TODO_CLIPBOARD_BE.get(), pos, state);
	}

	@Override
	public TodoList list() {
		return list;
	}

	@Override
	public @Nullable UUID owner() {
		return owner;
	}

	@Override
	public String ownerName() {
		return ownerName;
	}

	@Override
	public int revision() {
		return revision;
	}

	/** Vanilla's reach rule for a block's window, and the clipboard still in its place. */
	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void store(TodoList next) {
		list = next;
		changed();
	}

	/** Called by the block when a player hangs the clipboard. */
	public void setOwner(UUID uuid, String name) {
		this.owner = uuid;
		this.ownerName = name;
		changed();
	}

	/**
	 * Writes {@code value} over the list without a player — the demo stand and the screenshot rig, which
	 * build their list through {@link TodoListRules#apply} first. Cut to the wire limits like a loaded list.
	 */
	public void replaceList(TodoList value) {
		list = TodoListRules.fitWire(value);
		changed();
	}

	/** One more revision, saved, and the new list on its way to every client that sees the block. */
	private void changed() {
		revision++;
		setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new TodoClipboardMenu(syncId, inventory, this);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("TodoList", TodoListCodecs.CODEC, list);
		output.storeNullable("Owner", UUIDUtil.CODEC, owner);
		output.putString("OwnerName", ownerName);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		list = input.read("TodoList", TodoListCodecs.CODEC).orElse(TodoList.EMPTY);
		owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getStringOr("OwnerName", "");
		revision++;
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		if (!list.isEmpty()) {
			builder.set(ModDataComponents.TODO_LIST.get(), list);
			// A written clipboard does not stack: two lists cannot share one stack. A blank one keeps the item's
			// default of 16, like the fluid tank's filled/empty split.
			builder.set(DataComponents.MAX_STACK_SIZE, 1);
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter getter) {
		super.applyImplicitComponents(getter);
		TodoList carried = getter.get(ModDataComponents.TODO_LIST.get());
		list = carried == null ? TodoList.EMPTY : TodoListRules.fitWire(carried);
		// Read, not used: the stack size is derived from the list in collectImplicitComponents. A component left
		// unread is kept by vanilla and copied back onto the drop, so a cleared clipboard would drop unstackable.
		getter.get(DataComponents.MAX_STACK_SIZE);
		// Vanilla places the block first and applies the components after, so without this the client keeps the
		// blank sheet it was sent until the chunk reloads (the FluidTankBlockEntity lesson).
		changed();
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return saveWithoutMetadata(provider);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
