package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exact RTP of a machine (SLOTS.md §7.1, §7.3): full enumeration of the base game and of one free spin
 * ({@link SlotEnumerator}), the free-spin Markov chains, the closed forms of the three bonus games and the
 * jackpot terms. Reproduces {@code docs/design/slots-math/analysis.py}. Used by {@code slots.validateRtp}
 * (warn when RTP &gt; 0.99 or a buy RTP exceeds its machine, never auto-fix) and by the paytable RTP line.
 */
public final class SlotRtpV2 {
	private SlotRtpV2() {}

	/**
	 * Result; every RTP component is a fraction of the amount staked.
	 *
	 * @param fsPerTrigger   mean free-spin win per trigger with 3 / 4 / 5 scatters (× bet)
	 * @param fsExpectedSpins expected spins played per trigger (3 / 4 / 5)
	 * @param bonusPerTrigger mean bonus win per trigger (× bet, jackpots excluded)
	 * @param jackpotHits    expected jackpot awards per spin (Mini … Grand)
	 * @param buyRtp         buy-feature RTP incl. contribution (NaN without a buy)
	 */
	public record Report(Machine machine, double baseWays, double scatterPay, double freeSpins, double bonus, double jackpotSeed,
			double jackpotContribution, double total, double totalOwned, double hitPay, double hitAny, double pFreeSpins, double pBonus,
			double[] fsPerTrigger, double[] fsExpectedSpins, double bonusPerTrigger, double[] jackpotHits, double buyRtp) {
		/** House edge for cashback (SLOTS.md §7.1: 1 − house RTP). */
		public double houseEdge() {
			return 1 - total;
		}

		/** Streak cap {@code r_cap = 0.99 / RTP − 1} (SLOTS.md §8.2). */
		public double streakCap() {
			return 0.99 / total - 1;
		}
	}

	/** Computes the full report (a few seconds for the default strips; Nether's tumbles dominate). */
	public static Report compute(MachineDef def) {
		SlotEnumerator.Stats base = SlotEnumerator.enumerate(def, SlotEnumerator.Mode.BASE, 0);
		double n = base.n();
		double baseRtp = base.sumPay() / 5.0 / n;
		double[] pSc = new double[6];
		for (int k = 3; k <= 5; k++) pSc[k] = base.pScatter(k);
		double scatterPay = 0;
		for (int k = 3; k <= 5; k++) scatterPay += pSc[k] * def.scatterFifths()[k - 3] / 5.0;
		double pFs = pSc[3] + pSc[4] + pSc[5];
		double[] fsTrig = new double[6];
		double[] fsSpins = new double[6];
		int R = def.retrigger();
		int C = def.fsCap();
		if (def.stickyWilds()) {
			double[] e = new double[8];
			double[][][] trans = new double[8][8][2];
			for (int s = 0; s < 8; s++) {
				SlotEnumerator.Stats st = SlotEnumerator.enumerate(def, SlotEnumerator.Mode.FREE, s);
				e[s] = st.sumPay() / 5.0 / st.n() * def.fsMultiplier();
				for (int post = 0; post < 8; post++) for (int rt = 0; rt < 2; rt++) trans[s][post][rt] = (double) st.post()[post][rt] / st.n();
			}
			Map<Long, Double> memoV = new HashMap<>();
			Map<Long, Double> memoS = new HashMap<>();
			for (int k = 3; k <= 5; k++) {
				int nSp = def.freeSpins()[k - 3];
				fsTrig[k] = sticky(e, trans, 0, nSp, nSp, R, C, memoV, true);
				fsSpins[k] = sticky(e, trans, 0, nSp, nSp, R, C, memoS, false);
			}
		} else {
			SlotEnumerator.Stats fs = def.tumbles() ? SlotEnumerator.enumerate(def, SlotEnumerator.Mode.FREE, 0) : base;
			double e = fs.sumPay() / 5.0 / fs.n() * def.fsMultiplier();
			double rho = fs.pScatter(3) + fs.pScatter(4) + fs.pScatter(5);
			Map<Long, Double> memo = new HashMap<>();
			for (int k = 3; k <= 5; k++) {
				int nSp = def.freeSpins()[k - 3];
				fsSpins[k] = iidSpins(nSp, nSp, rho, R, C, memo);
				fsTrig[k] = e * fsSpins[k];
			}
		}
		double fsRtp = 0;
		for (int k = 3; k <= 5; k++) fsRtp += pSc[k] * fsTrig[k];
		MachineDef.Features f = def.features();
		double[] jp = new double[5];
		double bonusRtp = 0;
		double pBonus = 0;
		double bonusPerTrigger = 0;
		switch (def.machine()) {
			case OVERWORLD -> {
				double w = 0;
				double wc = 0;
				double ev = 0;
				for (int i = 0; i < f.pickValues().length; i++) {
					w += f.pickWeights()[i];
					if (f.pickValues()[i] == 0) wc += f.pickWeights()[i];
				}
				for (int i = 0; i < f.pickValues().length; i++) if (f.pickValues()[i] > 0) ev += f.pickWeights()[i] / w * f.pickValues()[i];
				double q = 1 - wc / w;
				double en = 0;
				for (int j = 1; j <= f.pickBoard(); j++) en += Math.pow(q, j);
				pBonus = (double) base.bonus() / n;
				bonusPerTrigger = q > 0 ? en * (ev / q) : 0;
				bonusRtp = pBonus * bonusPerTrigger;
				for (int i = 0; i < f.pickValues().length; i++) {
					int v = f.pickValues()[i];
					if (v < 0 && q > 0) jp[-v] += pBonus * en * (f.pickWeights()[i] / w) / q;
				}
			}
			case NETHER -> {
				double w = 0;
				double ev = 0;
				for (int x : f.holdWeights()) w += x;
				for (int i = 0; i < f.holdValues().length; i++) if (f.holdValues()[i] > 0) ev += f.holdWeights()[i] / w * f.holdValues()[i];
				double p = f.holdCoinPpm() / 1_000_000.0;
				Map<Integer, double[]> memo = new HashMap<>();
				double total = 0;
				double pFull = 0;
				for (int c = f.holdTrigger(); c < 16; c++) {
					long cnt = base.coins()[c];
					if (cnt == 0) continue;
					double pn = cnt / n;
					double[] ab = hold(c, f.holdRespins(), f.holdRespins(), p, memo);
					pBonus += pn;
					total += pn * ab[0] * ev;
					pFull += pn * ab[1];
					for (int i = 0; i < f.holdValues().length; i++) {
						int v = f.holdValues()[i];
						if (v < 0) jp[-v] += pn * ab[0] * f.holdWeights()[i] / w;
					}
				}
				jp[4] += pFull;
				bonusRtp = total;
				bonusPerTrigger = pBonus > 0 ? total / pBonus : 0;
			}
			case END -> {
				pBonus = (double) base.bonus() / n;
				bonusPerTrigger = ringEv(f.wheelRings(), 0, 1.0, pBonus, jp);
				bonusRtp = pBonus * bonusPerTrigger;
			}
		}
		double seedRtp = 0;
		double contrib = 0;
		double owned = 0;
		for (int t = 1; t <= 4; t++) {
			seedRtp += jp[t] * f.jackpotSeedMult()[t - 1];
			contrib += f.contributionPpm()[t - 1] / 1_000_000.0;
			owned += jp[t] * f.ownedMult()[t - 1];
		}
		double baseWays = baseRtp - scatterPay;
		double total = baseWays + scatterPay + fsRtp + bonusRtp + seedRtp + contrib;
		double totalOwned = baseWays + scatterPay + fsRtp + bonusRtp + owned;
		double buy = def.buyPriceFifths() > 0 ? fsTrig[3] / (def.buyPriceFifths() / 5.0) + contrib : Double.NaN;
		return new Report(def.machine(), baseWays, scatterPay, fsRtp, bonusRtp, seedRtp, contrib, total, totalOwned, base.hitPay() / n,
			base.hitAny() / n, pFs, pBonus, new double[] {fsTrig[3], fsTrig[4], fsTrig[5]}, new double[] {fsSpins[3], fsSpins[4], fsSpins[5]},
			bonusPerTrigger, new double[] {jp[1], jp[2], jp[3], jp[4]}, buy);
	}

	/** Expected spins played, independent free spins: {@code f(m, a) = 1 + ρ·f(m−1+d, a+d) + (1−ρ)·f(m−1, a)}. */
	static double iidSpins(int rem, int aw, double rho, int R, int C, Map<Long, Double> memo) {
		if (rem == 0) return 0;
		long key = (long) rem << 32 | aw;
		Double v = memo.get(key);
		if (v != null) return v;
		int d = Math.max(0, Math.min(R, C - aw));
		double r = 1 + rho * iidSpins(rem - 1 + d, aw + d, rho, R, C, memo) + (1 - rho) * iidSpins(rem - 1, aw, rho, R, C, memo);
		memo.put(key, r);
		return r;
	}

	/** Void Walker chain V(s, m, a) (value = true) or the expected number of spins (value = false). */
	static double sticky(double[] e, double[][][] trans, int mask, int rem, int aw, int R, int C, Map<Long, Double> memo, boolean value) {
		if (rem == 0) return 0;
		long key = ((long) mask << 48) | ((long) rem << 24) | aw;
		Double c = memo.get(key);
		if (c != null) return c;
		int d = Math.max(0, Math.min(R, C - aw));
		double v = value ? e[mask] : 1;
		for (int post = 0; post < 8; post++) {
			double p0 = trans[mask][post][0];
			double p1 = trans[mask][post][1];
			if (p0 > 0) v += p0 * sticky(e, trans, post, rem - 1, aw, R, C, memo, value);
			if (p1 > 0) v += p1 * sticky(e, trans, post, rem - 1 + d, aw + d, R, C, memo, value);
		}
		memo.put(key, v);
		return v;
	}

	/** Hoard chain from (n coins, r respins): {expected final coins, P(all 15)}. */
	static double[] hold(int n, int r, int respins, double p, Map<Integer, double[]> memo) {
		if (n >= 15) return new double[] {15, 1};
		if (r == 0) return new double[] {n, 0};
		int key = n * 64 + r;
		double[] c = memo.get(key);
		if (c != null) return c;
		int free = 15 - n;
		double e = 0;
		double pf = 0;
		for (int k = 0; k <= free; k++) {
			double pk = binom(free, k) * Math.pow(p, k) * Math.pow(1 - p, free - k);
			double[] ab = k > 0 ? hold(n + k, respins, respins, p, memo) : hold(n, r - 1, respins, p, memo);
			e += pk * ab[0];
			pf += pk * ab[1];
		}
		double[] res = {e, pf};
		memo.put(key, res);
		return res;
	}

	private static double binom(int n, int k) {
		double r = 1;
		for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
		return r;
	}

	/** Wheel EV from ring i (× bet), adding jackpot hit probabilities (per spin) to {@code jp}. */
	static double ringEv(int[][] rings, int i, double reach, double pTrig, double[] jp) {
		if (i >= rings.length) return 0;
		int[] ring = rings[i];
		double ev = 0;
		for (int v : ring) {
			double p = 1.0 / ring.length;
			if (v > 0) ev += p * v;
			else if (v < 0) jp[-v] += pTrig * reach * p;
			else ev += p * ringEv(rings, i + 1, reach * p, pTrig, jp);
		}
		return ev;
	}

	/** One {@code slots.validateRtp} finding. */
	public record Warning(Machine machine, boolean owned, boolean buy, double rtp, double machineRtp) {
		public String percent() {
			return String.format(Locale.ROOT, "%.3f %%", rtp * 100);
		}
	}

	/** Warnings for a report: RTP (house or owned) &gt; 0.99; buy RTP &gt; the machine RTP or more than 0.5 % below it. */
	public static List<Warning> warnings(Report r) {
		List<Warning> out = new ArrayList<>();
		if (!(r.total() <= 0.99)) out.add(new Warning(r.machine(), false, false, r.total(), r.total()));
		if (!(r.totalOwned() <= 0.99)) out.add(new Warning(r.machine(), true, false, r.totalOwned(), r.totalOwned()));
		if (!Double.isNaN(r.buyRtp()) && (r.buyRtp() > r.total() || r.buyRtp() < r.total() - 0.005)) {
			out.add(new Warning(r.machine(), false, true, r.buyRtp(), r.total()));
		}
		return out;
	}
}
