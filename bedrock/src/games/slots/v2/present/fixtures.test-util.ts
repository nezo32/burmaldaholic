/**
 * Test fixtures for the slots v2 presentation (lane B-L9): small machine defs with real-shaped strips, tapes and
 * rounds evaluated by the REAL engine (lane B-L8) and timelines from the REAL builder `buildSlotTimeline` (the
 * frames' beat-ordinal contract is verified against it; the old reference stub is gone). `refEvaluate` /
 * `refTumbles` stay as an independent reference 243-ways evaluator + tumble chain (SLOTS.md §1.1, §3.2) that the
 * tests compare the engine against. PURE; used only by `*.test.ts`.
 */
import { FxRng } from '../../../../core/logic/anim/seed';
import { SHARED_PROFILE, type TimingProfile } from '../../../../core/logic/anim/timeline';
import { type WinTier } from '../../../../core/logic/anim/win-tier';
import { defaultMachine } from '../logic/config';
import { evaluateSpin } from '../logic/engine';
import { slotTier } from '../logic/tiers';
import { buildSlotTimeline } from '../logic/timeline';
import { type MachineDef, type MachineId, type SpinTape, type SymbolRole, type TumbleChain, type TumbleStep, type WayWin, type Window, type WaysResult, REELS, ROWS, windowFromStops } from '../logic/types';
import { type SlotEngine, type SlotRound, SYMBOL_CODES, roundFromTape } from './frames';

export const STRIP_LEN = 24;

export function fakeDef(machine: MachineId, seed = 7): MachineDef {
  const codes = [...SYMBOL_CODES[machine]];
  const roles: SymbolRole[] = codes.map((_, i) => (i === 0 ? 'WILD' : i === 1 ? 'SCATTER' : i === 2 ? (machine === 'nether' ? 'COIN' : 'BONUS') : 'PAY'));
  const rng = new FxRng(seed);
  const bonusReels = machine === 'overworld' ? [0, 2, 4] : machine === 'end' ? [1, 2, 3] : [0, 1, 2, 3, 4];
  const strips: number[][] = [];
  for (let r = 0; r < REELS; r++) {
    const s: number[] = [];
    for (let i = 0; i < STRIP_LEN; i++) {
      let sym = 3 + rng.nextInt(8);
      if (i % 8 === 3) sym = 1; // scatters spaced ≥ 3
      else if (i % 11 === 6 && r >= 1 && r <= 3) sym = 0; // wilds on reels 2–4
      // Nether coins in pairs (a window can show 2, so ≥ 4 coins can still reach the Hoard trigger of 6)
      else if (machine === 'nether' ? i % 6 === 0 || i % 6 === 1 : i % 7 === 5 && bonusReels.includes(r)) sym = 2;
      s.push(sym);
    }
    strips.push(s);
  }
  return {
    machine,
    codes,
    roles,
    strips,
    paysFifths: codes.map((_, i) => (i < 3 ? [0, 0, 0] : i < 7 ? [4, 10, 20] : [1, 2, 4])),
    scatterFifths: [5, 50, 250],
    bonusReelsMask: bonusReels.reduce((m, r) => m | (1 << r), 0),
    freeSpins: [8, 10, 15],
    retrigger: machine === 'nether' ? 5 : machine === 'end' ? 4 : 8,
    fsCap: 50,
    fsMultiplier: machine === 'overworld' ? 2 : 1,
    // tumbles (a ladder) only on the Nether machine: the engine tumbles every machine that has a ladder
    ladder: machine === 'nether' ? [1, 2, 3, 5] : [],
    ladderFree: machine === 'nether' ? [2, 4, 6, 10] : [],
    capMultiple: 500,
    buyPriceFifths: 92,
    hunt: defaultMachine(machine).hunt,
    hoard: defaultMachine(machine).hoard,
    wheel: defaultMachine(machine).wheel,
    jackpot: defaultMachine(machine).jackpot,
  };
}

const cell = (r: number, y: number): number => r * ROWS + y;

/** Reference 243-ways evaluation (SLOTS.md §1.1); sticky mask bits 0–2 force reels 2–4 to wilds. */
export function refEvaluate(def: MachineDef, w0: Window, sticky = 0): WaysResult {
  const wild = def.roles.indexOf('WILD');
  const w = w0.slice();
  for (let i = 0; i < 3; i++) if (sticky & (1 << i)) for (let y = 0; y < ROWS; y++) w[cell(i + 1, y)] = wild;
  const wins: WayWin[] = [];
  let pay = 0;
  let mask = 0;
  def.roles.forEach((role, s) => {
    if (role !== 'PAY') return;
    let k = 0;
    let ways = 1;
    let m = 0;
    for (let r = 0; r < REELS; r++) {
      let n = 0;
      let rm = 0;
      for (let y = 0; y < ROWS; y++) {
        const x = w[cell(r, y)]!;
        if (x === s || (x === wild && r >= 1 && r <= 3)) {
          n++;
          rm |= 1 << cell(r, y);
        }
      }
      if (n === 0) break;
      k++;
      ways *= n;
      m |= rm;
    }
    if (k >= 3) {
      const p = def.paysFifths[s]![k - 3]! * ways;
      wins.push({ symbol: s, k, ways, payFifths: p, cellMask: m });
      pay += p;
      mask |= m;
    }
  });
  const count = (role: SymbolRole): number => w.filter((x) => def.roles[x] === role).length;
  return { wins, payFifths: pay, winMask: mask, scatters: count('SCATTER'), bonusCount: count('BONUS'), coins: count('COIN') };
}

/** Reference tumble chain (SLOTS.md §3.2 steps 1–5). */
export function refTumbles(def: MachineDef, stops: readonly number[], ladder: readonly number[]): TumbleChain {
  let w = windowFromStops(def, stops).slice();
  const tops = [...stops];
  const steps: TumbleStep[] = [];
  let total = 0;
  for (let step = 0; step < 30; step++) {
    const res = refEvaluate(def, w);
    if (res.wins.length === 0) break;
    const mult = ladder[Math.min(step, ladder.length - 1)]!;
    steps.push({ step, window: w.slice(), result: res, multiplier: mult, payFifths: res.payFifths * mult, tops: [...tops] });
    total += res.payFifths * mult;
    const next: number[] = [];
    for (let r = 0; r < REELS; r++) {
      const kept: number[] = [];
      for (let y = 0; y < ROWS; y++) if (!(res.winMask & (1 << cell(r, y)))) kept.push(w[cell(r, y)]!);
      const m = ROWS - kept.length;
      const strip = def.strips[r]!;
      const L = strip.length;
      const fresh: number[] = [];
      for (let i = m; i >= 1; i--) fresh.push(strip[(((tops[r]! - i) % L) + L) % L]!);
      tops[r] = tops[r]! - m;
      next.push(...fresh, ...kept);
    }
    w = next;
  }
  return { steps, finalWindow: w, payFifths: total };
}

export const REF_ENGINE: SlotEngine = { evaluateWays: refEvaluate, runTumbles: refTumbles };

export interface TapeExtras {
  bought?: boolean;
  free?: Array<{ stops: number[]; stickyMaskAfter?: number; retrigger?: boolean }>;
  hunt?: number[];
  hoard?: SpinTape['hoard'];
  wheel?: number[];
  jackpots?: SpinTape['jackpots'];
  capHit?: boolean;
  totalFifths?: number;
}

/** A tape for fixed stops; every pay comes from the real engine (`evaluateSpin`), as the draw computes it. */
export function fakeTape(def: MachineDef, stops: number[], x: TapeExtras = {}, bet = 50): SpinTape {
  const baseFifths = x.bought ? 0 : evaluateSpin(def, stops, false).payFifths;
  let sticky = 0;
  const spins = (x.free ?? []).map((f) => {
    const e = evaluateSpin(def, f.stops, true, sticky);
    // End: the sticky mask follows the eggs that land (as the draw does); an explicit mask may only add reels
    const after = e.stickyAfter | (f.stickyMaskAfter ?? 0);
    const p = after === e.stickyAfter ? e.payFifths : evaluateSpin(def, f.stops, true, after).payFifths;
    sticky = after;
    return { stops: f.stops, stickyMaskAfter: after, retrigger: f.retrigger ?? false, payFifths: p };
  });
  const fsFifths = spins.reduce((s, f) => s + f.payFifths, 0);
  return {
    machine: def.machine,
    bet,
    bought: x.bought ?? false,
    stops: x.bought ? [] : stops,
    freeSpins: spins.length ? { awarded: spins.length, spins, payFifths: fsFifths } : undefined,
    hunt: x.hunt ? { entries: x.hunt, opened: 0 } : undefined,
    hoard: x.hoard,
    wheel: x.wheel ? { segments: x.wheel } : undefined,
    jackpots: x.jackpots ?? [],
    totalFifths: x.totalFifths ?? baseFifths + fsFifths,
    capHit: x.capHit ?? false,
  };
}

const DEFS = new WeakMap<SlotRound, MachineDef>();

/** The presentation round of a tape, evaluated by the real engine (the default of `roundFromTape`). */
export function fakeRound(def: MachineDef, tape: SpinTape, tier?: WinTier): SlotRound {
  const round = roundFromTape(def, tape, { tier: tier ?? slotTier(tape.totalFifths) });
  DEFS.set(round, def);
  return round;
}

export const TURBO: TimingProfile = { speedPct: 200, reduceMotion: false, flashes: true };
export const REDUCED: TimingProfile = { speedPct: 100, reduceMotion: true, flashes: false };

/** The REAL spin timeline (lane B-L8 `buildSlotTimeline`) of a round made by `fakeRound`. */
export const timelineOf = (round: SlotRound, shared: TimingProfile = SHARED_PROFILE, local: TimingProfile = SHARED_PROFILE) => {
  const def = DEFS.get(round);
  if (!def) throw new Error('timelineOf: make the round with fakeRound');
  return buildSlotTimeline(round.tape, def, shared, local, 1);
};

/** Random stops for machine `m`, seeded. */
export function randomStops(rng: FxRng): number[] {
  return [0, 1, 2, 3, 4].map(() => rng.nextInt(STRIP_LEN));
}
