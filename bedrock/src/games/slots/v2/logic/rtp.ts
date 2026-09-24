/**
 * `slots.validateRtp` (SLOTS.md §7.1, §7.3, §7.5) — exact RTP of the configured tables. PURE. Lane S-B4.
 *
 * Bedrock uses the per-reel FACTORISED form, exact for non-tumbling machines: reels are independent, so every
 * expectation over the 5 stops factorises into per-reel sums (integers — no rounding until the final division):
 *   Σ_stops pay = Σ_P Σ_k pay(P,k) · Π_{r≤k} A_r(P) · Z_{k+1}(P) · Π_{r>k+1} L_r
 * with A_r = Σ_t n_r(P,t), Z_r = #{t : n_r(P,t) = 0}. Scatter counts are convolved per reel. End Void free spins
 * are exact the same way (a reel with a visible Dragon Egg is WWW; its scatter is covered). Nether tumbles are
 * not factorisable: the defaults ship the §7.1 numbers and changed Nether tables are re-checked by sampling
 * (`sampleRtp`, 10⁶ spins, driven in chunks by the module).
 */
import { REFERENCE_RTP } from './machines';
import { bonusOf, drawSpin, scatterOf, wildOf } from './engine';
import type { MachineDef, MachineId, SlotRng } from './types';
import { REELS, ROWS, symbolAt } from './types';

export interface RtpBreakdown {
  machine: MachineId;
  /** all values are fractions of the amount staked (0.95 = 95 %) */
  base: number;
  scatter: number;
  freeSpins: number;
  bonus: number;
  jackpotSeed: number;
  contributions: number;
  total: number;
  /** owned casino: fixed jackpots, no pools, no contribution */
  owned: number;
  /** buy feature (undefined = no buy): EV of the bought feature + contributions, ÷ price */
  buy?: number;
  /** how the numbers were obtained */
  method: 'factorised' | 'defaults' | 'sample';
}

// ---- per-reel statistics ---------------------------------------------------------------------------

interface ReelStat {
  L: number;
  /** per symbol: Σ_t n(P,t), #{t: n = 0} */
  A: number[];
  Z: number[];
  /** scatter count histogram per stop: counts[c] = #stops with c scatters */
  scat: number[];
  /** #stops showing the bonus symbol */
  bonus: number;
}

/** Reel statistics; `eff(stop)` may rewrite the 3 cells (End free spins: a visible egg → WWW). */
function reelStat(def: MachineDef, r: number, eff?: (cells: number[]) => number[]): ReelStat {
  const L = def.strips[r]!.length;
  const nSym = def.roles.length;
  const A = new Array<number>(nSym).fill(0);
  const Z = new Array<number>(nSym).fill(0);
  const scat = [0, 0, 0, 0];
  let bonus = 0;
  const wd = wildOf(def);
  const sc = scatterOf(def);
  const bn = bonusOf(def);
  for (let t = 0; t < L; t++) {
    let cells = [0, 1, 2].map((y) => symbolAt(def, r, t, y));
    if (eff) cells = eff(cells);
    for (let p = 0; p < nSym; p++) {
      if (def.roles[p] !== 'PAY') continue;
      let n = 0;
      for (const c of cells) if (c === p || c === wd) n++;
      A[p]! += n;
      if (n === 0) Z[p]!++;
    }
    let s = 0;
    for (const c of cells) if (c === sc) s++;
    scat[s]!++;
    if (bn >= 0 && cells.includes(bn)) bonus++;
  }
  return { L, A, Z, scat, bonus };
}

/** Σ over all stop combinations of the way pays (fifths), exact integer (< 2^53 for the default strips). */
function waysSum(def: MachineDef, st: ReelStat[]): number {
  let sum = 0;
  for (let p = 0; p < def.roles.length; p++) {
    if (def.roles[p] !== 'PAY') continue;
    for (let k = 3; k <= REELS; k++) {
      const pay = def.paysFifths[p]![k - 3]!;
      if (!pay) continue;
      let prod = pay;
      for (let r = 0; r < k; r++) prod *= st[r]!.A[p]!;
      if (k < REELS) {
        prod *= st[k]!.Z[p]!;
        for (let r = k + 1; r < REELS; r++) prod *= st[r]!.L;
      }
      sum += prod;
    }
  }
  return sum;
}

/** Histogram of the scatter total over all combinations (index = count). */
function scatterHistogram(st: ReelStat[]): number[] {
  let h = [1];
  for (const s of st) {
    const next = new Array<number>(h.length + 3).fill(0);
    h.forEach((a, i) => s.scat.forEach((b, j) => (next[i + j]! += a * b)));
    h = next;
  }
  return h;
}

const combos = (st: ReelStat[]): number => st.reduce((a, s) => a * s.L, 1);
const scatterPayOf = (def: MachineDef, n: number): number => (n >= 3 ? def.scatterFifths[Math.min(n, 5) - 3]! : 0);

export interface BaseStats {
  N: number;
  /** Σ way pays, fifths */
  waysFifths: number;
  /** Σ scatter pays, fifths */
  scatterFifths: number;
  /** scatter histogram 0…15 */
  scatters: number[];
  /** combinations that trigger the bonus game (chest/crystal machines) */
  bonusTriggers: number;
}

/** Exact base-game sums of a NON-tumbling machine (SLOTS.md §7.5 rows). */
export function baseStats(def: MachineDef): BaseStats {
  const st = [0, 1, 2, 3, 4].map((r) => reelStat(def, r));
  const scatters = scatterHistogram(st);
  let scatterFifths = 0;
  scatters.forEach((n, i) => (scatterFifths += n * scatterPayOf(def, i)));
  let bonusTriggers = 0;
  if (def.bonusReelsMask) {
    bonusTriggers = 1;
    for (let r = 0; r < REELS; r++) bonusTriggers *= def.bonusReelsMask & (1 << r) ? st[r]!.bonus : st[r]!.L;
  }
  return { N: combos(st), waysFifths: waysSum(def, st), scatterFifths, scatters, bonusTriggers };
}

// ---- End free spins (sticky expanding wilds) -------------------------------------------------------

export interface StickyStats {
  N: number;
  /** Σ spin pay (ways + scatter), fifths, per start mask 0…7 */
  sum: number[];
  /** trans[s][s'] = [no retrigger, retrigger] counts */
  trans: number[][][];
}

/** Exact per-state enumeration of one End free spin (SLOTS.md §7.3 Void Walker; §7.5 s0…s7 sums). */
export function stickyStats(def: MachineDef): StickyStats {
  const wd = wildOf(def);
  const sc = scatterOf(def);
  const sum: number[] = [];
  const trans: number[][][] = [];
  for (let s = 0; s < 8; s++) {
    const st: ReelStat[] = [];
    // per reel: distribution over (eggBit, scatterCount)
    const dist: Array<Map<string, number>> = [];
    for (let r = 0; r < REELS; r++) {
      const bit = r >= 1 && r <= 3 ? 1 << (r - 1) : 0;
      const sticky = bit && s & bit;
      const eff = (cells: number[]): number[] => (sticky || (bit && cells.includes(wd)) ? [wd, wd, wd] : cells);
      st.push(reelStat(def, r, eff));
      const d = new Map<string, number>();
      const L = def.strips[r]!.length;
      for (let t = 0; t < L; t++) {
        const cells = [0, 1, 2].map((y) => symbolAt(def, r, t, y));
        const egg = bit && (sticky || cells.includes(wd)) ? bit : 0;
        const scat = egg ? 0 : cells.filter((c) => c === sc).length;
        const key = `${egg}|${scat}`;
        d.set(key, (d.get(key) ?? 0) + 1);
      }
      dist.push(d);
    }
    const hist = scatterHistogram(st);
    let spay = 0;
    hist.forEach((n, i) => (spay += n * scatterPayOf(def, i)));
    sum.push(waysSum(def, st) + spay);
    // joint (mask, min(scatters, 3))
    let joint = new Map<string, number>([[`${s}|0`, 1]]);
    for (const d of dist) {
      const next = new Map<string, number>();
      for (const [k, a] of joint) {
        const [m, c] = k.split('|').map(Number) as [number, number];
        for (const [k2, b] of d) {
          const [egg, scat] = k2.split('|').map(Number) as [number, number];
          const key = `${m | egg}|${Math.min(3, c + scat)}`;
          next.set(key, (next.get(key) ?? 0) + a * b);
        }
      }
      joint = next;
    }
    const row: number[][] = Array.from({ length: 8 }, () => [0, 0]);
    for (const [k, n] of joint) {
      const [m, c] = k.split('|').map(Number) as [number, number];
      row[m]![c >= 3 ? 1 : 0]! += n;
    }
    trans.push(row);
  }
  return { N: combos([0, 1, 2, 3, 4].map((r) => ({ L: def.strips[r]!.length }) as ReelStat)), sum, trans };
}

/** Void Walker value V(s, m, a) (× bet), SLOTS.md §7.3. */
export function voidWalkerValue(def: MachineDef, stats: StickyStats, spins: number): number {
  const memo = new Map<string, number>();
  const E = stats.sum.map((x) => x / stats.N / 5);
  const V = (s: number, m: number, a: number): number => {
    if (m <= 0) return 0;
    const key = `${s}|${m}|${a}`;
    const hit = memo.get(key);
    if (hit !== undefined) return hit;
    let v = E[s]!;
    const d = Math.min(def.retrigger, def.fsCap - a);
    for (let s2 = 0; s2 < 8; s2++) {
      const [no, rt] = stats.trans[s]![s2]!;
      if (no) v += (no / stats.N) * V(s2, m - 1, a);
      if (rt) v += (rt / stats.N) * V(s2, m - 1 + d, a + d);
    }
    memo.set(key, v);
    return v;
  };
  return V(0, spins, spins);
}

/** Expected free spins played `f(n, n)` for independent spins (SLOTS.md §7.3). */
export function expectedSpins(def: MachineDef, rho: number, n: number): number {
  const memo = new Map<string, number>();
  const f = (m: number, a: number): number => {
    if (m <= 0) return 0;
    const key = `${m}|${a}`;
    const hit = memo.get(key);
    if (hit !== undefined) return hit;
    const d = Math.min(def.retrigger, def.fsCap - a);
    const v = 1 + rho * f(m - 1 + d, a + d) + (1 - rho) * f(m - 1, a);
    memo.set(key, v);
    return v;
  };
  return f(Math.min(n, def.fsCap), Math.min(n, def.fsCap));
}

// ---- bonus games -----------------------------------------------------------------------------------

/** Treasure Hunt: [value (× bet, excl. jackpots), expected gems per tier 1…4]. */
export function huntValue(def: MachineDef): { value: number; gems: number[]; opened: number } {
  const h = def.hunt!;
  const W = h.weights.reduce((a, b) => a + b, 0);
  let wc = 0;
  let prize = 0;
  const gemW = [0, 0, 0, 0, 0];
  h.values.forEach((v, i) => {
    const w = h.weights[i]!;
    if (v === 0) wc += w;
    else if (v > 0) prize += v * w;
    else gemW[-v]! += w;
  });
  const q = (W - wc) / W;
  let EN = 0;
  for (let j = 1; j <= h.board; j++) EN += Math.pow(q, j);
  const nonCreeper = W - wc;
  return { value: nonCreeper > 0 ? (EN * prize) / nonCreeper : 0, gems: gemW.map((w) => (nonCreeper > 0 ? (EN * w) / nonCreeper : 0)), opened: EN };
}

/** Dragon Wheel: [value (× bet, excl. jackpots), P(tier) per tier 1…4, P(reach ring)]. */
export function wheelValue(def: MachineDef): { value: number; tiers: number[]; reach: number[] } {
  const rings = def.wheel!.rings;
  let reach = 1;
  let value = 0;
  const tiers = [0, 0, 0, 0, 0];
  const reachAll: number[] = [];
  for (let i = 0; i < rings.length; i++) {
    const w = rings[i]!;
    reachAll.push(reach);
    let up = 0;
    for (const c of w) {
      if (c > 0) value += (reach * c) / w.length;
      else if (c < 0) tiers[-c]! += reach / w.length;
      else if (i < rings.length - 1) up++;
    }
    reach *= up / w.length;
  }
  return { value, tiers, reach: reachAll };
}

/** Hoard start distribution of the default Nether tables (coins 6…10 on the final base window, SLOTS.md §7.3). */
export const NETHER_HOARD_START: Readonly<Record<number, number>> = { 6: 108_845, 7: 16_483, 8: 3_316, 9: 240, 10: 32 };

/**
 * Piglin's Hoard Markov chain (SLOTS.md §7.3) from a start distribution {coins: weight}: mean final coins,
 * P(all cells), value (× bet, jackpot coins count 0) and expected jackpot coins per tier 1…3.
 */
export function hoardValue(def: MachineDef, start: Readonly<Record<number, number>>, cells = 15): { meanCoins: number; pFull: number; value: number; jackpotCoins: number[] } {
  const h = def.hoard!;
  const p = h.chanceMicro / 1_000_000;
  const memo = new Map<string, number[]>();
  // distribution of the final coin count from (n, r)
  const dist = (n: number, r: number): number[] => {
    if (n >= cells || r <= 0) {
      const d = new Array<number>(cells + 1).fill(0);
      d[n] = 1;
      return d;
    }
    const key = `${n}|${r}`;
    const hit = memo.get(key);
    if (hit) return hit;
    const out = new Array<number>(cells + 1).fill(0);
    const m = cells - n;
    let c = 1; // C(m, k)
    for (let k = 0; k <= m; k++) {
      if (k > 0) c = (c * (m - k + 1)) / k;
      const pk = c * Math.pow(p, k) * Math.pow(1 - p, m - k);
      if (pk === 0) continue;
      const sub = k > 0 ? dist(n + k, h.respins) : dist(n, r - 1);
      for (let i = 0; i <= cells; i++) out[i]! += pk * sub[i]!;
    }
    memo.set(key, out);
    return out;
  };
  let total = 0;
  let mean = 0;
  let pFull = 0;
  for (const [n, w] of Object.entries(start)) {
    const d = dist(Number(n), h.respins);
    total += w;
    d.forEach((q, i) => (mean += w * q * i));
    pFull += w * d[cells]!;
  }
  mean /= total;
  pFull /= total;
  const W = h.weights.reduce((a, b) => a + b, 0);
  let coinValue = 0;
  const jackpotCoins = [0, 0, 0, 0, 0];
  h.values.forEach((v, i) => {
    if (v > 0) coinValue += (v * h.weights[i]!) / W;
    else if (v < 0) jackpotCoins[-v]! += (mean * h.weights[i]!) / W;
  });
  return { meanCoins: mean, pFull, value: mean * coinValue, jackpotCoins };
}

// ---- the whole machine -----------------------------------------------------------------------------

const seedMult = (def: MachineDef, t: number): number => def.jackpot.seeds[t]! / def.jackpot.ref;

/** Exact RTP breakdown (non-tumbling machines) or the shipped defaults (Nether). */
export function computeRtp(def: MachineDef, isDefault = true): RtpBreakdown | undefined {
  const contributions = def.jackpot.contribution.reduce((a, b) => a + b, 0);
  if (def.ladder.length > 0) {
    if (!isDefault) return undefined; // Nether with changed tables: sample (sampleRtp)
    const ref = REFERENCE_RTP[def.machine];
    const owned = ref.owned / 100;
    const r: RtpBreakdown = {
      machine: def.machine,
      base: ref.base / 100,
      scatter: ref.scatter / 100,
      freeSpins: ref.freeSpins / 100,
      bonus: ref.bonus / 100,
      jackpotSeed: ref.jackpotSeed / 100,
      contributions,
      total: ref.total / 100,
      owned,
      method: 'defaults',
    };
    // buy: Inferno Spins as a 3-Tear trigger = 17.1981 × bet (SLOTS.md §4, §6.3)
    if (def.buyPriceFifths > 0) r.buy = (NETHER_FS_VALUE[0] + contributions * (def.buyPriceFifths / 5)) / (def.buyPriceFifths / 5);
    return r;
  }
  const b = baseStats(def);
  const N = b.N;
  const base = b.waysFifths / N / 5;
  const scatter = b.scatterFifths / N / 5;
  // free spins
  const pTrig = [3, 4, 5].map((n) => (b.scatters.slice(n, n === 5 ? undefined : n + 1).reduce((a, x) => a + x, 0) || 0) / N);
  let fsValue: number[];
  if (def.machine === 'end') {
    const ss = stickyStats(def);
    fsValue = def.freeSpins.map((n) => voidWalkerValue(def, ss, n));
  } else {
    const e = def.fsMultiplier * (base + scatter);
    const rho = b.scatters.slice(3).reduce((a, x) => a + x, 0) / N;
    fsValue = def.freeSpins.map((n) => e * expectedSpins(def, rho, n));
  }
  const freeSpins = pTrig.reduce((a, p, i) => a + p * fsValue[i]!, 0);
  // bonus
  const pB = b.bonusTriggers / N;
  let bonus = 0;
  let seedPart = 0;
  let ownedPart = 0;
  const hits = [0, 0, 0, 0, 0];
  if (def.hunt) {
    const h = huntValue(def);
    bonus = pB * h.value;
    for (let t = 1; t <= 4; t++) hits[t] = pB * h.gems[t]!;
  } else if (def.wheel) {
    const w = wheelValue(def);
    bonus = pB * w.value;
    for (let t = 1; t <= 4; t++) hits[t] = pB * w.tiers[t]!;
  }
  for (let t = 1; t <= 4; t++) {
    seedPart += hits[t]! * seedMult(def, t);
    ownedPart += hits[t]! * def.jackpot.owned[t]!;
  }
  const game = base + scatter + freeSpins + bonus;
  const r: RtpBreakdown = {
    machine: def.machine,
    base,
    scatter,
    freeSpins,
    bonus,
    jackpotSeed: seedPart,
    contributions,
    total: game + seedPart + contributions,
    owned: game + ownedPart,
    method: 'factorised',
  };
  if (def.buyPriceFifths > 0) {
    const price = def.buyPriceFifths / 5;
    r.buy = (fsValue[0]! + contributions * price) / price;
  }
  return r;
}

/** Inferno Spins values per trigger (3 / 4 / 5 Tears, × bet; SLOTS.md §4) for the default Nether tables. */
export const NETHER_FS_VALUE: readonly [number, number, number] = [17.1981, 21.4977, 28.6636];

export interface RtpWarning {
  machine: MachineId;
  kind: 'rtp' | 'buy' | 'buy_parity';
  value: number;
  limit: number;
}

/** Warnings of SLOTS.md §7.5: machine RTP > 0.99, buy RTP > machine RTP, or buy RTP more than 0.5 % below. */
export function rtpWarnings(r: RtpBreakdown, limit = 0.99): RtpWarning[] {
  const out: RtpWarning[] = [];
  if (r.total > limit) out.push({ machine: r.machine, kind: 'rtp', value: r.total, limit });
  if (r.owned > limit) out.push({ machine: r.machine, kind: 'rtp', value: r.owned, limit });
  if (r.buy !== undefined && r.buy > r.total + 1e-12) out.push({ machine: r.machine, kind: 'buy', value: r.buy, limit: r.total });
  else if (r.buy !== undefined && r.buy < r.total - 0.005) out.push({ machine: r.machine, kind: 'buy_parity', value: r.buy, limit: r.total - 0.005 });
  return out;
}

/**
 * Monte-Carlo estimate of the house RTP (base game + features + jackpot seeds at the reference bet +
 * contributions) — the sampled re-check for changed Nether tables. A generator so the module can spread the
 * work over ticks (`system.runJob`); yields every `chunk` spins, returns the estimate.
 */
export function* sampleRtp(def: MachineDef, spins: number, rng: SlotRng, chunk = 2000): Generator<number, number, void> {
  const bet = def.jackpot.ref;
  const pools = def.jackpot.seeds.slice();
  let ret = 0;
  for (let i = 0; i < spins; i++) {
    const tape = drawSpin({ def, bet, buy: false, owned: false, pools }, rng);
    ret += (tape.totalFifths * bet) / 5;
    for (const j of tape.jackpots) ret += j.chips; // at the seed: award = seed × min(1, bet/ref)
    if ((i + 1) % chunk === 0) yield i + 1;
  }
  return ret / (spins * bet) + def.jackpot.contribution.reduce((a, b) => a + b, 0);
}

/** Cells visible on a reel at a stop (helper for tests and presentation). */
export const reelCells = (def: MachineDef, r: number, t: number): number[] => Array.from({ length: ROWS }, (_, y) => symbolAt(def, r, t, y));
