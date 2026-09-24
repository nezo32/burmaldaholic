package dev.nezo.burmaldaholic.games.poker.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.games.poker.logic.Hand.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Seeded poker table simulator for the bot tests (BOTS.md §12.1, §12.2), same as Bedrock
 * {@code games/poker/logic/sim.ts}. Two independent streams, as in the game: the GAME rng shuffles (and
 * only shuffles), the BOT rng drives every bot decision, and scripted "humans" have their own stream.
 * Fixed-depth cash game: every hand starts at {@code stackBb} BB for every seat; the button moves one
 * seat per hand.
 */
final class PokerSim {
	/** A scripted human: sees exactly what the seat sees. */
	@FunctionalInterface
	interface Strategy {
		Action act(Hand h, int i, BotRng rng);
	}

	/** A seat: a bot profile, or a scripted human. */
	record Seat(BotProfile bot, Strategy human) {
		static Seat bot(BotProfile p) {
			return new Seat(p, null);
		}

		static Seat human(Strategy s) {
			return new Seat(null, s);
		}
	}

	/** A counting GAME rng (the test checks bots never draw from it). */
	static final class CountingRng implements PokerRng {
		private final PokerRng rng;
		long calls;

		CountingRng(long seed) {
			this.rng = PokerRng.of(new java.util.SplittableRandom(seed));
		}

		@Override
		public int nextInt(int bound) {
			calls++;
			return rng.nextInt(bound);
		}

		@Override
		public double nextDouble() {
			calls++;
			return rng.nextDouble();
		}
	}

	/**
	 * @param net     chips won per seat over all hands
	 * @param perHand per-hand net per seat
	 */
	record Result(long[] net, List<long[]> perHand, PokerTable table) {}

	private PokerSim() {}

	static Result simulate(List<Seat> seats, int hands, long bb, int stackBb, PokerRng gameRng, long botSeed, long humanSeed,
			PokerBotPolicy.Config cfg, Consumer<Hand> onDeal) {
		long stack = stackBb * bb;
		PokerTable t = new PokerTable(seats.size(), bb, Pots.RakeConfig.NONE);
		BotRng botRng = BotRng.seeded(botSeed);
		BotRng humanRng = BotRng.seeded(humanSeed);
		PokerBotPolicy policy = new PokerBotPolicy(cfg);
		for (int i = 0; i < seats.size(); i++) {
			Seat s = seats.get(i);
			if (s.human() != null) {
				t.addHuman("h" + i, "H" + i, stack);
			} else {
				BotProfile p = s.bot();
				t.seatBot(new BotProfile(String.format("b%07d", i), p.nameId(), p.level(), p.personality()), Purse.BANK, stack);
			}
		}
		long[] net = new long[seats.size()];
		List<long[]> perHand = new ArrayList<>();
		for (int n = 0; n < hands; n++) {
			for (PokerTable.Seat s : t.occupied()) {
				s.stack = stack;
			}
			Hand h = t.startHand(gameRng);
			if (h == null) {
				break;
			}
			if (onDeal != null) {
				onDeal.accept(h);
			}
			int[] handSeats = t.handSeats();
			for (int guard = 0; guard < 400 && !h.complete(); guard++) {
				int i = h.toAct();
				int seatIdx = handSeats[i];
				PokerTable.Seat ts = t.seat(seatIdx);
				Seat seat = seats.get(seatIdx);
				Action a;
				if (seat.human() == null) {
					a = policy.decideNow(ts.bot, PokerBotPolicy.view(h, i, t::statsOf, ts.tilt > 0), botRng);
				} else {
					a = seat.human().act(h, i, humanRng);
				}
				try {
					h.apply(h.coerce(a));
				} catch (IllegalStateException e) {
					h.apply(h.legal().canCheck() ? Action.check() : Action.fold());
				}
			}
			if (!h.complete()) {
				throw new IllegalStateException("hand did not finish");
			}
			long[] row = new long[seats.size()];
			for (int k = 0; k < h.players().size(); k++) {
				row[handSeats[k]] = h.result().net()[k];
				net[handSeats[k]] += h.result().net()[k];
			}
			perHand.add(row);
			t.settleHand();
		}
		return new Result(net, perHand, t);
	}

	/** Win rate in BB/100 and its 95 % half-width of per-hand values (chips). */
	static double[] bbPer100(double[] xs, long bb) {
		int n = xs.length;
		if (n == 0) {
			return new double[] {0, 0};
		}
		double mean = 0;
		for (double x : xs) {
			mean += x;
		}
		mean /= n;
		double var = 0;
		for (double x : xs) {
			var += (x - mean) * (x - mean);
		}
		var /= Math.max(1, n - 1);
		return new double[] {mean / bb * 100, 1.96 * Math.sqrt(var / n) / bb * 100};
	}

	// ---- scripted humans (BOTS.md §12.2) ------------------------------------------------------------------

	private static int[] board(Hand h) {
		return h.board().stream().mapToInt(Integer::intValue).toArray();
	}

	/** Shoves every decision. */
	static final Strategy ALWAYS_SHOVE = (h, i, rng) -> Action.allIn();

	/**
	 * Always bets the flop: open-raises 3 BB, calls raises up to 10 % of the stack; bets ⅔ pot on every flop
	 * checked to it; after that continues only with a pair or better (check / call), never bluffs.
	 */
	static final Strategy ALWAYS_BET_FLOP = (h, i, rng) -> {
		Hand.Legal l = h.legal(i);
		Hand.Player p = h.player(i);
		if (h.street() == Hand.Street.PREFLOP) {
			if (h.currentBet() <= h.bb()) {
				return l.canRaise() ? Action.raiseTo(3 * h.bb()) : l.canCheck() ? Action.check() : Action.call();
			}
			return l.toCall() <= 0.1 * (p.stack() + p.bet()) ? Action.call() : Action.fold();
		}
		int made = PokerBotPolicy.madeCategory(p.hole(), board(h));
		if (h.street() == Hand.Street.FLOP && h.currentBet() == 0) {
			return Action.raiseTo(Math.max(h.bb(), Math.round(h.potTotal() * 2.0 / 3)));
		}
		if (l.toCall() > 0) {
			return made >= 1 ? Action.call() : Action.fold();
		}
		return Action.check();
	};

	/**
	 * Calling station (loose-passive, VPIP ≈ 45 %): never raises and never bets; preflop calls with the top
	 * 45 % of hands (raises up to a third of the stack); on the flop calls any pair, draw or two overcards
	 * to bets ≤ pot; on the turn and river calls any pair, and a draw to bets ≤ ½ pot.
	 */
	static final Strategy CALLING_STATION = (h, i, rng) -> {
		Hand.Legal l = h.legal(i);
		Hand.Player p = h.player(i);
		if (l.canCheck()) {
			return Action.check();
		}
		if (h.street() == Hand.Street.PREFLOP) {
			return Ranges.handRankShare(p.hole()) <= 0.45 && l.toCall() <= (p.stack() + p.bet()) / 3 ? Action.call() : Action.fold();
		}
		int[] b = board(h);
		int made = PokerBotPolicy.madeCategory(p.hole(), b);
		long pot = h.potTotal();
		double frac = (double) l.toCall() / Math.max(1, pot - l.toCall());
		boolean draw = PokerBotPolicy.drawOuts(p.hole(), b) >= 8;
		if (h.street() == Hand.Street.FLOP) {
			int top = 0;
			for (int c : b) {
				top = Math.max(top, Cards.rank(c));
			}
			boolean over = Math.min(Cards.rank(p.hole()[0]), Cards.rank(p.hole()[1])) > top;
			return frac <= 1 && (made >= 1 || draw || over) ? Action.call() : Action.fold();
		}
		return made >= 1 || (draw && frac <= 0.5) ? Action.call() : Action.fold();
	};
}
