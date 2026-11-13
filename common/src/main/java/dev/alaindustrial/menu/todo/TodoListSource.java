package dev.alaindustrial.menu.todo;

import dev.alaindustrial.core.todo.TodoList;
import dev.alaindustrial.core.todo.TodoListRules;
import dev.alaindustrial.core.todo.TodoListRules.Edit;
import dev.alaindustrial.core.todo.TodoListRules.Op;
import dev.alaindustrial.core.todo.TodoListRules.Outcome;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Where the list a to-do clipboard window edits lives on the server (MOD-905): a clipboard hung in the world
 * ({@code TodoClipboardBlockEntity}) or one held in a hand. The window, its edit
 * payload and shift-click talk to this and nothing else, so both run the very same checks.
 *
 * <p><b>Every change goes through {@link #apply}</b>, which checks, in order: the revision the player's window
 * showed, the player's rights for that kind of edit ({@link TodoListRules#mayApply}), the item id against the
 * registry and the list rules. The revision is a counter of this session, never saved: a window acting on a
 * list that has moved since it was drawn is turned down rather than ticking the wrong line.
 */
public interface TodoListSource {

	TodoList list();

	/** Bumped on every change this session; the window's edits must name the one they were made against. */
	int revision();

	/** Who owns the list here, or {@code null} when nobody does (anyone may do everything but move the step). */
	@Nullable UUID owner();

	/** The owner's name for the window, or {@code ""}. */
	String ownerName();

	/** Whether {@code player}'s window may still edit this list (in reach, or still the stack in the hand). */
	boolean stillValid(Player player);

	/** Keeps {@code next}, bumps the revision and lets whoever shows the list know. */
	void store(TodoList next);

	/** Whether {@code player} may make an edit of kind {@code op} here. */
	default boolean may(Player player, Op op) {
		return TodoListRules.mayApply(op, owner(), player.getUUID(), list().access(), player.isSpectator());
	}

	/** Apply {@code edit} for {@code player}, made against list revision {@code expectedRevision}. */
	default Outcome apply(Player player, Edit edit, int expectedRevision) {
		if (expectedRevision != revision()) {
			return Outcome.STALE;
		}
		if (!may(player, edit.op())) {
			return Outcome.DENIED;
		}
		Edit checked = new Edit(edit.op(), edit.index(), canonicalItemId(edit.item()), edit.count(), edit.text());
		TodoList next = TodoListRules.apply(list(), checked);
		if (next == null) {
			return Outcome.INVALID;
		}
		if (!next.equals(list())) {
			store(next);
		}
		return Outcome.APPLIED;
	}

	/** The canonical id of the item {@code raw} names ({@code "stone"} → {@code "minecraft:stone"}), or {@code ""}. */
	static String canonicalItemId(String raw) {
		Identifier id = raw.isEmpty() ? null : Identifier.tryParse(raw);
		if (id == null) {
			return "";
		}
		Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
		return item == Items.AIR ? "" : BuiltInRegistries.ITEM.getKey(item).toString();
	}
}
