package dev.nezo.burmaldaholic.games.baccarat.logic;

import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Side;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Chemin de fer money when bots take part (BOTS.md §4.4, §5.1, §5.3, §5.4). PURE; same arithmetic as
 * Bedrock {@code settleChemmyWithBots}.
 *
 * <ul>
 *   <li>a bot banker pays <b>no rake</b> (the house does not rake itself);</li>
 *   <li>a human banker's rake on chips won from bot punters goes to the bank sink, never to an owner's
 *       bankroll ({@link Result#rakeToSink}); the rest ({@link Result#rakeToOwner}) is ordinary rake;</li>
 *   <li>heat (§5.4): per human, the net won from HOUSE-funded bots this coup — the human's coup result
 *       against a bot bank, or for a human banker the house bots' settled stakes minus the rake on them;</li>
 *   <li>{@code botShare} (§5.3): the bots' share of the money on the other side of each human.</li>
 * </ul>
 */
public final class ChemmyBotMoney {
	private ChemmyBotMoney() {}

	/**
	 * @param base        the bank's settlement (returns, win, rake, loss)
	 * @param matched     Σ punter stakes of the coup
	 * @param botMatched  Σ bot punter stakes of the coup
	 * @param humanPunts  number of human punters this coup
	 */
	public record Result<K>(ChemmyBank.Settlement<K> base, long matched, long botMatched, int humanPunts, long rakeToOwner, long rakeToSink,
			Map<K, Long> heat, Map<K, Double> botShare) {
		public long rake() {
			return base.rake();
		}

		public long bankDelta() {
			return base.bankDelta();
		}

		/** A bot was on the other side of this human (VIP weighting, no streak …). */
		public boolean vsBots(K human) {
			Double s = botShare.get(human);
			return s != null && s > 0;
		}

		/** Every chip on the other side of this human was a bot's. */
		public boolean onlyBots(K human) {
			Double s = botShare.get(human);
			return s != null && s >= 1;
		}
	}

	/**
	 * Settles the coup on {@code bank} (mutates it like {@link ChemmyBank#settle}) with the bot rules.
	 *
	 * @param rakeBp   the table's rake in basis points (ignored for a bot banker)
	 * @param isBot    the key is a bot
	 * @param houseBot the bot is funded by the bank (heat applies only to those)
	 */
	public static <K> Result<K> settle(ChemmyBank<K> bank, Side winner, int rakeBp, Predicate<K> isBot, Predicate<K> houseBot) {
		K banker = bank.banker();
		boolean bankerBot = banker != null && isBot.test(banker);
		Map<K, Long> stakes = new LinkedHashMap<>(bank.punts());
		long matched = 0;
		long botMatched = 0;
		long houseMatched = 0;
		int humans = 0;
		for (Map.Entry<K, Long> e : stakes.entrySet()) {
			matched += e.getValue();
			if (isBot.test(e.getKey())) {
				botMatched += e.getValue();
				if (houseBot.test(e.getKey())) {
					houseMatched += e.getValue();
				}
			} else {
				humans++;
			}
		}
		ChemmyBank.Settlement<K> r = bank.settle(winner, bankerBot ? 0 : rakeBp);
		long rake = r.rake();
		long rakeToSink = rake > 0 && matched > 0 ? Math.floorDiv(rake * botMatched, matched) : 0;
		Map<K, Long> heat = new LinkedHashMap<>();
		Map<K, Double> botShare = new LinkedHashMap<>();
		if (bankerBot) {
			for (Map.Entry<K, Long> e : stakes.entrySet()) {
				K id = e.getKey();
				if (isBot.test(id)) {
					continue;
				}
				botShare.put(id, 1.0);
				if (houseBot.test(banker)) {
					heat.merge(id, r.punterReturns().getOrDefault(id, 0L) - e.getValue(), Long::sum);
				}
			}
		} else if (banker != null && matched > 0) {
			botShare.put(banker, (double) botMatched / matched);
			if (houseMatched > 0) {
				long won = 0;
				for (Map.Entry<K, Long> e : stakes.entrySet()) {
					if (isBot.test(e.getKey()) && houseBot.test(e.getKey())) {
						won += e.getValue() - r.punterReturns().getOrDefault(e.getKey(), 0L);
					}
				}
				long rakeOnThem = rake > 0 ? Math.floorDiv(rake * houseMatched, matched) : 0;
				heat.put(banker, won - (won > 0 ? rakeOnThem : 0));
			}
		}
		return new Result<>(r, matched, botMatched, humans, rake - rakeToSink, rakeToSink, heat, botShare);
	}
}
