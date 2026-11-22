package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.machine.MachineChannels;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * MOD-998: a broken geothermal generator keeps its lava. The whole lava — the intake tank and the burn reserve
 * in the firebox — rides the dropped item as one volume in mB ({@code alaindustrial:stored_lava}) and goes back
 * in when the item is placed; the EU charge does not ride it. Every leg goes through the real player paths:
 * {@code gameMode.destroyBlock} with a pickaxe (contents spill, then the loot table) and {@code useItemOn}.
 *
 * <p>On the code before MOD-998 the drop carries no component and the placed generator stands empty, so both
 * lava scenarios go red; the empty-generator scenario guards the other half of the rule (a plain item stays
 * plain and keeps stacking).
 */
public final class GeothermalKeepsLavaScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(GeothermalKeepsLavaScenarios::brokenGeneratorCarriesItsLavaBackIn,
						"geothermal_broken_generator_carries_its_lava_back_in").ticks(20, 40),
				RosterEntry.of(GeothermalKeepsLavaScenarios::fullGeneratorRoundTripsWithoutLoss,
						"geothermal_full_generator_round_trips_without_loss").ticks(20, 40),
				RosterEntry.of(GeothermalKeepsLavaScenarios::emptyGeneratorDropsAPlainStackableItem,
						"geothermal_empty_generator_drops_a_plain_stackable_item").ticks(20, 40));

		private Roster() {
		}
	}

	private GeothermalKeepsLavaScenarios() {
	}

	private static final BlockPos GEO = new BlockPos(1, 2, 1);
	private static final BlockPos TARGET = new BlockPos(3, 2, 1);

	/**
	 * 3500 mB in the tank, 4250 ticks in the firebox, a part-charged buffer: the item carries 7750 mB, the placed
	 * generator holds 7750 mB again and an empty buffer.
	 *
	 * @implements MOD-998 — break, item with lava, place, the lava is back; the EU charge is not kept
	 */
	public static void brokenGeneratorCarriesItsLavaBackIn(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = placeWith(helper, 3_500L, 4_250);
		geo.getEnergyStorage().setAmountUntracked(2_000);
		roundTrip(helper, 7_750L);
	}

	/**
	 * A generator full in both stages (10 buckets in the tank, 10 buckets of burn) round-trips exactly at the
	 * default burn: nothing is clamped away, nothing is made.
	 *
	 * @implements MOD-998 — the lava is neither created nor lost beyond mB rounding
	 */
	public static void fullGeneratorRoundTripsWithoutLoss(GameTestHelper helper) {
		int fullReserve = 10 * dev.alaindustrial.core.environment.GeneratorConfig.geothermalBurnTicks;
		placeWith(helper, GeothermalGeneratorBlockEntity.TANK_CAPACITY, fullReserve);
		roundTrip(helper, GeothermalGeneratorBlockEntity.TANK_CAPACITY + 10 * FluidAmounts.BUCKET);
	}

	/**
	 * An empty generator drops an item with no lava component and the default stack size, so it still stacks
	 * with a freshly crafted one.
	 *
	 * @implements MOD-998 — an empty generator drops as before, no extra component
	 */
	public static void emptyGeneratorDropsAPlainStackableItem(GameTestHelper helper) {
		placeWith(helper, 0L, 0);
		ItemStack drop = breakAndCollect(helper);
		if (drop == null) {
			return;
		}
		if (drop.has(ModDataComponents.STORED_LAVA.get())) {
			helper.fail("an empty generator dropped with a lava component: "
					+ drop.get(ModDataComponents.STORED_LAVA.get()));
			return;
		}
		ItemStack fresh = new ItemStack(ModContent.GEOTHERMAL_GENERATOR_ITEM.get());
		if (!ItemStack.isSameItemSameComponents(drop, fresh) || drop.getMaxStackSize() != fresh.getMaxStackSize()) {
			helper.fail("an empty generator's drop must stack with a fresh one: drop=" + drop.getComponentsPatch());
			return;
		}
		helper.succeed();
	}

	private static void roundTrip(GameTestHelper helper, long expectedMb) {
		ItemStack drop = breakAndCollect(helper);
		if (drop == null) {
			return;
		}
		Long carried = drop.get(ModDataComponents.STORED_LAVA.get());
		if (carried == null || carried != expectedMb) {
			helper.fail("the dropped generator must carry " + expectedMb + " mB of lava, carries " + carried);
			return;
		}
		if (drop.getMaxStackSize() != 1 || drop.has(ModDataComponents.STORED_ENERGY.get())) {
			helper.fail("a generator with lava must not stack and must not carry EU: max="
					+ drop.getMaxStackSize() + " patch=" + drop.getComponentsPatch());
			return;
		}
		if (ItemStack.isSameItemSameComponents(drop, new ItemStack(ModContent.GEOTHERMAL_GENERATOR_ITEM.get()))) {
			helper.fail("a generator with lava must not merge with a plain one");
			return;
		}

		helper.setBlock(TARGET.below(), Blocks.STONE);
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, drop);
		BlockPos support = helper.absolutePos(TARGET.below());
		InteractionResult placed = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
				InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
		if (!placed.consumesAction()) {
			helper.fail("the generator item with lava did not place");
			return;
		}
		GeothermalGeneratorBlockEntity restored = helper.getBlockEntity(TARGET, GeothermalGeneratorBlockEntity.class);
		long held = restored.fluidTank.amount
				+ dev.alaindustrial.core.environment.GeothermalLava.reserveMb(
						restored.getDataAccess().get(MachineChannels.PROGRESS.ordinal()),
						dev.alaindustrial.core.environment.GeneratorConfig.geothermalBurnTicks);
		if (held != expectedMb) {
			helper.fail("the placed generator holds " + held + " mB, the item carried " + expectedMb);
			return;
		}
		if (restored.getEnergyStorage().getAmount() != 0) {
			helper.fail("the EU charge must not survive the break, the placed generator holds "
					+ restored.getEnergyStorage().getAmount());
			return;
		}
		helper.succeed();
	}

	/** Place a generator and load {@code tankMb} + {@code lavaTicks} into it through its own save format. */
	private static GeothermalGeneratorBlockEntity placeWith(GameTestHelper helper, long tankMb, int lavaTicks) {
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get().defaultBlockState());
		GeothermalGeneratorBlockEntity geo = helper.getBlockEntity(GEO, GeothermalGeneratorBlockEntity.class);
		CompoundTag tag = geo.saveCustomOnly(helper.getLevel().registryAccess());
		tag.putLong("FluidTankMb", tankMb);
		tag.putInt("LavaTicks", lavaTicks);
		geo.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
				helper.getLevel().registryAccess(), tag));
		return geo;
	}

	/** Break the generator as a survival player with a pickaxe and return the one generator item it dropped. */
	private static @Nullable ItemStack breakAndCollect(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(GEO);
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		if (!player.gameMode.destroyBlock(abs)) {
			helper.fail("the survival player could not break the generator");
			return null;
		}
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2.0),
				e -> e.getItem().is(ModContent.GEOTHERMAL_GENERATOR_ITEM.get()));
		if (drops.size() != 1 || drops.get(0).getItem().getCount() != 1) {
			helper.fail("expected exactly one generator item to drop, got " + drops.size());
			return null;
		}
		ItemStack drop = drops.get(0).getItem().copy();
		drops.get(0).discard();
		return drop;
	}
}
