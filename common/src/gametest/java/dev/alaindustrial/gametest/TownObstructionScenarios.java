package dev.alaindustrial.gametest;

import dev.alaindustrial.menu.SettlementCoreMenu;
import dev.alaindustrial.menu.TownDispatcherMenu;
import dev.alaindustrial.network.TownObstructionPayload;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.DispatcherView;
import dev.alaindustrial.settlement.drone.DispatcherViews;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.TownObstructionService;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobType;
import dev.alaindustrial.settlement.works.WorkQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.phys.Vec3;

/**
 * L2 for «show it» at a stopped job's «In the way: X at x y z» (MOD-890; R143's reason, R142's outline, R98's gate):
 * the town's restorer with the core's screen open gets the cell outlined; a stranger with a forged screen gets
 * nothing, and nobody gets a cell no stopped job of the town names. The decision is tested below the send: a mock
 * player on NeoForge takes no mod payload.
 */
public final class TownObstructionScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownObstructionScenarios::onlyTheTownsOwnSeeTheBlockInTheWay,
						"town_obstruction_only_the_towns_own_see_it").ticks(40));

		private Roster() {
		}
	}

	private TownObstructionScenarios() {
	}

	/**
	 * @implements MOD-890-OBSTRUCTION — the restorer's «show it» at the cell a stopped job names is answered with that
	 *     cell for eight seconds; a stranger's forged request and a request for a cell no job names are not.
	 * @implements MOD-908-SHOW — the cell of a job stopped «the drone cannot reach» is answered the same way.
	 * @implements MOD-908-DISPATCHER — the dispatcher's lines name the cells of both stops, and the restorer with the
	 *     dispatcher's screen open is answered as with the core's; a stranger at the dispatcher is not.
	 */
	public static void onlyTheTownsOwnSeeTheBlockInTheWay(GameTestHelper helper) {
		ServerPlayer restorer = AlaGameTestHelper.survivalPlayer(helper);
		ServerPlayer stranger = AlaGameTestHelper.survivalPlayer(helper);
		TownDroneScenarios.Rig rig = TownDroneScenarios.rig(helper, 0, restorer.getUUID(), false);
		BlockPos core = rig.core().getBlockPos();
		BlockPos inTheWay = rig.origin().offset(2, 1, 7);
		WorkQueue queue = rig.works().queue();
		WorkJob job = queue.raise(WorkJobType.BUILD, 0).or(() -> queue.open(WorkJobType.BUILD, 0))
				.orElseThrow(() -> helper.assertionException("control: no build job on the hall"));
		helper.assertTrue(queue.take(job.id(), "mod890") == WorkQueue.Outcome.OK, "control: the job was not taken");
		queue.block(job.id(), "mod890", BlockReason.obstructed("block.alaindustrial.town_dispatcher",
				inTheWay.getX(), inTheWay.getY(), inTheWay.getZ()), 0);

		Vec3 by = Vec3.atBottomCenterOf(core.above());
		restorer.setPos(by.x, by.y, by.z);
		stranger.setPos(by.x, by.y, by.z);
		helper.assertTrue(TownObstructionService.answer(restorer, inTheWay).isEmpty(),
				"no reply to a player with no core screen open");
		restorer.openMenu(rig.core());
		helper.assertTrue(restorer.containerMenu instanceof SettlementCoreMenu,
				"control: the restorer must open the core's screen");
		Optional<TownObstructionPayload> shown = TownObstructionService.answer(restorer, inTheWay);
		helper.assertTrue(shown.isPresent() && shown.get().pos().equals(inTheWay)
				&& shown.get().ticks() == TownObstructionService.HIGHLIGHT_TICKS,
				"the restorer must see the block in the way outlined, got " + shown);
		helper.assertTrue(TownObstructionService.answer(restorer, inTheWay.above()).isEmpty(),
				"a cell no stopped job names must not be outlined");
		// MOD-908: a job stopped «the drone cannot reach x y z» has its cell outlined the same way.
		BlockPos unreachable = rig.origin().offset(3, 1, 7);
		WorkJob clearing = queue.raise(WorkJobType.CLEAR, 0).or(() -> queue.open(WorkJobType.CLEAR, 0))
				.orElseThrow(() -> helper.assertionException("control: no clearing job on the hall"));
		helper.assertTrue(queue.take(clearing.id(), "mod908") == WorkQueue.Outcome.OK,
				"control: the clearing job was not taken");
		queue.block(clearing.id(), "mod908", BlockReason.noRoute(unreachable.getX(), unreachable.getY(),
				unreachable.getZ()), 0);
		Optional<TownObstructionPayload> reached = TownObstructionService.answer(restorer, unreachable);
		helper.assertTrue(reached.isPresent() && reached.get().pos().equals(unreachable),
				"the restorer must see the cell the drone cannot reach outlined, got " + reached);

		// A stranger with the core's screen forged open (the server never opens it for them, R98).
		stranger.containerMenu = new SettlementCoreMenu(1, stranger.getInventory(), rig.core(),
				ContainerLevelAccess.create(rig.level(), core));
		helper.assertTrue(TownObstructionService.answer(stranger, inTheWay).isEmpty(),
				"a stranger to the town must not see what stands in its way");

		restorer.closeContainer();
		stranger.containerMenu = stranger.inventoryMenu;

		// MOD-908: the dispatcher's screen shows the same stops, each with the cell its «show it» asks for.
		DispatcherView view = DispatcherViews.of(rig.level(), rig.dispatcher(),
				SettlementRegistry.get(rig.level().getServer()).find(rig.town()));
		List<ConstructionRow.Text> lines = new ArrayList<>(view.task());
		view.orders().forEach(order -> lines.add(order.step()));
		for (BlockPos named : List.of(inTheWay, unreachable)) {
			int[] cell = {named.getX(), named.getY(), named.getZ()};
			helper.assertTrue(lines.stream().anyMatch(line -> BlockReason.obstructedCell(line.key(), line.args())
					.map(found -> Arrays.equals(found, cell)).orElse(false)),
					"a line of the dispatcher must name the cell " + named + ": " + lines);
		}
		restorer.openMenu(rig.dispatcher());
		helper.assertTrue(restorer.containerMenu instanceof TownDispatcherMenu,
				"control: the restorer must open the dispatcher's screen");
		Optional<TownObstructionPayload> fromDispatcher = TownObstructionService.answer(restorer, unreachable);
		helper.assertTrue(fromDispatcher.isPresent() && fromDispatcher.get().pos().equals(unreachable),
				"the dispatcher's «show it» must outline the cell the drone cannot reach, got " + fromDispatcher);
		helper.assertTrue(TownObstructionService.answer(restorer, inTheWay).isPresent(),
				"the dispatcher's «show it» must outline the block in the way too");
		helper.assertTrue(TownObstructionService.answer(restorer, inTheWay.above()).isEmpty(),
				"from the dispatcher too, a cell no stopped job names must not be outlined");
		restorer.closeContainer();
		stranger.openMenu(rig.dispatcher());
		helper.assertTrue(TownObstructionService.answer(stranger, unreachable).isEmpty(),
				"a stranger at the dispatcher must not see the town's cells outlined");
		stranger.closeContainer();
		queue.unblock(job.id());
		queue.unblock(clearing.id());
		rig.release();
		helper.succeed();
	}
}
