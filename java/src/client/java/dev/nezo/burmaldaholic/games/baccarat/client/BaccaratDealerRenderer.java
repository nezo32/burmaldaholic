package dev.nezo.burmaldaholic.games.baccarat.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratDealer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Baccarat Dealer NPC: vanilla player model with the dealer skin ({@code textures/entity/baccarat/dealer.png}, 64×64). */
public class BaccaratDealerRenderer extends HumanoidMobRenderer<BaccaratDealer, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = Burmaldaholic.id("textures/entity/baccarat/dealer.png");

	public BaccaratDealerRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}
}
