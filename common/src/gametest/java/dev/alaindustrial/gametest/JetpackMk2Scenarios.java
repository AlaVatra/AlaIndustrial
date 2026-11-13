package dev.alaindustrial.gametest;

import dev.alaindustrial.KnobSnapshot;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemBuffers;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.energy.PoweredItem;
import dev.alaindustrial.item.wearable.JetpackItem;
import dev.alaindustrial.item.wearable.JetpackMotion;
import dev.alaindustrial.item.wearable.JetpackTier;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Loader-neutral gametest bodies for the Jetpack Mk2 (MOD-977): the craft that carries the tier-one charge, the
 * Mk2's own buffer and prices, the hover mode and its switch, and the reset of the "floating too long" counter.
 *
 * <p>As in {@link JetpackScenarios}, the flight is driven through {@link JetpackItem#serverFlightTick} with the
 * held-jump state passed in, and the airborne gate through {@code setOnGround}. The hover key is tested below its
 * payload ({@link JetpackItem#toggleHover}): the NeoForge mock player refuses mod payloads.
 */
public final class JetpackMk2Scenarios {

	/** This class's world gametests for every loader (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(JetpackMk2Scenarios::craftCarriesTheTierOneCharge,
						"jetpack_mk2_craft_carries_tier_one_charge"),
				RosterEntry.of(JetpackMk2Scenarios::benchCraftTakesTierOneAtAnyCharge,
						"jetpack_mk2_bench_craft_any_charge"),
				RosterEntry.of(JetpackMk2Scenarios::bufferAndIntakeAreTheMk2s, "jetpack_mk2_buffer_and_intake"),
				RosterEntry.of(JetpackMk2Scenarios::thrustBurnsTheMk2Price, "jetpack_mk2_thrust_burns_its_price"),
				RosterEntry.of(JetpackMk2Scenarios::hoverHoldsAfterTheFirstThrust,
						"jetpack_mk2_hover_holds_after_first_thrust"),
				RosterEntry.of(JetpackMk2Scenarios::hoverBeforeAnyThrustDoesNothing,
						"jetpack_mk2_hover_needs_a_thrust_first"),
				RosterEntry.of(JetpackMk2Scenarios::hoverSinksSlowlyAndLandsSoft, "jetpack_mk2_hover_sinks_slowly"),
				RosterEntry.of(JetpackMk2Scenarios::hoverWithNoSinkHoldsTheHeight,
						"jetpack_mk2_hover_sink_zero_holds"),
				RosterEntry.of(JetpackMk2Scenarios::drainedHoverGlides, "jetpack_mk2_drained_hover_glides"),
				RosterEntry.of(JetpackMk2Scenarios::hoverPausesWhereTheWorldMovesThePlayer,
						"jetpack_mk2_hover_pauses_in_water_ladder_web"),
				RosterEntry.of(JetpackMk2Scenarios::hoverKeyTogglesOnlyTheMk2, "jetpack_mk2_hover_key_toggles"),
				RosterEntry.of(JetpackMk2Scenarios::tierOneIgnoresAForgedHoverFlag,
						"jetpack_mk2_tier_one_ignores_hover_flag"),
				RosterEntry.of(JetpackMk2Scenarios::poweredFlightResetsTheFloatingCounter,
						"jetpack_mk2_resets_floating_counter"),
				RosterEntry.of(JetpackMk2Scenarios::clientReadsTheServersMultipliers,
						"jetpack_mk2_client_reads_server_multipliers"));

		private Roster() {}
	}

	private JetpackMk2Scenarios() {}

	private static ItemStack mk2(long eu) {
		ItemStack stack = new ItemStack(ModContent.JETPACK_MK2.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	private static ItemStack tierOne(long eu) {
		ItemStack stack = new ItemStack(ModContent.JETPACK.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	private static ItemStack hovering(ItemStack stack) {
		stack.set(ModDataComponents.JETPACK_HOVER.get(), true);
		return stack;
	}

	/** {@code player} in the air with an accumulated fall, its flight session cleared by a ground tick first. */
	private static <P extends Player> P airborne(GameTestHelper helper, P player) {
		player.setOnGround(true);
		JetpackItem.serverFlightTick(mk2(0), helper.getLevel(), player, false);
		player.setOnGround(false);
		player.fallDistance = 10.0;
		return player;
	}

	private static Player airborne(GameTestHelper helper) {
		return airborne(helper, helper.makeMockPlayer(GameType.SURVIVAL));
	}

	/**
	 * The Mk2 grid takes a tier-one jetpack in any state of charge and hands its EU to the Mk2: 30 000 EU in, the
	 * same 30 000 EU out. Red if the recipe is a plain shaped one (the Mk2 would come out empty).
	 *
	 * @implements MOD-977-MK2-CRAFT — the craft carries the tier-one charge into the Mk2
	 */
	public static void craftCarriesTheTierOneCharge(GameTestHelper helper) {
		for (long charge : new long[] {30_000L, 0L, 1_234L}) {
			List<ItemStack> grid = mk2Grid(tierOne(charge));
			ServerLevel level = helper.getLevel();
			CraftingInput input = CraftingInput.of(3, 3, grid);
			RecipeHolder<CraftingRecipe> recipe = level.getServer().getRecipeManager()
					.getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
			if (recipe == null) {
				helper.fail("the Mk2 recipe did not resolve with a tier-one jetpack at " + charge + " EU");
				return;
			}
			ItemStack output = recipe.value().assemble(input);
			if (!output.is(ModContent.JETPACK_MK2.get())) {
				helper.fail("the grid produced " + output + " instead of the Jetpack Mk2");
				return;
			}
			if (ItemEnergy.get(output) != charge) {
				helper.fail("a tier-one jetpack at " + charge + " EU must give a Mk2 at the same charge, got "
						+ ItemEnergy.get(output));
				return;
			}
		}
		helper.succeed();
	}

	/** The Mk2 grid of the shipped recipe, row by row, with {@code jetpack} in the centre. */
	private static List<ItemStack> mk2Grid(ItemStack jetpack) {
		List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
		grid.set(0, new ItemStack(ModContent.ELECTRUM_REINFORCED_PLATE.get()));
		grid.set(1, new ItemStack(ModContent.ADVANCED_CIRCUIT.get()));
		grid.set(2, new ItemStack(ModContent.ELECTRUM_REINFORCED_PLATE.get()));
		grid.set(3, new ItemStack(ModContent.REINFORCED_BEARING.get()));
		grid.set(4, jetpack);
		grid.set(5, new ItemStack(ModContent.REINFORCED_BEARING.get()));
		grid.set(6, new ItemStack(ModContent.GOLD_CABLE.get()));
		grid.set(7, new ItemStack(ModContent.ENERGY_CRYSTAL.get()));
		grid.set(8, new ItemStack(ModContent.GOLD_CABLE.get()));
		return grid;
	}

	/**
	 * The way a player crafts it (owner's review 2026-11-12: "from any tier-one jetpack, at any charge"): an empty,
	 * a half-charged and a full tier-one jetpack, worn first so the stack carries the look a used one has, go into a
	 * real {@link CraftingMenu}. The result slot shows the Mk2 at the jetpack's charge, a shift-click takes it into
	 * the inventory and uses the jetpack up, and the recipe-book auto-fill (the path the recipe book and the viewers'
	 * fill button drive) accepts the jetpack too. Red if the jetpack ingredient stops matching any one charge.
	 *
	 * @implements MOD-977-MK2-CRAFT-ANY — the craft at a bench takes a tier-one jetpack at any charge
	 */
	public static void benchCraftTakesTierOneAtAnyCharge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long full = ToolConfig.jetpackBuffer;
		for (long charge : new long[] {0L, full / 2, full}) {
			ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
			player.setItemSlot(EquipmentSlot.CHEST, tierOne(charge));
			JetpackItem.refreshWornAsset(player.getItemBySlot(EquipmentSlot.CHEST), charge);
			ItemStack used = player.getItemBySlot(EquipmentSlot.CHEST).copy();
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			CraftingMenu menu = new CraftingMenu(1, player.getInventory(),
					ContainerLevelAccess.create(level, helper.absolutePos(new BlockPos(1, 2, 1))));
			List<Slot> inputs = menu.getInputGridSlots();
			List<ItemStack> grid = mk2Grid(used);
			for (int i = 0; i < grid.size(); i++) {
				inputs.get(i).set(grid.get(i).copy());
			}
			menu.slotsChanged(inputs.get(0).container);
			ItemStack shown = menu.getResultSlot().getItem();
			if (!shown.is(ModContent.JETPACK_MK2.get()) || ItemEnergy.get(shown) != charge) {
				helper.fail("a bench with a tier-one jetpack at " + charge + " EU shows " + shown + " at "
						+ ItemEnergy.get(shown) + " EU, expected the Mk2 at the same charge");
				return;
			}
			menu.quickMoveStack(player, 0);
			ItemStack taken = findInInventory(player, ModContent.JETPACK_MK2.get());
			if (taken.isEmpty() || ItemEnergy.get(taken) != charge) {
				helper.fail("shift-clicking the result at " + charge + " EU must put the Mk2 at that charge in the"
						+ " inventory, found " + taken);
				return;
			}
			if (!inputs.get(4).getItem().isEmpty()) {
				helper.fail("taking the Mk2 must use up the tier-one jetpack, the centre still holds "
						+ inputs.get(4).getItem());
				return;
			}
			if (!autoFillCanCraft(level, used)) {
				helper.fail("the recipe-book auto-fill must accept a tier-one jetpack at " + charge + " EU");
				return;
			}
		}
		helper.succeed();
	}

	private static ItemStack findInInventory(Player player, Item item) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Whether the recipe-book auto-fill, which counts only the stacks vanilla calls usable, can lay out the Mk2. */
	private static boolean autoFillCanCraft(ServerLevel level, ItemStack jetpack) {
		RecipeHolder<CraftingRecipe> recipe = level.getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, mk2Grid(tierOne(0))), level).orElse(null);
		if (recipe == null) {
			return false;
		}
		StackedItemContents contents = new StackedItemContents();
		for (ItemStack part : mk2Grid(ItemStack.EMPTY)) {
			contents.accountSimpleStack(part);
		}
		contents.accountSimpleStack(jetpack);
		return contents.canCraft(recipe.value(), null);
	}

	/**
	 * The Mk2 reads its own knobs: buffer {@code jetpackMk2Buffer}, intake {@code jetpackMk2InputRate}, and tier one
	 * keeps its own. Red if the Mk2 resolves to the tier-one numbers.
	 *
	 * @implements MOD-977-MK2-BUFFER — the Mk2's buffer and intake are its own knobs
	 */
	public static void bufferAndIntakeAreTheMk2s(GameTestHelper helper) {
		ItemStack mk2 = mk2(0);
		ItemStack tierOne = tierOne(0);
		if (ItemEnergy.capacity(mk2) != ToolConfig.jetpackMk2Buffer) {
			helper.fail("the Mk2 must hold jetpackMk2Buffer = " + ToolConfig.jetpackMk2Buffer + ", holds "
					+ ItemEnergy.capacity(mk2));
		}
		if (ItemEnergy.capacity(tierOne) != ToolConfig.jetpackBuffer) {
			helper.fail("tier one must keep jetpackBuffer, holds " + ItemEnergy.capacity(tierOne));
		}
		PoweredItem mk2Item = (PoweredItem) mk2.getItem();
		PoweredItem tierOneItem = (PoweredItem) tierOne.getItem();
		if (mk2Item.energyInputRate(mk2) != ToolConfig.jetpackMk2InputRate
				|| tierOneItem.energyInputRate(tierOne) != ToolConfig.jetpackInputRate) {
			helper.fail("intake: Mk2 " + mk2Item.energyInputRate(mk2) + ", tier one "
					+ tierOneItem.energyInputRate(tierOne));
		}
		if (JetpackTier.MK2.buffer(ItemBuffers.live()) != ToolConfig.jetpackMk2Buffer) {
			helper.fail("JetpackTier.MK2 must pick jetpackMk2Buffer out of the buffers");
		}
		ItemEnergy.set(mk2, Long.MAX_VALUE);
		if (ItemEnergy.get(mk2) != ToolConfig.jetpackMk2Buffer) {
			helper.fail("a write past the buffer must clamp to jetpackMk2Buffer, got " + ItemEnergy.get(mk2));
		}
		helper.succeed();
	}

	/**
	 * A thrust tick of the Mk2 burns {@code jetpackMk2EuPerTick} and zeroes the fall.
	 *
	 * @implements MOD-977-MK2-THRUST — a thrust tick burns jetpackMk2EuPerTick
	 */
	public static void thrustBurnsTheMk2Price(GameTestHelper helper) {
		Player player = airborne(helper);
		ItemStack jetpack = mk2(ToolConfig.jetpackMk2Buffer);
		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		if (ItemEnergy.get(jetpack) != ToolConfig.jetpackMk2Buffer - ToolConfig.jetpackMk2EuPerTick) {
			helper.fail("one Mk2 thrust tick must burn " + ToolConfig.jetpackMk2EuPerTick + " EU, left "
					+ ItemEnergy.get(jetpack));
		}
		if (player.fallDistance != 0.0) {
			helper.fail("a thrust tick must reset the fall distance, got " + player.fallDistance);
		}
		helper.succeed();
	}

	/**
	 * With hover on, after one thrust, a released jump keeps the engine running at the hover price and the fall at
	 * zero, tick after tick. Red if hover is not implemented (the released jump would be a plain fall).
	 *
	 * @implements MOD-977-MK2-HOVER — hover holds the height after the first thrust, at jetpackMk2HoverEuPerTick
	 */
	public static void hoverHoldsAfterTheFirstThrust(GameTestHelper helper) {
		Player player = airborne(helper);
		ItemStack jetpack = hovering(mk2(ToolConfig.jetpackMk2Buffer));
		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		long afterThrust = ItemEnergy.get(jetpack);
		for (int tick = 1; tick <= 5; tick++) {
			player.fallDistance = 4.0;
			JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, false);
			if (ItemEnergy.get(jetpack) != afterThrust - (long) tick * ToolConfig.jetpackMk2HoverEuPerTick) {
				helper.fail("hover tick " + tick + " must burn " + ToolConfig.jetpackMk2HoverEuPerTick + " EU, left "
						+ ItemEnergy.get(jetpack));
				return;
			}
			if (player.fallDistance != 0.0) {
				helper.fail("a hover tick must reset the fall distance, got " + player.fallDistance);
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * Hover on, but no thrust yet this flight: a released jump (a plain jump, a step off a ledge) burns nothing and
	 * the fall keeps counting. Red if hover froze every jump in the air.
	 *
	 * @implements MOD-977-MK2-HOVER-GATE — hover picks up only after the first thrust of a flight
	 */
	public static void hoverBeforeAnyThrustDoesNothing(GameTestHelper helper) {
		Player player = airborne(helper);
		ItemStack jetpack = hovering(mk2(ToolConfig.jetpackMk2Buffer));
		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, false);
		if (ItemEnergy.get(jetpack) != ToolConfig.jetpackMk2Buffer) {
			helper.fail("hover before any thrust must burn nothing, left " + ItemEnergy.get(jetpack));
		}
		if (player.fallDistance != 10.0) {
			helper.fail("hover before any thrust must leave the fall alone, got " + player.fallDistance);
		}
		helper.succeed();
	}

	/**
	 * One tick of a hover the way the client moves it and the server bills it: the client's rule
	 * ({@link JetpackMotion#verticalSpeed} with the server's sink, read through {@link ServerBalance}), vanilla's move
	 * by that speed, then the server's tick. Returns the vertical speed the client set.
	 */
	private static double hoverTick(GameTestHelper helper, Player player, ItemStack jetpack) {
		JetpackMotion.Mode mode = JetpackMotion.mode(false, false, JetpackItem.isPowered(jetpack, player),
				JetpackItem.hoverActive(jetpack, player), true);
		double sink = JetpackTier.MK2.pick(0.0, ServerBalance.jetpackMk2HoverSinkPerTick());
		double vy = JetpackMotion.verticalSpeed(mode, player.getDeltaMovement().y, 1.0, sink);
		player.setDeltaMovement(0.0, vy, 0.0);
		player.move(MoverType.SELF, player.getDeltaMovement());
		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, false);
		return vy;
	}

	/** A Mk2 with hover on, the flight session opened by one thrust tick, its wearer {@code above} blocks up. */
	private static ItemStack hoverAbove(GameTestHelper helper, Player player, double above) {
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
		BlockPos floor = helper.absolutePos(new BlockPos(2, 2, 2));
		player.setPos(floor.getX() + 0.5, floor.getY() + above, floor.getZ() + 0.5);
		airborne(helper, player);
		ItemStack jetpack = hovering(mk2(ToolConfig.jetpackMk2Buffer));
		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		return jetpack;
	}

	/**
	 * The hover holds the player but lets them sink (owner's review 2026-11-12: "like levitation in reverse"): ten
	 * seconds lose {@code 200 x jetpackMk2HoverSinkPerTick} blocks, four at the default, at the hover price and with
	 * the fall zeroed every tick, so the landing four blocks down deals no damage. Red if the hover pins the height
	 * (no drop) or stops zeroing the fall (the landing hurts).
	 *
	 * @implements MOD-977-MK2-HOVER-SINK — a hover sinks by jetpackMk2HoverSinkPerTick and lands without damage
	 */
	public static void hoverSinksSlowlyAndLandsSoft(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		double sink = ServerBalance.jetpackMk2HoverSinkPerTick();
		ItemStack jetpack = hoverAbove(helper, player, 200 * sink + 0.5);
		double start = player.getY();
		long charge = ItemEnergy.get(jetpack);
		for (int tick = 1; tick <= 200; tick++) {
			hoverTick(helper, player, jetpack);
			if (player.fallDistance != 0.0) {
				helper.fail("hover tick " + tick + " must leave no fall to land with, got " + player.fallDistance);
				return;
			}
		}
		if (Math.abs(start - player.getY() - 200 * sink) > 1e-6) {
			helper.fail("ten seconds of hover must sink " + 200 * sink + " blocks, sank " + (start - player.getY()));
			return;
		}
		if (charge - ItemEnergy.get(jetpack) != 200L * ToolConfig.jetpackMk2HoverEuPerTick) {
			helper.fail("the slow sink must cost the hover price, spent " + (charge - ItemEnergy.get(jetpack)));
			return;
		}
		float health = player.getHealth();
		for (int tick = 0; tick < 100 && !player.onGround(); tick++) {
			hoverTick(helper, player, jetpack);
		}
		if (!player.onGround() || player.getHealth() != health) {
			helper.fail("the hover must set the player down unhurt: on ground " + player.onGround() + ", health "
					+ player.getHealth() + " of " + health);
			return;
		}
		helper.succeed();
	}

	/**
	 * With {@code jetpackMk2HoverSinkPerTick = 0} the hover holds the height exactly, the old behaviour an operator can
	 * still choose. Red if the sink ignores the knob.
	 *
	 * @implements MOD-977-MK2-HOVER-SINK-ZERO — a sink of 0 holds the height
	 */
	public static void hoverWithNoSinkHoldsTheHeight(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("jetpackMk2HoverSinkPerTick", 0.0);
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack jetpack = hoverAbove(helper, player, 3.0);
			double start = player.getY();
			for (int tick = 0; tick < 200; tick++) {
				hoverTick(helper, player, jetpack);
			}
			if (player.getY() != start) {
				helper.fail("a sink of 0 must hold the height, moved " + (player.getY() - start));
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * A Mk2 that runs dry while hovering glides without a held jump: no EU, no fall damage.
	 *
	 * @implements MOD-977-MK2-HOVER-GLIDE — a drained hover glides without a held jump
	 */
	public static void drainedHoverGlides(GameTestHelper helper) {
		Player player = airborne(helper);
		ItemStack jetpack = hovering(mk2(ToolConfig.jetpackMk2EuPerTick));
		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		if (ItemEnergy.get(jetpack) != 0) {
			helper.fail("the only charged tick must drain the Mk2 to 0, left " + ItemEnergy.get(jetpack));
		}
		player.fallDistance = 10.0;
		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, false);
		if (player.fallDistance != 0.0) {
			helper.fail("a drained Mk2 with hover on must glide (fall reset) without a held jump, got "
					+ player.fallDistance);
		}
		helper.succeed();
	}

	/**
	 * Hover pauses where the world already moves the player: in water, on a ladder, in a cobweb a released jump
	 * burns no EU and leaves the fall alone, and {@link JetpackItem#hoverActive} — the one rule the client moves by —
	 * says no. The same flight in open air hovers, so the cells are what differs. Red if the hover ignores them (the
	 * client would hold the player still against the water while the server billed 25 EU a tick).
	 *
	 * @implements MOD-977-MK2-HOVER-MEDIUM — hover pauses in a liquid, on a climbable block and in a cobweb
	 */
	public static void hoverPausesWhereTheWorldMovesThePlayer(GameTestHelper helper) {
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.WATER);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 2, 1), Blocks.LADDER);
		helper.setBlock(new BlockPos(5, 2, 1), Blocks.COBWEB);
		Object[][] cells = {
				{"in open air", new BlockPos(1, 2, 5), true},
				{"in water", new BlockPos(1, 2, 1), false},
				{"on a ladder", new BlockPos(3, 2, 1), false},
				{"in a cobweb", new BlockPos(5, 2, 1), false}};
		for (Object[] cell : cells) {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			BlockPos abs = helper.absolutePos((BlockPos) cell[1]);
			player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
			// Water is a state vanilla refreshes once a tick, not a block lookup.
			player.baseTick();
			airborne(helper, player);
			ItemStack jetpack = hovering(mk2(ToolConfig.jetpackMk2Buffer));
			JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
			long afterThrust = ItemEnergy.get(jetpack);
			player.fallDistance = 10.0;
			JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, false);
			boolean hovered = ItemEnergy.get(jetpack) == afterThrust - ToolConfig.jetpackMk2HoverEuPerTick
					&& player.fallDistance == 0.0;
			boolean idle = ItemEnergy.get(jetpack) == afterThrust && player.fallDistance == 10.0;
			boolean expected = (Boolean) cell[2];
			if (expected ? !hovered : !idle) {
				helper.fail(cell[0] + ", the hover must " + (expected ? "hold" : "pause") + ": EU "
						+ ItemEnergy.get(jetpack) + " (was " + afterThrust + "), fall " + player.fallDistance);
				return;
			}
			if (JetpackItem.hoverActive(jetpack, player) != expected) {
				helper.fail(cell[0] + ", hoverActive must say " + expected + " to the client too");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * The hover key flips the worn Mk2 on and off (off removes the component), says a tier-one jetpack cannot hover
	 * without touching it, and does nothing with no jetpack worn.
	 *
	 * @implements MOD-977-MK2-HOVER-KEY — the hover key toggles the worn Mk2 only
	 */
	public static void hoverKeyTogglesOnlyTheMk2(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		if (JetpackItem.toggleHover(player) != JetpackItem.HoverToggle.NOT_WORN) {
			helper.fail("with nothing worn the key must do nothing");
			return;
		}
		ItemStack mk2 = mk2(1_000);
		player.setItemSlot(EquipmentSlot.CHEST, mk2);
		ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
		if (JetpackItem.toggleHover(player) != JetpackItem.HoverToggle.ON || !JetpackItem.isHoverOn(worn)) {
			helper.fail("the first press must switch the worn Mk2's hover on");
			return;
		}
		if (JetpackItem.toggleHover(player) != JetpackItem.HoverToggle.OFF || JetpackItem.isHoverOn(worn)
				|| worn.has(ModDataComponents.JETPACK_HOVER.get())) {
			helper.fail("the second press must switch hover off and remove the component");
			return;
		}
		ItemStack tierOne = tierOne(1_000);
		player.setItemSlot(EquipmentSlot.CHEST, tierOne);
		if (JetpackItem.toggleHover(player) != JetpackItem.HoverToggle.UNSUPPORTED
				|| player.getItemBySlot(EquipmentSlot.CHEST).has(ModDataComponents.JETPACK_HOVER.get())) {
			helper.fail("a tier-one jetpack must answer that it cannot hover and stay untouched");
			return;
		}
		helper.succeed();
	}

	/**
	 * A tier-one jetpack carrying a hover flag (a command, an edited stack) still has no hover: a released jump after
	 * a thrust burns nothing and the fall counts.
	 *
	 * @implements MOD-977-MK2-TIER-ONE — tier one never hovers, whatever its components say
	 */
	public static void tierOneIgnoresAForgedHoverFlag(GameTestHelper helper) {
		Player player = airborne(helper);
		ItemStack jetpack = hovering(tierOne(ToolConfig.jetpackBuffer));
		if (JetpackItem.isHoverOn(jetpack)) {
			helper.fail("isHoverOn must be false on a tier-one jetpack");
		}
		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		long afterThrust = ItemEnergy.get(jetpack);
		player.fallDistance = 10.0;
		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, false);
		if (ItemEnergy.get(jetpack) != afterThrust || player.fallDistance != 10.0) {
			helper.fail("a released jump on tier one must stay a plain fall: EU " + ItemEnergy.get(jetpack)
					+ " (was " + afterThrust + "), fall " + player.fallDistance);
		}
		helper.succeed();
	}

	/**
	 * While a jetpack engine runs (thrust on either grade, hover on the Mk2) the server resets its "floating too
	 * long" counter, so a dedicated server with {@code allow-flight=false} kicks neither a long climb nor a hover
	 * after four seconds. A tick that burns no EU (engine idle, a drained glide) leaves the counter alone: wearing
	 * the item is not a licence to float. The counter is read by reflection: vanilla keeps it private, and the dev
	 * runtime uses the unobfuscated names on every loader.
	 *
	 * @implements MOD-977-MK2-KICK — a powered flight on either grade resets the floating counter, nothing else does
	 */
	public static void poweredFlightResetsTheFloatingCounter(GameTestHelper helper) {
		ServerPlayer player = airborne(helper, AlaGameTestHelper.survivalPlayer(helper));
		Field counter;
		try {
			counter = ServerGamePacketListenerImpl.class.getDeclaredField("aboveGroundTickCount");
			counter.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			helper.fail("cannot reach ServerGamePacketListenerImpl.aboveGroundTickCount: " + e);
			return;
		}
		ItemStack mk2 = hovering(mk2(ToolConfig.jetpackMk2Buffer));
		ItemStack tierOne = tierOne(ToolConfig.jetpackBuffer);
		// In this order: the first thrust opens the flight session that the hover and the glide need.
		Object[][] cases = {
				{"a Mk2 thrust tick", mk2, true, 0},
				{"a Mk2 hover tick", mk2, false, 0},
				{"a tier-one thrust tick", tierOne, true, 0},
				{"a tier-one idle tick", tierOne, false, 50},
				{"a drained glide tick", mk2(0), true, 50}};
		try {
			for (Object[] c : cases) {
				counter.setInt(player.connection, 50);
				JetpackItem.serverFlightTick((ItemStack) c[1], helper.getLevel(), player, (Boolean) c[2]);
				if (counter.getInt(player.connection) != (Integer) c[3]) {
					helper.fail(c[0] + " must leave the floating counter at " + c[3] + ", it reads "
							+ counter.getInt(player.connection));
					return;
				}
			}
		} catch (IllegalAccessException e) {
			helper.fail("cannot read the floating counter: " + e);
			return;
		}
		helper.succeed();
	}

	/**
	 * The client moves the player by the SERVER's multipliers and draws the Mk2's bar against the server's buffer
	 * (MOD-791): a snapshot carrying other numbers reaches {@link ServerBalance} and {@link ItemBuffers#shown()},
	 * and leaves {@link ToolConfig} alone. Received and forgotten inside this one synchronous body.
	 *
	 * @implements MOD-977-MK2-BALANCE — the client reads the Mk2 multipliers and buffer from the server
	 */
	public static void clientReadsTheServersMultipliers(GameTestHelper helper) {
		double localRise = ToolConfig.jetpackMk2RiseMultiplier;
		double localHorizontal = ToolConfig.jetpackMk2HorizontalMultiplier;
		int localBuffer = ToolConfig.jetpackMk2Buffer;
		double localSink = ToolConfig.jetpackMk2HoverSinkPerTick;
		ServerBalance.reset();
		try {
			byte[] snapshot = KnobSnapshot.of(Map.of("jetpackMk2RiseMultiplier", localRise + 1.0,
					"jetpackMk2HorizontalMultiplier", localHorizontal + 0.25, "jetpackMk2Buffer", localBuffer + 7,
					"jetpackMk2HoverSinkPerTick", localSink + 0.05))
					.encode();
			if (!ServerBalance.receive(snapshot)) {
				helper.fail("the client refused a snapshot carrying the Mk2 knobs");
				return;
			}
			if (JetpackTier.MK2.pick(1.0, ServerBalance.jetpackMk2RiseMultiplier()) != localRise + 1.0
					|| ServerBalance.jetpackMk2HorizontalMultiplier() != localHorizontal + 0.25) {
				helper.fail("the client must read the server's multipliers, got rise "
						+ ServerBalance.jetpackMk2RiseMultiplier() + ", horizontal "
						+ ServerBalance.jetpackMk2HorizontalMultiplier());
				return;
			}
			if (JetpackTier.BASIC.pick(1.0, ServerBalance.jetpackMk2RiseMultiplier()) != 1.0) {
				helper.fail("tier one must rise at 1.0 whatever the Mk2 knob says");
				return;
			}
			if (ServerBalance.jetpackMk2HoverSinkPerTick() != localSink + 0.05) {
				helper.fail("the client must sink the hover by the server's rate, got "
						+ ServerBalance.jetpackMk2HoverSinkPerTick());
				return;
			}
			if (JetpackTier.MK2.buffer(ItemBuffers.shown()) != localBuffer + 7) {
				helper.fail("the client must draw the Mk2's bar against the server's buffer");
				return;
			}
		} finally {
			ServerBalance.reset();
		}
		if (ToolConfig.jetpackMk2RiseMultiplier != localRise || ToolConfig.jetpackMk2Buffer != localBuffer) {
			helper.fail("receiving the snapshot rewrote ToolConfig");
			return;
		}
		helper.succeed();
	}
}
