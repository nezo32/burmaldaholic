package dev.nezo.burmaldaholic.core.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath;
import dev.nezo.burmaldaholic.core.bots.logic.HeatStage;
import org.junit.jupiter.api.Test;

/** BOTS.md §12.5 economy vectors: heat stages, purses, buy-in budget, attribution. */
class BotEconomyTest {
	@Test
	void heatStagesGoldPlayer() {
		long cap = BotEconomyMath.dailyCap(1000, 500, 5); // Gold tier max 1 000
		assertEquals(5000, cap);
		assertEquals(HeatStage.NONE, BotEconomyMath.heatStage(4999, cap, 2.0));
		assertEquals(HeatStage.HARD_ONLY, BotEconomyMath.heatStage(5010, cap, 2.0), "the next poker hand has only HARD house bots");
		assertEquals(HeatStage.HARD_ONLY, BotEconomyMath.heatStage(9999, cap, 2.0));
		assertEquals(HeatStage.SULKING, BotEconomyMath.heatStage(10000, cap, 2.0), "bots leave, …error.capped");
		assertEquals(HeatStage.NONE, BotEconomyMath.heatStage(-20000, cap, 2.0), "losses are never limited");
		assertEquals(HeatStage.NONE, BotEconomyMath.heatStage(1_000_000, 0, 2.0), "multiple 0 disables heat");
		assertEquals(7502, BotEconomyMath.sulkLine(5001, 1.5), "ceil(7 501.5)");
		assertTrue(HeatStage.SULKING.atLeast(HeatStage.HARD_ONLY));
	}

	@Test
	void capTable() {
		// §5.4: Bronze 500 · Silver 1 250 · Gold 5 000 · Platinum 12 500 · Diamond 50 000 · Netherite 250 000
		long[] tierMax = {100, 250, 1000, 2500, 10000, 50000};
		long[] caps = {500, 1250, 5000, 12500, 50000, 250000};
		for (int i = 0; i < tierMax.length; i++) {
			assertEquals(caps[i], BotEconomyMath.dailyCap(tierMax[i], 500, 5));
		}
	}

	@Test
	void mcdResets() {
		assertEquals(0, BotEconomyMath.mcDay(23999));
		assertEquals(1, BotEconomyMath.mcDay(24000));
		assertEquals(24000, BotEconomyMath.ticksToNextDay(24000));
		assertEquals(1, BotEconomyMath.ticksToNextDay(47999));
	}

	@Test
	void pursesAndBuyIns() {
		assertEquals(0, BotEconomyMath.affordable(150, 200), "S13: bankroll 150, buy-in 200 → no bot");
		assertEquals(3, BotEconomyMath.affordable(700, 200));
		assertEquals(Integer.MAX_VALUE, BotEconomyMath.affordable(0, 0), "atmosphere / no buy-in");
		assertEquals(0, BotEconomyMath.buyInsLeft(10, 10), "the 11th buy-in of the day is refused");
		assertEquals(1, BotEconomyMath.buyInsLeft(10, 9));
		assertEquals(Integer.MAX_VALUE, BotEconomyMath.buyInsLeft(0, 1000), "0 = unlimited");
	}

	@Test
	void attributionVectors() {
		assertEquals(96, BotEconomyMath.pokerPot(290, 100, 100, 0, 300), "fromBots_h");
		assertEquals(0, BotEconomyMath.pokerPot(0, 100, 100, 0, 300), "g: the bot won nothing");
		assertEquals(100, BotEconomyMath.pokerPot(200, 100, 100, 0, 200), "heads-up win +100");
		assertEquals(-100, BotEconomyMath.pokerPot(0, 100, 100, 200, 200), "heads-up loss −100");
		assertEquals(250, BotEconomyMath.vipCredit(400, 0.5, 0.75));
		assertEquals(3, BotEconomyMath.pvpRakeToBank(6, 100, 200), "rake to owner: 3 to the bank sink, 3 to the bankroll");
		assertEquals(97, BotEconomyMath.pvp(97, 100, 100, 0, 0));
		assertEquals(-50, BotEconomyMath.pvp(-100, 100, 200, 97, 194));
	}

	@Test
	void adaptiveHeatSameVectorsAsBedrock() {
		// > +20 BB/100 over ≥ 200 hands (Bedrock ledger.test.ts "adaptive heat")
		assertEquals(false, BotEconomyMath.adaptiveHot(199, 100));
		assertEquals(true, BotEconomyMath.adaptiveHot(200, 41));
		assertEquals(false, BotEconomyMath.adaptiveHot(200, 40));
		assertEquals(dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty.NORMAL,
			BotEconomyMath.levelUp(dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty.EASY));
		assertEquals(dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty.HARD,
			BotEconomyMath.levelUp(dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty.HARD));
		assertTrue(BotEconomyMath.ADAPTIVE_WINDOW >= BotEconomyMath.ADAPTIVE_MIN_HANDS);
	}
}
