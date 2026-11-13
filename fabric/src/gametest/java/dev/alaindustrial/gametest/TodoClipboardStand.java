package dev.alaindustrial.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.TodoClipboardBlock;
import dev.alaindustrial.block.entity.TodoClipboardBlockEntity;
import dev.alaindustrial.client.screen.TodoClipboardScreen;
import dev.alaindustrial.core.todo.TodoAccess;
import dev.alaindustrial.core.todo.TodoList;
import dev.alaindustrial.core.todo.TodoListRules;
import dev.alaindustrial.core.todo.TodoListRules.Edit;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.gametest.visual.VisualWorld;
import dev.alaindustrial.item.misc.TodoClipboardItem;
import dev.alaindustrial.menu.TodoClipboardMenu;
import dev.alaindustrial.visual.ShotGroup;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The to-do clipboard's rig for the L3 screen frame (MOD-905): a stone block north of the cell and the clipboard
 * hung on its south face, looking at the camera, with a list that shows every kind of line — items with counts, a
 * note, a done line struck through, and a name long enough to be cut. Built through the list rules, as a player's
 * edits are, so the frame shows a list the game can actually hold.
 */
final class TodoClipboardStand {

	/** The wall frames' pad, beside the first-person pad and clear of the screen rig. */
	private static final int WALL_MIN_X = 20;
	private static final int WALL_MIN_Z = 20;

	private TodoClipboardStand() {
	}

	static void place(ServerLevel level, BlockPos at) {
		level.setBlockAndUpdate(at.north(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(at, ModContent.TODO_CLIPBOARD.get().defaultBlockState()
				.setValue(TodoClipboardBlock.FACE, AttachFace.WALL)
				.setValue(TodoClipboardBlock.FACING, Direction.SOUTH));
		if (level.getBlockEntity(at) instanceof TodoClipboardBlockEntity clipboard) {
			clipboard.replaceList(list());
		}
	}

	/**
	 * Makes the rig's player the clipboard's owner, so the window shows the access switch, and hands them 12 oak logs
	 * and 128 cobblestone, so one counter falls short ("12/32") and one is green ("128/128"). Run before the window
	 * opens; {@link #forgetViewer} takes the items back so no later frame shows them.
	 */
	static void prepareViewer(ServerLevel level, BlockPos at) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
		if (level.getBlockEntity(at) instanceof TodoClipboardBlockEntity clipboard) {
			clipboard.setOwner(player.getUUID(), player.getName().getString());
		}
		player.getInventory().add(new ItemStack(Items.OAK_LOG, 12));
		player.getInventory().add(new ItemStack(Items.COBBLESTONE, 128));
	}

	/** The owner moves the access step to "others tick", through the server's own edit path. */
	static void letOthersTick(ServerLevel level, BlockPos at) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
		if (level.getBlockEntity(at) instanceof TodoClipboardBlockEntity clipboard) {
			clipboard.apply(player, Edit.setAccess(TodoAccess.OTHERS_TICK), clipboard.revision());
		}
	}

	/** Takes back what {@link #prepareViewer} handed out. */
	static void forgetViewer(ServerLevel level) {
		Inventory inventory = level.getServer().getPlayerList().getPlayers().get(0).getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(Items.OAK_LOG) || inventory.getItem(slot).is(Items.COBBLESTONE)) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	/**
	 * The to-do clipboard's owner view (MOD-905): the owner moves the access step on the server, the frame waits for
	 * the window's snapshot to show it, and the items handed out for the counters are taken back afterwards.
	 */
	static void captureAccess(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
		server.runOnServer(mc -> letOthersTick(mc.overworld(), pos));
		context.waitFor(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?> acs
				&& acs.getMenu() instanceof TodoClipboardMenu menu
				&& menu.shownList().access() == TodoAccess.OTHERS_TICK);
		context.waitTicks(2);
		ShotRecorder.capture("gui_todo_clipboard_access", ShotGroup.GUI, "todo_clipboard", List.of(), "");
		server.runOnServer(mc -> forgetViewer(mc.overworld()));
	}

	/** The rig's list: every kind of line, a done one and a name long enough to be cut. */
	static TodoList list() {
		List<Edit> edits = new ArrayList<>(List.of(Edit.addItem("minecraft:oak_log", 32),
				Edit.addItem("minecraft:cobblestone", 128),
				Edit.addItem(Industrialization.id("tin_ingot").toString(), 16)));
		TodoClipboardItem.exampleNote("gui.alaindustrial.todo_clipboard.example.sawmill_by_the_river")
				.ifPresent(edits::add);
		edits.add(Edit.addItem("minecraft:glass", 64));
		edits.add(Edit.toggleDone(1));
		return applied(edits);
	}

	/** Two lines with short names: oak logs the viewer carries enough of, stone he has none of. */
	private static TodoList shortList() {
		return applied(List.of(Edit.addItem("minecraft:oak_log", 8), Edit.addItem("minecraft:stone", 16)));
	}

	private static TodoList applied(List<Edit> edits) {
		TodoList list = TodoList.EMPTY;
		for (Edit edit : edits) {
			TodoList next = TodoListRules.apply(list, edit);
			list = next == null ? list : next;
		}
		return list;
	}

	/** The window frames' pad, east of the battery box's. */
	private static final int WINDOW_MIN_X = 80;

	/**
	 * The clipboard's window as its owner works it (MOD-905, owner's playtest of 2026-11-12), for {@code
	 * -Pplayerviewonly} iteration: the list, the help on hover of a count and of the search button, the item search
	 * with a query typed, the way back by Escape, an empty list's hint, and the same in Russian, the language the
	 * playtest was in. Escape is checked, not only pictured: the first press must leave the search with the window
	 * still open, the second must close it — the playtest's bug was a window Escape could not close.
	 */
	static void shootWindow(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		StandPad.lay(singleplayer, WINDOW_MIN_X, WALL_MIN_Z, WINDOW_MIN_X + 8, WALL_MIN_Z + 8);
		BlockPos at = new BlockPos(WINDOW_MIN_X + 4, StandPad.BLOCK_Y + 1, WALL_MIN_Z + 3);
		server.runOnServer(mc -> place(mc.overworld(), at));
		server.runCommand("clear @p");
		server.runCommand("tp @p " + at.getX() + ".5 " + StandPad.BLOCK_Y + " " + (at.getZ() + 2) + ".5 180 0");
		VisualWorld.awaitBlockOnClient(context, at, "alaindustrial:todo_clipboard");
		server.runOnServer(mc -> prepareViewer(mc.overworld(), at));

		openWindow(context, at);
		ShotRecorder.capture("gui_todo_clipboard_window_list", ShotGroup.GUI, "todo_clipboard", List.of(), "");
		cursorAt(context, 140, 27 + 8);
		ShotRecorder.capture("gui_todo_clipboard_window_tip_count", ShotGroup.GUI, "todo_clipboard", List.of(), "");
		cursorAt(context, 117 + 8, 110 + 8);
		ShotRecorder.capture("gui_todo_clipboard_window_tip_search", ShotGroup.GUI, "todo_clipboard", List.of(), "");
		clickAt(context, 117 + 8, 110 + 8);
		type(context, "oak");
		cursorAt(context, 88, 200);
		ShotRecorder.capture("gui_todo_clipboard_window_search", ShotGroup.GUI, "todo_clipboard", List.of(), "");
		escape(context);
		if (!context.computeOnClient(mc -> mc.gui.screen() instanceof TodoClipboardScreen)) {
			throw new AssertionError("[TODO] Escape in the item search closed the window; it must go back to the list");
		}
		ShotRecorder.capture("gui_todo_clipboard_window_after_escape", ShotGroup.GUI, "todo_clipboard", List.of(),
				"");
		escape(context);
		if (context.computeOnClient(mc -> mc.gui.screen() instanceof TodoClipboardScreen)) {
			throw new AssertionError("[TODO] Escape on the list did not close the window");
		}

		server.runOnServer(mc -> {
			if (mc.overworld().getBlockEntity(at) instanceof TodoClipboardBlockEntity clipboard) {
				clipboard.replaceList(TodoList.EMPTY);
			}
		});
		openWindow(context, at);
		ShotRecorder.capture("gui_todo_clipboard_window_empty", ShotGroup.GUI, "todo_clipboard", List.of(), "");
		VisualWorld.awaitNoScreen(context);

		String original = context.computeOnClient(mc -> mc.getLanguageManager().getSelected());
		ScreensClientGameTest.setLocale(context, "ru_ru");
		// The language is set before the reload's splash has faded; a frame taken under it shows the splash.
		context.waitFor(mc -> mc.gui.overlay() == null, 600);
		context.waitTicks(10);
		openWindow(context, at);
		ShotRecorder.capture("gui_todo_clipboard_window_empty_ru_ru", ShotGroup.GUI, "todo_clipboard", List.of(),
				"");
		VisualWorld.awaitNoScreen(context);
		server.runOnServer(mc -> {
			if (mc.overworld().getBlockEntity(at) instanceof TodoClipboardBlockEntity clipboard) {
				clipboard.replaceList(list());
			}
		});
		openWindow(context, at);
		cursorAt(context, 117 + 8, 110 + 8);
		ShotRecorder.capture("gui_todo_clipboard_window_list_ru_ru", ShotGroup.GUI, "todo_clipboard", List.of(), "");
		VisualWorld.awaitNoScreen(context);
		ScreensClientGameTest.setLocale(context, original);
		server.runOnServer(mc -> forgetViewer(mc.overworld()));
	}

	/** Right-clicks the clipboard the way a player's click reaches the server, and waits for its window. */
	private static void openWindow(ClientGameTestContext context, BlockPos at) {
		context.getInput().lookAt(at);
		context.waitTicks(2);
		context.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(at), Direction.SOUTH, at, false)));
		VisualWorld.awaitContainerScreen(context, "To-do Clipboard (todo_clipboard)");
		context.waitTicks(5);
	}

	/**
	 * A click at the window's point {@code (x, y)}, handed to the screen's own {@code mouseClicked} — the method a
	 * real click reaches; the test input's mouse press does not reach a container screen (see
	 * {@code OverlayInputStands}).
	 */
	private static void clickAt(ClientGameTestContext context, int x, int y) {
		cursorAt(context, x, y);
		context.runOnClient(mc -> {
			int left = (mc.getWindow().getGuiScaledWidth() - TodoClipboardMenu.WIDTH) / 2;
			int top = (mc.getWindow().getGuiScaledHeight() - TodoClipboardMenu.HEIGHT) / 2;
			mc.gui.screen().mouseClicked(new MouseButtonEvent(left + x + 0.5, top + y + 0.5,
					new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
		});
		context.waitTicks(2);
	}

	/** Types {@code text} into whatever the open screen has focused, character by character. */
	private static void type(ClientGameTestContext context, String text) {
		context.runOnClient(mc -> text.codePoints().forEach(c -> mc.gui.screen().charTyped(new CharacterEvent(c))));
		context.waitTicks(2);
	}

	/** Escape, handed to the open screen's {@code keyPressed} as the keyboard handler hands it. */
	private static void escape(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (mc.gui.screen() != null) {
				mc.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
			}
		});
		context.waitTicks(3);
	}

	/** Puts the mouse over the point {@code (x, y)} of the open window, in the window's own pixels. */
	private static void cursorAt(ClientGameTestContext context, int x, int y) {
		double[] at = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			int left = (mc.getWindow().getGuiScaledWidth() - TodoClipboardMenu.WIDTH) / 2;
			int top = (mc.getWindow().getGuiScaledHeight() - TodoClipboardMenu.HEIGHT) / 2;
			return new double[] {(left + x + 0.5) * scale, (top + y + 0.5) * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(3);
	}

	/**
	 * Two clipboards on a wall (MOD-905), a short list and the rig's long one, seen by a player who carries 12 oak
	 * logs and 128 cobblestone: from two blocks back, then close up on each.
	 */
	static void shootWall(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		StandPad.lay(singleplayer, WALL_MIN_X, WALL_MIN_Z, WALL_MIN_X + 10, WALL_MIN_Z + 8);
		BlockPos shortAt = new BlockPos(WALL_MIN_X + 4, StandPad.BLOCK_Y + 1, WALL_MIN_Z + 3);
		BlockPos longAt = shortAt.east(2);
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			for (int x = shortAt.getX() - 2; x <= longAt.getX() + 2; x++) {
				for (int y = StandPad.BLOCK_Y; y <= StandPad.BLOCK_Y + 3; y++) {
					level.setBlockAndUpdate(new BlockPos(x, y, shortAt.getZ() - 1), Blocks.STONE.defaultBlockState());
				}
			}
			place(level, longAt);
			place(level, shortAt);
			if (level.getBlockEntity(shortAt) instanceof TodoClipboardBlockEntity clipboard) {
				clipboard.replaceList(shortList());
			}
		});
		server.runCommand("clear @p");
		server.runCommand("give @p minecraft:oak_log 12");
		server.runCommand("give @p minecraft:cobblestone 128");
		server.runCommand("tp @p " + (shortAt.getX() + 1) + ".5 " + StandPad.BLOCK_Y + " " + (shortAt.getZ() + 3)
				+ ".5 180 10");
		VisualWorld.awaitBlockOnClient(context, longAt, "alaindustrial:todo_clipboard");
		context.waitTicks(20);
		ShotRecorder.capture("world_todo_clipboard_wall_pair");
		for (BlockPos at : new BlockPos[] {shortAt, longAt}) {
			server.runCommand("tp @p " + at.getX() + ".5 " + StandPad.BLOCK_Y + " " + (at.getZ() + 1) + ".8 180 5");
			context.waitTicks(10);
			ShotRecorder.capture("world_todo_clipboard_wall_" + (at == shortAt ? "short" : "long") + "_close");
		}
		server.runCommand("clear @p");
	}
}
