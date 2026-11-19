package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.gametest.TownDroneScenarios.ChunkHold;
import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildingState;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownOrders;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * L2 suite of a warehouse restored from its ruin by the drones alone (MOD-908; epic MOD-824, MOD-888): the desert
 * warehouse ruin stands twenty blocks from the hall whose roof holds the only nest, as in the owner's desert town, and
 * its order ends with the warehouse restored and no job ever stopped «the drone cannot reach x y z». Replayed by every
 * loader through the roster (ADR-038).
 */
public final class TownWarehouseRuinScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownWarehouseRuinScenarios::aDesertWarehouseRuinIsRestoredByTheDronesAlone,
						"town_warehouse_ruin_desert_ruin_is_restored_by_the_drones_alone").ticks(48000));

		private Roster() {}
	}

	private TownWarehouseRuinScenarios() {}

	private static final Identifier RUIN = Industrialization.id("ala_town/desert/warehouse");
	private static final Identifier INTACT = Industrialization.id("ala_town/desert/intact/warehouse");
	private static final String NAME = "settlement.alaindustrial.building.warehouse";
	/** South of the rig's hall and two blocks lower, as the owner's warehouse lay from his hall (MOD-908). */
	private static final BlockPos OFFSET = new BlockPos(0, -2, -20);

	/**
	 * @implements MOD-908-RESTORE — the desert warehouse ruin, its plan learned and every material in the buffer, is
	 *     ordered; one drone from the hall's roof clears and builds it to the end, and no job of it ever stops «the
	 *     drone cannot reach». Before the fix the route search gave up after a fixed 6000 cells while the box round the
	 *     nest and the warehouse holds more open cells: once the roof was up, the cell under it — reached only by the
	 *     door — was called unreachable and the building job stopped there for good.
	 */
	public static void aDesertWarehouseRuinIsRestoredByTheDronesAlone(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		ServerLevel level = rig.level();
		BlockPos at = rig.origin().offset(OFFSET);
		ChunkHold hold = ChunkHold.forTest(helper, level, at);
		for (int x = -4; x <= 15; x++) {
			for (int z = -4; z <= 11; z++) {
				for (int y = -3; y < 0; y++) {
					level.setBlock(at.offset(x, y, z), Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		LineStructures.templates(level).getOrCreate(RUIN).placeInWorld(level, at, at,
				new StructurePlaceSettings().setRotation(Rotation.NONE)
						.addProcessor(JigsawReplacementProcessor.INSTANCE)
						.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK),
				RandomSource.create(908L), Block.UPDATE_CLIENTS);
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		TownBuilding hall = rig.works().buildings().getFirst();
		registry.works().forget(rig.town());
		registry.works().town(rig.town()).learnBuildings(List.of(
				TownBuilding.found(hall.template(), hall.intact(), hall.name(), hall.origin(), hall.rotation()),
				TownBuilding.found(RUIN, Optional.of(INTACT), NAME, at, Rotation.NONE)));
		TownWorksScanner.measureNow(level, rig.town(), 0);
		TownWorksScanner.measureNow(level, rig.town(), 1);
		helper.assertTrue(rig.works().buildings().get(1).state() == BuildingState.RUIN,
				"control: the warehouse must stand as a ruin: " + rig.works().buildings().get(1));
		rig.works().learnHousePlan(INTACT.toString()); // MOD-892: the plan is the restorer's to bring
		BuildMaterials.forBuilding(level, rig.town(), 1).flatMap(staged -> staged.progress(level)).orElseThrow()
				.needed().forEach(need -> BuiltInRegistries.ITEM.getOptional(Identifier.parse(need.item()))
						.ifPresent(item -> rig.dispatcher().buffer().insert(new ItemStack(item, need.count()))));
		Settlement town = registry.find(rig.town()).orElseThrow();
		Optional<String> refused = TownOrders.place(level, town, 1, "test");
		helper.assertTrue(refused.isEmpty(), "the order on the warehouse must be placed: " + refused);
		helper.onEachTick(() -> {
			rig.tick();
			for (WorkJob job : rig.works().queue().jobs()) {
				helper.assertTrue(job.reason().map(BlockReason::key).filter(BlockReason.NO_ROUTE::equals).isEmpty(),
						"no job of the warehouse may stop «the drone cannot reach»: " + job);
			}
		});
		helper.succeedWhen(() -> {
			WorkJob build = rig.works().queue().jobs().stream()
					.filter(job -> job.building() == 1 && job.type() == WorkJobType.BUILD).reduce((a, b) -> b)
					.orElseThrow(() -> helper.assertionException("the order raised no building job"));
			helper.assertTrue(build.status() == WorkJobStatus.DONE, "the building job is " + build + ", nest "
					+ rig.nest(0).status());
			TownWorksScanner.measureNow(level, rig.town(), 1);
			TownBuilding after = rig.works().buildings().get(1);
			helper.assertTrue(after.everRestored() && after.state() == BuildingState.INTACT
					&& after.missingKeys().isEmpty(), "the warehouse must stand restored: " + after);
			hold.release();
			rig.release();
		});
	}
}
