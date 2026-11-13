package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.TodoInventoryTally;
import dev.alaindustrial.core.todo.TodoList;
import dev.alaindustrial.mixin.client.ItemModelsAccessor;
import dev.alaindustrial.mixin.client.ItemStackRenderStateAccessor;
import dev.alaindustrial.mixin.client.LayerRenderStateAccessor;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The list written on a to-do clipboard held in the hand (MOD-905, owner decision 2026-11-12: "a clipboard in the
 * hand with the text on its sheet, like a map"). An item-model type, {@code alaindustrial:todo_sheet}, that
 * {@code items/todo_clipboard.json} composes over the clipboard's own block model in the first-person contexts;
 * every other context keeps the flat icon.
 *
 * <p><b>The same page as on the wall.</b> The text, icons and counters are laid out and drawn by
 * {@link TodoSheet}, the painter the wall's {@link TodoClipboardRenderer} uses; the held model is the block model
 * itself, so the painter's frame — the clipboard lying in its unit cell — is the item's model frame too.
 *
 * <p><b>Why a model type and not {@code minecraft:special}.</b> A special renderer gets only the stack: it could
 * neither resolve the icons of the lines' items (no {@link ItemModelResolver}, no level) nor be registered without
 * a new accessor into {@code SpecialModelRenderers}. A model type gets both in {@link #update}, registers through
 * the accessor {@link BlueprintResultItemModel} already uses, and hands its page to a layer as that layer's special
 * renderer — exactly what vanilla's {@code SpecialModelWrapper} does — so it is the same code on Fabric, NeoForge
 * and Forge and needs no mixin of its own.
 */
public final class TodoSheetItemModel implements ItemModel {

	/** The id used in {@code items/*.json} as {@code "type": "alaindustrial:todo_sheet"}. */
	public static final String TYPE_PATH = "todo_sheet";

	/** Draws a page the model laid out; it never reads a stack itself. */
	private static final SpecialModelRenderer<TodoSheet.Page> PAINTER = new SpecialModelRenderer<>() {
		@Override
		public void submit(TodoSheet.@Nullable Page page, PoseStack poseStack, SubmitNodeCollector collector,
				int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
			if (page != null) {
				TodoSheet.submit(page, poseStack, collector, lightCoords);
			}
		}

		@Override
		public void getExtents(Consumer<Vector3fc> output) {
			// Flat on the sheet, inside the board the composite's first layer already spans.
		}

		@Override
		public TodoSheet.@Nullable Page extractArgument(ItemStack stack) {
			return null;
		}
	};

	private final Matrix4fc transformation;

	private TodoSheetItemModel(Matrix4fc transformation) {
		this.transformation = transformation;
	}

	/** Adds the type to the vanilla late-bound registry; a {@code ClientSetup} step on every loader. */
	public static void register() {
		ItemModelsAccessor.alaindustrial$idMapper().put(Industrialization.id(TYPE_PATH), Unbaked.MAP_CODEC);
	}

	@Override
	public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
			ItemDisplayContext displayContext, ClientLevel level, ItemOwner owner, int seed) {
		state.appendModelIdentityElement(this);
		TodoList list = stack.get(ModDataComponents.TODO_LIST.get());
		ItemStackRenderStateAccessor access = (ItemStackRenderStateAccessor) state;
		if (list == null || list.isEmpty() || access.alaindustrial$activeLayerCount() == 0) {
			// A blank sheet, or nothing to write on: the board is always drawn first by the enclosing composite.
			return;
		}
		state.appendModelIdentityElement(list);
		ItemTransform board = ((LayerRenderStateAccessor) access.alaindustrial$layers()[0])
				.alaindustrial$itemTransform();
		TodoSheet.Page page = new TodoSheet.Page();
		TodoSheet.extract(page, Minecraft.getInstance().font, ModContent.TODO_CLIPBOARD.get().getName(), list,
				TodoInventoryTally.current(), resolver, level, seed);
		ItemStackRenderState.LayerRenderState layer = state.newLayer();
		// The board's own display transform: the page must sit on the board in every hand and at every angle.
		layer.setItemTransform(board);
		layer.setLocalTransform(transformation);
		layer.setExtents(ItemStackRenderState.LayerRenderState.NO_EXTENTS_SUPPLIER);
		layer.setupSpecialModel(PAINTER, page);
	}

	/** The unbaked form: nothing to configure, the page comes from the stack at render time. */
	public record Unbaked() implements ItemModel.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			// The page's items are whatever the list names at render time; there is no model to pre-resolve.
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
			return new TodoSheetItemModel(transformation);
		}
	}
}
