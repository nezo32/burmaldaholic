package dev.nezo.burmaldaholic.games.extras.logic;

import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import java.util.ArrayList;
import java.util.List;

/**
 * Dice Duel (GAME_DESIGN.md §11.5). Always honest (a table game: no streak re-draw, no modifiers).
 *
 * <p>Vs house: player and dealer each roll 2d6; higher total wins 1:1; ties push, except ties on a total in
 * {@code houseWinsTieOn} (default [7]) which the house wins. HE = (6/36)² = 2.78 %.
 *
 * <p>PvP: both roll 2d6, higher total takes the pot minus the rake; ties re-roll, after {@link #PVP_MAX_ROLLS}
 * ties in a row both stakes are refunded. Pure Java.
 */
public final class DiceDuel {
	public static final int PVP_MAX_ROLLS = 3;
	private static final int[] WAYS = {0, 0, 1, 2, 3, 4, 5, 6, 5, 4, 3, 2, 1};

	private DiceDuel() {}

	public record Roll(int a, int b) {
		public int total() {
			return a + b;
		}
	}

	public static Roll roll2d6(CasinoRng rng) {
		return new Roll(1 + rng.nextInt(6), 1 + rng.nextInt(6));
	}

	public enum HouseOutcome {
		WIN, LOSE, PUSH, HOUSE_TIE
	}

	public record HouseDuel(Roll player, Roll dealer, HouseOutcome outcome) {}

	public static HouseOutcome judge(Roll player, Roll dealer, int[] houseWinsTieOn) {
		int a = player.total();
		int b = dealer.total();
		if (a > b) {
			return HouseOutcome.WIN;
		}
		if (a < b) {
			return HouseOutcome.LOSE;
		}
		return contains(houseWinsTieOn, a) ? HouseOutcome.HOUSE_TIE : HouseOutcome.PUSH;
	}

	public static HouseDuel duelHouse(CasinoRng rng, int[] houseWinsTieOn) {
		Roll player = roll2d6(rng);
		Roll dealer = roll2d6(rng);
		return new HouseDuel(player, dealer, judge(player, dealer, houseWinsTieOn));
	}

	/** Total return (stake included): win 2×, push 1×, loss / house tie 0. */
	public static long houseReturn(long stake, HouseOutcome o) {
		return switch (o) {
			case WIN -> 2 * stake;
			case PUSH -> stake;
			case LOSE, HOUSE_TIE -> 0;
		};
	}

	/** Exact RTP of the house duel. */
	public static double houseRtp(int[] houseWinsTieOn) {
		double tie = 0;
		double houseTie = 0;
		for (int s = 2; s <= 12; s++) {
			double p = Math.pow(WAYS[s] / 36.0, 2);
			tie += p;
			if (contains(houseWinsTieOn, s)) {
				houseTie += p;
			}
		}
		double win = (1 - tie) / 2;
		return 2 * win + (tie - houseTie);
	}

	private static boolean contains(int[] values, int v) {
		if (values == null) {
			return false;
		}
		for (int x : values) {
			if (x == v) {
				return true;
			}
		}
		return false;
	}

	// ---- PvP ---------------------------------------------------------------------------------

	public record PvpRound(Roll a, Roll b) {}

	public enum PvpResult {
		A, B, REFUND
	}

	public record PvpDuel(List<PvpRound> rounds, PvpResult result) {}

	public static PvpDuel duelPvp(CasinoRng rng, int maxRolls) {
		List<PvpRound> rounds = new ArrayList<>();
		for (int i = 0; i < Math.max(1, maxRolls); i++) {
			Roll a = roll2d6(rng);
			Roll b = roll2d6(rng);
			rounds.add(new PvpRound(a, b));
			int d = a.total() - b.total();
			if (d != 0) {
				return new PvpDuel(List.copyOf(rounds), d > 0 ? PvpResult.A : PvpResult.B);
			}
		}
		return new PvpDuel(List.copyOf(rounds), PvpResult.REFUND);
	}

	/** Pot split: winner receives {@code pot − rake}; rake = floor(pot × percent / 100). */
	public record PvpPayout(long pot, long rake, long winnerGets) {}

	public static PvpPayout pvpPayout(long stake, int rakePercent) {
		long pot = 2 * stake;
		long rake = pot * Math.max(0, rakePercent) / 100;
		return new PvpPayout(pot, rake, pot - rake);
	}
}
