package dev.alaindustrial.item.apiary;

import dev.alaindustrial.block.HiveAttachmentBlock;
import dev.alaindustrial.client.tooltip.ItemTooltip;
import dev.alaindustrial.menu.ApiaryMapMenu;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The apiary map (MOD-923): a list of up to {@link ApiaryMapPoints#MAX} hive attachments that tells an apiary station
 * which hives its drones serve.
 *
 * <p>Right-click a hive attachment to write it, shift+right-click it to cross it out; right-click anywhere else in the
 * air to open the list, where every row can be crossed out too (owner decision 2026-11-07, item 17). A row is the
 * attachment's {@link GlobalPos}: an attachment in another dimension is kept and shown as such, and one attachment may
 * be written on several maps.
 */
public class ApiaryMapItem extends Item {

	public ApiaryMapItem(Properties properties) {
		super(properties);
	}

	/** What a click on an attachment did; the tests read it, the player reads its action-bar line. */
	public enum Edit {
		WRITTEN("message.alaindustrial.apiary_map.written", ChatFormatting.GREEN),
		ALREADY("message.alaindustrial.apiary_map.already", ChatFormatting.GRAY),
		FULL("message.alaindustrial.apiary_map.full", ChatFormatting.RED),
		CROSSED_OUT("message.alaindustrial.apiary_map.crossed_out", ChatFormatting.YELLOW),
		NOT_ON_MAP("message.alaindustrial.apiary_map.not_on_map", ChatFormatting.GRAY);

		private final String key;
		private final ChatFormatting colour;

		Edit(String key, ChatFormatting colour) {
			this.key = key;
			this.colour = colour;
		}
	}

	/**
	 * Writes ({@code crossOut == false}) or crosses out the attachment at {@code pos} on {@code stack}. Server-side,
	 * shared by the click and by the tests, so both go through the one rule: no duplicate, no ninth row.
	 */
	public static Edit edit(ItemStack stack, GlobalPos pos, boolean crossOut) {
		ApiaryMapPoints points = points(stack);
		if (crossOut) {
			if (!points.contains(pos)) {
				return Edit.NOT_ON_MAP;
			}
			stack.set(ModDataComponents.APIARY_MAP_POINTS.get(), points.without(pos));
			return Edit.CROSSED_OUT;
		}
		if (points.contains(pos)) {
			return Edit.ALREADY;
		}
		if (points.isFull()) {
			return Edit.FULL;
		}
		stack.set(ModDataComponents.APIARY_MAP_POINTS.get(), points.with(pos));
		return Edit.WRITTEN;
	}

	/** The rows on a map; an unwritten map has none. */
	public static ApiaryMapPoints points(ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.APIARY_MAP_POINTS.get(), ApiaryMapPoints.EMPTY);
	}

	/**
	 * A click on a hive attachment writes it or crosses it out. Any other block does nothing: the list opens from
	 * the air ({@link #use}), so a click on a chest or a hive is never mistaken for one.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!(level.getBlockState(pos).getBlock() instanceof HiveAttachmentBlock)) {
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		Edit edit = edit(stack, GlobalPos.of(level.dimension(), pos), player.isSecondaryUseActive());
		int count = points(stack).size();
		player.sendSystemMessage(Component.translatable(edit.key, count, ApiaryMapPoints.MAX)
				.withStyle(edit.colour), true);
		if (edit == Edit.WRITTEN || edit == Edit.CROSSED_OUT) {
			level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f,
					edit == Edit.WRITTEN ? 1.2f : 0.8f);
		}
		return InteractionResult.SUCCESS;
	}

	/** Right-click in the air opens the list. The menu reads the map out of the hand on both sides. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		serverPlayer.openMenu(new SimpleMenuProvider(
				(syncId, inventory, p) -> new ApiaryMapMenu(syncId, inventory),
				Component.translatable("item.alaindustrial.apiary_map")));
		return InteractionResult.SUCCESS;
	}

	/** The map the player holds, main hand first, or empty if neither hand has one. */
	public static ItemStack heldMap(Player player) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (main.getItem() instanceof ApiaryMapItem) {
			return main;
		}
		ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
		return off.getItem() instanceof ApiaryMapItem ? off : ItemStack.EMPTY;
	}

	// Item#appendHoverText is soft-deprecated by Mojang but is still the only hook for a line computed from the
	// stack's own data (see TeleporterRemoteItem, MOD-498).
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		// Style 09b (MOD-958): the purpose line comes from the hook; the count is a fact, the how a key action.
		new ItemTooltip().info("tooltip.alaindustrial.apiary_map.count", points(stack).size(), ApiaryMapPoints.MAX)
				.key("tooltip.alaindustrial.apiary_map.how")
				.emit(adder);
	}
}
