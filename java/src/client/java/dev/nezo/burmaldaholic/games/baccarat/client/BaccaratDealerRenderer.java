package dev.nezo.burmaldaholic.games.baccarat.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.dealer.DealerRenderer;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratDealer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Baccarat Dealer NPC: vanilla player model with the dealer skin ({@code textures/entity/baccarat/dealer.png}, 64×64)
 * on the shared gesturing {@link DealerRenderer}: it gestures once the baccarat table implements
 * {@link dev.nezo.burmaldaholic.client.dealer.DealerCueSource} (lane J-L4); until then it rests with the breathing sway.
 */
public class BaccaratDealerRenderer extends DealerRenderer<BaccaratDealer> {
	public BaccaratDealerRenderer(EntityRendererProvider.Context context) {
		super(context, Burmaldaholic.id("textures/entity/baccarat/dealer.png"));
	}
}
