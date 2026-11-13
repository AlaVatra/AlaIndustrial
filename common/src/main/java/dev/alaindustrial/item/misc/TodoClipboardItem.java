package dev.alaindustrial.item.misc;

import dev.alaindustrial.client.tooltip.ItemTooltip;
import dev.alaindustrial.core.todo.TodoEntry;
import dev.alaindustrial.core.todo.TodoList;
import dev.alaindustrial.core.todo.TodoListRules.Edit;
import dev.alaindustrial.core.tooltip.TooltipRole;
import dev.alaindustrial.menu.todo.HeldTodoClipboard;
import dev.alaindustrial.menu.TodoClipboardMenu;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The to-do clipboard as an item (MOD-905): a blank one stacks to 16, a written one carries its list in
 * {@code alaindustrial:todo_list} and says how far it has got — "Done 2 of 5", the lines themselves under
 * [Shift].
 *
 * <p><b>A right click in the air opens the list's window for the clipboard in the hand</b> (owner decision
 * 2026-11-12): the holder writes on it, and the window writes into exactly that stack ({@link HeldTodoClipboard}).
 * A right click on a block still hangs it.
 *
 * <p>Also the one place that turns a line into words, so the window, the sheet on the wall and this tooltip
 * read a line the same way ({@link #lineText}).
 */
public class TodoClipboardItem extends BlockItem {

	private static final String KEY = "tooltip.alaindustrial.todo_clipboard.";

	public TodoClipboardItem(Block block, Properties properties) {
		super(block, properties);
	}

	// Soft-deprecated by vanilla but still the hook for per-stack lines (see FluidTankBlockItem, MOD-498).
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		TodoList list = stack.get(ModDataComponents.TODO_LIST.get());
		if (list == null || list.isEmpty()) {
			return;
		}
		ItemTooltip tooltip = new ItemTooltip().info(KEY + "progress", list.doneCount(), list.size());
		for (TodoEntry entry : list.entries()) {
			tooltip.detail(entry.done() ? TooltipRole.STATE_ON : TooltipRole.STATE_OFF, KEY + "entry", null,
					lineText(entry));
		}
		tooltip.emit(adder);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			HeldTodoClipboard held = new HeldTodoClipboard(player, hand, player.getItemInHand(hand));
			player.openMenu(new SimpleMenuProvider((syncId, inventory, who) -> new TodoClipboardMenu(syncId, inventory,
					held), player.getItemInHand(hand).getHoverName()));
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * A note written from lang key {@code key} in the server's language, for a list the mod writes itself (the demo
	 * stand, the screenshot rig): player-facing text only ever comes from a translation. A note is text, not a key,
	 * so it is resolved here; where the server has no translation (a Fabric dedicated server knows only vanilla's
	 * English) the note is left out rather than written as its key.
	 */
	public static Optional<Edit> exampleNote(String key) {
		Language language = Language.getInstance();
		return language.has(key) ? Optional.of(Edit.addNote(language.getOrDefault(key))) : Optional.empty();
	}

	/** The list written on {@code stack}, or a blank one. */
	public static TodoList listOf(ItemStack stack) {
		TodoList list = stack.get(ModDataComponents.TODO_LIST.get());
		return list == null ? TodoList.EMPTY : list;
	}

	/**
	 * Writes {@code list} onto {@code stack} the way a clipboard taken off a wall carries it: a written one holds the
	 * list and stacks to 1, a blank one holds neither and stacks to 16 again.
	 */
	public static void writeList(ItemStack stack, TodoList list) {
		if (list.isEmpty()) {
			stack.remove(ModDataComponents.TODO_LIST.get());
			// Back to the item's own 16, not "no stack size": setting the prototype's value drops the patch entry.
			stack.set(DataComponents.MAX_STACK_SIZE, stack.getPrototype().get(DataComponents.MAX_STACK_SIZE));
		} else {
			stack.set(ModDataComponents.TODO_LIST.get(), list);
			stack.set(DataComponents.MAX_STACK_SIZE, 1);
		}
	}

	/** {@code "32 × Oak Log"} for an item line (the id if no such item is loaded), the text for a note. */
	public static String lineText(TodoEntry entry) {
		if (entry.isNote()) {
			return entry.note();
		}
		return Component.translatable("gui.alaindustrial.todo_clipboard.item_line", entry.count(), itemName(entry))
				.getString();
	}

	/**
	 * The item's name for an item line, or its id when no such item is loaded; the note for a note. The window
	 * and the sheet show this beside the "have/need" counter, which carries the count.
	 */
	public static String itemName(TodoEntry entry) {
		if (entry.isNote()) {
			return entry.note();
		}
		ItemStack stack = stackOf(entry.item());
		return stack.isEmpty() ? entry.item() : stack.getHoverName().getString();
	}

	/** One of the item {@code id} names, or {@link ItemStack#EMPTY} for a note, an unknown id or air. */
	public static ItemStack stackOf(String id) {
		Identifier key = id.isEmpty() ? null : Identifier.tryParse(id);
		Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}
}
