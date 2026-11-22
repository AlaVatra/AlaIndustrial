package dev.alaindustrial.gametest;

import dev.alaindustrial.advancement.GeothermalMilestone;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.stats.PlayerModStats;
import dev.alaindustrial.stats.PlayerStatsStore;
import dev.alaindustrial.stats.PlayerStatsTracker;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;
import static dev.alaindustrial.gametest.ReactorAdvancementScenarios.earned;

/**
 * The geothermal advancement chain (MOD-1001), on every loader: "First Lava" when an owned generator burns lava,
 * "Bucket That Pays Off" when the owner's career EU from geothermal generators reaches a million, "Station" when
 * five generators of one owner burn lava at the same time. Each is credited to the generator's owner and to nobody
 * else, and moving a generator does not count it twice.
 */
public final class GeothermalAdvancementScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(GeothermalAdvancementScenarios::firstLavaGoesToTheOwner,
								"mod1001_first_lava_goes_to_the_owner")
						.ticks(20, 40),
				RosterEntry.of(GeothermalAdvancementScenarios::lavaMillionCountsCareerEuFromLava,
								"mod1001_lava_million_counts_career_eu_from_lava")
						.ticks(20, 40),
				RosterEntry.of(GeothermalAdvancementScenarios::stationNeedsFiveRunningBlocksOfOneOwner,
								"mod1001_station_needs_five_running_blocks_of_one_owner")
						.ticks(20, 40));

		private Roster() {}
	}

	private GeothermalAdvancementScenarios() {}

	/** Six spots two blocks apart, so no generator touches another. */
	private static final List<BlockPos> SPOTS = List.of(
			new BlockPos(1, 2, 1), new BlockPos(3, 2, 1), new BlockPos(5, 2, 1),
			new BlockPos(1, 2, 3), new BlockPos(3, 2, 3), new BlockPos(5, 2, 3));

	/**
	 * @implements MOD-1001 — "First Lava" goes to the generator's owner when it burns lava, not before, and a
	 *     generator nobody placed credits nobody.
	 */
	public static void firstLavaGoesToTheOwner(GameTestHelper helper) {
		ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
		ServerPlayer bystander = AlaGameTestHelper.survivalPlayer(helper);
		GeothermalGeneratorBlockEntity unowned = generator(helper, SPOTS.get(0), null);
		drive(unowned, helper, 3);
		helper.assertTrue(!earned(helper, owner, "first_lava") && !earned(helper, bystander, "first_lava"),
				"a generator nobody placed credited somebody");

		GeothermalGeneratorBlockEntity owned = generator(helper, SPOTS.get(1), owner);
		owned.fluidTank.amount = 0L;
		owned.fluidTank.fluid = FluidHolder.EMPTY;
		drive(owned, helper, 1);
		helper.assertTrue(!earned(helper, owner, "first_lava"), "first_lava before any lava burnt");
		fill(owned);
		drive(owned, helper, 1);
		helper.assertTrue(earned(helper, owner, "first_lava"), "the owner's generator burnt lava, no first_lava");
		helper.assertTrue(!earned(helper, bystander, "first_lava"), "first_lava went to a bystander");
		helper.assertTrue(!earned(helper, owner, "lava_million") && !earned(helper, owner, "geothermal_station"),
				"one generator's first burn earned a later step");
		helper.succeed();
	}

	/**
	 * @implements MOD-1001 — the million is the owner's career EU from geothermal generators: EU from another
	 *     generator does not count, the generator's own production crossing the line does, with no statistics chip.
	 */
	public static void lavaMillionCountsCareerEuFromLava(GameTestHelper helper) {
		ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
		var server = helper.getLevel().getServer();
		PlayerStatsTracker.get().flush(server);
		Identifier coal = BuiltInRegistries.BLOCK.getKey(ModContent.GENERATOR.get());
		Identifier lava = BuiltInRegistries.BLOCK.getKey(ModContent.GEOTHERMAL_GENERATOR.get());
		PlayerStatsTracker tracker = PlayerStatsTracker.get();
		tracker.recordProduction(server, owner.getUUID(), coal, 5L * GeothermalMilestone.LAVA_MILLION_EU);
		tracker.recordProduction(server, owner.getUUID(), lava, GeothermalMilestone.LAVA_MILLION_EU - 1L);
		PlayerStatsTracker.get().flush(server);
		helper.assertTrue(!earned(helper, owner, "lava_million"),
				"five million EU from coal and one short of a million from lava earned lava_million");

		GeothermalGeneratorBlockEntity owned = generator(helper, SPOTS.get(0), owner);
		helper.assertTrue(!owned.hasStatsChip(), "the rig needs a generator without the statistics chip");
		drive(owned, helper, 1);
		PlayerStatsTracker.get().flush(server);
		long fromLava = PlayerStatsStore.get(owner).producedByGenerator().getOrDefault(lava, 0L);
		helper.assertTrue(fromLava >= GeothermalMilestone.LAVA_MILLION_EU,
				"the generator's burn did not reach the owner's career statistics: " + fromLava);
		helper.assertTrue(earned(helper, owner, "lava_million"), "a million EU from lava, no lava_million");
		PlayerStatsStore.set(owner, PlayerModStats.EMPTY);
		helper.succeed();
	}

	/**
	 * @implements MOD-1001 — "Station": five generators of one owner burning at the same time. Four of the owner's
	 *     and one of another player's are not one; four, one of them broken and placed again, are four; five are.
	 */
	public static void stationNeedsFiveRunningBlocksOfOneOwner(GameTestHelper helper) {
		ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
		ServerPlayer other = AlaGameTestHelper.survivalPlayer(helper);
		GeothermalGeneratorBlockEntity[] gens = new GeothermalGeneratorBlockEntity[4];
		for (int i = 0; i < 4; i++) {
			gens[i] = generator(helper, SPOTS.get(i), owner);
		}
		GeothermalGeneratorBlockEntity foreign = generator(helper, SPOTS.get(4), other);
		burnAll(helper, gens);
		drive(foreign, helper, 1);
		helper.assertTrue(!earned(helper, owner, "geothermal_station") && !earned(helper, other, "geothermal_station"),
				"four of one owner and one of another made a station");

		// The first generator is broken and placed again on a free spot: still four blocks.
		helper.setBlock(SPOTS.get(0), Blocks.AIR);
		gens[0] = generator(helper, SPOTS.get(5), owner);
		burnAll(helper, gens);
		helper.assertTrue(!earned(helper, owner, "geothermal_station"),
				"a generator moved to a new spot counted twice and made a station of four");

		GeothermalGeneratorBlockEntity fifth = generator(helper, SPOTS.get(0), owner);
		burnAll(helper, gens);
		drive(fifth, helper, 1);
		helper.assertTrue(earned(helper, owner, "geothermal_station"), "five running generators, no station");
		helper.assertTrue(!earned(helper, other, "geothermal_station"), "the station went to another player");
		helper.succeed();
	}

	/** A geothermal generator at {@code pos} with lava in its tank, owned by {@code owner} (none when null). */
	private static GeothermalGeneratorBlockEntity generator(GameTestHelper helper, BlockPos pos,
			ServerPlayer owner) {
		GeothermalGeneratorBlockEntity geo = AlaGameTestHelper.place(helper, pos,
				ModContent.GEOTHERMAL_GENERATOR.get(), GeothermalGeneratorBlockEntity.class);
		if (owner != null) {
			geo.setOwner(owner.getUUID(), "GeothermalOwner");
		}
		fill(geo);
		return geo;
	}

	private static void fill(GeothermalGeneratorBlockEntity geo) {
		geo.fluidTank.fluid = FluidHolder.of(Fluids.LAVA);
		geo.fluidTank.amount = 2 * FluidAmounts.BUCKET;
	}

	private static void burnAll(GameTestHelper helper, GeothermalGeneratorBlockEntity[] gens) {
		for (GeothermalGeneratorBlockEntity geo : gens) {
			drive(geo, helper, 1);
		}
	}
}
