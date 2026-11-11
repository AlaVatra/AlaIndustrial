package dev.alaindustrial.registry.forge;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.loot.forge.InjectionLootModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The one global loot modifier serializer of the Forge loader (MOD-962): {@code alaindustrial:injections}, the
 * codec of {@link InjectionLootModifier}. The modifier instance itself is data,
 * {@code data/alaindustrial/loot_modifiers/injections.json}, enabled by its line in
 * {@code data/forge/loot_modifiers/global_loot_modifiers.json}.
 */
public final class ModLootModifiersForge {

	public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
			DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Industrialization.MOD_ID);

	public static final RegistryObject<MapCodec<InjectionLootModifier>> INJECTIONS =
			SERIALIZERS.register("injections", () -> InjectionLootModifier.CODEC);

	private ModLootModifiersForge() {
	}

	public static void register(BusGroup modBusGroup) {
		SERIALIZERS.register(modBusGroup);
	}
}
