package dev.alaindustrial.settlement.works;

import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.menu.TownDispatcherMenu;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.TownObstructionPayload;
import dev.alaindustrial.settlement.SettlementRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * «Show it» at a stopped job's «In the way: X at x y z» (MOD-890, owner's playtest of 0.1.220; the obstruction is
 * R143's): the block in the way blinks with an outline seen through walls for {@link #HIGHLIGHT_TICKS}, as «Find» shows
 * a worker (R142). Only the asker sees it.
 *
 * <p><b>Who may ask (R98).</b> One of the town's own, with its core's screen open and standing by it
 * ({@link TownPulseService#openCore}), or with its dispatcher's screen open (MOD-908: the same button sits there);
 * and only for a cell one of the town's stopped jobs names as in the way or out of the drone's reach — a forged
 * request for any other cell, or from a stranger, gets no reply. The decision ({@link #answer}) is apart from
 * the send, so a both-lane gametest checks it: a mock player on NeoForge takes no mod payload.
 */
public final class TownObstructionService {

	/** How long the block in the way blinks, in ticks: eight seconds, as «Find» (R142). */
	public static final int HIGHLIGHT_TICKS = 160;

	private TownObstructionService() {
	}

	/** {@code viewer} pressed «show it» at the line naming {@code pos}. */
	public static void request(ServerPlayer viewer, BlockPos pos) {
		answer(viewer, pos).ifPresent(payload -> {
			NetworkDispatcher dispatcher = NetworkDispatcher.get();
			if (dispatcher.canSendToPlayer(viewer, TownObstructionPayload.TYPE)) {
				dispatcher.sendToPlayer(viewer, payload);
			}
		});
	}

	/** The reply to {@code viewer}'s «show it» at {@code pos}, or empty when it is refused. */
	public static Optional<TownObstructionPayload> answer(ServerPlayer viewer, BlockPos pos) {
		int town = TownPulseService.openCore(viewer).map(SettlementCoreBlockEntity::settlementId)
				.orElseGet(() -> openDispatcherTown(viewer));
		if (town <= 0) {
			return Optional.empty();
		}
		Optional<TownWorks> works = SettlementRegistry.get(viewer.level().getServer()).works().find(town);
		if (works.isEmpty() || !named(works.get(), pos)) {
			return Optional.empty();
		}
		return Optional.of(new TownObstructionPayload(pos.immutable(), HIGHLIGHT_TICKS));
	}

	/**
	 * The town of the dispatcher whose screen {@code viewer} has open, while the menu is still valid for them — close
	 * by and one of the town's own ({@code TownDispatcherMenu#stillValid}) — or 0.
	 */
	private static int openDispatcherTown(ServerPlayer viewer) {
		if (!(viewer.containerMenu instanceof TownDispatcherMenu menu) || menu.dispatcher() == null
				|| !menu.stillValid(viewer)) {
			return 0;
		}
		TownDispatcherBlockEntity dispatcher = menu.dispatcher();
		if (dispatcher.isRemoved() || dispatcher.getLevel() != viewer.level()
				|| viewer.level().getBlockEntity(dispatcher.getBlockPos()) != dispatcher) {
			return 0;
		}
		return Math.max(0, dispatcher.settlementId());
	}

	/** Whether a stopped job of {@code works} names {@code pos} as the cell in its way. */
	static boolean named(TownWorks works, BlockPos pos) {
		for (WorkJob job : works.queue().jobs()) {
			if (job.status() != WorkJobStatus.BLOCKED || job.reason().isEmpty()) {
				continue;
			}
			BlockReason reason = job.reason().get();
			Optional<int[]> cell = BlockReason.obstructedCell(reason.key(), reason.args());
			if (cell.isPresent() && cell.get()[0] == pos.getX() && cell.get()[1] == pos.getY()
					&& cell.get()[2] == pos.getZ()) {
				return true;
			}
		}
		return false;
	}
}
