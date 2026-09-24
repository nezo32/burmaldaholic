/**
 * Spin timeline builder (slots.md §2.2–§2.3, SLOTS.md §10.2–§10.4), twin of Java `SlotTimeline`; beat kinds are
 * the same strings. Drives the DDUI/classic form frames, the `slot_reels` entity driver and the settle timeout.
 * PURE. Lane S-B3 (vectors `test/fx/vectors/slots_timeline.json`).
 *
 * Layout (all times computed at normal speed in integer ms, then scaled: shared beats by the SPINNING player's
 * profile (turbo = 200 %), local beats by the viewer's profile):
 *   SHARED (clock S; the reveal gate is their end):
 *     base spin: SPIN_UP 0–120; REEL_LAND(r) the 350 ms before stop_r (stops from `stopTimes`, §10.3);
 *       ANTICIPATE(r) from the previous stop to stop_r; SYMBOL_LAND(r) cues for scatter/bonus/coin/wild cells;
 *       win overview WIN_SHOW (stop_5 + 150, 900 ms) — or per tumble step WIN_SHOW 600 + TUMBLE_EXPLODE 250 /
 *       MULT_UP 200 + TUMBLE_FALL 300 + pause 200 (step = 1 350 ms);
 *     bonus: BONUS_INTRO args[0] = 1 hunt / 2 hoard / 3 wheel (600 / 800 / 700 ms). The Treasure Hunt is
 *       INTERACTIVE: the timeline PAUSES at the end of the hunt intro (`huntPauseMs`) until the picks are done
 *       (the i-th pick reveals entry i, D6); the picks and the dimmed end reveal are local presentation (lane
 *       B-L9), so no HUNT_OPEN beat is in the spin timeline. HOARD_RESPIN(i) 900 each, HOARD_COLLECT 120 per coin;
 *       WHEEL_SPIN(lane ring, args[0] segment) 4 500 / 4 000 / 5 000 + 600 result glow (in the beat), WHEEL_UP 800
 *       zoom before the next ring;
 *     free spins: FS_INTRO 2 000; per spin FS_SPIN(args[0] = i) cue + the base layout × 0.8 (sticky reels do not
 *       spin; WILD_EXPAND stop + 270 / 300 ms, WILD_STICK + 570 / 200 ms); FS_RETRIGGER 1 200; FS_OUTRO roll-up of
 *       the feature total + 1 500 hold; MAX_WIN cue when the cap ended the spin.
 *   LOCAL (clock L, after the gate): MAX_WIN plate 200; ROLLUP (args [tierOrdinal, totalFifths]; 1 500 for
 *     "Returned"; ≤ 300 with reduce motion); WAY_CYCLE 700 per winning symbol (args [symbol, pay]) next to the
 *     roll-up, only when the base spin is the last reel segment; then the JACKPOT celebrations in tape order as the
 *     climax (animation/slots.md §2.5; args [tier, i]; Mini 2 000, Minor 2 500, Major 3 000, Grand 4 000; × 0.7
 *     after the first); END cue.
 * Ordinal contract with the frames (lane B-L9, `present/frames.ts`): a reel segment starts at SPIN_UP (base) or
 * FS_SPIN (free spin i); REEL_LAND lane r ENDS at reel r's stop; the k-th TUMBLE_EXPLODE / TUMBLE_FALL of a segment
 * removes the wins of evaluation k−1 and drops evaluation k; the j-th WIN_SHOW highlights evaluation j (only
 * winning evaluations get one); the i-th JACKPOT beat is `tape.jackpots[i]`; MULT_UP args[0] = the multiplier.
 * Beat args never carry information that is not public at the beat's start (F7).
 */
import { LOCAL, SHARED, type Timeline, TimelineBuilder, type TimingProfile, scaleMs } from '../../../../core/logic/anim/timeline';
import { rollUpDurationMs } from '../../../../core/logic/anim/rollup';
import { tierOrdinal } from '../../../../core/logic/anim/win-tier';
import { stopTimes } from './anticipation';
import { applySticky, bonusOf, coinOf, evaluateSpin, ladderAt, scatterOf, stickyReel, wildOf } from './engine';
import { slotTier } from './tiers';
import type { MachineDef, SpinTape, Window } from './types';
import { REELS, ROWS, tapeTotalChips, windowFromStops } from './types';

export const SLOT_BEAT = {
  SPIN_UP: 'slots.spin_up',
  REEL_LAND: 'slots.reel_land',
  ANTICIPATE: 'slots.anticipate',
  SYMBOL_LAND: 'slots.symbol_land',
  WIN_SHOW: 'slots.win_show',
  WAY_CYCLE: 'slots.way_cycle',
  TUMBLE_EXPLODE: 'slots.tumble_explode',
  TUMBLE_FALL: 'slots.tumble_fall',
  MULT_UP: 'slots.mult_up',
  WILD_EXPAND: 'slots.wild_expand',
  WILD_STICK: 'slots.wild_stick',
  FS_INTRO: 'slots.fs_intro',
  FS_SPIN: 'slots.fs_spin',
  FS_RETRIGGER: 'slots.fs_retrigger',
  FS_OUTRO: 'slots.fs_outro',
  BONUS_INTRO: 'slots.bonus_intro',
  HUNT_OPEN: 'slots.hunt_open',
  HOARD_RESPIN: 'slots.hoard_respin',
  HOARD_COLLECT: 'slots.hoard_collect',
  WHEEL_SPIN: 'slots.wheel_spin',
  WHEEL_UP: 'slots.wheel_up',
  JACKPOT: 'slots.jackpot',
  ROLLUP: 'slots.rollup',
  MAX_WIN: 'slots.max_win',
  END: 'slots.end',
} as const;

/** Timing tokens (normal speed, ms; slots.md §2.3). */
export const SLOT_MS = {
  SPIN_UP: 120,
  LAND: 350,
  WIN_DELAY: 150,
  WIN_ALL: 900,
  WAY_CYCLE: 700,
  TUMBLE_SHOW: 600,
  TUMBLE_EXPLODE: 250,
  MULT_UP: 200,
  TUMBLE_FALL: 300,
  TUMBLE_PAUSE: 200,
  WILD_EXPAND_AT: 270,
  WILD_EXPAND: 300,
  WILD_STICK: 200,
  FS_INTRO: 2000,
  FS_RETRIGGER: 1200,
  FS_OUTRO_HOLD: 1500,
  HUNT_INTRO: 600,
  HUNT_OPEN: 600,
  HUNT_DIM: 80,
  HOARD_INTRO: 800,
  HOARD_RESPIN: 900,
  HOARD_COLLECT: 120,
  WHEEL_INTRO: 700,
  WHEEL_RINGS: [4500, 4000, 5000] as readonly number[],
  WHEEL_UP: 800,
  WHEEL_GLOW: 600,
  MAX_WIN: 200,
  REDUCED_ROLLUP: 300,
  RETURNED: 1500,
  JACKPOT: [0, 2000, 2500, 3000, 4000] as readonly number[],
} as const;

/** Bonus kinds in BONUS_INTRO args[0] (lane B-L9 contract: the hunt pauses after the intro with args[0] = 1). */
export const BONUS_CODE = { hunt: 1, hoard: 2, wheel: 3 } as const;

export interface SlotTimelineOptions {
  /** `slots.anticipation` (default true) */
  anticipation?: boolean;
  /** `slots.bigWinTiers` */
  bigWinTiers?: readonly [number, number, number, number];
}

interface RawBeat {
  at: number;
  dur: number;
  kind: string;
  lane: number;
  group: number;
  args: number[];
}

/** Integer 0.8 × (free spins, SLOTS.md §10.2). */
const fsScale = (ms: number): number => Math.floor((ms * 4) / 5);

class Plan {
  readonly beats: RawBeat[] = [];
  group = 0;
  add(at: number, dur: number, kind: string, lane: number, ...args: number[]): number {
    this.beats.push({ at, dur, kind, lane, group: this.group, args });
    return at + dur;
  }
  next(): void {
    this.group++;
  }
}

function specialSymbol(def: MachineDef, s: number): boolean {
  return s === scatterOf(def) || s === bonusOf(def) || s === coinOf(def) || s === wildOf(def);
}

/**
 * One reel phase (base or free spin) from `t0`; returns the time the phase ends (after its win show / tumbles).
 * `k` = time scale for free spins.
 */
function reelPhase(p: Plan, def: MachineDef, stops: readonly number[], free: boolean, stickyBefore: number, t0: number, k: (ms: number) => number, anticipation: boolean, returned = false): { end: number; landed: Window } {
  const e = evaluateSpin(def, stops, free, stickyBefore);
  const landed: Window = e.chain ? e.chain.steps[0]!.window : applySticky(def, windowFromStops(def, stops), stickyBefore);
  // anticipation sees the landed window with sticky reels as WWW (a sticky reel is stopped from the start)
  const times = stopTimes(def, landed, anticipation, !free);
  p.add(t0, k(SLOT_MS.SPIN_UP), SLOT_BEAT.SPIN_UP, -1);
  for (let r = 0; r < REELS; r++) {
    const stickyBit = r >= 1 && r <= 3 ? 1 << (r - 1) : 0;
    const stop = t0 + k(times[r]!);
    if (r > 0 && times[r]! - times[r - 1]! > 150) p.add(t0 + k(times[r - 1]!), stop - (t0 + k(times[r - 1]!)), SLOT_BEAT.ANTICIPATE, r);
    if (!(stickyBefore & stickyBit)) {
      p.add(stop - k(SLOT_MS.LAND), k(SLOT_MS.LAND), SLOT_BEAT.REEL_LAND, r, stops[r]!);
      for (let y = 0; y < ROWS; y++) {
        const s = landed[r * ROWS + y]!;
        if (specialSymbol(def, s)) p.add(stop, 0, SLOT_BEAT.SYMBOL_LAND, r, y, s);
      }
    }
    if (free && stickyBit && e.stickyAfter & stickyBit && !(stickyBefore & stickyBit)) {
      p.add(stop + k(SLOT_MS.WILD_EXPAND_AT), k(SLOT_MS.WILD_EXPAND), SLOT_BEAT.WILD_EXPAND, r);
      p.add(stop + k(SLOT_MS.WILD_EXPAND_AT + SLOT_MS.WILD_EXPAND), k(SLOT_MS.WILD_STICK), SLOT_BEAT.WILD_STICK, r, stickyReel(r - 1));
    }
  }
  let t = t0 + k(times[REELS - 1]!);
  // wild expansion ends before the win show
  for (const b of p.beats) if (b.kind === SLOT_BEAT.WILD_STICK && b.at + b.dur > t) t = b.at + b.dur;
  t += k(SLOT_MS.WIN_DELAY);
  if (e.chain) {
    const ladder = free ? def.ladderFree : def.ladder;
    for (const st of e.chain.steps) {
      if (st.result.payFifths === 0) break;
      // a Returned spin (F9) still tumbles (the window changes) but shows no win
      if (!returned) p.add(t, k(SLOT_MS.TUMBLE_SHOW), SLOT_BEAT.WIN_SHOW, -1, st.step, st.payFifths);
      const x = t + k(SLOT_MS.TUMBLE_SHOW);
      p.add(x, k(SLOT_MS.TUMBLE_EXPLODE), SLOT_BEAT.TUMBLE_EXPLODE, -1, st.step, st.result.winMask);
      p.add(x, k(SLOT_MS.MULT_UP), SLOT_BEAT.MULT_UP, -1, ladderAt(ladder, st.step + 1), st.step + 1);
      p.add(x + k(SLOT_MS.TUMBLE_EXPLODE), k(SLOT_MS.TUMBLE_FALL), SLOT_BEAT.TUMBLE_FALL, -1, st.step + 1);
      t = x + k(SLOT_MS.TUMBLE_EXPLODE + SLOT_MS.TUMBLE_FALL + SLOT_MS.TUMBLE_PAUSE);
    }
    if (e.scatterFifths > 0 && !returned) t = p.add(t, k(SLOT_MS.WIN_ALL), SLOT_BEAT.WIN_SHOW, -1, e.chain.steps.length - 1, e.scatterFifths);
  } else if (e.payFifths > 0 && !returned) {
    t = p.add(t, k(SLOT_MS.WIN_ALL), SLOT_BEAT.WIN_SHOW, -1, 0, e.payFifths);
  }
  return { end: t, landed };
}

/**
 * The spin's timeline. `shared` = the spinning player's profile (published in `TimelineSeed.speedPct`), `local` =
 * the viewer's profile, `seed` = cosmetic seed (`seedMix(posHash, spinSeq)`).
 */
export function buildSlotTimeline(tape: SpinTape, def: MachineDef, shared: TimingProfile, local: TimingProfile, seed: number, opts: SlotTimelineOptions = {}): Timeline {
  const anticipation = opts.anticipation ?? true;
  const p = new Plan();
  const id = (ms: number): number => ms;
  let t = 0;
  let baseWins: Array<{ symbol: number; pay: number }> = [];
  // "Returned" (the whole spin pays less than the bet, F9): no win show, no dim, no way cycle
  const returnedSpin = slotTier(tape.totalFifths, opts.bigWinTiers) === 'RETURN';
  if (!tape.bought) {
    const r = reelPhase(p, def, tape.stops, false, 0, 0, id, anticipation, returnedSpin);
    t = r.end;
    const e = evaluateSpin(def, tape.stops, false);
    if (e.ways) baseWins = e.ways.wins.map((w) => ({ symbol: w.symbol, pay: w.payFifths })).sort((a, b) => b.pay - a.pay || a.symbol - b.symbol);
  }
  // ---- bonus ----
  if (tape.hunt) {
    // interactive: the timeline pauses at the end of this beat until the picks are done (huntPauseMs)
    p.next();
    t = p.add(t, SLOT_MS.HUNT_INTRO, SLOT_BEAT.BONUS_INTRO, -1, BONUS_CODE.hunt);
  } else if (tape.hoard) {
    p.next();
    const h = tape.hoard;
    t = p.add(t, SLOT_MS.HOARD_INTRO, SLOT_BEAT.BONUS_INTRO, -1, BONUS_CODE.hoard, h.initialCells.length);
    let left = def.hoard?.respins ?? 3;
    let coins = h.initialCells.length;
    h.respinCells.forEach((cells, i) => {
      p.next();
      left = cells.length > 0 ? (def.hoard?.respins ?? 3) : left - 1;
      coins += cells.length;
      t = p.add(t, SLOT_MS.HOARD_RESPIN, SLOT_BEAT.HOARD_RESPIN, -1, i, cells.length, left);
    });
    p.next();
    t = p.add(t, SLOT_MS.HOARD_COLLECT * coins, SLOT_BEAT.HOARD_COLLECT, -1, coins);
  } else if (tape.wheel) {
    p.next();
    t = p.add(t, SLOT_MS.WHEEL_INTRO, SLOT_BEAT.BONUS_INTRO, -1, BONUS_CODE.wheel);
    tape.wheel.segments.forEach((seg, ring) => {
      p.next();
      if (ring > 0) t = p.add(t, SLOT_MS.WHEEL_UP, SLOT_BEAT.WHEEL_UP, ring, ring);
      t = p.add(t, (SLOT_MS.WHEEL_RINGS[ring] ?? 4000) + SLOT_MS.WHEEL_GLOW, SLOT_BEAT.WHEEL_SPIN, ring, seg);
    });
  }
  // ---- free spins ----
  if (tape.freeSpins) {
    const fs = tape.freeSpins;
    p.next();
    t = p.add(t, SLOT_MS.FS_INTRO, SLOT_BEAT.FS_INTRO, -1, fs.awarded);
    let sticky = 0;
    let total = fs.awarded;
    fs.spins.forEach((s, i) => {
      p.next();
      p.add(t, 0, SLOT_BEAT.FS_SPIN, -1, i, total);
      const r = reelPhase(p, def, s.stops, true, sticky, t, fsScale, anticipation, returnedSpin);
      t = r.end;
      sticky = s.stickyMaskAfter;
      if (s.retrigger) {
        const before = total;
        total = Math.min(def.fsCap, total + def.retrigger);
        t = p.add(t, SLOT_MS.FS_RETRIGGER, SLOT_BEAT.FS_RETRIGGER, -1, total - before, total);
      }
    });
    p.next();
    const featureChips = (fs.payFifths * tape.bet) / 5;
    t = p.add(t, rollUpDurationMs(featureChips, tape.bet, 600, 8000) + SLOT_MS.FS_OUTRO_HOLD, SLOT_BEAT.FS_OUTRO, -1, fs.payFifths);
  }
  if (tape.capHit) p.add(t, 0, SLOT_BEAT.MAX_WIN, -1, def.capMultiple);

  // ---- scale the shared part ----
  const b = new TimelineBuilder(`slots.${tape.machine}`, seed);
  const sh = (ms: number): number => scaleMs(shared, ms);
  for (const x of p.beats) {
    b.group(x.group).clock(SHARED);
    const at = sh(x.at);
    b.add(at, sh(x.at + x.dur) - at, x.kind, x.lane, ...x.args);
  }
  const gate = sh(t);
  // ---- local part ----
  let g = p.group + 1;
  const lo = (ms: number): number => scaleMs(local, ms);
  let off = 0;
  const addLocal = (dur: number, kind: string, lane: number, ...args: number[]): void => {
    const at = gate + lo(off);
    b.group(g).clock(LOCAL).add(at, gate + lo(off + dur) - at, kind, lane, ...args);
    off += dur;
  };
  if (tape.capHit) {
    addLocal(local.reduceMotion ? 0 : SLOT_MS.MAX_WIN, SLOT_BEAT.MAX_WIN, -1, def.capMultiple);
    g++;
  }
  const chips = tapeTotalChips(tape);
  const tier = slotTier(tape.totalFifths, opts.bigWinTiers);
  if (tape.totalFifths > 0) {
    const cycleStart = off;
    const returned = tier === 'RETURN';
    // Returned (< 1× bet): no roll-up, the muted line holds 1 500 ms (animation/slots.md §4.12)
    let d = returned ? SLOT_MS.RETURNED : rollUpDurationMs(chips, tape.bet, 600, 8000);
    if (local.reduceMotion) d = Math.min(d, SLOT_MS.REDUCED_ROLLUP);
    addLocal(d, SLOT_BEAT.ROLLUP, -1, tierOrdinal(tier), tape.totalFifths);
    const rollEnd = off;
    // the way cycle runs next to the roll-up (decoration, skippable); only when the base spin is the last segment
    off = cycleStart;
    if (!returned && !tape.freeSpins) for (const w of baseWins) addLocal(SLOT_MS.WAY_CYCLE, SLOT_BEAT.WAY_CYCLE, -1, w.symbol, w.pay);
    off = Math.max(off, rollEnd);
    g++;
  }
  // the jackpots are the climax, after the spin roll-up (animation/slots.md §2.5)
  tape.jackpots.forEach((j, i) => {
    const full = SLOT_MS.JACKPOT[j.tier]!;
    const d = local.reduceMotion ? SLOT_MS.REDUCED_ROLLUP : i === 0 ? full : Math.floor((full * 7) / 10);
    addLocal(d, SLOT_BEAT.JACKPOT, -1, j.tier, i);
    g++;
  });
  addLocal(0, SLOT_BEAT.END, -1);
  return b.build();
}

/** Where the timeline pauses for the Treasure Hunt picks: end of the hunt BONUS_INTRO (args[0] = 1), or undefined. */
export function huntPauseMs(tl: Timeline): number | undefined {
  const b = tl.beats.find((x) => x.kind === SLOT_BEAT.BONUS_INTRO && x.args[0] === BONUS_CODE.hunt);
  return b ? b.at + b.dur : undefined;
}

/** Terminal display of a spin (fidelity: the last frame of every presentation equals this). */
export interface SlotTerminal {
  /** the window the reels rest on (after tumbles; for a bought feature / after free spins: the last free spin's) */
  window: number[];
  totalFifths: number;
  jackpots: number[];
  capHit: boolean;
}

export function slotTerminal(tape: SpinTape, def: MachineDef): SlotTerminal {
  let window: number[] = [];
  if (!tape.bought) window = evaluateSpin(def, tape.stops, false).finalWindow.slice();
  let sticky = 0;
  for (const s of tape.freeSpins?.spins ?? []) {
    window = evaluateSpin(def, s.stops, true, sticky).finalWindow.slice();
    sticky = s.stickyMaskAfter;
  }
  return { window, totalFifths: tape.totalFifths, jackpots: tape.jackpots.map((j) => j.chips), capHit: tape.capHit };
}
