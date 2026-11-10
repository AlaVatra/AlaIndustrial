package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.AbstractModChestBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.core.forge.BlockCapabilityProviderForge;
import dev.alaindustrial.core.forge.ForeignFluidPort;
import dev.alaindustrial.core.forge.ForgeTransferLosses;
import dev.alaindustrial.core.item.ItemLookup;
import dev.alaindustrial.core.item.ItemMover;
import dev.alaindustrial.item.fluid.ItemFluid;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import dev.alaindustrial.registry.BlockCapabilityRoster;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ItemCapabilityRoster;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.VanillaInventoryCodeHooks;

/**
 * Forge-only gametests of the Forge bridges (MOD-962; slice 2.3, moved from the stage-2 probe in stage 3): P1 every
 * handler a manifest block entity hands out is invalidated when the block goes (the port/capability equality is
 * also the roster-wide sweep {@code block_capabilities_match_ports}); P2 the iron furnace's faces through the path
 * Forge's hopper takes (the {@code BaseContainerBlockEntity} mixin); P3 every chest tier as one joined inventory,
 * moved by our own item port, and the pair's handler invalidated when it splits; P4 {@code FluidTank}'s snapshot
 * contract and a lying foreign tank's shortfall returned to the paying tank; P5 the provider found through its own
 * capability; P6 buckets and capsules through the item fluid bridge, and the item-stack capabilities.
 *
 * <p>Each check works inside its test's rig, from {@link #origin}.
 */
public final class ForgeBridgeScenarios {

	private ForgeBridgeScenarios() {
	}

	/** The rig cell the checks build on; P3 also uses the cell east of it and three south. */
	private static BlockPos origin(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(2, 2, 2));
	}

	public static void capabilitiesInvalidatedOnRemoval(GameTestHelper helper) {
		ForgeChecks.run(helper, "P1 capabilities", () -> p1Capabilities(helper.getLevel(), origin(helper)));
	}

	public static void furnaceFacesThroughHopperPath(GameTestHelper helper) {
		ForgeChecks.run(helper, "P2 furnace", () -> p2Furnace(helper.getLevel(), origin(helper)));
	}

	public static void chestPairsJoinAndSplit(GameTestHelper helper) {
		ForgeChecks.run(helper, "P3 chests", () -> p3Chests(helper.getLevel(), origin(helper)));
	}

	public static void tankSnapshotAndLyingForeignTank(GameTestHelper helper) {
		ForgeChecks.run(helper, "P4 tank", ForgeBridgeScenarios::p4Tank);
	}

	public static void providerFoundThroughOwnCapability(GameTestHelper helper) {
		ForgeChecks.run(helper, "P5 self", () -> p5Self(helper.getLevel(), origin(helper)));
	}

	public static void itemFluidBridge(GameTestHelper helper) {
		ForgeChecks.run(helper, "P6 item fluid", ForgeBridgeScenarios::p6ItemFluid);
	}

	private static List<String> p1Capabilities(ServerLevel level, BlockPos origin) {
		List<String> bad = new ArrayList<>();
		int blocks = 0;
		int invalidated = 0;
		int silenced = 0;
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			for (String blockId : def.blocks()) {
				Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(blockId));
				level.setBlock(origin, block.defaultBlockState(), Block.UPDATE_ALL);
				BlockEntity be = level.getBlockEntity(origin);
				if (be == null || !def.type().isInstance(be)) {
					bad.add(blockId + ": no " + def.type().getSimpleName() + " after placing");
					continue;
				}
				blocks++;
				boolean anyEnergy = false;
				boolean anyFluid = false;
				List<LazyOptional<?>> held = new ArrayList<>();
				List<Object> raw = new ArrayList<>();
				for (Direction face : Direction.values()) {
					boolean expectEnergy = be instanceof EnergyPortHost host && host.energyPort(face) != null
							&& !BlockCapabilityRoster.NO_ENERGY_CAPABILITY.contains(def);
					boolean expectFluid = be instanceof FluidPortHost host && host.fluidPort(face) != null;
					anyEnergy |= expectEnergy;
					anyFluid |= expectFluid;
					compare(bad, blockId, face, "energy", be, ForgeCapabilities.ENERGY, expectEnergy, held, raw);
					compare(bad, blockId, face, "fluid", be, ForgeCapabilities.FLUID_HANDLER, expectFluid, held, raw);
					compare(bad, blockId, face, "item", be, ForgeCapabilities.ITEM_HANDLER, be instanceof Container,
							held, raw);
				}
				compare(bad, blockId, null, "energy", be, ForgeCapabilities.ENERGY, anyEnergy, held, raw);
				compare(bad, blockId, null, "fluid", be, ForgeCapabilities.FLUID_HANDLER, anyFluid, held, raw);
				level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				for (LazyOptional<?> optional : held) {
					if (optional.isPresent()) {
						bad.add(blockId + ": a handler handed out before removal is still present after it");
					} else {
						invalidated++;
					}
				}
				for (Object handler : raw) {
					if (answersAfterRemoval(handler)) {
						bad.add(blockId + ": a raw " + handler.getClass().getSimpleName() + " kept past removal still"
								+ " answers (a foreign mod holding it could pull the broken block's contents)");
					} else {
						silenced++;
					}
				}
			}
		}
		ForgeChecks.log("P1 {} blocks of {} block-entity entries checked on 7 sides; {} handlers"
				+ " invalidated on removal, {} raw handlers silent after it", blocks,
				ContentManifest.BLOCK_ENTITIES.size(), invalidated, silenced);
		if (blocks < 30 || silenced == 0) {
			bad.add("only " + blocks + " blocks and " + silenced + " raw handlers checked; the sweep proves nothing");
		}
		return bad;
	}

	private static void compare(List<String> bad, String blockId, Direction face, String what, BlockEntity be,
			Capability<?> cap, boolean expected, List<LazyOptional<?>> held, List<Object> raw) {
		LazyOptional<?> optional = be.getCapability(cap, face);
		boolean present = optional.resolve().isPresent();
		if (present) {
			held.add(optional);
			raw.add(optional.resolve().get());
		}
		if (present != expected) {
			bad.add(blockId + "/" + (face == null ? "no-side" : face.getName()) + ": " + what + " "
					+ (present ? "PRESENT" : "ABSENT") + ", the block entity says "
					+ (expected ? "PRESENT" : "ABSENT"));
		}
	}

	/** True if a handler of a removed block entity still offers or takes anything. */
	private static boolean answersAfterRemoval(Object handler) {
		if (handler instanceof IEnergyStorage energy) {
			return energy.canReceive() || energy.canExtract() || energy.extractEnergy(1_000, true) > 0;
		}
		if (handler instanceof IFluidHandler fluid) {
			return fluid.getTanks() > 0;
		}
		return handler instanceof IItemHandler items && items.getSlots() > 0;
	}

	private static List<String> p2Furnace(ServerLevel level, BlockPos origin) {
		List<String> bad = new ArrayList<>();
		level.setBlock(origin, ModContent.IRON_FURNACE.get().defaultBlockState(), Block.UPDATE_ALL);
		WorldlyContainer furnace = (WorldlyContainer) level.getBlockEntity(origin);
		IItemHandler fromAbove = VanillaInventoryCodeHooks.getItemHandler(level, origin, Direction.UP).orElse(null);
		IItemHandler fromBelow = VanillaInventoryCodeHooks.getItemHandler(level, origin, Direction.DOWN).orElse(null);
		if (fromAbove == null || fromBelow == null) {
			bad.add("no item handler from above or below");
			return bad;
		}
		ForgeChecks.log("P2 iron furnace: {} slot(s) from above ({}), {} from below", fromAbove.getSlots(),
				fromAbove.getClass().getSimpleName(), fromBelow.getSlots());
		if (fromAbove.getSlots() != furnace.getSlotsForFace(Direction.UP).length
				|| fromBelow.getSlots() != furnace.getSlotsForFace(Direction.DOWN).length) {
			bad.add("the handler ignores the furnace's faces (Forge's flat InvWrapper answered, not ours)");
		}
		furnace.setItem(0, new ItemStack(Items.RAW_IRON, 4));
		furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
		for (int slot = 0; slot < fromBelow.getSlots(); slot++) {
			if (fromBelow.extractItem(slot, 64, true).is(Items.RAW_IRON)) {
				bad.add("the input slot can be emptied from below");
			}
		}
		if (!fromBelow.extractItem(0, 64, false).is(Items.IRON_INGOT)) {
			bad.add("the result cannot be taken from below");
		}
		ItemStack rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(fromAbove,
				new ItemStack(Items.RAW_IRON, 2), false);
		if (!rest.isEmpty() || furnace.getItem(0).getCount() != 6) {
			bad.add("from above the input slot did not take 2 raw iron (slot 0 holds " + furnace.getItem(0) + ")");
		}
		furnace.clearContent();
		level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		return bad;
	}

	private static List<String> p3Chests(ServerLevel level, BlockPos origin) {
		List<String> bad = new ArrayList<>();
		BlockPos right = origin;
		BlockPos left = origin.east();
		BlockPos sink = origin.south(3);
		int tiers = 0;
		for (Block tier : BuiltInRegistries.BLOCK) {
			if (!(tier instanceof AbstractModChestBlock)) {
				continue;
			}
			tiers++;
			String name = BuiltInRegistries.BLOCK.getKey(tier).getPath();
			level.setBlock(right, chest(tier, ChestType.SINGLE), Block.UPDATE_ALL);
			level.setBlock(left, chest(tier, ChestType.LEFT), Block.UPDATE_ALL);
			level.setBlock(sink, chest(ModContent.IRON_CHEST.get(), ChestType.SINGLE), Block.UPDATE_ALL);
			Container rightHalf = (Container) level.getBlockEntity(right);
			Container leftHalf = (Container) level.getBlockEntity(left);
			int half = rightHalf.getContainerSize();
			LazyOptional<IItemHandler> held = level.getBlockEntity(right)
					.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
			int seen = held.map(IItemHandler::getSlots).orElse(0);
			int hopper = VanillaInventoryCodeHooks.getItemHandler(level, left, Direction.UP)
					.map(IItemHandler::getSlots).orElse(0);
			if (seen != 2 * half || hopper != 2 * half) {
				bad.add(name + ": the pair shows " + seen + " slots from the right half and " + hopper
						+ " from the left to a hopper, expected " + (2 * half));
			}
			for (int slot = 0; slot < half; slot++) {
				rightHalf.setItem(slot, new ItemStack(Items.DIAMOND));
				leftHalf.setItem(slot, new ItemStack(Items.EMERALD));
			}
			int moved = ItemMover.move(ItemLookup.get().find(level, left, Direction.UP),
					ItemLookup.get().find(level, sink, Direction.UP), 2 * half + 1);
			if (moved != 2 * half || !rightHalf.isEmpty()) {
				bad.add(name + ": our item port moved " + moved + " of " + (2 * half) + " out of the pair");
			}
			level.setBlock(left, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			boolean stale = held.isPresent();
			int after = level.getBlockEntity(right).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
					.map(IItemHandler::getSlots).orElse(0);
			if (stale || after != half) {
				bad.add(name + ": after the split the old handler is " + (stale ? "still valid" : "invalidated")
						+ " and a new one shows " + after + " slots, expected " + half);
			}
			((Container) level.getBlockEntity(sink)).clearContent();
			level.setBlock(right, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(sink, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		ForgeChecks.log("P3 {} chest tiers joined, drained through ItemLookup and split", tiers);
		if (tiers == 0) {
			bad.add("no chest tier found");
		}
		return bad;
	}

	private static BlockState chest(Block block, ChestType type) {
		return block.defaultBlockState().setValue(HorizontalMachineBlock.FACING, Direction.SOUTH)
				.setValue(AbstractModChestBlock.TYPE, type);
	}

	private static List<String> p4Tank() {
		List<String> bad = new ArrayList<>();
		FluidHolder water = FluidHolder.of(Fluids.WATER);
		FluidTank tank = new FluidTank(4_000, f -> true, f -> true, () -> { });
		EnergyTransactions.get().runCommitting(txn -> tank.insert(water, 2_500, txn));
		if (tank.createSnapshot() != 2_500) {
			bad.add("createSnapshot() is " + tank.createSnapshot() + ", not the amount 2500");
		}
		tank.readSnapshot(1_000);
		if (tank.getAmount() != 1_000 || !tank.fluid().is(Fluids.WATER)) {
			bad.add("readSnapshot(1000) left " + tank.getAmount() + " mB of " + tank.fluid());
		}
		tank.readSnapshot(2_500);
		LyingTank foreign = new LyingTank(300);
		FluidPort port = ForeignFluidPort.of(foreign);
		long lostBefore = ForgeTransferLosses.get(ForgeTransferLosses.Kind.FLUID);
		EnergyTransactions.get().runCommitting(txn -> port.insert(water, tank.extract(water, 1_000, txn), txn));
		long lost = ForgeTransferLosses.get(ForgeTransferLosses.Kind.FLUID) - lostBefore;
		ForgeChecks.log("P4 lying foreign tank took {} of 1000 promised; our tank holds {} (expected 2200),"
				+ " lost {}", foreign.stored, tank.getAmount(), lost);
		if (foreign.stored != 300 || tank.getAmount() != 2_200 || lost != 0) {
			bad.add("the shortfall did not return to the paying tank");
		}
		return bad;
	}

	private static List<String> p5Self(ServerLevel level, BlockPos origin) {
		List<String> bad = new ArrayList<>();
		for (Block block : List.of(ModContent.GENERATOR.get(), ModContent.IRON_FURNACE.get())) {
			level.setBlock(origin, block.defaultBlockState(), Block.UPDATE_ALL);
			BlockEntity be = level.getBlockEntity(origin);
			boolean found = be.getCapability(BlockCapabilityProviderForge.SELF).isPresent();
			ForgeChecks.log("P5 {}: provider found through SELF = {}", be.getClass().getSimpleName(), found);
			if (!found) {
				bad.add(be.getClass().getSimpleName() + ": no provider through SELF");
			}
			level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		return bad;
	}

	private static List<String> p6ItemFluid() {
		List<String> bad = new ArrayList<>();
		ItemFluidBridge bridge = ItemFluidBridge.get();
		FluidTank tank = new FluidTank(4_000, f -> true, f -> true, () -> { });
		SimpleContainer slots = new SimpleContainer(2);
		slots.setItem(0, new ItemStack(Items.WATER_BUCKET));
		expect(bad, "water bucket into the tank", bridge.drainSlotIntoTank(slots, 0, 1, tank, 1_000) == 1_000
				&& tank.getAmount() == 1_000 && slots.getItem(1).is(Items.BUCKET) && slots.getItem(0).isEmpty());
		slots.setItem(0, slots.removeItemNoUpdate(1));
		expect(bad, "empty bucket filled from the tank", bridge.fillSlotFromTank(slots, 0, 1, tank, 1_000) == 1_000
				&& tank.getAmount() == 0 && slots.getItem(1).is(Items.WATER_BUCKET));
		EnergyTransactions.get().runCommitting(txn -> tank.insert(FluidHolder.of(Fluids.LAVA), 1_000, txn));
		slots.setItem(0, new ItemStack(ModContent.VACUUM_CAPSULE.get(), 2));
		slots.setItem(1, ItemStack.EMPTY);
		expect(bad, "capsule filled from the tank", bridge.fillSlotFromTank(slots, 0, 1, tank, 1_000) == 1_000
				&& slots.getItem(0).getCount() == 1 && slots.getItem(1).is(ModContent.FILLED_VACUUM_CAPSULE.get())
				&& ItemFluid.get(slots.getItem(1)) == Fluids.LAVA);
		slots.setItem(0, slots.removeItemNoUpdate(1));
		expect(bad, "capsule drained into the tank", bridge.drainSlotIntoTank(slots, 0, 1, tank, 1_000) == 1_000
				&& tank.getAmount() == 1_000 && slots.getItem(1).is(ModContent.VACUUM_CAPSULE.get()));
		slots.setItem(0, new ItemStack(Items.COD_BUCKET));
		slots.setItem(1, ItemStack.EMPTY);
		expect(bad, "fish bucket refused", !bridge.isFluidContainer(new ItemStack(Items.COD_BUCKET))
				&& bridge.drainSlotIntoTank(slots, 0, 1, tank, 1_000) == 0 && slots.getItem(0).is(Items.COD_BUCKET));
		expect(bad, "capsule stack has FLUID_HANDLER_ITEM", new ItemStack(ModContent.VACUUM_CAPSULE.get())
				.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent());
		int powered = 0;
		for (Item item : ItemCapabilityRoster.energyItems()) {
			ItemStack stack = new ItemStack(item);
			if (stack.getCapability(ForgeCapabilities.ENERGY).map(s -> s.canReceive()).orElse(false)) {
				powered++;
			} else {
				bad.add(BuiltInRegistries.ITEM.getKey(item) + " has no receiving ENERGY capability");
			}
		}
		ForgeChecks.log("P6 bucket and capsule both ways, fish bucket refused; {} of {} powered items expose"
				+ " ENERGY", powered, ItemCapabilityRoster.energyItems().size());
		if (powered == 0) {
			bad.add("no powered item found");
		}
		return bad;
	}

	private static void expect(List<String> bad, String what, boolean held) {
		if (!held) {
			bad.add(what + " failed");
		}
	}

	/** Another mod's tank that promises any room in simulation and takes only {@code realRoom} for real. */
	private static final class LyingTank implements IFluidHandler {
		private final int realRoom;
		int stored;

		LyingTank(int realRoom) {
			this.realRoom = realRoom;
		}

		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return stored == 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, stored);
		}

		@Override
		public int getTankCapacity(int tank) {
			return 10_000;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return true;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (action.simulate()) {
				return resource.getAmount();
			}
			int taken = Math.min(resource.getAmount(), realRoom - stored);
			stored += taken;
			return taken;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return FluidStack.EMPTY;
		}
	}
}
