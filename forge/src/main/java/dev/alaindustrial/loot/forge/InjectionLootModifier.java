package dev.alaindustrial.loot.forge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Config;
import dev.alaindustrial.loot.LootInjections;
import dev.alaindustrial.registry.forge.ModLootModifiersForge;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/**
 * Forge's replay of the shared loot injection table {@link LootInjections#INJECTIONS} (MOD-962): ONE global
 * loot modifier, {@code alaindustrial:injections}, that walks the table on every top-level roll instead of one
 * JSON file per row, so a new row needs no Forge file.
 *
 * <p>A row matches when the queried table id ({@link LootContext#getQueriedLootTableId()}, the id Forge stamps
 * before it runs the modifiers, the same one {@code forge:loot_table_id} tests) equals the row's target. As on
 * Fabric and NeoForge, a row applies to any table with that id, built-in or replaced by a datapack or another
 * mod (see {@link LootInjections}). The bonus-chest gate is read at roll time, as on the other two loaders.
 *
 * <p>The sub-table is rolled once through {@link LootTable#getRandomItemsRaw}, exactly as NeoForge's
 * {@code AddTableLootModifier} and vanilla's nested table entry do: the Raw roll does not run the global loot
 * modifiers again, so the sub-table's items are modified only once (by the modifiers that follow this one on
 * the target table) and this modifier never re-enters itself.
 */
public final class InjectionLootModifier extends LootModifier {

	public static final MapCodec<InjectionLootModifier> CODEC =
			RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, InjectionLootModifier::new));

	public InjectionLootModifier(LootItemCondition[] conditions) {
		super(conditions);
	}

	@SuppressWarnings("deprecation") // getRandomItemsRaw: deprecated only to steer callers to the modified roll
	@Override
	protected ObjectArrayList<ItemStack> doApply(LootTable table, ObjectArrayList<ItemStack> generatedLoot,
			LootContext context) {
		Identifier queried = context.getQueriedLootTableId();
		for (LootInjections.Injection injection : LootInjections.INJECTIONS) {
			if (!injection.target().identifier().equals(queried)
					|| (injection.bonusChestGate() && !Config.bonusChestEnabled)) {
				continue;
			}
			context.getResolver().get(injection.table()).ifPresent(extra -> extra.value().getRandomItemsRaw(
					context, LootTable.createStackSplitter(context.getLevel(), generatedLoot::add)));
		}
		return generatedLoot;
	}

	@Override
	public MapCodec<? extends IGlobalLootModifier> codec() {
		return ModLootModifiersForge.INJECTIONS.get();
	}
}
