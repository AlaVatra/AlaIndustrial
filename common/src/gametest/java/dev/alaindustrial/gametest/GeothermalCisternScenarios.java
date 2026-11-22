package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.environment.GeothermalLava;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.menu.UpgradeSlotKind;
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
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * MOD-996: the cistern chip doubles the geothermal generator's lava tank (10 → 20 buckets), cannot be taken out
 * while the lava is over the plain tank, and no lava is lost on a save, a chip removal or a break.
 *
 * <p>Red on the code before MOD-996: the tank takes 10 buckets whatever is in the panel, arm 1 is the locked
 * overclock arm, a load clamps the tank to 10 buckets, and the MOD-998 restore clamps to the plain tank.
 */
public final class GeothermalCisternScenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(GeothermalCisternScenarios::cisternDoublesTheTank,
						"geothermal_cistern_doubles_the_tank").ticks(20, 40),
				RosterEntry.of(GeothermalCisternScenarios::cisternStaysInWhileTheLavaIsOverThePlainTank,
						"geothermal_cistern_stays_in_while_the_lava_is_over_the_plain_tank").ticks(20, 40),
				RosterEntry.of(GeothermalCisternScenarios::cisternLavaSurvivesASaveAndLoad,
						"geothermal_cistern_lava_survives_a_save_and_load").ticks(20, 40),
				RosterEntry.of(GeothermalCisternScenarios::brokenWithACisternPlacedWithoutKeepsAllTheLava,
						"geothermal_cistern_broken_with_placed_without_keeps_all_the_lava").ticks(20, 40));

		private Roster() {
		}
	}

	private GeothermalCisternScenarios() {
	}

	private static final BlockPos GEO = new BlockPos(1, 2, 1);
	private static final BlockPos TARGET = new BlockPos(3, 2, 1);
	private static final int CISTERN_ARM = 1;

	/**
	 * Without the chip the tank takes 10 buckets; with it, 20, and the capability's capacity follows.
	 *
	 * @implements MOD-996 — with the chip the tank is bigger and the readouts show it; without it, as before
	 */
	public static void cisternDoublesTheTank(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		long plain = pour(geo, 25 * FluidAmounts.BUCKET);
		long plainGauge = geo.fluidPort(Direction.NORTH).getCapacity();
		if (plain != GeothermalGeneratorBlockEntity.TANK_CAPACITY || plainGauge != 10 * FluidAmounts.BUCKET) {
			helper.fail("without the chip the tank must take 10 buckets and show 10: took " + plain
					+ " gauge " + plainGauge);
			return;
		}
		fit(geo);
		long more = pour(geo, 25 * FluidAmounts.BUCKET);
		long gauge = geo.fluidPort(Direction.NORTH).getCapacity();
		if (geo.fluidTank.amount != GeothermalGeneratorBlockEntity.CISTERN_TANK_CAPACITY
				|| more != GeothermalGeneratorBlockEntity.TANK_CAPACITY || gauge != 20 * FluidAmounts.BUCKET) {
			helper.fail("with the chip the tank must hold 20 buckets and show 20: tank " + geo.fluidTank.amount
					+ " took " + more + " gauge " + gauge);
			return;
		}
		helper.succeed();
	}

	/**
	 * The cistern arm takes the chip; the chip does not come out while the tank or the firebox is over the plain
	 * capacity, and comes out once it is not.
	 *
	 * @implements MOD-996 — taking the chip out never destroys lava
	 */
	public static void cisternStaysInWhileTheLavaIsOverThePlainTank(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		GeothermalGeneratorMenu menu = new GeothermalGeneratorMenu(1, player.getInventory(), geo,
				ContainerLevelAccess.create(helper.getLevel(), geo.getBlockPos()));
		Slot arm = armSlot(menu, geo);
		ItemStack chip = new ItemStack(ModContent.CISTERN_CHIP.get());
		if (!(arm instanceof MachineMenu.UpgradeSlot upgrade) || upgrade.kind() != UpgradeSlotKind.CISTERN
				|| !arm.mayPlace(chip)) {
			helper.fail("arm 1 of the geothermal generator must be the cistern arm and take the chip");
			return;
		}
		fit(geo);
		pour(geo, 15 * FluidAmounts.BUCKET);
		if (arm.mayPickup(player)) {
			helper.fail("the cistern came out with 15 buckets in a 10-bucket plain tank");
			return;
		}
		load(geo, GeothermalGeneratorBlockEntity.TANK_CAPACITY, 15 * GeneratorConfig.geothermalBurnTicks);
		if (arm.mayPickup(player)) {
			helper.fail("the cistern came out with 15 buckets in the firebox over its plain 10");
			return;
		}
		load(geo, GeothermalGeneratorBlockEntity.TANK_CAPACITY, 10 * GeneratorConfig.geothermalBurnTicks);
		if (!arm.mayPickup(player)) {
			helper.fail("the cistern must come out once both stages are back within the plain tank");
			return;
		}
		helper.succeed();
	}

	/**
	 * 18 buckets in a cistern tank come back after a save and a load — the load clamped to 10 before.
	 *
	 * @implements MOD-996 — the tank's lava survives a world reload at any capacity
	 */
	public static void cisternLavaSurvivesASaveAndLoad(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		fit(geo);
		pour(geo, 18 * FluidAmounts.BUCKET);
		CompoundTag tag = geo.saveWithFullMetadata(helper.getLevel().registryAccess());
		var copy = new GeothermalGeneratorBlockEntity(geo.getBlockPos(), geo.getBlockState());
		copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
				helper.getLevel().registryAccess(), tag));
		if (copy.fluidTank.amount != 18 * FluidAmounts.BUCKET || !copy.hasCistern()) {
			helper.fail("18 buckets with a cistern must survive a reload, got " + copy.fluidTank.amount
					+ " cistern=" + copy.hasCistern());
			return;
		}
		helper.succeed();
	}

	/**
	 * Break a generator holding 20 buckets in its tank and 15 in its firebox with a cistern: the chip drops on its
	 * own, the item carries 35 buckets, and a generator placed without the chip holds all 35 — the surplus over
	 * its plain tank waits there and refuses new lava until it is burned.
	 *
	 * @implements MOD-996 — a restored item with more lava than fits follows the tank rule without losing lava
	 */
	public static void brokenWithACisternPlacedWithoutKeepsAllTheLava(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = place(helper);
		fit(geo);
		load(geo, GeothermalGeneratorBlockEntity.CISTERN_TANK_CAPACITY, 15 * GeneratorConfig.geothermalBurnTicks);
		long expected = 35 * FluidAmounts.BUCKET;

		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(GEO);
		ServerPlayer breaker = AlaGameTestHelper.survivalPlayer(helper);
		breaker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		if (!breaker.gameMode.destroyBlock(abs)) {
			helper.fail("the survival player could not break the generator");
			return;
		}
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2.0), e -> true);
		ItemStack generator = ItemStack.EMPTY;
		boolean chipDropped = false;
		for (ItemEntity drop : drops) {
			if (drop.getItem().is(ModContent.GEOTHERMAL_GENERATOR_ITEM.get())) {
				generator = drop.getItem().copy();
			}
			chipDropped |= drop.getItem().is(ModContent.CISTERN_CHIP.get());
			drop.discard();
		}
		Long carried = generator.get(ModDataComponents.STORED_LAVA.get());
		if (!chipDropped || carried == null || carried != expected) {
			helper.fail("the chip must drop on its own and the item carry " + expected + " mB: chip=" + chipDropped
					+ " carried=" + carried);
			return;
		}

		helper.setBlock(TARGET.below(), Blocks.STONE);
		ServerPlayer placer = AlaGameTestHelper.survivalPlayer(helper);
		placer.setItemInHand(InteractionHand.MAIN_HAND, generator);
		BlockPos support = helper.absolutePos(TARGET.below());
		InteractionResult placed = placer.gameMode.useItemOn(placer, level, placer.getMainHandItem(),
				InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
		if (!placed.consumesAction()) {
			helper.fail("the generator item did not place");
			return;
		}
		GeothermalGeneratorBlockEntity restored = helper.getBlockEntity(TARGET, GeothermalGeneratorBlockEntity.class);
		int reserve = restored.getDataAccess()
				.get(dev.alaindustrial.block.entity.machine.MachineChannels.PROGRESS.ordinal());
		long held = restored.fluidTank.amount + GeothermalLava.reserveMb(reserve, GeneratorConfig.geothermalBurnTicks);
		if (held != expected) {
			helper.fail("a generator placed without the chip lost lava: holds " + held + " of " + expected);
			return;
		}
		if (pour(restored, FluidAmounts.BUCKET) != 0) {
			helper.fail("a tank over its plain capacity must take no new lava");
			return;
		}
		helper.succeed();
	}

	private static GeothermalGeneratorBlockEntity place(GameTestHelper helper) {
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get().defaultBlockState());
		return helper.getBlockEntity(GEO, GeothermalGeneratorBlockEntity.class);
	}

	private static void fit(GeothermalGeneratorBlockEntity geo) {
		geo.setItem(geo.upgradeSlotStart() + CISTERN_ARM, new ItemStack(ModContent.CISTERN_CHIP.get()));
	}

	/** Push lava in through the generator's own capability, as a pump would; returns what it took. */
	private static long pour(GeothermalGeneratorBlockEntity geo, long mb) {
		long[] taken = new long[1];
		EnergyTransactions.get().runCommitting(txn -> taken[0] = geo.fluidPort(Direction.NORTH)
				.insert(FluidHolder.of(Fluids.LAVA), mb, txn));
		return taken[0];
	}

	/** Set the tank and the firebox through the generator's own save format (the upgrade slots stay). */
	private static void load(GeothermalGeneratorBlockEntity geo, long tankMb, int lavaTicks) {
		var access = geo.getLevel().registryAccess();
		CompoundTag tag = geo.saveCustomOnly(access);
		tag.putLong("FluidTankMb", tankMb);
		tag.putInt("LavaTicks", lavaTicks);
		geo.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, access, tag));
	}

	private static Slot armSlot(MachineMenu menu, GeothermalGeneratorBlockEntity geo) {
		int index = geo.upgradeSlotStart() + CISTERN_ARM;
		for (Slot slot : menu.slots) {
			if (slot.container == geo && slot.getContainerSlot() == index) {
				return slot;
			}
		}
		throw new IllegalStateException("no menu slot bound to the cistern arm (container index " + index + ")");
	}
}
