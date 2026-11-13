package dev.alaindustrial.gametest;

import dev.alaindustrial.block.TodoClipboardBlock;
import dev.alaindustrial.block.entity.TodoClipboardBlockEntity;
import dev.alaindustrial.core.todo.TodoAccess;
import dev.alaindustrial.core.todo.TodoEntry;
import dev.alaindustrial.core.todo.TodoList;
import dev.alaindustrial.core.todo.TodoListCodecs;
import dev.alaindustrial.core.todo.TodoListRules;
import dev.alaindustrial.core.todo.TodoListRules.Edit;
import dev.alaindustrial.core.todo.TodoListRules.Outcome;
import dev.alaindustrial.item.misc.TodoClipboardItem;
import dev.alaindustrial.menu.TodoClipboardMenu;
import dev.alaindustrial.menu.todo.HeldTodoClipboard;
import dev.alaindustrial.network.TodoClipboardEditPayload;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

/**
 * MOD-905 — the to-do clipboard: it hangs on all six faces and falls with its list when the support goes; the list
 * rides the item and comes back when the clipboard is hung again; the server holds every limit, the owner's rights
 * and the revision guard; the NBT keeps list and owner; shift-click and the edit payload end in the same rules.
 *
 * <p>No scenario goes through the window's snapshot: NeoForge refuses a mod payload to a mock player, and nothing
 * here opens a menu ({@code TodoClipboardMenu.broadcastChanges} is the only sender).
 */
public final class TodoClipboardScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TodoClipboardScenarios::hangsOnEveryFaceAndFallsWithItsList,
						"todo_clipboard_hangs_on_every_face_and_falls_with_its_list"),
				RosterEntry.of(TodoClipboardScenarios::listRidesTheItemAndComesBack,
						"todo_clipboard_list_rides_the_item_and_comes_back"),
				RosterEntry.of(TodoClipboardScenarios::clearedClipboardDropsStackable,
						"todo_clipboard_cleared_clipboard_drops_stackable"),
				RosterEntry.of(TodoClipboardScenarios::serverHoldsTheLimits,
						"todo_clipboard_server_holds_the_limits"),
				RosterEntry.of(TodoClipboardScenarios::accessLadderDecidesWhoTicksAndEdits,
						"todo_clipboard_access_ladder_decides_who_ticks_and_edits"),
				RosterEntry.of(TodoClipboardScenarios::staleRevisionIsRefused,
						"todo_clipboard_stale_revision_is_refused"),
				RosterEntry.of(TodoClipboardScenarios::nbtKeepsListAndOwner,
						"todo_clipboard_nbt_keeps_list_and_owner"),
				RosterEntry.of(TodoClipboardScenarios::shiftClickAndPayloadEndInTheRules,
						"todo_clipboard_shift_click_and_payload_end_in_the_rules"),
				RosterEntry.of(TodoClipboardScenarios::heldClipboardWritesOnlyTheStackItOpenedWith,
						"todo_clipboard_held_clipboard_writes_only_the_stack_it_opened_with"),
				RosterEntry.of(TodoClipboardScenarios::heldStackOfBlanksSplitsOffOneWrittenClipboard,
						"todo_clipboard_held_stack_of_blanks_splits_off_one_written_clipboard"));

		private Roster() {}
	}

	private TodoClipboardScenarios() {}

	private static final BlockPos SUPPORT = new BlockPos(2, 2, 2);
	private static final BlockPos ALONE = new BlockPos(1, 1, 1);
	private static final String OAK = "minecraft:oak_log";

	private static BlockState clipboard(AttachFace face, Direction facing) {
		return ModContent.TODO_CLIPBOARD.get().defaultBlockState()
				.setValue(TodoClipboardBlock.FACE, face).setValue(TodoClipboardBlock.FACING, facing);
	}

	/** A clipboard nobody owns at {@code pos}, hung on the floor block below it (placed by the test). */
	private static TodoClipboardBlockEntity placeAlone(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos.below(), Blocks.STONE);
		helper.setBlock(pos, clipboard(AttachFace.FLOOR, Direction.NORTH));
		return helper.getBlockEntity(pos, TodoClipboardBlockEntity.class);
	}

	private static TodoList sample() {
		TodoList list = TodoListRules.apply(TodoList.EMPTY, Edit.addItem(OAK, 32));
		list = TodoListRules.apply(list, Edit.addNote("build a generator"));
		return TodoListRules.apply(list, Edit.toggleDone(1)).withAccess(TodoAccess.OTHERS_EDIT);
	}

	private static void expect(GameTestHelper helper, Outcome expected, Outcome actual, String what) {
		if (expected != actual) {
			helper.fail(what + ": expected " + expected + ", got " + actual);
		}
	}

	/**
	 * Four walls, the floor and the ceiling of one stone all hold a clipboard; removing the stone pops all six as
	 * items, and the one that carried a list drops it on its item (loot copy_components, no explosion condition).
	 */
	public static void hangsOnEveryFaceAndFallsWithItsList(GameTestHelper helper) {
		helper.setBlock(SUPPORT, Blocks.STONE);
		BlockPos[] spots = {SUPPORT.north(), SUPPORT.south(), SUPPORT.west(), SUPPORT.east(), SUPPORT.above(),
				SUPPORT.below()};
		BlockState[] states = {clipboard(AttachFace.WALL, Direction.NORTH), clipboard(AttachFace.WALL, Direction.SOUTH),
				clipboard(AttachFace.WALL, Direction.WEST), clipboard(AttachFace.WALL, Direction.EAST),
				clipboard(AttachFace.FLOOR, Direction.NORTH), clipboard(AttachFace.CEILING, Direction.NORTH)};
		for (int i = 0; i < spots.length; i++) {
			helper.setBlock(spots[i], states[i]);
			BlockPos abs = helper.absolutePos(spots[i]);
			if (!helper.getLevel().getBlockState(abs).canSurvive(helper.getLevel(), abs)) {
				helper.fail("a clipboard at " + spots[i] + " (" + states[i] + ") cannot hold on the stone");
				return;
			}
		}
		TodoClipboardBlockEntity written = helper.getBlockEntity(SUPPORT.north(), TodoClipboardBlockEntity.class);
		written.applyComponentsFromItemStack(withList(sample()));

		helper.setBlock(SUPPORT, Blocks.AIR);
		for (BlockPos spot : spots) {
			if (!helper.getLevel().getBlockState(helper.absolutePos(spot)).isAir()) {
				helper.fail("the clipboard at " + spot + " stayed without its support");
				return;
			}
		}
		AABB box = new AABB(helper.absolutePos(SUPPORT)).inflate(2.0);
		int total = 0;
		int carryingList = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			ItemStack stack = entity.getItem();
			if (!stack.is(ModContent.TODO_CLIPBOARD_ITEM.get())) {
				continue;
			}
			total += stack.getCount();
			if (sample().equals(stack.get(ModDataComponents.TODO_LIST.get()))) {
				carryingList += stack.getCount();
			}
		}
		if (total != spots.length || carryingList != 1) {
			helper.fail("six clipboards fell: expected 6 items, 1 with the list; got " + total + " items, "
					+ carryingList + " with the list");
			return;
		}
		helper.succeed();
	}

	/**
	 * A written clipboard puts its list and a stack size of 1 on its item; a blank one puts neither (it stacks to
	 * 16); hanging the written item again restores the very same list.
	 */
	public static void listRidesTheItemAndComesBack(GameTestHelper helper) {
		TodoClipboardBlockEntity blank = placeAlone(helper, ALONE);
		DataComponentMap blankComponents = blank.collectComponents();
		if (blankComponents.has(ModDataComponents.TODO_LIST.get())
				|| blankComponents.has(DataComponents.MAX_STACK_SIZE)) {
			helper.fail("a blank clipboard wrote a list or a stack size onto its item: " + blankComponents);
			return;
		}
		if (new ItemStack(ModContent.TODO_CLIPBOARD_ITEM.get()).getMaxStackSize() != 16) {
			helper.fail("a blank clipboard does not stack to 16");
			return;
		}
		blank.applyComponentsFromItemStack(withList(sample()));
		DataComponentMap written = blank.collectComponents();
		if (!sample().equals(written.get(ModDataComponents.TODO_LIST.get()))
				|| !Integer.valueOf(1).equals(written.get(DataComponents.MAX_STACK_SIZE))) {
			helper.fail("a written clipboard did not carry its list with a stack size of 1: " + written);
			return;
		}
		ItemStack dropped = new ItemStack(ModContent.TODO_CLIPBOARD_ITEM.get());
		dropped.applyComponents(written);
		TodoClipboardBlockEntity again = placeAlone(helper, ALONE.east(2));
		again.applyComponentsFromItemStack(dropped);
		if (!sample().equals(again.list())) {
			helper.fail("the list hung again differs: " + again.list());
			return;
		}
		helper.succeed();
	}

	/**
	 * A written clipboard hung from its real drop (list plus stack size 1), then cleared and broken, drops a blank
	 * item that stacks to 16 again. The stack size the item brought must not outlive the list: a component the
	 * block entity does not read is kept by vanilla and copied back onto the drop.
	 */
	public static void clearedClipboardDropsStackable(GameTestHelper helper) {
		ItemStack written = withList(sample());
		written.set(DataComponents.MAX_STACK_SIZE, 1);
		TodoClipboardBlockEntity board = placeAlone(helper, ALONE);
		board.applyComponentsFromItemStack(written);
		board.replaceList(TodoList.EMPTY);
		BlockPos abs = helper.absolutePos(ALONE);
		helper.getLevel().destroyBlock(abs, true);
		ItemStack drop = ItemStack.EMPTY;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2.0))) {
			if (entity.getItem().is(ModContent.TODO_CLIPBOARD_ITEM.get())) {
				drop = entity.getItem();
			}
		}
		if (drop.isEmpty()) {
			helper.fail("the cleared clipboard dropped nothing");
			return;
		}
		if (drop.has(ModDataComponents.TODO_LIST.get()) || drop.getMaxStackSize() != 16) {
			helper.fail("a cleared clipboard dropped a list or a stack size of " + drop.getMaxStackSize()
					+ " instead of 16: " + drop.getComponentsPatch());
			return;
		}
		helper.succeed();
	}

	/**
	 * The server keeps the owner's limits whatever the window sends: an 11th line, an unknown or empty id, air, a
	 * count out of range, a 41-character note with control characters. A short id is stored in its full form.
	 */
	public static void serverHoldsTheLimits(GameTestHelper helper) {
		TodoClipboardBlockEntity board = placeAlone(helper, ALONE);
		ServerPlayer player = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		expect(helper, Outcome.INVALID, board.apply(player, Edit.addItem("minecraft:not_an_item", 3),
				board.revision()), "an unknown item id");
		expect(helper, Outcome.INVALID, board.apply(player, Edit.addItem("minecraft:air", 3), board.revision()),
				"air");
		expect(helper, Outcome.INVALID, board.apply(player, Edit.addItem("::bad id::", 3), board.revision()),
				"an id that does not parse");
		expect(helper, Outcome.APPLIED, board.apply(player, Edit.addItem("stone", 0), board.revision()),
				"a short id with count 0");
		TodoEntry first = board.list().entries().get(0);
		if (!"minecraft:stone".equals(first.item()) || first.count() != 1) {
			helper.fail("the short id and the zero count were not normalised: " + first);
			return;
		}
		expect(helper, Outcome.APPLIED, board.apply(player, Edit.addNote("§c" + "x".repeat(45) + "\n"),
				board.revision()), "a long note");
		String note = board.list().entries().get(1).note();
		if (note.length() != TodoListRules.MAX_NOTE || note.indexOf('§') >= 0 || note.indexOf('\n') >= 0) {
			helper.fail("the note was not cleaned and cut to " + TodoListRules.MAX_NOTE + ": '" + note + "'");
			return;
		}
		expect(helper, Outcome.APPLIED, board.apply(player, Edit.setCount(0, 99_999), board.revision()),
				"a huge count");
		if (board.list().entries().get(0).count() != TodoListRules.MAX_COUNT) {
			helper.fail("a count of 99999 was not pulled down to " + TodoListRules.MAX_COUNT);
			return;
		}
		while (board.list().size() < TodoListRules.MAX_ENTRIES) {
			expect(helper, Outcome.APPLIED, board.apply(player, Edit.addItem(OAK, 1), board.revision()), "filling");
		}
		expect(helper, Outcome.INVALID, board.apply(player, Edit.addItem(OAK, 1), board.revision()), "an 11th line");
		expect(helper, Outcome.INVALID, board.apply(player, Edit.remove(TodoListRules.MAX_ENTRIES), board.revision()),
				"a line past the end");
		if (board.list().size() != TodoListRules.MAX_ENTRIES) {
			helper.fail("the list holds " + board.list().size() + " lines, not " + TodoListRules.MAX_ENTRIES);
			return;
		}
		helper.succeed();
	}

	/**
	 * The access ladder on a hung clipboard, every step × (owner, guest) × (tick, change a line), through the
	 * server's own edit path: the owner always; a guest ticks from "others tick" up and adds a line only at
	 * "others edit"; only the owner moves the step; a clipboard nobody owns is open to all. A refused edit must
	 * leave the list as it was.
	 */
	public static void accessLadderDecidesWhoTicksAndEdits(GameTestHelper helper) {
		TodoClipboardBlockEntity board = placeAlone(helper, ALONE);
		ServerPlayer owner = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		ServerPlayer guest = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		if (owner.getUUID().equals(guest.getUUID())) {
			helper.fail("the two mock players share a UUID; the rights cannot be told apart");
			return;
		}
		expect(helper, Outcome.APPLIED, board.apply(guest, Edit.addItem(OAK, 2), board.revision()),
				"anyone on an ownerless clipboard");
		board.setOwner(owner.getUUID(), "Owner");
		// step, guest may tick, guest may edit
		Object[][] table = {{TodoAccess.OWNER_ONLY, false, false}, {TodoAccess.OTHERS_TICK, true, false},
				{TodoAccess.OTHERS_EDIT, true, true}};
		for (Object[] row : table) {
			TodoAccess step = (TodoAccess) row[0];
			expect(helper, Outcome.DENIED, board.apply(guest, Edit.setAccess(step), board.revision()),
					"a guest moving the step to " + step);
			expect(helper, Outcome.APPLIED, board.apply(owner, Edit.setAccess(step), board.revision()),
					"the owner moving the step to " + step);
			if (!checkRights(helper, board, owner, step, true, true)
					|| !checkRights(helper, board, guest, step, (Boolean) row[1], (Boolean) row[2])) {
				return;
			}
		}
		helper.succeed();
	}

	/** One player's tick and line edit on one step: applied and undone when allowed, refused and harmless when not. */
	private static boolean checkRights(GameTestHelper helper, TodoClipboardBlockEntity board, ServerPlayer who,
			TodoAccess step, boolean mayTick, boolean mayEdit) {
		String name = (board.owner() != null && board.owner().equals(who.getUUID()) ? "owner" : "guest") + " at "
				+ step;
		TodoList before = board.list();
		Outcome tick = board.apply(who, Edit.toggleDone(0), board.revision());
		expect(helper, mayTick ? Outcome.APPLIED : Outcome.DENIED, tick, name + " ticking");
		if (tick == Outcome.APPLIED) {
			board.apply(who, Edit.toggleDone(0), board.revision());
		}
		Outcome add = board.apply(who, Edit.addNote("from " + name), board.revision());
		expect(helper, mayEdit ? Outcome.APPLIED : Outcome.DENIED, add, name + " adding a line");
		if (add == Outcome.APPLIED) {
			board.apply(who, Edit.remove(board.list().size() - 1), board.revision());
		}
		if (!before.equals(board.list())) {
			helper.fail(name + ": the list changed under the rights walk: " + before + " -> " + board.list());
			return false;
		}
		return true;
	}

	/** An edit made against an older list is refused and changes nothing; the current revision goes through. */
	public static void staleRevisionIsRefused(GameTestHelper helper) {
		TodoClipboardBlockEntity board = placeAlone(helper, ALONE);
		ServerPlayer player = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		int seen = board.revision();
		expect(helper, Outcome.APPLIED, board.apply(player, Edit.addItem(OAK, 1), seen), "the first edit");
		expect(helper, Outcome.STALE, board.apply(player, Edit.toggleDone(0), seen), "an edit on the old revision");
		if (board.list().entries().get(0).done()) {
			helper.fail("a stale edit ticked the line");
			return;
		}
		expect(helper, Outcome.APPLIED, board.apply(player, Edit.toggleDone(0), board.revision()),
				"the same edit on the current revision");
		helper.succeed();
	}

	/** Saving and loading the block entity keeps the list, the owner and the owner's name. */
	public static void nbtKeepsListAndOwner(GameTestHelper helper) {
		TodoClipboardBlockEntity board = placeAlone(helper, ALONE);
		board.applyComponentsFromItemStack(withList(sample()));
		UUID owner = UUID.randomUUID();
		board.setOwner(owner, "Planner");
		RegistryAccess registries = helper.getLevel().registryAccess();
		CompoundTag tag = board.saveCustomOnly(registries);
		BlockPos abs = helper.absolutePos(ALONE);
		TodoClipboardBlockEntity restored = new TodoClipboardBlockEntity(abs, helper.getLevel().getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		if (!sample().equals(restored.list()) || !owner.equals(restored.owner())
				|| !"Planner".equals(restored.ownerName())) {
			helper.fail("the reload lost something: list=" + restored.list() + " owner=" + restored.owner()
					+ " name=" + restored.ownerName());
			return;
		}
		// A list saved before the access ladder carried "shared": on reads as "others edit", off as "only me".
		for (boolean shared : new boolean[] {true, false}) {
			CompoundTag old = new CompoundTag();
			old.putBoolean("shared", shared);
			TodoAccess read = TodoListCodecs.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow().access();
			if (read != TodoAccess.fromShared(shared)) {
				helper.fail("an old save with shared=" + shared + " read as " + read);
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * Shift-clicking a stack in the window adds a line with its item and count and leaves the stack where it was;
	 * the edit payload reaches the rules only for the window the player has open.
	 */
	public static void shiftClickAndPayloadEndInTheRules(GameTestHelper helper) {
		TodoClipboardBlockEntity board = placeAlone(helper, ALONE);
		ServerPlayer player = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		BlockPos abs = helper.absolutePos(ALONE);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 1.5);
		player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 48));
		TodoClipboardMenu menu = new TodoClipboardMenu(1, player.getInventory(), board);
		int hotbarFirst = 27;
		ItemStack moved = menu.quickMoveStack(player, hotbarFirst);
		if (!moved.isEmpty() || player.getInventory().getItem(0).getCount() != 48) {
			helper.fail("shift-click moved the stack: returned " + moved + ", left "
					+ player.getInventory().getItem(0));
			return;
		}
		if (board.list().size() != 1 || !TodoEntry.item("minecraft:cobblestone", 48).equals(board.list().entries()
				.get(0))) {
			helper.fail("shift-click did not add 48 cobblestone: " + board.list());
			return;
		}
		player.containerMenu = menu;
		TodoClipboardEditPayload.handle(TodoClipboardEditPayload.of(menu.containerId + 1, board.revision(),
				Edit.toggleDone(0)), player);
		if (board.list().entries().get(0).done()) {
			helper.fail("an edit for another window id was applied");
			return;
		}
		TodoClipboardEditPayload.handle(new TodoClipboardEditPayload(menu.containerId, board.revision(), 999, 0, "",
				0, ""), player);
		TodoClipboardEditPayload.handle(TodoClipboardEditPayload.of(menu.containerId, board.revision(),
				Edit.toggleDone(0)), player);
		if (!board.list().entries().get(0).done()) {
			helper.fail("the edit payload for the open window did not tick the line: " + board.list());
			return;
		}
		helper.succeed();
	}

	/**
	 * A window opened on the clipboard in the hand writes into that very stack, and into nothing else: after the
	 * player switches to another clipboard, or the opened stack is swapped for a twin or dropped, the window is no
	 * longer valid and the edit payload changes neither stack (a deferred action writes to the item it started
	 * with).
	 */
	public static void heldClipboardWritesOnlyTheStackItOpenedWith(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		Inventory inventory = player.getInventory();
		ItemStack opened = new ItemStack(ModContent.TODO_CLIPBOARD_ITEM.get());
		ItemStack other = withList(sample());
		inventory.setItem(0, opened);
		inventory.setItem(1, other);
		inventory.setSelectedSlot(0);
		HeldTodoClipboard held = new HeldTodoClipboard(player, InteractionHand.MAIN_HAND, opened);
		TodoClipboardMenu menu = new TodoClipboardMenu(1, inventory, held);
		player.containerMenu = menu;
		TodoClipboardEditPayload.handle(TodoClipboardEditPayload.of(menu.containerId, held.revision(),
				Edit.addNote("from the hand")), player);
		TodoList written = TodoClipboardItem.listOf(opened);
		if (written.size() != 1 || !"from the hand".equals(written.entries().get(0).note())
				|| !held.list().equals(written)) {
			helper.fail("the window did not write onto the stack in the hand: " + written);
			return;
		}
		inventory.setSelectedSlot(1);
		if (menu.stillValid(player)) {
			helper.fail("the window stayed valid after the player switched to another clipboard");
			return;
		}
		TodoClipboardEditPayload.handle(TodoClipboardEditPayload.of(menu.containerId, held.revision(),
				Edit.toggleDone(0)), player);
		inventory.setSelectedSlot(0);
		inventory.setItem(0, opened.copy());
		TodoClipboardEditPayload.handle(TodoClipboardEditPayload.of(menu.containerId, held.revision(),
				Edit.clear()), player);
		inventory.setItem(0, ItemStack.EMPTY);
		TodoClipboardEditPayload.handle(TodoClipboardEditPayload.of(menu.containerId, held.revision(),
				Edit.addNote("after the drop")), player);
		if (!sample().equals(TodoClipboardItem.listOf(other)) || !written.equals(TodoClipboardItem.listOf(opened))) {
			helper.fail("an edit after the switch, the swap or the drop reached a stack: other="
					+ TodoClipboardItem.listOf(other) + " opened=" + TodoClipboardItem.listOf(opened));
			return;
		}
		helper.succeed();
	}

	/**
	 * Writing on a stack of blank clipboards in the hand leaves one written clipboard in the hand (stack size 1) and
	 * puts the other blanks back into the inventory, still stacking to 16; nothing is lost or duplicated.
	 */
	public static void heldStackOfBlanksSplitsOffOneWrittenClipboard(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		Inventory inventory = player.getInventory();
		ItemStack blanks = new ItemStack(ModContent.TODO_CLIPBOARD_ITEM.get(), 16);
		inventory.setItem(0, blanks);
		inventory.setSelectedSlot(0);
		HeldTodoClipboard held = new HeldTodoClipboard(player, InteractionHand.MAIN_HAND, blanks);
		if (held.apply(player, Edit.addItem(OAK, 8), held.revision()) != Outcome.APPLIED) {
			helper.fail("the holder could not write on the stack in the hand");
			return;
		}
		ItemStack hand = player.getMainHandItem();
		if (hand != blanks || hand.getCount() != 1 || hand.getMaxStackSize() != 1
				|| TodoClipboardItem.listOf(hand).size() != 1) {
			helper.fail("the hand should hold one written clipboard: " + hand + " " + hand.getComponentsPatch());
			return;
		}
		int blank = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack != hand && stack.is(ModContent.TODO_CLIPBOARD_ITEM.get())) {
				if (stack.has(ModDataComponents.TODO_LIST.get()) || stack.getMaxStackSize() != 16) {
					helper.fail("a returned blank carries a list or a stack size of " + stack.getMaxStackSize());
					return;
				}
				blank += stack.getCount();
			}
		}
		if (blank != 15) {
			helper.fail("15 blank clipboards should be back in the inventory, found " + blank);
			return;
		}
		held.apply(player, Edit.clear(), held.revision());
		if (hand.has(ModDataComponents.TODO_LIST.get()) || hand.getMaxStackSize() != 16) {
			helper.fail("a cleared clipboard in the hand did not turn blank: " + hand.getComponentsPatch());
			return;
		}
		helper.succeed();
	}

	private static ItemStack withList(TodoList list) {
		ItemStack stack = new ItemStack(ModContent.TODO_CLIPBOARD_ITEM.get());
		stack.set(ModDataComponents.TODO_LIST.get(), list);
		return stack;
	}
}
