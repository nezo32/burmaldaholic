package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.List;

/**
 * Evaluation of one reel spin (a base spin or one free spin) from its 5 stops: the tumble chain (Nether), End's
 * expanding sticky Dragon Eggs in free spins, the scatter pay and the trigger counts on the final window. A pure,
 * deterministic function of {@code (def, stops, free, stickyBefore)} — the presentation re-derives everything from
 * the tape with it, so nothing here needs to be persisted.
 *
 * @param stops         the 5 stops
 * @param landed        the window as it lands (before expansion / tumbles)
 * @param chain         the evaluation chain (one step unless the machine tumbles)
 * @param finalWindow   window where triggers are checked (after tumbles; End free spins: after expansion)
 * @param wayFifths     Σ way pays of the chain, ladder applied (fifths)
 * @param scatters      scatters on the final window (covered scatters under a sticky reel do not count)
 * @param scatterFifths scatter pay (fifths)
 * @param payFifths     {@code wayFifths + scatterFifths}; free-spin multipliers are applied by the draw
 * @param stickyBefore  End free spins: sticky mask before the spin (bits 0–2 = reels 2–4)
 * @param stickyAfter   End free spins: sticky mask after the spin (every reel where an Egg landed)
 * @param bonus         the bonus game triggers on the final window
 * @param coinMask      15-bit mask of coin cells on the final window (Nether)
 * @param fiveTop       5 of a kind of the machine's top symbol at any step
 */
public record SpinEval(int[] stops, Window landed, Tumble.Chain chain, Window finalWindow, long wayFifths, int scatters,
		long scatterFifths, long payFifths, int stickyBefore, int stickyAfter, boolean bonus, int coinMask, boolean fiveTop) {
	/** Number of tumbles (Nether; 0 elsewhere). */
	public int tumbles() {
		return chain.tumbles();
	}

	public int coins() {
		return Integer.bitCount(coinMask);
	}

	/** Reels (bits 0–2 = reels 2–4) whose Egg expanded on THIS spin (newly sticky). */
	public int newlySticky() {
		return stickyAfter & ~stickyBefore;
	}

	public static SpinEval of(MachineDef def, int[] stops, boolean free, int stickyBefore) {
		Window landed = Window.fromStops(def, stops);
		Tumble.Chain chain;
		int stickyAfter = 0;
		if (free && def.stickyWilds()) {
			int[] c = landed.cells();
			int wild = def.wild();
			stickyAfter = stickyBefore;
			for (int j = 0; j < 3; j++) {
				int r = j + 1;
				boolean has = (stickyBefore >> j & 1) != 0;
				for (int y = 0; y < 3 && !has; y++) has = c[r * 3 + y] == wild;
				if (has) {
					for (int y = 0; y < 3; y++) c[r * 3 + y] = wild;
					stickyAfter |= 1 << j;
				}
			}
			Ways.Result res = Ways.evaluate(def, c, 0);
			Window w = new Window(c);
			Tumble.Step step = new Tumble.Step(0, w, res, 1, res.payFifths(), stops.clone());
			chain = new Tumble.Chain(List.of(step), w, res.payFifths(), 0);
		} else {
			chain = Tumble.run(def, stops, free ? def.ladderFree() : def.ladder());
		}
		Tumble.Step last = chain.steps().getLast();
		int scatters = last.result().scatters();
		long scat = Ways.scatterPay(def, scatters);
		int coin = def.coin();
		int coinMask = 0;
		if (coin >= 0) {
			int[] fc = chain.finalWindow().cells();
			for (int i = 0; i < 15; i++) if (fc[i] == coin) coinMask |= 1 << i;
		}
		int top = def.topSymbol();
		boolean five = false;
		for (Tumble.Step s : chain.steps()) {
			for (Ways.WayWin w : s.result().wins()) five |= w.symbol() == top && w.k() == 5;
		}
		return new SpinEval(stops.clone(), landed, chain, chain.finalWindow(), chain.payFifths(), scatters, scat, chain.payFifths() + scat,
			stickyBefore, stickyAfter, last.result().bonusTriggered(def), coinMask, five);
	}
}
