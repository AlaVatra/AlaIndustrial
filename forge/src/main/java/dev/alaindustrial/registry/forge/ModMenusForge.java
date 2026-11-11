package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ContentManifest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge {@link MenuType} registration: a replay of the loader-neutral {@link ContentManifest#MENUS} through a
 * {@link DeferredRegister} (lazy); the twin of {@code ModMenusNeoForge} (MOD-962).
 *
 * <p>Forge builds a networked {@code MenuType} via {@code IForgeMenuType.create(IContainerFactory)}. Since
 * {@code IContainerFactory<T> extends MenuType.MenuSupplier<T>} and the manifest's factory ignores the extra
 * buffer, the 2-arg client factory plugs straight in.
 *
 * <p><b>Timing.</b> The {@code static} block queues every entry into {@link #MENUS} at class load; the
 * {@code ModContent} bindings are held in {@link #BINDINGS} and applied by {@link #init()}. A
 * {@link RegistryObject} is a {@code Supplier}, so binding it before the {@code RegisterEvent} fires is safe.
 */
public final class ModMenusForge {
	public static final DeferredRegister<MenuType<?>> MENUS =
			DeferredRegister.create(Registries.MENU, Industrialization.MOD_ID);

	/** ModContent bindings, deferred until {@link #init()} runs (after {@code MENUS} is populated). */
	private static final List<Runnable> BINDINGS = new ArrayList<>();

	static {
		for (ContentManifest.MenuDef<?> def : ContentManifest.MENUS) {
			declare(def);
		}
	}

	private ModMenusForge() {
	}

	private static <T extends AbstractContainerMenu> void declare(ContentManifest.MenuDef<T> def) {
		RegistryObject<MenuType<T>> holder =
				register(def.id(), (id, inv, buf) -> def.factory().create(id, inv));
		BINDINGS.add(() -> def.bind().accept(holder::get));
	}

	private static <T extends AbstractContainerMenu> RegistryObject<MenuType<T>> register(
			String name, IContainerFactory<T> factory) {
		return MENUS.register(name, () -> IForgeMenuType.create(factory));
	}

	/**
	 * Publishes each registered {@code MenuType} holder into its {@link dev.alaindustrial.registry.ModContent}
	 * slot, after checking the replay covered the whole manifest.
	 */
	public static void init() {
		if (BINDINGS.size() != ContentManifest.MENUS.size()) {
			throw new IllegalStateException("ModMenusForge registered " + BINDINGS.size() + " of "
					+ ContentManifest.MENUS.size() + " manifest menus");
		}
		BINDINGS.forEach(Runnable::run);
	}
}
