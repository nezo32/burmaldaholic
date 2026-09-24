/**
 * Slots v2 engine (SLOTS.md §1–§5, §7.5). PURE; twin of Java `Ways`, `Tumble`, `SlotDraw`. All money in FIFTHS
 * of the bet (integers). Lanes S-B1 (ways, tumbles) and S-B2 (draw).
 *
 * ## Draw order (normative for both editions: the same `SlotRng` sequence gives the same tape)
 * 1. base spin (not bought): `nextInt(L_r)` for r = 1…5;
 * 2. bonus game triggered on the final base window (before free spins, SLOTS.md §3.2 "Hoard first"):
 *    - Treasure Hunt: `board` × `weighted(hunt.weights)` — all entries in reveal order;
 *    - Piglin's Hoard: `weighted(coinWeights)` per coin of the final window (cells ascending), then per respin
 *      and per empty cell ascending `nextInt(1 000 000) < chanceMicro`, and for a landed coin at once
 *      `weighted(coinWeights)`;
 *    - Dragon Wheel: `nextInt(|outer|)`, on UP `nextInt(|middle|)`, on UP `nextInt(|core|)`;
 * 3. free spins (triggered or bought): per spin `nextInt(L_r)` for r = 1…5 (sticky reels included).
 * Nothing is drawn after the max-win cap is reached (SLOTS.md §1.3).
 */
import type { FreeSpin, JackpotAward, MachineDef, SlotRng, SpinTape, TumbleChain, TumbleStep, Window, WayWin, WaysResult } from './types';
import { CELLS, REELS, ROWS, windowFromStops } from './types';

// ---- symbols -------------------------------------------------------------------------------------

export const wildOf = (def: MachineDef): number => def.roles.indexOf('WILD');
export const scatterOf = (def: MachineDef): number => def.roles.indexOf('SCATTER');
export const bonusOf = (def: MachineDef): number => def.roles.indexOf('BONUS');
export const coinOf = (def: MachineDef): number => def.roles.indexOf('COIN');
/** Sticky mask bit b (0–2) covers reel b + 2 (1-based). */
export const stickyReel = (bit: number): number => bit + 1;

/** Window with sticky reels (End free spins) forced to WWW. */
export function applySticky(def: MachineDef, w: Window, stickyMask: number): number[] {
  const c = w.slice();
  if (!stickyMask) return c;
  const wd = wildOf(def);
  for (let b = 0; b < 3; b++) if (stickyMask & (1 << b)) for (let y = 0; y < ROWS; y++) c[stickyReel(b) * ROWS + y] = wd;
  return c;
}

// ---- 243 ways (S-B1) -----------------------------------------------------------------------------

/** 243-ways evaluation (SLOTS.md §1.1); `stickyMask` bits 0–2 force reels 2–4 to WWW. */
export function evaluateWays(def: MachineDef, w: Window, stickyMask = 0): WaysResult {
  const cells = applySticky(def, w, stickyMask);
  const wd = wildOf(def);
  const wins: WayWin[] = [];
  let pay = 0;
  let winMask = 0;
  for (let p = 0; p < def.roles.length; p++) {
    if (def.roles[p] !== 'PAY') continue;
    const pays = def.paysFifths[p]!;
    let ways = 1;
    let k = 0;
    let mask = 0;
    for (let r = 0; r < REELS; r++) {
      let n = 0;
      for (let y = 0; y < ROWS; y++) {
        const s = cells[r * ROWS + y]!;
        if (s === p || s === wd) {
          n++;
          mask |= 1 << (r * ROWS + y);
        }
      }
      if (n === 0) break;
      ways *= n;
      k++;
    }
    if (k < 3) continue;
    // the mask above may include cells of reel k+1… only when n>0, i.e. never past k
    const per = pays[k - 3]!;
    if (per <= 0) continue;
    const amount = per * ways;
    wins.push({ symbol: p, k, ways, payFifths: amount, cellMask: mask });
    pay += amount;
    winMask |= mask;
  }
  let scatters = 0;
  let coins = 0;
  let bonusCount = 0;
  const sc = scatterOf(def);
  const cn = coinOf(def);
  const bn = bonusOf(def);
  for (let r = 0; r < REELS; r++) {
    let hasBonus = false;
    for (let y = 0; y < ROWS; y++) {
      const s = cells[r * ROWS + y]!;
      if (s === sc) scatters++;
      else if (s === cn) coins++;
      else if (s === bn) hasBonus = true;
    }
    if (hasBonus && def.bonusReelsMask & (1 << r)) bonusCount++;
  }
  return { wins, payFifths: pay, winMask, scatters, bonusCount, coins };
}

// ---- tumbles (S-B1) ------------------------------------------------------------------------------

export const ladderAt = (ladder: readonly number[], step: number): number => (ladder.length === 0 ? 1 : ladder[Math.min(step, ladder.length - 1)]!);

/**
 * Nether tumble chain (SLOTS.md §3.2): evaluate, pay × ladder[step], remove every winning cell, drop the
 * survivors, refill each reel from the strip ABOVE the window (`top_r −= m`), repeat until no win.
 */
export function runTumbles(def: MachineDef, stops: readonly number[], ladder: readonly number[]): TumbleChain {
  const tops = stops.slice(0, REELS);
  let window: number[] = windowFromStops(def, stops).slice();
  const steps: TumbleStep[] = [];
  let total = 0;
  for (let step = 0; ; step++) {
    const result = evaluateWays(def, window);
    const multiplier = ladderAt(ladder, step);
    const payFifths = result.payFifths * multiplier;
    steps.push({ step, window, result, multiplier, payFifths, tops: tops.slice() });
    if (result.payFifths === 0) break;
    total += payFifths;
    const next: number[] = new Array<number>(CELLS);
    for (let r = 0; r < REELS; r++) {
      const keep: number[] = [];
      for (let y = 0; y < ROWS; y++) if (!(result.winMask & (1 << (r * ROWS + y)))) keep.push(window[r * ROWS + y]!);
      const m = ROWS - keep.length;
      const strip = def.strips[r]!;
      const L = strip.length;
      for (let i = 0; i < m; i++) next[r * ROWS + i] = strip[(((tops[r]! - m + i) % L) + L) % L]!;
      for (let i = 0; i < keep.length; i++) next[r * ROWS + m + i] = keep[i]!;
      tops[r] = (((tops[r]! - m) % L) + L) % L;
    }
    window = next;
  }
  return { steps, finalWindow: window, payFifths: total };
}

// ---- one evaluated spin (base or free) -------------------------------------------------------------

export interface SpinEval {
  /** window where triggers are checked (after tumbles; sticky reels applied) */
  finalWindow: Window;
  /** way pays (× ladder) + scatter pay, × the free-spin multiplier; fifths */
  payFifths: number;
  scatters: number;
  bonusCount: number;
  coins: number;
  /** tumble chain (ladder machines) */
  chain?: TumbleChain;
  /** plain evaluation (non-tumbling machines) */
  ways?: WaysResult;
  /** End free spins: sticky mask after this spin's eggs expanded */
  stickyAfter: number;
  scatterFifths: number;
}

const scatterPay = (def: MachineDef, n: number): number => (n >= 3 ? def.scatterFifths[Math.min(n, 5) - 3]! : 0);

/** Eggs visible on reels 2–4 (End free spins) as a sticky mask. */
export function eggMask(def: MachineDef, w: Window): number {
  const wd = wildOf(def);
  let m = 0;
  for (let b = 0; b < 3; b++) for (let y = 0; y < ROWS; y++) if (w[stickyReel(b) * ROWS + y] === wd) m |= 1 << b;
  return m;
}

/**
 * Evaluate the spin at `stops`. `free` = a free spin: Overworld wins × fsMultiplier; Nether uses the free ladder;
 * End expands every Dragon Egg on reels 2–4 and keeps it sticky (`sticky` = mask before the spin).
 */
export function evaluateSpin(def: MachineDef, stops: readonly number[], free: boolean, sticky = 0): SpinEval {
  const mult = free ? def.fsMultiplier : 1;
  const ladderMachine = def.ladder.length > 0;
  if (ladderMachine) {
    const chain = runTumbles(def, stops, free ? def.ladderFree : def.ladder);
    const last = chain.steps[chain.steps.length - 1]!.result;
    const sp = scatterPay(def, last.scatters);
    return {
      finalWindow: chain.finalWindow,
      payFifths: (chain.payFifths + sp) * mult,
      scatters: last.scatters,
      bonusCount: last.bonusCount,
      coins: last.coins,
      chain,
      stickyAfter: sticky,
      scatterFifths: sp * mult,
    };
  }
  const raw = windowFromStops(def, stops);
  const stickyAfter = free && def.machine === 'end' ? sticky | eggMask(def, raw) : sticky;
  const ways = evaluateWays(def, raw, stickyAfter);
  const sp = scatterPay(def, ways.scatters);
  return {
    finalWindow: applySticky(def, raw, stickyAfter),
    payFifths: (ways.payFifths + sp) * mult,
    scatters: ways.scatters,
    bonusCount: ways.bonusCount,
    coins: ways.coins,
    ways,
    stickyAfter,
    scatterFifths: sp * mult,
  };
}

/** Free spins for `n` scatters (0 when < 3). */
export const freeSpinsFor = (def: MachineDef, scatters: number): number => (scatters >= 3 ? def.freeSpins[Math.min(scatters, 5) - 3]! : 0);

export const bonusTriggered = (def: MachineDef, e: Pick<SpinEval, 'bonusCount' | 'coins'>): boolean => {
  if (def.hoard) return e.coins >= def.hoard.trigger;
  if (def.bonusReelsMask === 0) return false;
  let reels = 0;
  for (let m = def.bonusReelsMask; m; m &= m - 1) reels++;
  return e.bonusCount >= reels;
};

// ---- draw (S-B2) ----------------------------------------------------------------------------------

export interface DrawRequest {
  def: MachineDef;
  bet: number;
  buy: boolean;
  owned: boolean;
  /** pool values (seed + increment, chips) per tier 1..4 (index 0 unused); ignored when owned */
  pools: number[];
}

/** Weighted pick with integer weights (Σ ≤ 2^31 − 1). */
export function weighted(rng: SlotRng, weights: readonly number[]): number {
  let total = 0;
  for (const w of weights) total += w;
  let r = rng.nextInt(total);
  for (let i = 0; i < weights.length; i++) {
    r -= weights[i]!;
    if (r < 0) return i;
  }
  return weights.length - 1;
}

/** Owned-casino reservation per spin (SLOTS.md §8.6): cap × bet (also for a bought feature). */
export const reservation = (def: MachineDef, bet: number): number => def.capMultiple * bet;

/** Stake of a spin: the bet, or the buy price (fifths × bet / 5; exact because bets are multiples of 5). */
export const stakeOf = (def: MachineDef, bet: number, buy: boolean): number => (buy ? (def.buyPriceFifths * bet) / 5 : bet);

/** Pool award (SLOTS.md §5.2) and the pool left behind. */
export function poolAward(def: MachineDef, pool: number, tier: number, bet: number): { award: number; pool: number } {
  const ref = def.jackpot.ref;
  const b = Math.min(bet, ref);
  const seed = def.jackpot.seeds[tier]!;
  const award = Math.floor((pool * b) / ref);
  const inc = Math.max(0, pool - seed);
  return { award, pool: seed + Math.floor((inc * (ref - b)) / ref) };
}

class Acc {
  total = 0;
  capHit = false;
  readonly jackpots: JackpotAward[] = [];
  constructor(
    readonly req: DrawRequest,
    readonly capFifths: number,
    readonly pools: number[],
  ) {}
  /** Add fifths; true when the cap was reached (the feature ends at once). */
  add(f: number): boolean {
    if (this.capHit) return true;
    this.total += f;
    if (this.total >= this.capFifths) {
      this.total = this.capFifths;
      this.capHit = true;
    }
    return this.capHit;
  }
  jackpot(tier: number): boolean {
    const { def, bet, owned } = this.req;
    const t = tier as 1 | 2 | 3 | 4;
    if (owned) {
      const fifths = def.jackpot.owned[t]! * 5;
      this.jackpots.push({ tier: t, chips: (fifths * bet) / 5, owned: true });
      return this.add(fifths);
    }
    const { award, pool } = poolAward(def, this.pools[t] ?? def.jackpot.seeds[t]!, t, bet);
    this.pools[t] = pool;
    this.jackpots.push({ tier: t, chips: award, owned: false });
    return this.capHit;
  }
  /** A prize code: > 0 ×bet, < 0 jackpot tier. */
  prize(code: number): boolean {
    return code > 0 ? this.add(code * 5) : code < 0 ? this.jackpot(-code) : this.capHit;
  }
}

function drawStops(def: MachineDef, rng: SlotRng): number[] {
  return def.strips.map((s) => rng.nextInt(s.length));
}

/** CONFIRM → DRAW TAPE (SLOTS.md §1.2). Deterministic in `rng`; see the draw order at the top of this file. */
export function drawSpin(req: DrawRequest, rng: SlotRng): SpinTape {
  const { def, bet, buy } = req;
  const acc = new Acc(req, def.capMultiple * 5, req.pools.slice());
  const tape: SpinTape = { machine: def.machine, bet, bought: buy, stops: [], jackpots: acc.jackpots, totalFifths: 0, capHit: false };
  let fsAward: number;
  if (!buy) {
    tape.stops = drawStops(def, rng);
    const base = evaluateSpin(def, tape.stops, false);
    acc.add(base.payFifths);
    if (!acc.capHit && bonusTriggered(def, base)) drawBonus(def, base, acc, tape, rng);
    fsAward = freeSpinsFor(def, base.scatters);
  } else {
    if (def.buyPriceFifths <= 0) throw new Error(`${def.machine}: no buy feature`);
    fsAward = def.freeSpins[0];
  }
  if (fsAward > 0 && !acc.capHit) tape.freeSpins = drawFreeSpins(def, fsAward, acc, rng);
  tape.totalFifths = acc.total;
  tape.capHit = acc.capHit;
  return tape;
}

function drawBonus(def: MachineDef, base: SpinEval, acc: Acc, tape: SpinTape, rng: SlotRng): void {
  if (def.hunt) {
    const h = def.hunt;
    const entries: number[] = [];
    for (let i = 0; i < h.board; i++) entries.push(h.values[weighted(rng, h.weights)]!);
    for (const e of entries) {
      if (e === 0) break;
      if (acc.prize(e)) break;
    }
    // `opened` is the pick PROGRESS (0 at draw; the service persists it per pick, SLOTS.md §8.1)
    tape.hunt = { entries, opened: 0 };
  } else if (def.hoard) {
    const h = def.hoard;
    const cn = coinOf(def);
    const filled = new Array<boolean>(CELLS).fill(false);
    const initialCells: number[] = [];
    const initialValues: number[] = [];
    for (let c = 0; c < CELLS; c++)
      if (base.finalWindow[c] === cn) {
        filled[c] = true;
        initialCells.push(c);
        initialValues.push(h.values[weighted(rng, h.weights)]!);
      }
    const respinCells: number[][] = [];
    const respinValues: number[][] = [];
    let count = initialCells.length;
    let left = h.respins;
    while (left > 0 && count < CELLS) {
      const cells: number[] = [];
      const values: number[] = [];
      for (let c = 0; c < CELLS; c++) {
        if (filled[c]) continue;
        if (rng.nextInt(1_000_000) < h.chanceMicro) {
          filled[c] = true;
          cells.push(c);
          values.push(h.values[weighted(rng, h.weights)]!);
        }
      }
      respinCells.push(cells);
      respinValues.push(values);
      count += cells.length;
      left = cells.length > 0 ? h.respins : left - 1;
    }
    tape.hoard = { initialCells, initialValues, respinCells, respinValues };
    // collect in reveal order: locked coins, then each respin's coins; all 15 → Grand
    const all = [...initialValues, ...respinValues.flat()];
    for (const v of all) if (acc.prize(v)) break;
    if (!acc.capHit && count >= CELLS) acc.jackpot(4);
  } else if (def.wheel) {
    const segments: number[] = [];
    for (let ring = 0; ring < def.wheel.rings.length; ring++) {
      const wedges = def.wheel.rings[ring]!;
      const seg = rng.nextInt(wedges.length);
      segments.push(seg);
      const code = wedges[seg]!;
      if (code === 0 && ring < def.wheel.rings.length - 1) continue;
      acc.prize(code);
      break;
    }
    tape.wheel = { segments };
  }
}

function drawFreeSpins(def: MachineDef, awarded: number, acc: Acc, rng: SlotRng): NonNullable<SpinTape['freeSpins']> {
  const spins: FreeSpin[] = [];
  let remaining = Math.min(awarded, def.fsCap);
  let given = remaining;
  let sticky = 0;
  let pay = 0;
  while (remaining > 0 && !acc.capHit) {
    remaining--;
    const stops = drawStops(def, rng);
    const e = evaluateSpin(def, stops, true, sticky);
    sticky = e.stickyAfter;
    const retrigger = e.scatters >= 3;
    if (retrigger) {
      const d = Math.min(def.retrigger, def.fsCap - given);
      remaining += d;
      given += d;
    }
    spins.push({ stops, stickyMaskAfter: sticky, retrigger, payFifths: e.payFifths });
    pay += e.payFifths;
    acc.add(e.payFifths);
  }
  return { awarded: Math.min(awarded, def.fsCap), spins, payFifths: pay };
}

// ---- tape helpers ---------------------------------------------------------------------------------

/**
 * Chests the Treasure Hunt of a tape opens: up to and including the Creeper, all of them, or fewer when the
 * max-win cap ends the hunt (SLOTS.md §1.3). A pure function of the tape (the picks only reveal it).
 */
export function huntOpens(def: MachineDef, tape: SpinTape): number {
  const h = tape.hunt;
  if (!h) return 0;
  const cap = def.capMultiple * 5;
  let total = tape.bought ? 0 : evaluateSpin(def, tape.stops, false).payFifths;
  let gem = 0;
  let n = 0;
  for (const e of h.entries) {
    n++;
    if (e === 0) break;
    if (e > 0) total += e * 5;
    else if (tape.jackpots[gem++]?.owned) total += def.jackpot.owned[-e]! * 5;
    if (total >= cap) break;
  }
  return n;
}

/** Pool values after the tape's awards were applied (the module persists these at draw time). */
export function poolsAfter(def: MachineDef, pools: readonly number[], tape: SpinTape): number[] {
  const out = pools.slice();
  for (const j of tape.jackpots) if (!j.owned) out[j.tier] = poolAward(def, out[j.tier] ?? def.jackpot.seeds[j.tier]!, j.tier, tape.bet).pool;
  return out;
}

/** Free-spin count played (retriggers included). */
export const freeSpinsPlayed = (t: SpinTape): number => t.freeSpins?.spins.length ?? 0;

/** Feature code of a tape (Showdown record, statistics): 0 none, 1 free spins, 2 hunt, 3 hoard, 4 wheel. */
export const featureCode = (t: SpinTape): number => (t.hunt ? 2 : t.hoard ? 3 : t.wheel ? 4 : t.freeSpins ? 1 : 0);

/** Any feature triggered by the spin itself (not bought): contracts `slots_feature`, autoplay stop rule. */
export const featureTriggered = (t: SpinTape): boolean => !t.bought && (t.freeSpins !== undefined || t.hunt !== undefined || t.hoard !== undefined || t.wheel !== undefined);
