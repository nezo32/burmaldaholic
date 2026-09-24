/**
 * Slots v2 frame engine for Bedrock text reels (animation/slots.md §6.1–§6.3, task BS1; lane B-L9).
 * PURE (no @minecraft imports): samples the spin `Timeline` (built by `games/slots/v2/logic/timeline.ts`)
 * at a time t and returns the glyph cells of the 5 × 3 reel window. The DDUI form and the classic action-bar
 * fallback render the same frames; the in-world entity uses the same timeline.
 *
 * Fidelity (animation/slots.md §2.1, docs/architecture/animation.md §3.4):
 *  - F2 real strip: a scrolling reel r shows `S_r[p], S_r[p+1], S_r[p+2]` with `p` counting down to the drawn
 *    stop `t_r`, one strip row per 100 ms (10 rows/s); the last three cells are always the paid window.
 *  - F3 honest anticipation: only ANTICIPATE beats of the timeline (from `anticipationPlan`) tint a column.
 *  - F4 no row overshoot: a landing column shows its exact window at once, with a 1-frame win-plane flash.
 *  - F8 interrupt = reveal: `frame(round, tl, tl.endMs())` equals `terminal(round)` (tested for every vector,
 *    reduce motion and skip).
 *
 * Beat contract of the real builder (lane B-L8 `buildSlotTimeline`, `logic/timeline.ts`; verified by the tests,
 * which build every timeline with it):
 *  - a spin segment starts at SPIN_UP (base) or FS_SPIN (free spin i, i = ordinal of FS_SPIN beats);
 *  - REEL_LAND lane r ENDS at reel r's stop time; a sticky reel has no REEL_LAND (it never spins: its stop is
 *    the segment start); ANTICIPATE lane r spans the anticipation of reel r;
 *  - the k-th TUMBLE_EXPLODE / TUMBLE_FALL of a segment removes the wins of evaluation k−1 and drops in
 *    evaluation k; the j-th WIN_SHOW highlights evaluation j; WAY_CYCLE args[0] = symbol id (LOCAL, after the
 *    gate, only when the base spin is the last segment);
 *  - WILD_EXPAND lane r expands the Dragon Egg of reel r (End free spins);
 *  - LOCAL: ROLLUP, then the JACKPOT beats (the i-th = `tape.jackpots[i]`), END.
 * Ordinals are used instead of args wherever possible so the frames do not depend on arg layouts.
 */
import { type Beat, type FrameModel, type Timeline, beatEnd } from '../../../../core/logic/anim/timeline';
import type { WinTier } from '../../../../core/logic/anim/win-tier';
import { evaluateWays, runTumbles } from '../logic/engine';
import { SLOT_BEAT } from '../logic/timeline';
import { type MachineDef, type MachineId, type SpinTape, type SymbolRole, type TumbleChain, type WayWin, type Window, type WaysResult, REELS, ROWS, windowFromStops } from '../logic/types';

// ---------------------------------------------------------------------------------------------------------
// Glyphs (SLOTS.md §2, animation/slots.md §6.1; docs/architecture/animation.md §6)
// ---------------------------------------------------------------------------------------------------------

/** Symbol codes in SLOTS.md §2 order = glyph offset inside the machine's row of the E2 sheet. */
export const SYMBOL_CODES: Readonly<Record<MachineId, readonly string[]>> = {
  overworld: ['WD', 'SC', 'BN', 'DI', 'EM', 'GO', 'IR', 'AP', 'CA', 'WH', 'BE'],
  nether: ['WD', 'SC', 'CN', 'SK', 'BR', 'MC', 'QZ', 'NW', 'CF', 'WF', 'GD'],
  end: ['WD', 'SC', 'BN', 'DH', 'EL', 'SS', 'CH', 'EP', 'PU', 'ER', 'ES'],
};

/** Symbol ids (lang `gui.burmaldaholic.slots.symbol.<id>`), same order as `SYMBOL_CODES`. */
export const SYMBOL_IDS: Readonly<Record<MachineId, readonly string[]>> = {
  overworld: ['totem', 'compass', 'chest', 'diamond', 'emerald', 'gold_ingot', 'iron_ingot', 'apple', 'carrot', 'wheat', 'sweet_berries'],
  nether: ['lava_bucket', 'ghast_tear', 'piglin_coin', 'wither_skull', 'blaze_rod', 'magma_cream', 'quartz', 'nether_wart', 'crimson_fungus', 'warped_fungus', 'glowstone'],
  end: ['dragon_egg', 'ender_eye', 'end_crystal', 'dragon_head', 'elytra', 'shulker_shell', 'chorus_fruit', 'ender_pearl', 'purpur', 'end_rod', 'end_stone'],
};

export const MACHINE_GLYPH_BASE: Readonly<Record<MachineId, number>> = { overworld: 0xe200, nether: 0xe210, end: 0xe220 };

/** Shared slot glyphs (base plane; win plane = +0x100, blur plane = +0x200). */
export const SLOT_GLYPH = {
  BADGE: [0xe230, 0xe231, 0xe232, 0xe233] as const,
  CHEST_CLOSED: 0xe234,
  CHEST_OPEN: 0xe235,
  CREEPER: 0xe236,
  EMPTY: 0xe237,
  POINTER: 0xe238,
  STICKY: 0xe239,
  PLATE: 0xe23a,
  PIP_FULL: 0xe23b,
  PIP_EMPTY: 0xe23c,
  ARROW: 0xe23d,
  EMBER: 0xe23e,
  WAY_NODE: 0xe23f,
  WEDGE: 0xe243,
} as const;

export const PLANE_BASE = 0;
export const PLANE_WIN = 1;
export const PLANE_BLUR = 2;
export const PLANE_OFFSET = [0, 0x100, 0x200] as const;

export const TINT_NONE = 0;
/** `§8` (win show: non-winning cells) — [V] B-S0: tinting of glyphs */
export const TINT_DIM = 1;
/** `§e` (anticipating column) */
export const TINT_ANTIC = 2;
/** `§7` (creeper swell off-phase) */
export const TINT_GRAY = 3;
export const TINT_CODE = ['§f', '§8', '§e', '§7'] as const;

/** Code point of `base` on `plane`. */
export const onPlane = (base: number, plane: number): number => base + (PLANE_OFFSET[plane] ?? 0);

// ---------------------------------------------------------------------------------------------------------
// Timing tokens (animation/slots.md §2.3, §6.2; Bedrock rounds to 50 ms, frames every 2 t)
// ---------------------------------------------------------------------------------------------------------

export const FRAME_MS = 100;
/** Bedrock text reels: one strip row per frame (10 rows/s). */
export const ROW_MS = 100;
/** Spin-up: the first 3 ticks move with base glyphs. */
export const SPIN_UP_MS = 150;
/** The last 3 rows before a stop use base glyphs (reads as slowing down). */
export const SLOW_ROWS = 3;
/** Land "clunk": 1 frame on the win plane; scatters / bonus symbols 2 frames. */
export const LAND_FLASH_MS = 100;
export const SPECIAL_FLASH_MS = 200;
/** Trigger cells blink win ↔ base at 2.5 Hz while a later reel anticipates. */
export const TRIGGER_BLINK_MS = 200;

// ---------------------------------------------------------------------------------------------------------
// The round as the presentation sees it
// ---------------------------------------------------------------------------------------------------------

/** One evaluation of a window: [0] = after landing (and expansion), [k] = after tumble k. */
export interface EvalView {
  readonly window: Window;
  readonly winMask: number;
  readonly wins: readonly WayWin[];
  /** tumble ladder multiplier of this evaluation (1 without tumbles) */
  readonly multiplier: number;
  readonly payFifths: number;
}

/** One reel spin (base or free spin). */
export interface SpinView {
  readonly stops: readonly number[];
  /** strip window at the stops (before sticky / expanding wilds) */
  readonly landed: Window;
  /** sticky reels before / after this spin (bit 0..2 = reels 2..4), End free spins */
  readonly stickyBefore: number;
  readonly stickyAfter: number;
  readonly evals: readonly EvalView[];
  readonly payFifths: number;
  readonly retrigger: boolean;
}

export interface SlotRound {
  readonly machine: MachineId;
  readonly bet: number;
  readonly strips: readonly (readonly number[])[];
  readonly roles: readonly SymbolRole[];
  /** base-plane glyph code point per def symbol index */
  readonly glyphs: readonly number[];
  /** lang symbol id per def symbol index */
  readonly symbolIds: readonly string[];
  /** window shown before the spin starts (the last settled window) */
  readonly rest: Window;
  /** absent for a bought feature */
  readonly base?: SpinView;
  readonly free: readonly SpinView[];
  readonly fsAwarded: number;
  readonly fsRetrigger: number;
  readonly ladder: readonly number[];
  readonly ladderFree: readonly number[];
  readonly tape: SpinTape;
  /** server-decided slot tier of the whole spin (never re-derived, animation/slots.md §2.5) */
  readonly tier: WinTier;
  /** spin total in chips excluding progressive awards (tape.totalFifths × bet / 5) */
  readonly totalChips: number;
}

export interface SlotEngine {
  evaluateWays(def: MachineDef, w: Window, stickyMask?: number): WaysResult;
  runTumbles(def: MachineDef, stops: readonly number[], ladder: readonly number[]): TumbleChain;
}

/** Default: the shared pure engine (lane B-L8). Tests inject fakes while the engine is a stub. */
export const DEFAULT_ENGINE: SlotEngine = { evaluateWays, runTumbles };

const bit = (i: number): number => 1 << i;
export const cellIndex = (reel: number, row: number): number => reel * ROWS + row;

/** Window with sticky reels (mask bit 0..2 = reels 2..4) forced to the wild. */
export function withSticky(w: Window, mask: number, wild: number): Window {
  if (!mask || wild < 0) return w;
  const out = w.slice();
  for (let i = 0; i < 3; i++) if (mask & bit(i)) for (let y = 0; y < ROWS; y++) out[cellIndex(i + 1, y)] = wild;
  return out;
}

function spinView(def: MachineDef, engine: SlotEngine, stops: readonly number[], stickyBefore: number, stickyAfter: number, ladder: readonly number[], pay: number | undefined, retrigger: boolean): SpinView {
  const landed = windowFromStops(def, stops);
  const wild = def.roles.indexOf('WILD');
  let evals: EvalView[];
  if (def.machine === 'nether') {
    const chain = engine.runTumbles(def, stops, ladder);
    const steps = chain.steps.filter((s) => s.result.wins.length > 0);
    evals = steps.map((s) => ({ window: s.window, winMask: s.result.winMask, wins: s.result.wins, multiplier: s.multiplier, payFifths: s.payFifths }));
    if (evals.length === 0) {
      const r = engine.evaluateWays(def, landed, 0);
      evals = [{ window: landed, winMask: r.winMask, wins: r.wins, multiplier: ladder[0] ?? 1, payFifths: r.payFifths }];
    } else evals.push({ window: chain.finalWindow, winMask: 0, wins: [], multiplier: ladder[Math.min(evals.length, ladder.length - 1)] ?? 1, payFifths: 0 });
  } else {
    const w = withSticky(landed, stickyAfter, wild);
    const r = engine.evaluateWays(def, landed, stickyAfter);
    evals = [{ window: w, winMask: r.winMask, wins: r.wins, multiplier: 1, payFifths: r.payFifths }];
  }
  if (pay === undefined) {
    // base spin: the scatter pay belongs to the last evaluation (the builder's last WIN_SHOW carries it)
    const scat = def.machine === 'nether' ? (engine.runTumbles(def, stops, ladder).steps.at(-1)?.result.scatters ?? 0) : engine.evaluateWays(def, landed, stickyAfter).scatters;
    const sp = scat >= 3 ? (def.scatterFifths[Math.min(scat, 5) - 3] ?? 0) : 0;
    if (sp > 0) {
      const last = evals[evals.length - 1]!;
      evals[evals.length - 1] = { ...last, payFifths: last.payFifths + sp };
    }
  }
  const payFifths = pay ?? evals.reduce((s, e) => s + e.payFifths, 0);
  return { stops: [...stops], landed, stickyBefore, stickyAfter, evals, payFifths, retrigger };
}

export interface RoundOptions {
  /** window before the spin; default: the landed base window of stops 0 */
  rest?: Window;
  tier: WinTier;
  engine?: SlotEngine;
}

/** Builds the presentation view of a drawn tape. Never draws anything: every value comes from the tape. */
export function roundFromTape(def: MachineDef, tape: SpinTape, opts: RoundOptions): SlotRound {
  const engine = opts.engine ?? DEFAULT_ENGINE;
  const codes = SYMBOL_CODES[def.machine];
  const glyphs = def.codes.map((c, i) => MACHINE_GLYPH_BASE[def.machine] + (codes.indexOf(c) >= 0 ? codes.indexOf(c) : i));
  const symbolIds = def.codes.map((c, i) => SYMBOL_IDS[def.machine][codes.indexOf(c) >= 0 ? codes.indexOf(c) : i] ?? 'creeper');
  const base = tape.bought ? undefined : spinView(def, engine, tape.stops, 0, 0, def.ladder, undefined, false);
  const free: SpinView[] = [];
  let sticky = 0;
  for (const fs of tape.freeSpins?.spins ?? []) {
    free.push(spinView(def, engine, fs.stops, sticky, fs.stickyMaskAfter, def.ladderFree, fs.payFifths, fs.retrigger));
    sticky = fs.stickyMaskAfter;
  }
  return {
    machine: def.machine,
    bet: tape.bet,
    strips: def.strips,
    roles: def.roles,
    glyphs,
    symbolIds,
    rest: opts.rest ?? windowFromStops(def, [0, 0, 0, 0, 0]),
    base,
    free,
    fsAwarded: tape.freeSpins?.awarded ?? 0,
    fsRetrigger: def.retrigger,
    ladder: def.ladder,
    ladderFree: def.ladderFree,
    tape,
    tier: opts.tier,
    totalChips: (tape.totalFifths * tape.bet) / 5,
  };
}

// ---------------------------------------------------------------------------------------------------------
// Frames
// ---------------------------------------------------------------------------------------------------------

export interface Cell {
  /** base-plane code point */
  readonly g: number;
  readonly plane: number;
  readonly tint: number;
}

export interface ReelFrame {
  /** 15 cells, index = reel × 3 + row */
  readonly cells: readonly Cell[];
  /** reels showing the anticipation arrow (fallback when glyph tinting is unavailable) */
  readonly arrows: number;
}

export interface FrameOptions {
  reduceMotion: boolean;
  flashes: boolean;
  /** `§` colour codes tint glyphs ([V] B-S0); false: no dim, arrows instead of the anticipation tint */
  tinting: boolean;
}

export const DEFAULT_FRAME_OPTIONS: FrameOptions = { reduceMotion: false, flashes: true, tinting: true };

const cell = (g: number, plane = PLANE_BASE, tint = TINT_NONE): Cell => ({ g, plane, tint });

const stripAt = (strip: readonly number[], i: number): number => strip[((i % strip.length) + strip.length) % strip.length]!;

/** A spin segment of the timeline (base spin or one free spin). */
interface Segment {
  spin: SpinView;
  start: number;
  next: number;
  stop: number[];
  antic: Array<Beat | undefined>;
  expand: Array<Beat | undefined>;
  explode: Beat[];
  fall: Beat[];
  winShow: Beat[];
  cycles: Beat[];
  landed: boolean;
}

interface Segments {
  round: SlotRound;
  list: Segment[];
}

const segCache = new WeakMap<Timeline, Segments>();

/** Spin segments of the timeline (cached per timeline; rebuilt only when the round changes). */
function segmentsOf(round: SlotRound, tl: Timeline): Segment[] {
  const hit = segCache.get(tl);
  if (hit && hit.round === round) return hit.list;
  const list: Segment[] = [];
  let fs = 0;
  let baseUsed = false;
  let cur: Segment | undefined;
  const open = (spin: SpinView | undefined, at: number): void => {
    if (!spin) return;
    cur = { spin, start: at, next: Number.POSITIVE_INFINITY, stop: [], antic: [], expand: [], explode: [], fall: [], winShow: [], cycles: [], landed: false };
    list.push(cur);
  };
  for (const b of tl.beats) {
    switch (b.kind) {
      case SLOT_BEAT.FS_SPIN:
        open(round.free[fs++], b.at);
        break;
      case SLOT_BEAT.SPIN_UP:
        if (cur && !cur.landed && cur.stop.length === 0 && cur.spin !== round.base) cur.start = b.at;
        else if (!baseUsed && round.base) {
          baseUsed = true;
          open(round.base, b.at);
        } else if (!round.base && !cur) open(round.free[fs++], b.at);
        break;
      case SLOT_BEAT.REEL_LAND:
        if (cur && b.lane >= 0 && b.lane < REELS) {
          cur.stop[b.lane] = beatEnd(b);
          cur.landed = true;
        }
        break;
      case SLOT_BEAT.ANTICIPATE:
        if (cur && b.lane >= 0 && b.lane < REELS) cur.antic[b.lane] = b;
        break;
      case SLOT_BEAT.WILD_EXPAND:
        if (cur && b.lane >= 1 && b.lane <= 3) cur.expand[b.lane] = b;
        break;
      case SLOT_BEAT.TUMBLE_EXPLODE:
        cur?.explode.push(b);
        break;
      case SLOT_BEAT.TUMBLE_FALL:
        cur?.fall.push(b);
        break;
      case SLOT_BEAT.WIN_SHOW:
        cur?.winShow.push(b);
        break;
      case SLOT_BEAT.WAY_CYCLE:
        cur?.cycles.push(b);
        break;
      default:
        break;
    }
  }
  for (let i = 0; i < list.length; i++) {
    const s = list[i]!;
    s.next = list[i + 1]?.start ?? Number.POSITIVE_INFINITY;
    // no REEL_LAND: a sticky reel (it does not spin, stopped from the segment start)
    for (let r = 0; r < REELS; r++) if (s.stop[r] === undefined) s.stop[r] = s.start;
  }
  segCache.set(tl, { round, list });
  return list;
}

function segmentAt(list: readonly Segment[], t: number): Segment | undefined {
  let s: Segment | undefined;
  for (const x of list) if (x.start <= t) s = x;
  return s;
}

const isTrigger = (round: SlotRound, sym: number): boolean => {
  const role = round.roles[sym];
  return role === 'SCATTER' || role === 'BONUS';
};

/** F9: a spin that returns less than the bet (but more than 0) is "Returned": no win glow, dim or pulse. */
export const isReturned = (round: SlotRound): boolean => round.totalChips > 0 && round.totalChips < round.bet;

/**
 * Roles whose landed cells blink while later reels anticipate: only the ones that caused the anticipation
 * (SLOTS.md §10.3 on the reels already stopped), so a Nether coin anticipation never blinks unrelated scatters.
 * Presentation only — the stop times still come from the timeline (F3).
 */
function anticipationRoles(round: SlotRound, w: Window, stopped: number): Set<SymbolRole> {
  const out = new Set<SymbolRole>();
  const cnt = (role: SymbolRole): number => {
    let n = 0;
    for (let i = 0; i < stopped * ROWS; i++) if (round.roles[w[i]!] === role) n++;
    return n;
  };
  const has = (r: number): boolean => r < stopped && [0, 1, 2].some((y) => round.roles[w[cellIndex(r, y)]!] === 'BONUS');
  if (cnt('SCATTER') >= 2) out.add('SCATTER');
  if ((round.machine === 'overworld' && has(0) && has(2)) || (round.machine === 'end' && has(1) && has(2))) out.add('BONUS');
  if (round.machine === 'nether' && cnt('COIN') >= 4) out.add('COIN');
  return out;
}

/** Final evaluation of the last spin: the paid result the round ends on. */
export function finalEval(round: SlotRound): EvalView | undefined {
  const spin = round.free[round.free.length - 1] ?? round.base;
  return spin?.evals[spin.evals.length - 1];
}

/** The settled frame (end of the timeline, skip, reduce motion, late join). */
export function terminalFrame(round: SlotRound): ReelFrame {
  const e = finalEval(round);
  if (!e) return { cells: round.rest.map((s) => cell(round.glyphs[s]!)), arrows: 0 };
  const mask = isReturned(round) ? 0 : e.winMask;
  return { cells: e.window.map((s, i) => cell(round.glyphs[s]!, mask & bit(i) ? PLANE_WIN : PLANE_BASE)), arrows: 0 };
}

/** Gravity fall of one tumble (1 row per step, `step` 0…3): kept cells drop, new cells enter from the top. */
function fallColumn(before: readonly number[], removed: number, after: readonly number[], step: number, reel: number): Array<number | undefined> {
  const out: Array<number | undefined> = [undefined, undefined, undefined];
  const kept: Array<{ sym: number; from: number }> = [];
  for (let y = 0; y < ROWS; y++) if (!(removed & bit(cellIndex(reel, y)))) kept.push({ sym: before[y]!, from: y });
  const m = ROWS - kept.length;
  // kept cells: final row = m + index (order kept)
  kept.forEach((k, i) => {
    const to = m + i;
    out[Math.min(to, k.from + step)] = k.sym;
  });
  // new cells i (final row i) start above the window and fall `step` rows
  for (let i = 0; i < m; i++) {
    const y = i - m + step;
    if (y >= 0 && y < ROWS && out[y] === undefined) out[y] = after[i]!;
  }
  return out;
}

function column(w: Window, reel: number): number[] {
  return [w[cellIndex(reel, 0)]!, w[cellIndex(reel, 1)]!, w[cellIndex(reel, 2)]!];
}

/** Frame of the reel window at timeline time `t` (ms). */
export function reelFrame(round: SlotRound, tl: Timeline, t: number, o: FrameOptions = DEFAULT_FRAME_OPTIONS): ReelFrame {
  const segs = segmentsOf(round, tl);
  const seg = segmentAt(segs, t);
  if (!seg) return { cells: round.rest.map((s) => cell(round.glyphs[s]!)), arrows: 0 };
  const spin = seg.spin;
  const wild = round.roles.indexOf('WILD');
  const cells: Cell[] = new Array<Cell>(REELS * ROWS);
  const lastStop = Math.max(...seg.stop);
  const pulse = o.flashes && !o.reduceMotion;
  let arrows = 0;

  if (t < lastStop) {
    // ---- reels phase: scrolling, landing, anticipation
    const anticipating = seg.antic.some((a, r) => a !== undefined && t >= a.at && t < seg.stop[r]!);
    const blinkRoles = anticipating ? anticipationRoles(round, spin.landed, seg.stop.filter((s) => t >= s).length) : undefined;
    const frameIdx = Math.floor(t / FRAME_MS);
    for (let r = 0; r < REELS; r++) {
      const stop = seg.stop[r]!;
      const sticky = r >= 1 && r <= 3 && (spin.stickyBefore & bit(r - 1)) !== 0;
      const strip = round.strips[r]!;
      const a = seg.antic[r];
      const antic = a !== undefined && t >= a.at && t < stop;
      for (let y = 0; y < ROWS; y++) {
        const i = cellIndex(r, y);
        if (sticky) {
          cells[i] = cell(round.glyphs[wild]!, PLANE_WIN);
          continue;
        }
        if (t < stop) {
          let sym: number;
          let plane: number;
          if (o.reduceMotion) {
            // no scrolling: a static blurred strip window above the stop (never the result), then the result
            sym = stripAt(strip, spin.stops[r]! + ROWS + y);
            plane = PLANE_BLUR;
          } else {
            const rowsLeft = Math.ceil((stop - t) / ROW_MS);
            sym = stripAt(strip, spin.stops[r]! + rowsLeft + y);
            plane = t - seg.start < SPIN_UP_MS || rowsLeft <= SLOW_ROWS ? PLANE_BASE : PLANE_BLUR;
          }
          let tint = TINT_NONE;
          if (antic) {
            if (!o.tinting) arrows |= bit(r);
            else tint = !pulse || frameIdx % 2 === 0 ? TINT_ANTIC : TINT_NONE;
          }
          cells[i] = cell(round.glyphs[sym]!, plane, tint);
        } else if (seg.expand[r] && t >= seg.expand[r]!.at) {
          // End: the egg of a landed reel expands while later reels still spin (WILD_EXPAND = stop + 270 ms)
          const eb = seg.expand[r]!;
          const col = column(spin.landed, r);
          const done = t >= beatEnd(eb) || o.reduceMotion;
          const egg = col.some((x, yy) => x === wild && Math.abs(yy - y) <= 1);
          cells[i] = done ? cell(round.glyphs[wild]!, PLANE_WIN) : cell(round.glyphs[egg ? wild : col[y]!]!, egg ? PLANE_WIN : PLANE_BASE);
        } else {
          const sym = spin.landed[i]!;
          const trig = isTrigger(round, sym);
          const flashMs = trig ? SPECIAL_FLASH_MS : LAND_FLASH_MS;
          let plane = !o.reduceMotion && t - stop < flashMs ? PLANE_WIN : PLANE_BASE;
          if (blinkRoles?.has(round.roles[sym]!)) plane = !pulse || Math.floor(t / TRIGGER_BLINK_MS) % 2 === 0 ? PLANE_WIN : PLANE_BASE;
          cells[i] = cell(round.glyphs[sym]!, plane);
        }
      }
    }
    return { cells, arrows };
  }

  // ---- after landing: expansion, win show, tumbles
  let k = 0;
  while (k < seg.explode.length && seg.explode[k]!.at <= t) k++;
  const nEval = spin.evals.length;
  const evalIdx = Math.min(k, nEval - 1);
  const explodeBeat = k > 0 ? seg.explode[k - 1] : undefined;
  const fallBeat = k > 0 ? seg.fall[k - 1] : undefined;
  let window: Array<number | undefined> = spin.evals[evalIdx]!.window.slice();
  let ember = 0;
  if (explodeBeat && k - 1 < nEval && t < beatEnd(explodeBeat)) {
    const prev = spin.evals[k - 1]!;
    window = prev.window.slice();
    ember = prev.winMask;
  } else if (explodeBeat && fallBeat && fallBeat.at <= t && t < beatEnd(fallBeat) && k - 1 < nEval && !o.reduceMotion) {
    const prev = spin.evals[k - 1]!;
    const next = spin.evals[evalIdx]!;
    const step = Math.min(ROWS, 1 + Math.floor((ROWS * (t - fallBeat.at)) / Math.max(1, fallBeat.dur)));
    for (let r = 0; r < REELS; r++) {
      const col = fallColumn(column(prev.window, r), prev.winMask, column(next.window, r), step, r);
      for (let y = 0; y < ROWS; y++) window[cellIndex(r, y)] = col[y];
    }
  } else if (explodeBeat && fallBeat && t < fallBeat.at && k - 1 < nEval) {
    // between explode end and fall start: holes stay
    const prev = spin.evals[k - 1]!;
    window = prev.window.slice();
    for (let i = 0; i < window.length; i++) if (prev.winMask & bit(i)) window[i] = undefined;
  }
  // expanding wilds (End): before / during WILD_EXPAND the landed column, then WWW
  if (k === 0) {
    for (let r = 1; r <= 3; r++) {
      const eb = seg.expand[r];
      if (!eb || t >= beatEnd(eb)) continue;
      const col = column(spin.landed, r);
      if (t >= eb.at && !o.reduceMotion) {
        const eggRows = col.map((s, y) => (s === wild ? y : -1)).filter((y) => y >= 0);
        for (let y = 0; y < ROWS; y++) if (eggRows.some((e) => Math.abs(e - y) <= 1)) col[y] = wild;
      } else if (t >= eb.at) continue;
      for (let y = 0; y < ROWS; y++) window[cellIndex(r, y)] = col[y]!;
    }
  }
  // highlight: the win show of the current evaluation
  let mask = spin.evals[evalIdx]!.winMask;
  let dim = false;
  const ws = seg.winShow;
  let j = -1;
  for (let x = 0; x < ws.length; x++) if (ws[x]!.at <= t) j = x;
  if ((explodeBeat && t < beatEnd(fallBeat ?? explodeBeat)) || j < evalIdx || isReturned(round)) {
    mask = 0;
  } else if (j >= 0 && j < nEval) {
    const show = ws[j]!;
    const ev = spin.evals[j]!;
    const cyc = seg.cycles.filter((c) => c.at >= show.at && c.at < (ws[j + 1]?.at ?? seg.next));
    const active = cyc.findIndex((c) => t >= c.at && t < beatEnd(c));
    if (active >= 0) {
      const c = cyc[active]!;
      const sym = c.args.length > 0 ? c.args[0]! : ev.wins[active]?.symbol;
      mask = ev.wins.filter((w) => w.symbol === sym).reduce((m, w) => m | w.cellMask, 0);
      dim = true;
    } else if (t < beatEnd(show) || (cyc.length > 0 && t < beatEnd(cyc[cyc.length - 1]!))) {
      mask = ev.winMask;
      dim = true;
    } else mask = ev.winMask;
  }
  for (let i = 0; i < REELS * ROWS; i++) {
    const sym = window[i];
    if (sym === undefined || ember & bit(i)) {
      cells[i] = cell(SLOT_GLYPH.EMBER);
      continue;
    }
    const lit = (mask & bit(i)) !== 0;
    cells[i] = cell(round.glyphs[sym]!, lit ? PLANE_WIN : PLANE_BASE, dim && !lit && o.tinting && mask !== 0 ? TINT_DIM : TINT_NONE);
  }
  return { cells, arrows };
}

export const sameCell = (a: Cell, b: Cell): boolean => a.g === b.g && a.plane === b.plane && a.tint === b.tint;
export const sameFrame = (a: ReelFrame, b: ReelFrame): boolean => a.arrows === b.arrows && a.cells.length === b.cells.length && a.cells.every((c, i) => sameCell(c, b.cells[i]!));

/** Pure frame model (docs/architecture/animation.md §3.6). */
export const SLOT_FRAME_MODEL: FrameModel<SlotRound, ReelFrame> = {
  frame: (o, tl, t) => reelFrame(o, tl, t),
  terminal: (o) => terminalFrame(o),
};

// ---------------------------------------------------------------------------------------------------------
// Text rendering (DDUI label R / classic action bar)
// ---------------------------------------------------------------------------------------------------------

/** Glyphs of a row are 2 spaces apart (animation/slots.md §6.2). */
export const CELL_GAP = '  ';
/** [V] B-S0: width of one 32 px glyph cell in spaces, for the fallback arrow row. */
export const ARROW_PAD = '    ';

const cellText = (c: Cell, tinting: boolean): string => (tinting ? TINT_CODE[c.tint] ?? '§f' : '§f') + String.fromCodePoint(onPlane(c.g, c.plane));

/** Three glyph rows (plus the arrow row above them when `arrows` is set). Contains no letters. */
export function renderRows(f: ReelFrame, tinting = true): string[] {
  const rows: string[] = [];
  if (f.arrows) {
    const a: string[] = [];
    for (let r = 0; r < REELS; r++) a.push(f.arrows & bit(r) ? `§e${String.fromCodePoint(SLOT_GLYPH.ARROW)}` : ARROW_PAD);
    rows.push(a.join(CELL_GAP) + '§r');
  }
  for (let y = 0; y < ROWS; y++) {
    const parts: string[] = [];
    for (let r = 0; r < REELS; r++) parts.push(cellText(f.cells[cellIndex(r, y)]!, tinting));
    rows.push(parts.join(CELL_GAP) + '§r');
  }
  return rows;
}

export const popcount = (m: number): number => {
  let n = 0;
  for (let x = m; x; x &= x - 1) n++;
  return n;
};
