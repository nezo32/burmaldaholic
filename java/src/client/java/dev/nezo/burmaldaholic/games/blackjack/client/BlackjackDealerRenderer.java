package dev.nezo.burmaldaholic.games.blackjack.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackDealer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Dealer NPC: vanilla player model with the dealer skin ({@code textures/entity/blackjack/dealer.png}, 64×64). */
public class BlackjackDealerRenderer extends HumanoidMobRenderer<BlackjackDealer, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = Burmaldaholic.id("textures/entity/blackjack/dealer.png");

	public BlackjackDealerRenderer(EntityRendererProvider.Context context) {
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
