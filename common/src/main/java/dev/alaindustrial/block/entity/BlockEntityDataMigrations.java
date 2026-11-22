package dev.alaindustrial.block.entity;

import java.util.List;
import java.util.function.Consumer;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.ItemStack;

/**
 * The save-format ladder for block entities (MOD-556) — the block-entity twin of the config file's
 * ladder in {@link dev.alaindustrial.config.ConfigSchema} ({@code VERSION} plus its migrations).
 *
 * <p><b>Why this exists.</b> Until MOD-556 no block entity wrote a version at all, so a change to a
 * saved layout had to be recognised by guesswork at the point of reading: the Battery Box worked out
 * that a save predated its discharge slot by noticing an item without an EU buffer sitting in that
 * slot. A guess like that is invisible in the code (it looks like an ordinary load), it cannot say
 * when it is finished, and it re-runs on every load forever. With a version on disk the same change
 * becomes a rung of a ladder that runs exactly once, and the risk a layout change carries for an
 * existing world is finally written down.
 *
 * <p><b>How to add the next hop</b> (deliberately three steps, in the shape of the config file's ladder):
 * <ol>
 *   <li>Write a {@code private static void vNtoVN1(EnergyBlockEntity be)} that repairs the data in
 *       place. It sees the block entity as the PREVIOUS rung left it, so each step only has to know
 *       about its own hop; a step that concerns one kind of block opens with an {@code instanceof}
 *       and returns for everything else.</li>
 *   <li>Append {@code new Step(N, BlockEntityDataMigrations::vNtoVN1)} to {@link #STEPS}.</li>
 *   <li>Raise {@link #DATA_VERSION} to {@code N + 1}.</li>
 * </ol>
 * {@code PersistenceScenarios.mod556_dataVersionMatchesTheLadder} pins the pair: {@link #STEPS} must
 * hold exactly one rung per version hop, ascending, so a bump with no rung (every old save silently
 * unconverted) or a rung with no bump (it never runs) fails the build instead of a player's world.
 *
 * <p><b>Every step must be safe to run twice.</b> An older jar of this mod reads a new save fine — it
 * simply never asks for {@link #DATA_VERSION_KEY} — but when it saves again the key is gone, so the
 * next launch of a current jar sees the data as version 0 and walks the ladder over it a second time.
 * A rung that is idempotent on already-repaired data (the Battery Box one is: a correct layout has
 * nothing that looks like the old one) survives that round trip; a rung that is not would corrupt on
 * it. This is the price of downgrade tolerance, and it is worth paying — the alternative is a mod
 * that destroys a world when the player boots the previous version once.
 *
 * <p><b>Where a step is applied.</b> In one place, and nobody has to remember it (MOD-701, ADR-037).
 * {@code BlockEntity#loadWithComponents} and {@code #loadCustomOnly} are {@code final} in 26.2 (checked
 * with {@code javap}), so there is no after-the-whole-load hook of the game's to hang a call on;
 * {@link EnergyBlockEntity} builds its own instead. Its {@code loadAdditional} is {@code final} and runs
 * a fixed order — read the version and the energy buffer, call the overridable
 * {@link EnergyBlockEntity#loadMachineData} so the subclass reads its own data, then {@link #migrate} —
 * which means a rung always sees a fully populated block entity, and a new rung touches this file only.
 * A subclass that needs to read data overrides {@code loadMachineData}; it cannot override
 * {@code loadAdditional}, so it cannot skip the ladder. Block entities outside the
 * {@link EnergyBlockEntity} hierarchy do not carry the version and are not walked.
 */
public final class BlockEntityDataMigrations {

	private BlockEntityDataMigrations() {}

	/**
	 * Layout version of the data a block entity of this mod writes, stored under
	 * {@link #DATA_VERSION_KEY}. Bumped when the SHAPE or the MEANING of stored data changes — never
	 * for adding a key, which is absent-safe and needs no rung. See the class doc for the recipe.
	 */
	public static final int DATA_VERSION = 3;

	/**
	 * NBT key holding {@link #DATA_VERSION}. Absent means "written before MOD-556", i.e. version 0 —
	 * the one case where a missing key is information rather than a default.
	 *
	 * <p>Namespaced on purpose, unlike the mod's other block-entity keys. {@code DataVersion} is the
	 * name vanilla already uses for a schema number (on a chunk, on a level), so a bare one here would
	 * be the single key most likely to collide with something a future version, a loader or a
	 * structure tool decides to write into a block-entity tag.
	 */
	public static final String DATA_VERSION_KEY = "AlaDataVersion";

	/**
	 * One rung: "data stored at {@code fromVersion} becomes data at {@code fromVersion + 1} once
	 * {@code apply} has repaired the block entity in place". Kept as data rather than a chain of
	 * {@code if}s so the next hop is one list entry.
	 */
	private record Step(int fromVersion, Consumer<EnergyBlockEntity> apply) {}

	/**
	 * The ladder, ascending, one rung per version hop ({@code STEPS.get(i).fromVersion() == i},
	 * {@code STEPS.size() == DATA_VERSION}).
	 */
	private static final List<Step> STEPS = List.of(
			new Step(0, BlockEntityDataMigrations::v0ToV1RenumberBatteryBoxSlots),
			new Step(1, BlockEntityDataMigrations::v1ToV2ShiftGeneratorUpgradeSlots),
			new Step(2, BlockEntityDataMigrations::v2ToV3ShiftGeothermalUpgradeSlots));

	/** Number of rungs on the ladder — the oracle the version pin compares against. */
	public static int stepCount() {
		return STEPS.size();
	}

	/** The version rung {@code index} converts FROM. */
	public static int stepFromVersion(int index) {
		return STEPS.get(index).fromVersion();
	}

	/**
	 * Walk {@code be} up the ladder from {@code fromVersion} to {@link #DATA_VERSION}, repairing it in
	 * place. A block entity already at (or above) the current version is left alone.
	 */
	public static void migrate(EnergyBlockEntity be, int fromVersion) {
		if (fromVersion >= DATA_VERSION) {
			return;
		}
		for (Step step : STEPS) {
			if (step.fromVersion() >= fromVersion) {
				step.apply().accept(be);
			}
		}
	}

	/**
	 * v0 → v1: a Battery Box saved before MOD-083 loads its upgrade chips one slot too low (C-20).
	 *
	 * <p>Upgrade slots are numbered <em>after</em> the machine slots, so adding the discharge slot moved
	 * them from 1…4 to 2…5. A box saved before that change therefore reads its first upgrade chip into
	 * what is now the discharge slot. The tell is unambiguous, because
	 * {@link BatteryBoxBlockEntity#canPlaceItem} has always refused anything without an EU buffer: a
	 * non-powered item sitting in the discharge slot can only have come from the old numbering. When
	 * that is what we see, shift the whole run one slot up.
	 *
	 * <p>Idempotent, as every rung must be (see the class doc): after the shift the discharge slot is
	 * empty, so a second pass over the repaired data returns immediately.
	 */
	private static void v0ToV1RenumberBatteryBoxSlots(EnergyBlockEntity be) {
		if (!(be instanceof BatteryBoxBlockEntity box)) {
			return;
		}
		ItemStack inDischarge = box.getItem(BatteryBoxBlockEntity.DISCHARGE_SLOT);
		if (inDischarge.isEmpty() || ItemEnergy.capacity(inDischarge) > 0) {
			return;
		}
		for (int slot = box.getContainerSize() - 1; slot > BatteryBoxBlockEntity.DISCHARGE_SLOT; slot--) {
			box.setItem(slot, box.getItem(slot - 1));
		}
		box.setItem(BatteryBoxBlockEntity.DISCHARGE_SLOT, ItemStack.EMPTY);
	}

	/**
	 * v1 → v2: a fuel generator saved before MOD-987 holds its upgrade chips one slot too low.
	 *
	 * <p>The ash slot went in right after the fuel slot, and the upgrade block follows the machine slots, so the
	 * chips moved from 1…4 to 2…5. The arms of the panel are typed by position (mute, overclocker, statistics), so
	 * every chip of an old save has to move one slot up, not only the one that would land in the ash slot: an old
	 * save puts its first chip into what is now the ash slot and the other three into the arm before their own.
	 * The whole run moves; the container grew by one, so the chip of the old last arm lands in the new last slot.
	 *
	 * <p>Two shapes are left alone. Ash in the ash slot is a save of the new layout that an older jar opened and
	 * wrote back: it kept the slots as they were, so the chips are already in place. A filled last slot is a
	 * layout the old one cannot be — the old container ended one slot earlier — and shifting it would overwrite
	 * the chip there, so nothing is ever lost. Every other shape below version 2 is the old layout: the current
	 * jar stamps 2, and an older jar writes its own five slots. That is also why the one shape both layouts share
	 * (empty ash slot, an empty last arm) is decided by the version rather than by the content; a second pass over
	 * repaired data with a full panel returns at the last-slot check and destroys nothing in any case.
	 */
	private static void v1ToV2ShiftGeneratorUpgradeSlots(EnergyBlockEntity be) {
		if (!(be instanceof GeneratorBlockEntity generator)) {
			return;
		}
		int last = generator.getContainerSize() - 1;
		if (generator.getItem(GeneratorBlockEntity.ASH_SLOT).is(ModContent.ASH.get())
				|| !generator.getItem(last).isEmpty()) {
			return;
		}
		for (int slot = last; slot > GeneratorBlockEntity.ASH_SLOT; slot--) {
			generator.setItem(slot, generator.getItem(slot - 1));
		}
		generator.setItem(GeneratorBlockEntity.ASH_SLOT, ItemStack.EMPTY);
	}

	/**
	 * v2 → v3: a geothermal generator saved before MOD-995/MOD-997 holds its upgrade chips two slots too low.
	 *
	 * <p>The shavings slot (MOD-995) and the catalyst slot (MOD-997) went in after the two lava slots, and the upgrade
	 * block follows the machine slots, so the chips moved from 2…5 to 4…7. The arms are typed by position, so the
	 * whole run moves two slots up; the container grew by two, so the chips of the old last two arms land in the new
	 * last two slots. The tank ({@code FluidTankMb}) and the burn reserve ({@code LavaTicks}) are not slots and are
	 * left as they were: an old world keeps every drop of its lava.
	 *
	 * <p>Shapes left alone, as in {@link #v1ToV2ShiftGeneratorUpgradeSlots}: shavings in the shavings slot or blaze
	 * powder in the catalyst slot is a save of the new layout an older jar opened and wrote back (an upgrade arm never
	 * holds either), and a filled slot among the last two is a layout the old one cannot be — shifting would overwrite
	 * it. A second pass over repaired data with a full panel returns there too, so nothing is ever lost.
	 */
	private static void v2ToV3ShiftGeothermalUpgradeSlots(EnergyBlockEntity be) {
		if (!(be instanceof GeothermalGeneratorBlockEntity geo)) {
			return;
		}
		int first = GeothermalGeneratorBlockEntity.SHAVINGS_SLOT;
		int shift = GeothermalGeneratorBlockEntity.SLOT_COUNT - first;
		int last = geo.getContainerSize() - 1;
		ItemStack catalyst = geo.getItem(GeothermalGeneratorBlockEntity.CATALYST_SLOT);
		if (geo.getItem(first).is(ModContent.OBSIDIAN_SHAVINGS.get())
				|| GeothermalGeneratorBlockEntity.isCatalyst(catalyst)) {
			return;
		}
		for (int slot = last; slot > last - shift; slot--) {
			if (!geo.getItem(slot).isEmpty()) {
				return;
			}
		}
		for (int slot = last; slot >= GeothermalGeneratorBlockEntity.SLOT_COUNT; slot--) {
			geo.setItem(slot, geo.getItem(slot - shift));
		}
		for (int slot = first; slot < GeothermalGeneratorBlockEntity.SLOT_COUNT; slot++) {
			geo.setItem(slot, ItemStack.EMPTY);
		}
	}
}
