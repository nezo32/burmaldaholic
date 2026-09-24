package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.ArrayList;
import java.util.List;

/** 243-ways evaluation (SLOTS.md §1.1). */
public final class Ways {
	private Ways() {}

	/**
	 * One winning symbol.
	 *
	 * @param symbol     paying symbol index
	 * @param k          reels matched (3–5)
	 * @param ways       n_1 × … × n_k
	 * @param payFifths  pay(P, k) × ways, fifths of the bet, before multipliers
	 * @param cellMask   15-bit mask of every cell taking part (the symbol and wilds on reels 1…k)
	 */
	public record WayWin(int symbol, int k, int ways, long payFifths, int cellMask) {}

	/**
	 * Evaluation of one window.
	 *
	 * @param wins          winning symbols in symbol order
	 * @param payFifths     Σ way pays (no scatter pay, no multiplier)
	 * @param winMask       union of the wins' cell masks (cells that explode in a tumble)
	 * @param scatters      scatter count on the window
	 * @param bonusCount    bonus reels (of {@code bonusReelsMask}) showing at least one bonus symbol
	 * @param coins         coin count (Nether)
	 * @param bonusReels    bit r set when reel r (0-based) shows the bonus symbol and it counts there
	 */
	public record Result(List<WayWin> wins, long payFifths, int winMask, int scatters, int bonusCount, int coins, int bonusReels) {
		/** The bonus game triggers: the bonus symbol on every one of its reels. */
		public boolean bonusTriggered(MachineDef def) {
			return def.bonusReelsMask() != 0 && (bonusReels & def.bonusReelsMask()) == def.bonusReelsMask();
		}
	}

	/**
	 * Evaluates a window. {@code stickyMask} (End free spins) forces reels 2–4 (bits 0–2) to WWW before
	 * evaluation; expanding wilds on the window are applied by the caller ({@link SpinEval}).
	 */
	public static Result evaluate(MachineDef def, Window window, int stickyMask) {
		return evaluate(def, window.cells(), stickyMask);
	}

	/** Same on a raw 15-cell array (index = reel × 3 + row); the array is not modified. */
	public static Result evaluate(MachineDef def, int[] cellsIn, int stickyMask) {
		int[] c = cellsIn;
		int wild = def.wild();
		if (stickyMask != 0 && wild >= 0) {
			c = cellsIn.clone();
			for (int j = 0; j < 3; j++) {
				if ((stickyMask >> j & 1) != 0) for (int y = 0; y < 3; y++) c[(j + 1) * 3 + y] = wild;
			}
		}
		SymbolRole[] roles = def.roles();
		int[][] pays = def.paysFifths();
		List<WayWin> wins = new ArrayList<>(2);
		long pay = 0;
		int mask = 0;
		for (int s = 0; s < roles.length; s++) {
			if (roles[s] != SymbolRole.PAY) continue;
			int[] p = pays[s];
			if (p[0] == 0 && p[1] == 0 && p[2] == 0) continue;
			int ways = 1;
			int k = 0;
			for (int r = 0; r < 5; r++) {
				int cnt = 0;
				for (int y = 0; y < 3; y++) {
					int x = c[r * 3 + y];
					if (x == s || (x == wild && r > 0)) cnt++;
				}
				if (cnt == 0) break;
				ways *= cnt;
				k++;
			}
			if (k >= 3 && p[k - 3] > 0) {
				long w = (long) ways * p[k - 3];
				int m = 0;
				for (int r = 0; r < k; r++) {
					for (int y = 0; y < 3; y++) {
						int x = c[r * 3 + y];
						if (x == s || (x == wild && r > 0)) m |= Window.bit(r, y);
					}
				}
				wins.add(new WayWin(s, k, ways, w, m));
				pay += w;
				mask |= m;
			}
		}
		int scatter = def.scatter();
		int bonus = def.bonus();
		int coin = def.coin();
		int scatters = 0;
		int bonusCount = 0;
		int coins = 0;
		int bonusReels = 0;
		for (int r = 0; r < 5; r++) {
			for (int y = 0; y < 3; y++) {
				int x = c[r * 3 + y];
				if (x == scatter) scatters++;
				if (x == coin) coins++;
				if (x == bonus && (def.bonusReelsMask() >> r & 1) != 0) bonusReels |= 1 << r;
			}
		}
		bonusCount = Integer.bitCount(bonusReels);
		return new Result(List.copyOf(wins), pay, mask, scatters, bonusCount, coins, bonusReels);
	}

	/** Scatter pay (fifths, before free-spin multipliers) for a scatter count. */
	public static long scatterPay(MachineDef def, int scatters) {
		return scatters >= 3 ? def.scatterFifths()[Math.min(scatters, 5) - 3] : 0;
	}
}
