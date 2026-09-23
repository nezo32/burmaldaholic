package dev.nezo.burmaldaholic.games.craps.logic;

import dev.nezo.burmaldaholic.games.craps.logic.BetResolution.Outcome;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Resolves bets against a roll (GAME_DESIGN §10). Every bet is resolved independently from the
 * pre-roll table point, so the same code drives the live table and the "bets stay working"
 * auto-completion of a player who left (§4.1). Pure Java.
 */
public final class CrapsResolver {
	private CrapsResolver() {}

	/** Two fair dice from a uniform source {@code bound -> [0, bound)}. */
	public static int[] rollDice(IntUnaryOperator uniform) {
		return new int[] {1 + uniform.applyAsInt(6), 1 + uniform.applyAsInt(6)};
	}

	/** Resolves one bet. {@code tablePoint} is the point BEFORE the roll (0 = come-out). */
	public static BetResolution resolve(Bet bet, int tablePoint, int total, CrapsRules rules) {
		boolean comeOut = tablePoint == 0;
		long f = bet.flat();
		long o = bet.odds();
		return switch (bet.kind()) {
			case FIELD -> {
				long r = CrapsMath.fieldReturn(total, f, rules);
				yield BetResolution.resolved(bet, r > 0 ? Outcome.WIN : Outcome.LOSE, r);
			}
			case PASS -> {
				if (comeOut) {
					if (total == 7 || total == 11) {
						yield BetResolution.resolved(bet, Outcome.WIN, 2 * f + o);
					}
					if (total == 2 || total == 3 || total == 12) {
						yield BetResolution.resolved(bet, Outcome.LOSE, 0);
					}
					yield BetResolution.stay(bet);
				}
				if (total == tablePoint) {
					yield BetResolution.resolved(bet, Outcome.WIN, 2 * f + o + CrapsMath.oddsWin(OddsSide.TAKE, tablePoint, o));
				}
				yield total == 7 ? BetResolution.resolved(bet, Outcome.LOSE, 0) : BetResolution.stay(bet);
			}
			case DONT_PASS -> {
				if (comeOut) {
					if (total == 2 || total == 3) {
						yield BetResolution.resolved(bet, Outcome.WIN, 2 * f + o);
					}
					if (total == 12) {
						yield BetResolution.resolved(bet, Outcome.PUSH, f + o);
					}
					if (total == 7 || total == 11) {
						yield BetResolution.resolved(bet, Outcome.LOSE, 0);
					}
					yield BetResolution.stay(bet);
				}
				if (total == 7) {
					yield BetResolution.resolved(bet, Outcome.WIN, 2 * f + o + CrapsMath.oddsWin(OddsSide.LAY, tablePoint, o));
				}
				yield total == tablePoint ? BetResolution.resolved(bet, Outcome.LOSE, 0) : BetResolution.stay(bet);
			}
			case COME -> {
				int p = bet.point();
				if (p == 0) {
					if (total == 7 || total == 11) {
						yield BetResolution.resolved(bet, Outcome.WIN, 2 * f + o);
					}
					if (total == 2 || total == 3 || total == 12) {
						yield BetResolution.resolved(bet, Outcome.LOSE, o);
					}
					yield new BetResolution(bet, Outcome.MOVE, 0, false, total);
				}
				// Come odds are OFF on the come-out roll: returned unchanged whatever happens.
				if (total == p) {
					yield comeOut ? new BetResolution(bet, Outcome.WIN, 2 * f + o, o > 0, 0)
						: BetResolution.resolved(bet, Outcome.WIN, 2 * f + o + CrapsMath.oddsWin(OddsSide.TAKE, p, o));
				}
				if (total == 7) {
					yield comeOut ? new BetResolution(bet, Outcome.LOSE, o, o > 0, 0) : BetResolution.resolved(bet, Outcome.LOSE, 0);
				}
				yield BetResolution.stay(bet);
			}
			case DONT_COME -> {
				int p = bet.point();
				if (p == 0) {
					if (total == 2 || total == 3) {
						yield BetResolution.resolved(bet, Outcome.WIN, 2 * f + o);
					}
					if (total == 12) {
						yield BetResolution.resolved(bet, Outcome.PUSH, f + o);
					}
					if (total == 7 || total == 11) {
						yield BetResolution.resolved(bet, Outcome.LOSE, 0);
					}
					yield new BetResolution(bet, Outcome.MOVE, 0, false, total);
				}
				// Lay odds are always working.
				if (total == 7) {
					yield BetResolution.resolved(bet, Outcome.WIN, 2 * f + o + CrapsMath.oddsWin(OddsSide.LAY, p, o));
				}
				yield total == p ? BetResolution.resolved(bet, Outcome.LOSE, 0) : BetResolution.stay(bet);
			}
		};
	}

	/**
	 * Result of one roll over a set of bets.
	 *
	 * @param remaining bets still working (copies; moved come bets carry their point)
	 */
	public record RollResult(int d1, int d2, int pointBefore, RollEvent event, List<BetResolution> resolutions, List<Bet> remaining) {
		public int total() {
			return d1 + d2;
		}
	}

	/** Applies a roll without mutating the input bets. */
	public static RollResult apply(int point, List<Bet> bets, int d1, int d2, CrapsRules rules) {
		int total = d1 + d2;
		RollEvent event = RollEvent.of(point, total);
		List<BetResolution> resolutions = new ArrayList<>(bets.size());
		List<Bet> remaining = new ArrayList<>();
		for (Bet b : bets) {
			BetResolution r = resolve(b, point, total, rules);
			resolutions.add(r);
			if (r.outcome() == Outcome.STAY) {
				remaining.add(b.copy());
			} else if (r.outcome() == Outcome.MOVE) {
				Bet moved = b.copy();
				moved.moveTo(r.movedTo());
				remaining.add(moved);
			}
		}
		return new RollResult(d1, d2, point, event, resolutions, remaining);
	}

	/**
	 * Plays out a player's bets after they left the table (§4.1: craps bets stay working until
	 * resolved): honest dice for them alone until every bet is resolved. Same expected return as
	 * staying. Returns the total return per bet id; bets still open after {@code maxRolls} (never in
	 * practice) are returned as a push.
	 */
	public static Map<Integer, Long> autoComplete(int point, List<Bet> bets, IntUnaryOperator uniform, CrapsRules rules, int maxRolls) {
		Map<Integer, Long> out = new HashMap<>();
		List<Bet> live = bets;
		int p = point;
		for (int i = 0; i < maxRolls && !live.isEmpty(); i++) {
			int[] d = rollDice(uniform);
			RollResult r = apply(p, live, d[0], d[1], rules);
			for (BetResolution x : r.resolutions()) {
				if (x.outcome().resolved()) {
					out.put(x.bet().id(), x.totalReturn());
				}
			}
			live = r.remaining();
			p = r.event().nextPoint();
		}
		for (Bet b : live) {
			out.put(b.id(), b.staked());
		}
		return out;
	}

	public static Map<Integer, Long> autoComplete(int point, List<Bet> bets, IntUnaryOperator uniform, CrapsRules rules) {
		return autoComplete(point, bets, uniform, rules, 10_000);
	}
}
