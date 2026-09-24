package dev.nezo.burmaldaholic.games.slots.v2.present.preview;

import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SymbolRole;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Tumble;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Ways;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * PREVIEW FIXTURE (lane J-L9): a small reference implementation of the SLOTS.md §1.1 ways evaluation and the §3.2
 * tumble chain, used ONLY to build fake tapes and preview timelines until lane J-L8's engine (S-M1) lands. Each
 * method first tries the real engine ({@link Ways#evaluate}, {@link Tumble#run}) and falls back to this code while
 * those still throw {@link UnsupportedOperationException}. Never used to settle money.
 */
public final class PreviewEngine {
	private PreviewEngine() {}

	/** 15 cells (reel × 3 + row) of the strip window at {@code stops}. */
	public static int[] window(MachineDef def, int[] stops) {
		int[] c = new int[15];
		for (int r = 0; r < 5; r++) for (int y = 0; y < 3; y++) c[r * 3 + y] = def.symbolAt(r, stops[r], y);
		return c;
	}

	/** Ways evaluation; {@code stickyMask} bit r-1 forces reel r (2..4) to wilds. */
	public static Ways.Result evaluate(MachineDef def, int[] cells, int stickyMask) {
		try {
			return Ways.evaluate(def, new Window(cells), stickyMask);
		} catch (UnsupportedOperationException notYet) {
			return evaluateLocal(def, cells, stickyMask);
		}
	}

	static Ways.Result evaluateLocal(MachineDef def, int[] cellsIn, int stickyMask) {
		int[] cells = applySticky(def, cellsIn, stickyMask);
		SymbolRole[] roles = def.roles();
		List<Ways.WayWin> wins = new ArrayList<>();
		long pay = 0;
		int winMask = 0;
		for (int p = 0; p < roles.length; p++) {
			if (roles[p] != SymbolRole.PAY) continue;
			int ways = 1;
			int k = 0;
			int mask = 0;
			for (int r = 0; r < 5; r++) {
				int n = 0;
				for (int y = 0; y < 3; y++) {
					int s = cells[r * 3 + y];
					if (s == p || roles[s] == SymbolRole.WILD) {
						n++;
						mask |= Window.bit(r, y);
					}
				}
				if (n == 0) {
					// drop the cells of this reel from the mask
					break;
				}
				ways *= n;
				k++;
			}
			// clear bits beyond k (the loop may have added bits of reel k before breaking: it did not, n == 0)
			if (k >= 3 && def.paysFifths()[p][k - 3] > 0) {
				int keep = (1 << (k * 3)) - 1;
				mask &= keep;
				long w = (long) def.paysFifths()[p][k - 3] * ways;
				wins.add(new Ways.WayWin(p, k, ways, w, mask));
				pay += w;
				winMask |= mask;
			}
		}
		int scatters = 0;
		int bonus = 0;
		int coins = 0;
		for (int r = 0; r < 5; r++) {
			for (int y = 0; y < 3; y++) {
				SymbolRole role = roles[cells[r * 3 + y]];
				if (role == SymbolRole.SCATTER) scatters++;
				if (role == SymbolRole.BONUS && (def.bonusReelsMask() & (1 << r)) != 0) bonus++;
				if (role == SymbolRole.COIN) coins++;
			}
		}
		return new Ways.Result(wins, pay, winMask, scatters, bonus, coins);
	}

	/** Cells with the sticky reels (bits 0..2 = reels 2..4) shown as wilds. */
	public static int[] applySticky(MachineDef def, int[] cells, int stickyMask) {
		int[] out = cells.clone();
		for (int b = 0; b < 3; b++) {
			if ((stickyMask & (1 << b)) == 0) continue;
			int r = b + 1;
			for (int y = 0; y < 3; y++) out[r * 3 + y] = wildIndex(def);
		}
		return out;
	}

	public static int wildIndex(MachineDef def) {
		for (int i = 0; i < def.roles().length; i++) if (def.roles()[i] == SymbolRole.WILD) return i;
		return 0;
	}

	/** Scatter pay in fifths for {@code count} scatters (0 below 3). */
	public static long scatterPay(MachineDef def, int count) {
		if (count < 3) return 0;
		return def.scatterFifths()[Math.min(count, 5) - 3];
	}

	/** Tumble chain (SLOTS.md §3.2) from the stops with {@code ladder}. */
	public static Tumble.Chain tumble(MachineDef def, int[] stops, int[] ladder) {
		try {
			return Tumble.run(def, stops, ladder);
		} catch (UnsupportedOperationException notYet) {
			return tumbleLocal(def, stops, ladder);
		}
	}

	static Tumble.Chain tumbleLocal(MachineDef def, int[] stops, int[] ladder) {
		int[] cells = window(def, stops);
		int[] tops = stops.clone();
		List<Tumble.Step> steps = new ArrayList<>();
		long total = 0;
		for (int step = 0; step < 64; step++) {
			Ways.Result res = evaluateLocal(def, cells, 0);
			if (res.wins().isEmpty()) break;
			int mult = ladder.length == 0 ? 1 : ladder[Math.min(step, ladder.length - 1)];
			long pay = res.payFifths() * mult;
			total += pay;
			steps.add(new Tumble.Step(step, new Window(cells), res, mult, pay, tops.clone()));
			// remove, fall, refill from above
			int[] next = new int[15];
			for (int r = 0; r < 5; r++) {
				int[] survivors = new int[3];
				int n = 0;
				for (int y = 0; y < 3; y++) if ((res.winMask() & Window.bit(r, y)) == 0) survivors[n++] = cells[r * 3 + y];
				int m = 3 - n;
				int len = def.stripLength(r);
				for (int i = 0; i < m; i++) next[r * 3 + i] = def.strips()[r][Math.floorMod(tops[r] - m + i, len)];
				for (int i = 0; i < n; i++) next[r * 3 + m + i] = survivors[i];
				tops[r] = Math.floorMod(tops[r] - m, len);
			}
			cells = next;
		}
		return new Tumble.Chain(steps, new Window(cells), total);
	}

	/** Cells removed between step {@code s} and the next (the winners of step s). */
	public static int explodeMask(Tumble.Step s) {
		return s.result().winMask();
	}
}
