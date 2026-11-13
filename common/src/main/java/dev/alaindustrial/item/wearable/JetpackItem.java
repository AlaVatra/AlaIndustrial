package dev.alaindustrial.item.wearable;

import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.EnergyBar;
import dev.alaindustrial.item.energy.PoweredItem;
import dev.alaindustrial.item.energy.PoweredItem.LowChargeProfile;
import dev.alaindustrial.item.energy.ItemBuffers;
import dev.alaindustrial.item.energy.ItemEnergy;

import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.TooltipRole;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.level.block.WebBlock;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import dev.alaindustrial.item.energy.PoweredToolTooltip;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Jetpack (MOD-148) — a worn EU flight device: {@link ToolConfig#jetpackBuffer} EU in the chest slot,
 * charged like the rest of the powered-item family (Battery Box charge slot, worn Energy Pack,
 * foreign chargers via MOD-084). Holding jump while airborne burns {@link ToolConfig#jetpackEuPerTick}
 * EU/tick and lifts the player; with no charge left (or above {@link ToolConfig#jetpackMaxY}) the same
 * held jump turns into a powerless glide — a damped fall that never deals fall damage. Releasing
 * jump mid-air is a normal, damaging fall: the safety is the engine, not the garment.
 *
 * <p>The work is split by authority. Player motion is client-authoritative in 26.2, so the actual
 * thrust/glide velocity change happens on the client ({@code dev.alaindustrial.client.JetpackFlight});
 * the server sees the same held-jump state through the vanilla input sync
 * ({@code ServerPlayer.getLastClientInput()}) and independently burns the EU, resets the fall
 * distance and emits the exhaust — no custom packet, both sides gate on the same
 * {@link #isPowered}/{@code jump}/airborne conditions.
 *
 * <p>Like the Energy Pack it is <b>not</b> armor: no {@code ArmorMaterial}, no durability, no
 * enchantments — {@code EQUIPPABLE} alone makes it wearable and a lone attribute modifier grants
 * {@link #ARMOR_POINTS} (a point under iron: it is a flight device that also shields, not a
 * chestplate that also flies). Charge lives in the shared {@code pouch_energy} component through
 * {@link ItemEnergy}.
 *
 * <p>One class for both grades (MOD-977): the {@link JetpackTier} it is built with picks the buffer, the intake,
 * the thrust price and the worn look, and only the Mk2 has the hover mode ({@link #isHoverOn}), whose flag lives
 * in the {@code jetpack_hover} component. The rules of a flight tick are {@link JetpackMotion}'s.
 */
public class JetpackItem extends Item implements PoweredItem {

	/** Worn look while the tier-one jetpack holds charge — see {@link EnergyPackItem#ENERGY_PACK_ASSET} for
	 * why the key is built by hand; the Mk2's is {@link JetpackTier#litAsset()}. */
	public static final ResourceKey<EquipmentAsset> JETPACK_ASSET = JetpackTier.BASIC.litAsset();

	/** The drained look — dark nozzles, red indicator. Swapped by {@link #refreshWornAsset} from the
	 * single place charge changes ({@link ItemEnergy#set}), like the Energy Pack's. */
	public static final ResourceKey<EquipmentAsset> JETPACK_OFF_ASSET = JetpackTier.BASIC.offAsset();

	/** Armor points while worn — one under an iron chestplate (6): "iron or slightly weaker". */
	public static final double ARMOR_POINTS = 5.0;

	/**
	 * Players whose engine has thrusted at least once during the CURRENT airborne stretch
	 * (server side; cleared the tick they touch ground). The powerless glide is a safety net for an
	 * engine that cut out mid-flight — a jetpack that never fired this flight gives no glide and no
	 * fall protection, so wearing an empty jetpack falls exactly like wearing nothing (playtest
	 * feedback: an empty pack must not "levitate"). Transient by design: not persisted, a relog
	 * mid-air simply loses the protection.
	 */
	private static final Set<UUID> THRUSTED_THIS_FLIGHT = ConcurrentHashMap.newKeySet();

	private final JetpackTier tier;

	public JetpackItem(Properties properties, JetpackTier tier) {
		super(properties);
		this.tier = tier;
	}

	/** Which grade this item is (MOD-977). */
	public JetpackTier tier() {
		return tier;
	}

	/** The grade of the jetpack in {@code stack}; anything else reads as tier one. */
	public static JetpackTier tierOf(ItemStack stack) {
		return stack.getItem() instanceof JetpackItem jetpack ? jetpack.tier : JetpackTier.BASIC;
	}

	// --- worn look follows the charge (same contract as the Energy Pack) ---

	/**
	 * Point the stack's {@code EQUIPPABLE} at the lit or the drained asset, following its EU. Both
	 * states are written explicitly — removing the component would make the jetpack unwearable, not
	 * "default" (see {@link EnergyPackItem#refreshWornAsset}). Writes only on an actual flip.
	 */
	public static void refreshWornAsset(ItemStack stack, long eu) {
		JetpackTier tier = tierOf(stack);
		ResourceKey<EquipmentAsset> wanted = eu > 0 ? tier.litAsset() : tier.offAsset();
		Equippable current = stack.get(DataComponents.EQUIPPABLE);
		if (current == null || current.assetId().orElse(null) != wanted) {
			stack.set(DataComponents.EQUIPPABLE, equippable(wanted));
		}
	}

	private static Equippable equippable(ResourceKey<EquipmentAsset> asset) {
		return Equippable.builder(EquipmentSlot.CHEST)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC)
				.setAsset(asset)
				.setDamageOnHurt(false)
				.build();
	}

	/**
	 * The jetpack's equipment properties, applied identically by both loaders. Deliberately not
	 * {@code humanoidArmor(...)} — same reasoning as {@link EnergyPackItem#equipmentProperties}: no
	 * durability (it must not break mid-flight), no enchantability; the default asset is the drained
	 * one because a fresh jetpack comes out of the grid empty.
	 */
	public static Properties equipmentProperties(Properties properties, JetpackTier tier) {
		return properties
				.stacksTo(1)
				.component(DataComponents.EQUIPPABLE, equippable(tier.offAsset()))
				.attributes(ItemAttributeModifiers.builder()
						.add(Attributes.ARMOR,
								new AttributeModifier(tier.armorModifierId(), ARMOR_POINTS,
										AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.CHEST)
						.build());
	}

	// --- server side: EU burn, fall-damage safety, exhaust ---

	/**
	 * Whether the engine may thrust right now: any charge at all, and under the altitude ceiling.
	 * Deliberately {@code > 0}, not {@code >= jetpackEuPerTick}: the last tick of a nearly-empty
	 * jetpack burns whatever remains (the spend clamps at 0), so the buffer always drains to a clean
	 * zero — a 15–20 EU tail would otherwise sit forever below the per-tick cost, showing 1% that can
	 * never be used (playtest feedback). The client checks the same predicate before applying lift
	 * (the component is network-synchronized), so the two sides flip together give or take one sync.
	 *
	 * <p>The server's own ceiling, {@link ToolConfig#jetpackMaxY}: the server tick and the gametests ask this form.
	 */
	public static boolean isPowered(ItemStack stack, Player player) {
		return isPowered(stack, player, ToolConfig.jetpackMaxY);
	}

	/**
	 * {@link #isPowered(ItemStack, Player)} against the ceiling {@code maxY} (MOD-791). The client passes the
	 * server's ceiling ({@code ServerBalance.jetpackMaxY()}): its own file may hold another one. The server
	 * never moves the player, so a client ceiling above the server's lifted the player past it for free (no EU
	 * spent), and one below it glided while the server billed EU for thrust that never happened.
	 * Asks {@link ItemEnergy#hasCharge}, not {@code get > 0}: the same answer without reading the item's
	 * capacity from the local balance.
	 */
	public static boolean isPowered(ItemStack stack, Player player, int maxY) {
		return ItemEnergy.hasCharge(stack) && player.getY() < maxY;
	}

	/**
	 * Runs only worn ({@code slot == CHEST}); in 26.2 the equipment tick is server-only. The held
	 * jump is read from the vanilla input sync — the one movement key the server already knows.
	 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (slot != EquipmentSlot.CHEST || !(entity instanceof ServerPlayer player)) {
			return;
		}
		// Self-heal the worn look (a /give with a preset charge never passes through ItemEnergy.set).
		refreshWornAsset(stack, ItemEnergy.get(stack));
		serverFlightTick(stack, level, player, player.getLastClientInput().jump());
	}

	/**
	 * One server tick of the flight logic, with the input state passed in so gametests can drive it
	 * deterministically. On the ground the airborne-thrust session ends (and nothing else happens —
	 * a held jump there is a vanilla jump). While jump is held airborne: a powered engine burns up to
	 * {@link ToolConfig#jetpackEuPerTick} EU (the spend clamps, so the last tick takes the remainder and
	 * the buffer hits a clean 0; creative burns nothing — {@link ItemEnergy#spend}) and the exhaust
	 * plays; the fall distance is zeroed every such tick. With no thrust available the held jump only
	 * glides — zeroed fall included — if the engine actually fired during THIS airborne stretch: the
	 * glide is the safety net for a mid-flight cutout, not a free feather-fall for an empty backpack.
	 * Releasing jump stops the reset and the fall accumulates from that point — a plain, damaging
	 * fall by the player's own choice.
	 */
	public static void serverFlightTick(ItemStack stack, ServerLevel level, Player player, boolean jumpHeld) {
		if (player.onGround()) {
			THRUSTED_THIS_FLIGHT.remove(player.getUUID());
			JetpackLight.extinguish(level, player);
			return;
		}
		JetpackTier tier = tierOf(stack);
		// MOD-977: the hover mode needs no input of its own. Holding height and the slow descent cost the same, so
		// the server never needs to know whether sneak is held: the client moves the player, the server bills.
		JetpackMotion.Mode mode = JetpackMotion.mode(jumpHeld, false, isPowered(stack, player),
				hoverActive(stack, player), THRUSTED_THIS_FLIGHT.contains(player.getUUID()));
		switch (mode) {
			case THRUST, HOVER, HOVER_DESCEND -> {
				int price = mode == JetpackMotion.Mode.THRUST ? tier.euPerTick() : tier.hoverEuPerTick();
				ItemEnergy.spend(stack, Math.min(price, ItemEnergy.get(stack)), player);
				if (mode == JetpackMotion.Mode.THRUST) {
					THRUSTED_THIS_FLIGHT.add(player.getUUID());
				}
				player.resetFallDistance();
				exhaust(level, player);
				// Torch-like glow follows the engine — only while it actually runs (see JetpackLight).
				// Stamp with the server tick counter (never null, unlike a level's game time via overworld()),
				// the same clock the per-tick sweep reads, so a live light is never swept mid-flight.
				JetpackLight.ignite(level, player, level.getServer().getTickCount());
				keepFlightAllowed(player);
			}
			case GLIDE -> {
				// Glide after a mid-flight cutout: no thrust, no cost, no glow, but no fall damage either. No counter
				// reset: a glide sinks, which vanilla never counts as floating, and resetting it for a free mode would
				// let a client that holds its height on an empty jetpack float forever.
				player.resetFallDistance();
				JetpackLight.extinguish(level, player);
			}
			// Engine idle in the air, or an empty jetpack that never fired this flight: no glow, a normal fall.
			case OFF -> JetpackLight.extinguish(level, player);
		}
	}

	/**
	 * Whether the hover mode holds this player now (MOD-977): switched on, on a grade that has it, and not while
	 * riding or in creative flight, where the client leaves the motion alone and nothing must be billed, nor where
	 * the surroundings already move the player ({@link #holdsPlayerItself}). The one rule: the client moves by it,
	 * the server bills by it.
	 */
	public static boolean hoverActive(ItemStack stack, Player player) {
		return isHoverOn(stack) && !player.isPassenger() && !player.getAbilities().flying
				&& !holdsPlayerItself(player);
	}

	/**
	 * Whether the player is in a liquid, on a climbable block (ladder, vine, scaffolding) or in a cobweb (MOD-977).
	 * There vanilla governs the vertical motion itself, so a hover would fight it and bill EU for nothing: the hover
	 * pauses and picks up again on leaving. Asked of the current position on each side, never synced, so both read
	 * the same block states. The web test is the block check vanilla's inside-block pass makes, on the box shrunk by
	 * the same 1e-5, because the stuck multiplier it leaves behind is protected and cleared by every move.
	 */
	public static boolean holdsPlayerItself(Player player) {
		return player.isInLiquid() || player.onClimbable()
				|| player.level().getBlockStatesIfLoaded(player.getBoundingBox().deflate(1.0E-5))
						.anyMatch(state -> state.getBlock() instanceof WebBlock);
	}

	/**
	 * A dedicated server with {@code allow-flight=false} disconnects a player who floats for 80 ticks without the
	 * right to fly ({@code ServerGamePacketListenerImpl}): a long tier-one climb and a Mk2 hover both float by design.
	 * Resetting the counter on a tick the engine runs, on either grade, keeps it from counting a powered flight
	 * (MOD-977); a tick that burns no EU never resets it. A vanilla method that none of the three loaders patches;
	 * a mock player without a connection has nothing to reset.
	 */
	private static void keepFlightAllowed(Player player) {
		if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
			serverPlayer.connection.resetFlyingTicks();
		}
	}

	// --- hover mode (MOD-977) ---

	/** Whether the hover mode is switched on: only a grade that has it, and absent reads as off. */
	public static boolean isHoverOn(ItemStack stack) {
		return tierOf(stack).supportsHover()
				&& Boolean.TRUE.equals(stack.get(ModDataComponents.JETPACK_HOVER.get()));
	}

	/** What a press of the hover key did. */
	public enum HoverToggle {
		/** No jetpack in the chest slot: the key belongs to the jetpack, so nothing is said. */
		NOT_WORN,
		/** A jetpack without the mode: say so, or the key looks broken. */
		UNSUPPORTED,
		/** Switched on. */
		ON,
		/** Switched off. */
		OFF
	}

	/**
	 * Flips the hover mode of the jetpack in {@code player}'s chest slot. On is written as {@code true}, off removes
	 * the component, so a jetpack that never hovered stays component-identical to one fresh from the grid.
	 */
	public static HoverToggle toggleHover(Player player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!(chest.getItem() instanceof JetpackItem)) {
			return HoverToggle.NOT_WORN;
		}
		if (!tierOf(chest).supportsHover()) {
			return HoverToggle.UNSUPPORTED;
		}
		if (isHoverOn(chest)) {
			chest.remove(ModDataComponents.JETPACK_HOVER.get());
			return HoverToggle.OFF;
		}
		chest.set(ModDataComponents.JETPACK_HOVER.get(), true);
		return HoverToggle.ON;
	}

	/**
	 * Exhaust under the player's back: a puff of smoke every thrust tick and a soft hiss every eight
	 * — enough to read as an engine without a dedicated looping sound (that polish is future work).
	 */
	private static void exhaust(ServerLevel level, Player player) {
		level.sendParticles(ParticleTypes.SMOKE,
				player.getX(), player.getY() + 0.4, player.getZ(), 2, 0.15, 0.1, 0.15, 0.02);
		level.sendParticles(ParticleTypes.SMALL_FLAME,
				player.getX(), player.getY() + 0.3, player.getZ(), 1, 0.1, 0.05, 0.1, 0.01);
		if (level.getGameTime() % 8 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.05f, 1.8f);
		}
	}

	// --- item bar shows the EU charge in the LV tier colour (the numbers are in the tooltip) ---

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return EnergyBar.width(stack, MAX_BAR_WIDTH);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return EnergyBar.color(tier.energyTier());
	}

	/** MOD-707: this item's EU buffer, read by {@code ItemEnergy.capacity} through {@link PoweredItem}. */
	@Override
	public long energyCapacity(ItemStack stack, ItemBuffers buffers) {
		return tier.buffer(buffers);
	}

	@Override
	public long energyInputRate(ItemStack stack) {
		return tier.inputRate();
	}

	/** Warns once, then every second, and says so when the buffer runs out (MOD-652). */
	@Override
	public @Nullable LowChargeProfile lowChargeProfile() {
		return LowChargeProfile.JETPACK;
	}

	@Override
	public void onChargeChanged(ItemStack stack, long charge) {
		// Same contract as the pack: the worn model follows the charge from the single write point.
		refreshWornAsset(stack, charge);
	}

	/**
	 * How to fly, then the charge (MOD-148). The Mk2 (MOD-977) shows only whether hover is on and the charge; how to
	 * fly, the hover key, the slow sink and where hover pauses wait under [SHIFT], one short line each (owner's
	 * review 2026-11-12: the tooltip must be the size of the other worn items').
	 */
	@Override
	public PoweredToolTooltip toolTooltip() {
		PoweredToolTooltip tooltip = PoweredToolTooltip.of(tier.id(), List.of());
		if (!tier.supportsHover()) {
			return tooltip;
		}
		String prefix = "tooltip.alaindustrial." + tier.id();
		MachineTooltipSpec.Line usage = MachineTooltipSpec.action(prefix + ".usage");
		MachineTooltipSpec.Line key = MachineTooltipSpec.action(prefix + ".hover_key");
		MachineTooltipSpec.Line sink = MachineTooltipSpec.text(prefix + ".hover_sink", TooltipRole.INFO);
		MachineTooltipSpec.Line limits = MachineTooltipSpec.text(prefix + ".hover_limits", TooltipRole.INFO);
		return tooltip.withoutUsage().withBeforeCharge(JetpackItem::hoverLine)
				.withDetail(stack -> sink).withDetail(stack -> limits)
				.withDetail(stack -> usage).withDetail(stack -> key);
	}

	private static MachineTooltipSpec.@Nullable Line hoverLine(ItemStack stack) {
		String prefix = "tooltip.alaindustrial." + tierOf(stack).id();
		return isHoverOn(stack)
				? MachineTooltipSpec.text(prefix + ".hover_on", TooltipRole.STATE_ON)
				: MachineTooltipSpec.text(prefix + ".hover_off", TooltipRole.STATE_OFF);
	}
}
