package dev.nezo.burmaldaholic.games.poker.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import java.util.ArrayList;
import java.util.List;

/** Shared helpers for the poker bot tests (scripted rng, view builder, profiles). */
final class PokerTestSupport {
	private PokerTestSupport() {}

	/** An rng that replays the given doubles (then repeats the last); nextInt = floor(v × bound). */
	static BotRng seq(double... values) {
		return new BotRng() {
			private int i;

			@Override
			public double nextDouble() {
				return values[Math.min(i++, values.length - 1)];
			}

			@Override
			public int nextInt(int bound) {
				return Math.min(bound - 1, (int) Math.floor(nextDouble() * bound));
			}
		};
	}

	/** Never takes a random branch (every chance check fails). */
	static final BotRng NEVER = seq(0.99);

	static BotProfile bot(BotDifficulty level, Personality p) {
		return new BotProfile("b0000001", "creeper42", level, p);
	}

	static final BotProfile EASY = bot(BotDifficulty.EASY, Personality.ROCK);
	static final BotProfile EASY_TAG = bot(BotDifficulty.EASY, Personality.TAG);
	static final BotProfile NORMAL = bot(BotDifficulty.NORMAL, Personality.TAG);
	static final BotProfile HARD = bot(BotDifficulty.HARD, Personality.TAG);

	static Equity.Result res(double equity, double pct, int samples) {
		return new Equity.Result(equity, pct, samples);
	}

	static Equity.Result res(double equity) {
		return res(equity, 0.5, 700);
	}

	/** Mutable view builder with the Bedrock test defaults (preflop, MP, facing the BB, 2 opponents). */
	static final class V {
		int[] hole;
		int[] board = new int[0];
		Hand.Street street = Hand.Street.PREFLOP;
		long pot = 15;
		long toCall = 10;
		long stack = 1000;
		long bet;
		long currentBet = 10;
		long bb = 10;
		boolean canCheck;
		boolean canRaise = true;
		long minRaiseTo = 20;
		long maxRaiseTo = 1000;
		PokerBotPolicy.Position position = PokerBotPolicy.Position.MP;
		boolean inPosition;
		int limpers;
		boolean facingRaise;
		int raises;
		boolean foldedTo;
		boolean preflopRaiser;
		int opponents = 2;
		List<PokerBotPolicy.Opp> opps = new ArrayList<>(List.of(
			new PokerBotPolicy.Opp(true, Ranges.Tag.ANY, null, Ranges.OpponentType.REGULAR, false),
			new PokerBotPolicy.Opp(false, Ranges.Tag.ANY, null, Ranges.OpponentType.REGULAR, false)));
		Ranges.Tag ownTag = Ranges.Tag.ANY;
		boolean tilt;

		V(String hole) {
			this.hole = Cards.parseAll(hole);
		}

		V board(String b) {
			board = Cards.parseAll(b);
			return this;
		}

		/** Flop, checked to the bot: pot 100. */
		V flop() {
			street = Hand.Street.FLOP;
			pot = 100;
			currentBet = 0;
			toCall = 0;
			canCheck = true;
			minRaiseTo = 10;
			return this;
		}

		/** Flop, facing a 50 bet into 100. */
		V flopBet() {
			street = Hand.Street.FLOP;
			pot = 150;
			currentBet = 50;
			toCall = 50;
			canCheck = false;
			minRaiseTo = 100;
			return this;
		}

		/** Preflop, facing a raise to {@code to}. */
		V facing(long to) {
			facingRaise = true;
			raises = 1;
			currentBet = to;
			toCall = to;
			return this;
		}

		PokerBotPolicy.View build() {
			return new PokerBotPolicy.View(hole, board, street, pot, toCall, stack, bet, currentBet, bb, canCheck, canRaise, minRaiseTo,
				maxRaiseTo, position, inPosition, limpers, facingRaise, raises, foldedTo, preflopRaiser, opponents, opps, ownTag, tilt);
		}
	}

	static final PokerBotPolicy POLICY = new PokerBotPolicy(new PokerBotPolicy.Config(300, 700));

	/** decide + legalize with a finished work result (or null). */
	static Hand.Action act(BotProfile bot, V v, Equity.Result work, BotRng rng) {
		return POLICY.act(bot, v.build(), work, rng);
	}
}
