package dev.alaindustrial.registry.forge;

import dev.alaindustrial.registry.CreativeTabContent;
import dev.alaindustrial.registry.VanillaCreativeTabs;
import java.util.List;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Adds public Ala Industrial content to the matching vanilla creative tabs on Forge; the twin of
 * {@code ModCreativeTabEventsNeoForge} (MOD-962).
 *
 * <p>Which items go into which vanilla tab, in which order, and against which anchor is one shared list in
 * {@link CreativeTabContent}; this file supplies only the {@link CreativeTabContent.AnchoredSink} that knows
 * how to say "after this one" on Forge.
 *
 * <p><b>Forge's shape.</b> The event is a record on its own global bus
 * ({@code BuildCreativeModeTabContentsEvent.BUS}, EventBus 7), not a mod-bus event. Its entries are ONE
 * {@code MutableHashedLinkedMap<ItemStack, TabVisibility>} built by
 * {@code ForgeHooks.onCreativeModeTabBuildContents} with the {@code ItemStackLinkedSet.TYPE_AND_TAG} strategy
 * (item type plus components), so a freshly built anchor stack matches the entry already in the tab; the
 * visibility is the map's value rather than a second set, as it is on NeoForge.
 */
public final class ModCreativeTabEventsForge {
	private ModCreativeTabEventsForge() {
	}

	/** Hooks the listener on the event's global bus. Fired on the logical client only. */
	public static void register() {
		BuildCreativeModeTabContentsEvent.BUS.addListener(ModCreativeTabEventsForge::buildCreativeTabContents);
	}

	static void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey().equals(VanillaCreativeTabs.COMBAT)) {
			CreativeTabContent.combat(anchored(event));
		} else if (event.getTabKey().equals(VanillaCreativeTabs.TOOLS_AND_UTILITIES)) {
			CreativeTabContent.toolsAndUtilities(anchored(event));
		} else if (event.getTabKey().equals(VanillaCreativeTabs.INGREDIENTS)) {
			CreativeTabContent.ingredients(event::accept);
		} else if (event.getTabKey().equals(VanillaCreativeTabs.BUILDING_BLOCKS)) {
			CreativeTabContent.buildingBlocks(event::accept);
		} else if (event.getTabKey().equals(VanillaCreativeTabs.NATURAL_BLOCKS)) {
			CreativeTabContent.naturalBlocks(event::accept);
		} else if (event.getTabKey().equals(VanillaCreativeTabs.FUNCTIONAL_BLOCKS)) {
			CreativeTabContent.functionalBlocks(event::accept);
		}
	}

	/**
	 * The tab-contents event, seen as the loader-neutral {@link CreativeTabContent.AnchoredSink}. Forge's
	 * {@code putAfter} takes ONE entry, so a group goes in as a chain: anchor then first, first then second, and
	 * so on, landing the same order as Fabric's group insert.
	 */
	private static CreativeTabContent.AnchoredSink anchored(BuildCreativeModeTabContentsEvent event) {
		return new CreativeTabContent.AnchoredSink() {
			@Override
			public void accept(ItemLike item) {
				event.accept(item);
			}

			@Override
			public void insertAfter(ItemLike anchor, List<ItemLike> items) {
				chainAfter(event, anchor, items);
			}
		};
	}

	/** The chain itself, outside the anonymous class so the guarded helper is not shadowed by name. */
	private static void chainAfter(BuildCreativeModeTabContentsEvent event, ItemLike anchor,
			List<ItemLike> items) {
		ItemStack previous = anchor.asItem().getDefaultInstance();
		for (ItemLike item : items) {
			ItemStack stack = item.asItem().getDefaultInstance();
			placeOrAppend(event, previous, stack);
			previous = stack;
		}
	}

	/**
	 * Places {@code stack} directly after {@code anchor}, appending it at the end of the tab instead when the
	 * anchor is absent (MOD-349: any other mod may take a vanilla anchor out of its tab).
	 *
	 * <p>Forge's {@code MutableHashedLinkedMap.putAfter} already degrades to a plain {@code put} when the anchor
	 * is missing, so unlike NeoForge nothing here can throw; the explicit check states the MOD-349 contract on
	 * this side rather than leaning on that library detail. Note the other half of the difference: a stack that
	 * is ALREADY in the map is not rejected as on NeoForge, it is moved after the anchor (and its visibility
	 * merged), so a double insert of our own content does not surface here.
	 */
	private static void placeOrAppend(BuildCreativeModeTabContentsEvent event, ItemStack anchor, ItemStack stack) {
		if (event.getEntries().contains(anchor)) {
			event.getEntries().putAfter(anchor, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		} else {
			event.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		}
	}
}
