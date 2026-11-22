package dev.alaindustrial.network;

import dev.alaindustrial.block.entity.machine.ResultChoiceHost;
import dev.alaindustrial.block.entity.machine.ResultChooser;
import dev.alaindustrial.recipe.ResultOptions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The per-viewer half of a furnace's result button (MOD-1018), one field of each menu that has the button: sends
 * the options as a {@link ResultChoicePayload} when they change, and keeps the last packet on the client. Server
 * and client copies are separate objects, as the menus are.
 */
public final class ResultChoiceSync {
	private final int containerId;
	/** Server side: what was last sent, as item ids, sources and the selection — {@code ItemStack} has no equals. */
	private @Nullable String lastSent;
	/** Client side: the latest packet. */
	private ResultChoicePayload received;

	public ResultChoiceSync(int containerId) {
		this.containerId = containerId;
		this.received = ResultChoicePayload.none(containerId);
	}

	/**
	 * What the button shows for {@code host} right now. Public for the gametests, which read it the way the menu
	 * sends it.
	 */
	public static ResultChoicePayload snapshot(int containerId, ResultChoiceHost host) {
		ServerLevel level = host.resultChoiceLevel();
		if (level == null) {
			return ResultChoicePayload.none(containerId);
		}
		ItemStack input = host.resultChoiceInput();
		ResultChooser chooser = host.resultChooser();
		List<ResultOptions.Option> options = chooser.options(level, input);
		int shown = Math.min(options.size(), ResultChoicePayload.MAX_OPTIONS);
		List<ResultChoicePayload.Entry> entries = new ArrayList<>(shown);
		for (int i = 0; i < shown; i++) {
			ResultOptions.Option option = options.get(i);
			entries.add(new ResultChoicePayload.Entry(option.result().copy(), option.source()));
		}
		int selected = chooser.selectedIndex(level, input);
		return new ResultChoicePayload(containerId, entries, selected < shown ? selected : -1);
	}

	/** Server side, from {@code broadcastChanges}: send the options to {@code viewer} if they changed. */
	public void broadcast(@Nullable ResultChoiceHost host, Object viewer) {
		if (host == null || !(viewer instanceof ServerPlayer player)) {
			return;
		}
		ResultChoicePayload next = snapshot(containerId, host);
		String key = fingerprint(next);
		if (key.equals(lastSent)) {
			return;
		}
		lastSent = key;
		NetworkDispatcher.get().sendToPlayer(player, next);
	}

	/** Client side: the latest packet (none until the first lands). */
	public ResultChoicePayload received() {
		return received;
	}

	/** Client side: accept a packet; one addressed to another menu is dropped. */
	public void accept(ResultChoicePayload payload) {
		if (payload.containerId() == containerId) {
			received = payload;
		}
	}

	private static String fingerprint(ResultChoicePayload payload) {
		StringBuilder key = new StringBuilder().append(payload.selected());
		for (ResultChoicePayload.Entry entry : payload.options()) {
			key.append('|').append(ResultOptions.keyOf(entry.result().getItem())).append('*')
					.append(entry.result().getCount()).append('@').append(entry.source());
		}
		return key.toString();
	}
}
