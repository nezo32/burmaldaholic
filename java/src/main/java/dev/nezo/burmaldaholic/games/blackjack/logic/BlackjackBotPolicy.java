package dev.nezo.burmaldaholic.games.blackjack.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Action;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Offer;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Seat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Blackjack atmosphere bots (BOTS.md §4.7, pvp-bots.md §4.6). PURE; mirrors Bedrock
 * {@code games/blackjack/logic/bots.ts}.
 *
 * <p>Bots sit beside humans with VIRTUAL bets (never debited, paid or reserved) but play REAL cards from
 * the shared shoe, so humans see their hands. Levels:
 * <ul>
 *   <li>EASY "mimic the dealer": hit below 17, stand on 17+, never double, split only A-A and 8-8;
 *       insurance / even money 50 %; bet 1–5 × min, doubles after a loss (reset at 8 ×).</li>
 *   <li>NORMAL: basic strategy with 5 % errors on soft totals and doubles; never insures; flat 2–5 × min.</li>
 *   <li>HARD: perfect basic strategy for the table's rules (S17/H17, DAS, resplit up to maxHands, late
 *       surrender); never insures; flat 5–10 × min (≤ table max).</li>
 * </ul>
 * Every choice uses the BOT rng; the view holds only what a human in that seat sees (own hand, the
 * dealer's up card, legal actions, table rules) — never the hole card or the shoe.
 */
public final class BlackjackBotPolicy implements BotPolicy<BlackjackBotPolicy.View, BlackjackBotPolicy.Act> {
	public static final BlackjackBotPolicy INSTANCE = new BlackjackBotPolicy();

	/** NORMAL error rate on soft totals and doubles (BOTS.md §4.7, test §12.3: 5 % ± 0.5 %). */
	public static final double NORMAL_ERROR_RATE = 0.05;
	/** EASY takes insurance / even money half the time. */
	public static final double EASY_INSURANCE_RATE = 0.5;

	/** What a human in the bot's seat sees. */
	public sealed interface View {
		/** Insurance / even money is offered (dealer shows an ace). */
		record Insurance(Offer offer, List<Card> hand, Card upCard) implements View {}

		/** The bot's hand acts. {@code hands} = hands this seat holds now (split count). */
		record Turn(List<Card> hand, Card upCard, List<Action> legal, BlackjackRules rules, int hands) implements View {}
	}

	/** The bot's answer: a play, or the insurance / even-money decision (virtual: nothing is raised). */
	public sealed interface Act {
		record Play(Action action) implements Act {}

		record Insure(boolean take) implements Act {}
	}

	private BlackjackBotPolicy() {}

	// ---- basic strategy for the table's rules ------------------------------------------------------

	private enum Want {
		H, S, D, DS
	}

	/** Pair splitting (4–8 decks). {@code das} = double after split allowed. */
	private static boolean pairSplits(Card c, int up, boolean das) {
		int v = c.value();
		if (c.isAce() || v == 8) {
			return true;
		}
		if (v == 10 || v == 5) {
			return false;
		}
		if (v == 9) {
			return up <= 6 || up == 8 || up == 9;
		}
		if (v == 7) {
			return up <= 7;
		}
		if (v == 6) {
			return das ? up <= 6 : up >= 3 && up <= 6;
		}
		if (v == 4) {
			return das && (up == 5 || up == 6);
		}
		return das ? up <= 7 : up >= 4 && up <= 7; // 2-2, 3-3
	}

	private static Want soft(int total, int up, boolean h17) {
		if (total >= 20) {
			return Want.S;
		}
		if (total == 19) {
			return h17 && up == 6 ? Want.DS : Want.S;
		}
		if (total == 18) {
			return (h17 ? up >= 2 && up <= 6 : up >= 3 && up <= 6) ? Want.DS : up <= 8 ? Want.S : Want.H;
		}
		if (total == 17) {
			return up >= 3 && up <= 6 ? Want.D : Want.H;
		}
		if (total >= 15) {
			return up >= 4 && up <= 6 ? Want.D : Want.H;
		}
		return up >= 5 && up <= 6 ? Want.D : Want.H;
	}

	private static Want hard(int total, int up, boolean h17) {
		if (total >= 17) {
			return Want.S;
		}
		if (total >= 13) {
			return up <= 6 ? Want.S : Want.H;
		}
		if (total == 12) {
			return up >= 4 && up <= 6 ? Want.S : Want.H;
		}
		if (total == 11) {
			return up <= 10 || h17 ? Want.D : Want.H;
		}
		if (total == 10) {
			return up <= 9 ? Want.D : Want.H;
		}
		if (total == 9) {
			return up >= 3 && up <= 6 ? Want.D : Want.H;
		}
		return Want.H;
	}

	/** Late surrender spots (4–8 decks): S17 16 v 9–A, 15 v 10; H17 adds 15 v A, 17 v A, 8-8 v A. */
	private static boolean surrenderWanted(List<Card> cards, int up, boolean h17) {
		if (cards.size() != 2) {
			return false;
		}
		Hands.Value v = Hands.value(cards);
		if (v.soft()) {
			return false;
		}
		if (Hands.isPair(cards) && cards.getFirst().value() == 8) {
			return h17 && up == 11;
		}
		return switch (v.total()) {
			case 16 -> up >= 9;
			case 15 -> up == 10 || (h17 && up == 11);
			case 17 -> h17 && up == 11;
			default -> false;
		};
	}

	/** Perfect basic strategy (4–8 decks) for the table's rules, among the legal actions. */
	public static Action tableStrategy(List<Card> cards, Card dealerUp, List<Action> legal, BlackjackRules rules) {
		int up = dealerUp.value();
		boolean h17 = rules.dealerHitsSoft17();
		if (legal.contains(Action.SURRENDER) && surrenderWanted(cards, up, h17)) {
			return Action.SURRENDER;
		}
		if (legal.contains(Action.SPLIT) && Hands.isPair(cards) && pairSplits(cards.getFirst(), up, rules.doubleAfterSplit())) {
			return Action.SPLIT;
		}
		if (!legal.contains(Action.HIT)) {
			return Action.STAND; // split aces
		}
		Hands.Value v = Hands.value(cards);
		Want want = v.soft() ? soft(v.total(), up, h17) : hard(v.total(), up, h17);
		return switch (want) {
			case D -> legal.contains(Action.DOUBLE) ? Action.DOUBLE : Action.HIT;
			case DS -> legal.contains(Action.DOUBLE) ? Action.DOUBLE : Action.STAND;
			case S -> Action.STAND;
			case H -> Action.HIT;
		};
	}

	/** EASY "mimic the dealer": hit below 17, stand on 17+, never double, split only A-A and 8-8. */
	public static Action mimicDealer(List<Card> cards, List<Action> legal) {
		if (legal.contains(Action.SPLIT) && Hands.isPair(cards) && (cards.getFirst().isAce() || cards.getFirst().value() == 8)) {
			return Action.SPLIT;
		}
		if (!legal.contains(Action.HIT)) {
			return Action.STAND;
		}
		return Hands.total(cards) < 17 ? Action.HIT : Action.STAND;
	}

	/** A spot where NORMAL may err: a soft total, or basic strategy says double. */
	public static boolean isErrorSpot(List<Card> cards, Action correct) {
		return correct == Action.DOUBLE || Hands.value(cards).soft();
	}

	/** NORMAL's mistake: a different legal action (double → hit / stand, soft stand ↔ hit). */
	public static Action mistakeFor(Action correct, List<Action> legal, BotRng rng) {
		List<Action> pool = switch (correct) {
			case DOUBLE -> List.of(Action.HIT, Action.STAND);
			case STAND -> List.of(Action.HIT);
			case HIT -> List.of(Action.STAND);
			default -> List.of(Action.HIT, Action.STAND);
		};
		List<Action> options = new ArrayList<>();
		for (Action a : pool) {
			if (a != correct && legal.contains(a)) {
				options.add(a);
			}
		}
		return options.isEmpty() ? correct : options.get(rng.nextInt(options.size()));
	}

	// ---- the policy ----------------------------------------------------------------------------------

	@Override
	public Act decide(BotProfile bot, View view, Object work, BotRng rng) {
		return switch (view) {
			case View.Insurance ins -> new Act.Insure(bot.level() == BotDifficulty.EASY && rng.nextDouble() < EASY_INSURANCE_RATE);
			case View.Turn t -> switch (bot.level()) {
				case EASY -> new Act.Play(mimicDealer(t.hand(), t.legal()));
				case NORMAL -> {
					Action correct = tableStrategy(t.hand(), t.upCard(), t.legal(), t.rules());
					boolean err = isErrorSpot(t.hand(), correct) && rng.nextDouble() < NORMAL_ERROR_RATE;
					yield new Act.Play(err ? mistakeFor(correct, t.legal(), rng) : correct);
				}
				default -> new Act.Play(tableStrategy(t.hand(), t.upCard(), t.legal(), t.rules()));
			};
		};
	}

	@Override
	public Act legalize(View view, Act action) {
		return switch (view) {
			case View.Insurance ins -> action instanceof Act.Insure ? action : new Act.Insure(false);
			case View.Turn t -> {
				if (action instanceof Act.Play p && t.legal().contains(p.action())) {
					yield action;
				}
				yield new Act.Play(t.legal().contains(Action.STAND) || t.legal().isEmpty() ? Action.STAND : t.legal().getFirst());
			}
		};
	}

	// ---- virtual bets ----------------------------------------------------------------------------------

	/**
	 * Per-bot betting memory: {@code base} = flat bet (NORMAL / HARD) or the progression base (EASY);
	 * {@code next} = the next virtual bet.
	 */
	public record BetMemory(long base, long next) {}

	/** Initial memory of a new bot at a table with minimum {@code min} and maximum {@code max} (≤ 0 = none). */
	public static BetMemory newBetMemory(BotDifficulty level, BotRng rng, long min, long max) {
		long m = Math.max(1, min);
		int lo = level == BotDifficulty.EASY ? 1 : level == BotDifficulty.NORMAL ? 2 : 5;
		int hi = level == BotDifficulty.EASY ? 5 : level == BotDifficulty.NORMAL ? 5 : 10;
		long base = Math.max(m, Math.min(cap(max), m * rng.between(lo, hi)));
		return new BetMemory(base, base);
	}

	/**
	 * After a round: EASY doubles after a net loss and resets once the doubled bet would pass 8 × min (or
	 * the max), or after a win / push draws a fresh 1–5 × min base; NORMAL / HARD stay flat.
	 */
	public static BetMemory afterRound(BotDifficulty level, BetMemory mem, long net, BotRng rng, long min, long max) {
		if (level != BotDifficulty.EASY) {
			return mem;
		}
		long m = Math.max(1, min);
		if (net < 0) {
			long doubled = mem.next() * 2;
			if (doubled <= 8 * m && doubled <= cap(max)) {
				return new BetMemory(mem.base(), doubled);
			}
			return new BetMemory(mem.base(), mem.base());
		}
		long base = Math.max(m, Math.min(cap(max), m * rng.between(1, 5)));
		return new BetMemory(base, base);
	}

	private static long cap(long max) {
		return max <= 0 ? Long.MAX_VALUE : max;
	}

	/** Think delay per blackjack action (BOTS.md §7.3): EASY 15–40 t, NORMAL 10–30 t, HARD 10–25 t. */
	public static int thinkTicks(BotRng rng, BotDifficulty level, BotSpeed speed, double fastFactor) {
		if (speed == BotSpeed.INSTANT) {
			return 0;
		}
		int t = switch (level) {
			case EASY -> rng.between(15, 40);
			case NORMAL -> rng.between(10, 30);
			default -> rng.between(10, 25);
		};
		return speed == BotSpeed.FAST ? (int) Math.round(t * fastFactor) : t;
	}

	/**
	 * Seats whose settlement moves money: settled seats of UNPAID HUMAN participants only. Bot seats
	 * (virtual bets) are never payable — the table pays exactly this list.
	 */
	public static List<Seat> payableSeats(BlackjackRound round, IntPredicate unpaidHuman) {
		List<Seat> out = new ArrayList<>();
		for (Seat s : round.seats()) {
			if (s.settled && unpaidHuman.test(s.seat)) {
				out.add(s);
			}
		}
		return out;
	}
}
