package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.RecipeFamily;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge recipe registration (MOD-962), the counterpart of {@code ModRecipesNeoForge}. These
 * {@link DeferredRegister}s replay {@link ModRecipes#families()}: one {@link RecipeType} (when the family has
 * its own) and one {@link RecipeSerializer} per {@link RecipeFamily}, in list order. {@link #register} binds
 * each family to its holders (each a {@code Supplier}, resolved lazily after the {@code RegisterEvent}) and
 * attaches both registers to the mod bus.
 */
public final class ModRecipesForge {
	public static final DeferredRegister<RecipeType<?>> TYPES =
			DeferredRegister.create(Registries.RECIPE_TYPE, Industrialization.MOD_ID);
	public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
			DeferredRegister.create(Registries.RECIPE_SERIALIZER, Industrialization.MOD_ID);

	private static final List<Runnable> BINDERS = new ArrayList<>();

	static {
		for (RecipeFamily<?> family : ModRecipes.families()) {
			BINDERS.add(register(family));
		}
	}

	/** Queues one family on the two registers and returns the step that binds it to the holders. */
	private static <R extends Recipe<?>> Runnable register(RecipeFamily<R> family) {
		Supplier<RecipeType<R>> type = family.createType().isPresent()
				? TYPES.<RecipeType<R>>register(family.id(), () -> family.createType().orElseThrow())::get
				: RecipeFamily.noType(family.id());
		RegistryObject<RecipeSerializer<R>> serializer =
				SERIALIZERS.register(family.id(), family::createSerializer);
		return () -> family.bind(type, serializer::get);
	}

	/** Binds each family to its holders, then attaches both registers to the mod bus. */
	public static void register(BusGroup modBusGroup) {
		for (Runnable binder : BINDERS) {
			binder.run();
		}
		TYPES.register(modBusGroup);
		SERIALIZERS.register(modBusGroup);
	}

	private ModRecipesForge() {
	}
}
