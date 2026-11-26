package dev.alaindustrial.client.render.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.Industrialization;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.CowRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.cow.CowVariant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws the shielding suit on a cow (MOD-569, the owner's "light kit"): the helmet is a gas mask, the
 * chestplate a blanket over the back with two belly straps, the leggings gaiters, the boots boots. Only the
 * worn pieces show. The same trick {@link ShieldingSuitLayer} plays on the villager: each piece is a 64x64
 * overlay painted on the UV sheet of the cow's OWN model and drawn as a cutout copy of it, so it follows every
 * pose and needs no geometry of its own.
 *
 * <p><b>One sheet per UV layout.</b> {@code CowRenderer} swaps the model per variant and age before the layers
 * run, so {@link #getParentModel()} is already the model being drawn; only the sheet has to match it. 26.3 has
 * three layouts a player meets: the adult (temperate {@code CowModel}; the warm model differs only in horns the
 * suit leaves bare, so it shares the sheet), the cold one ({@code ColdCowModel}: another muzzle position and a
 * fur shell over the body, which the blanket is painted on) and the calf ({@code BabyCowModel}, every variant).
 * The sheets are written by {@code tools/gen_cow_shielding_suit_assets.py}.
 */
public class CowShieldingSuitLayer extends RenderLayer<CowRenderState, CowModel> {

	private static final Logger LOGGER = LoggerFactory.getLogger("CowShieldingSuitLayer");

	private static final Sheets ADULT = Sheets.of("adult");
	private static final Sheets COLD = Sheets.of("cold");
	private static final Sheets BABY = Sheets.of("baby");

	/** Set after the first failure: a layer that cannot render must vanish, not spam or kill frames. */
	private boolean broken;

	public CowShieldingSuitLayer(RenderLayerParent<CowRenderState, CowModel> parent) {
		super(parent);
	}

	/** The sheets that fit the model {@code CowRenderer} picks for this state. */
	static Sheets sheetsFor(boolean baby, CowVariant variant) {
		if (baby) {
			return BABY;
		}
		return variant != null && variant.modelAndTexture().model() == CowVariant.ModelType.COLD ? COLD : ADULT;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CowRenderState state, float yaw,
			float tick) {
		if (this.broken || state.isInvisible || state.variant == null
				|| !(state instanceof ShieldingSuitRenderState suit)) {
			return;
		}
		try {
			CowModel model = this.getParentModel();
			Sheets sheets = sheetsFor(state.isBaby, state.variant);
			if (!suit.alaindustrial$head().isEmpty()) {
				renderColoredCutoutModel(model, sheets.head(), poseStack, collector, light, state, -1, 3);
			}
			if (!suit.alaindustrial$chest().isEmpty()) {
				renderColoredCutoutModel(model, sheets.chest(), poseStack, collector, light, state, -1, 4);
			}
			if (!suit.alaindustrial$legs().isEmpty()) {
				renderColoredCutoutModel(model, sheets.legs(), poseStack, collector, light, state, -1, 5);
			}
			if (!suit.alaindustrial$feet().isEmpty()) {
				renderColoredCutoutModel(model, sheets.feet(), poseStack, collector, light, state, -1, 6);
			}
		} catch (Throwable t) {
			this.broken = true;
			LOGGER.error("cow shielding suit overlay failed once and is now disabled until relaunch", t);
		}
	}

	/** The four overlays of one UV layout. */
	record Sheets(Identifier head, Identifier chest, Identifier legs, Identifier feet) {

		static Sheets of(String layout) {
			String dir = "textures/entity/cow/shielding/" + layout + "/";
			return new Sheets(Industrialization.id(dir + "head.png"), Industrialization.id(dir + "chest.png"),
					Industrialization.id(dir + "legs.png"), Industrialization.id(dir + "feet.png"));
		}
	}
}
