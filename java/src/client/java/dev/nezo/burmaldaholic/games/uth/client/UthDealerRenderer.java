package dev.nezo.burmaldaholic.games.uth.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.dealer.DealerRenderer;
import dev.nezo.burmaldaholic.games.uth.UthDealer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Hold'em Dealer NPC: vanilla player model with the dealer skin ({@code textures/entity/uth/dealer.png}, 64×64),
 * gesturing with the nearest UTH table (deal, flip, pay, sweep; task J-C11).
 */
public class UthDealerRenderer extends DealerRenderer<UthDealer> {
	public UthDealerRenderer(EntityRendererProvider.Context context) {
		super(context, Burmaldaholic.id("textures/entity/uth/dealer.png"));
	}
}
