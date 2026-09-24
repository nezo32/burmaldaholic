package dev.nezo.burmaldaholic.games.poker.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath;
import java.util.function.IntPredicate;

/**
 * Money rules of a finished poker hand against MONEY bots (BOTS.md §5.3, §5.4). Pure, integer, floor;
 * same rules as Bedrock {@code games/poker/logic/money.ts}.
 *
 * <ul>
 *   <li>{@code botShare} (VIP / {@code wager} credit weight, streak skip): the bots' share of the chips
 *       matched against the human's contributions — per pot: {@code paid_h × botPaid / (pot − paid_h)},
 *       summed and divided by Σ paid_h. Every money bot counts (house- or owner-funded).</li>
 *   <li>{@code houseNet} (heat, {@code BotLedger.record}): per pot p, {@code floor(won_h × houseBotPaid / pot)
 *       − floor(paid_h × houseBotWon / pot)} ({@link BotEconomyMath#pokerPot}). Owner-funded bots are not
 *       tracked. Uncalled bets were returned before the pots were built, so they never count.</li>
 * </ul>
 */
public final class PokerMoney {
	private PokerMoney() {}

	/**
	 * @param botShare per hand player: bots' share 0..1 of the chips matched against them (humans; 0 for bots)
	 * @param houseNet per hand player: net chips won from house-funded bots this hand (humans; 0 for bots)
	 */
	public record Attribution(double[] botShare, long[] houseNet) {}

	/** {@code isBot(i)} = hand player i is a money bot; {@code isHouseBot(i)} = funded by the bank. */
	public static Attribution attribute(Hand h, IntPredicate isBot, IntPredicate isHouseBot) {
		return attribute(h.result(), h.players().size(), isBot, isHouseBot);
	}

	/** Same for a finished hand's result with {@code n} players. */
	public static Attribution attribute(Hand.Result r, int n, IntPredicate isBot, IntPredicate isHouseBot) {
		double[] botShare = new double[n];
		long[] houseNet = new long[n];
		if (r == null) {
			return new Attribution(botShare, houseNet);
		}
		for (int i = 0; i < n; i++) {
			if (isBot.test(i)) {
				continue;
			}
			long paidAll = 0;
			double matchedByBots = 0;
			for (Hand.PotResult pot : r.pots()) {
				long mine = pot.paidBy(i);
				if (mine <= 0 && !pot.winners().contains(i)) {
					continue;
				}
				long others = pot.amount() - mine;
				long bots = 0;
				long houseBotPaid = 0;
				long houseBotWon = 0;
				for (int j = 0; j < n; j++) {
					if (j != i && isBot.test(j)) {
						bots += pot.paidBy(j);
					}
					if (isHouseBot.test(j)) {
						houseBotPaid += pot.paidBy(j);
						houseBotWon += pot.wonBy(j);
					}
				}
				if (mine > 0 && others > 0) {
					paidAll += mine;
					matchedByBots += (double) mine * bots / others;
				}
				houseNet[i] += BotEconomyMath.pokerPot(pot.wonBy(i), mine, houseBotPaid, houseBotWon, pot.amount());
			}
			botShare[i] = paidAll > 0 ? Math.min(1, matchedByBots / paidAll) : 0;
		}
		return new Attribution(botShare, houseNet);
	}
}
