package dev.nezo.burmaldaholic.games.blackjack.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.dealer.DealerRenderer;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackDealer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Dealer NPC: vanilla player model with the dealer skin ({@code textures/entity/blackjack/dealer.png}, 64×64) on the
 * shared gesturing {@link DealerRenderer}: it gestures once the blackjack table implements
 * {@link dev.nezo.burmaldaholic.client.dealer.DealerCueSource} (lane J-L4); until then it rests with the breathing sway.
 */
public class BlackjackDealerRenderer extends DealerRenderer<BlackjackDealer> {
	public BlackjackDealerRenderer(EntityRendererProvider.Context context) {
		super(context, Burmaldaholic.id("textures/entity/blackjack/dealer.png"));
	}
}
