package dev.alaindustrial.registry.forge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.CreativeTabContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge creative-tab registration; the twin of {@code ModCreativeTabNeoForge} (MOD-962). The item list is
 * shared with the other two loaders via {@link CreativeTabContent}, so release-visible content stays identical.
 */
public final class ModCreativeTabForge {
	public static final DeferredRegister<CreativeModeTab> TABS =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Industrialization.MOD_ID);

	public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.alaindustrial"))
					.icon(() -> new ItemStack(ModItemsForge.MACERATOR_ITEM.get()))
					.displayItems((params, output) -> CreativeTabContent.main(
							CreativeTabContent.Sink.of(output::accept, output::accept)))
					.build());

	private ModCreativeTabForge() {
	}
}
