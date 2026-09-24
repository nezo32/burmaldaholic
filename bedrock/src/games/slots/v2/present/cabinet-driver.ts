/**
 * In-world cabinet driver (animation/slots.md §6.6, task BS7): turns one spin's presentation data into entity
 * property writes and one-shot animations on the `burmaldaholic:slot_reels` prop, at the same timeline ticks as
 * the player's form (fidelity F10), so spectators see exactly the paid window land.
 *
 * Layers:
 *  - `CABINET`   — the prop contract shared with the art generator (tools/assets/modules/slots.mjs bundles this
 *                  file): property ids, enum values, strip padding, wheel rings, plate values. ONE source of truth.
 *  - `planCabinet(spin)` — PURE: `CabinetSpin` → ordered cues `{at, props, anims, particle}` (tested: budgets,
 *                  terminal state == result, fidelity of stops/masks).
 *  - `CabinetPlayer` — schedules the cues against an injected clock + prop target (no @minecraft import here; the
 *                  Minecraft adapters live in `games/slots/cabinet.ts`). `skip()` / `finish()` jump to the terminal
 *                  state (F8: interrupt = reveal).
 *
 * Budget (slots.md §2.6): ≤ 7 `setProperty` in the spin-start tick (r0…r4, seq, state), + the few per beat that
 * change (win mask, mult, sticky, hold, wheel, g0…g2). The client does all per-frame motion in Molang.
 * Tumbles (D3, MUST): the reels keep the FIRST landed window; each step pulses the win frames (win mask + mult
 * change) and spawns `ember_burst`. NICE: `g0…g2` show the final window over the reels after the chain.
 */
import type { MachineId } from '../logic/types';

// ---------------------------------------------------------------------------------------------------------------
// The prop contract (read by the art generator — keep literal and JSON-friendly)
const NS = 'burmaldaholic:';
/** Namespaced id builder (property / particle ids of the prop). */
const PROP = (name: string): string => `${NS}${name}`;

export const CABINET = {
  entity: PROP('slot_reels'),
  /** machine index = property `machine` = geometry/texture array index */
  machines: ['overworld', 'nether', 'end'] as readonly MachineId[],
  prop: {
    machine: PROP('machine'),
    reels: [PROP('r0'), PROP('r1'), PROP('r2'), PROP('r3'), PROP('r4')],
    state: PROP('state'),
    seq: PROP('seq'),
    win: PROP('win'),
    sticky: PROP('sticky'),
    hold: PROP('hold'),
    mult: PROP('mult'),
    wheel: PROP('wheel'),
    rows: [PROP('g0'), PROP('g1'), PROP('g2')],
  },
  /** `state` enum (property values, in order) */
  states: ['idle', 'spin', 'land', 'win', 'big', 'feature', 'jackpot'] as const,
  maxStop: 44,
  seqModulo: 256,
  /** strip textures: 1 wrap cell above index 0 and 2 below the last index, so every window is contiguous */
  stripPadTop: 1,
  stripPadBottom: 2,
  /** looping blur texture: `blurLoop` distinct cells + 3 wrap cells */
  blurLoop: 4,
  blurCellsPerSecond: 12,
  /** Dragon Wheel rings (SLOTS.md §3.3): segments per ring; `wheel` = ring × 100 + segment + 1 (0 = none) */
  wheelRings: [20, 16, 12],
  wheelTurns: 3,
  wheelSpinSeconds: [4.5, 4.0, 5.0],
  /** Nether ladder plates that exist on the overlay (base ×1/2/3/5, free spins ×2/4/6/10) */
  multPlates: [1, 2, 3, 4, 5, 6, 10],
  maxMult: 10,
  /** seconds: win-frame blink window, tumble pop, sticky grow, coin pop */
  winBlinkSeconds: 3,
  popSeconds: 0.3,
  stickyGrowSeconds: 0.3,
  coinPopSeconds: 0.2,
  /** land bounce (F4): arrive from −0.8 cell, overshoot ≤ 0.18 cell into the real next cell, settle by 0.35 s */
  landArriveSeconds: 0.15,
  landSettleSeconds: 0.35,
  landOvershootCells: 0.18,
  /** one-shot animations (played by the script at the REEL_LAND ticks) */
  landAnimation: (r: number): string => `animation.burmaldaholic.slot_reels.land${r}`,
  landController: (r: number): string => `burmaldaholic.slot_reels.land${r}`,
  particles: { tumble: PROP('ember_burst'), sticky: PROP('void_motes') },
  /** entity tag linking a prop to its block: `<tagPrefix><x>,<y>,<z>` */
  tagPrefix: PROP('cabinet@'),
  family: 'burmaldaholic_slot_reels',
} as const;

export type CabinetState = (typeof CABINET.states)[number];
export type PropWrite = Readonly<Record<string, number | string>>;

export const machineIndex = (m: MachineId): number => CABINET.machines.indexOf(m);

/** Win/hold mask bit of a window cell (index = reel × 3 + row, same as `Window`). */
export const cellBit = (reel: number, row: number): number => 1 << (reel * 3 + row);

/** Packs one window row (5 cells, symbol index + 1, 4 bits each; 0 = no overlay) for `g0…g2` (< 2^20). */
export function packRow(window: readonly number[], row: number): number {
  let v = 0;
  for (let r = 0; r < 5; r++) v += ((window[r * 3 + row]! + 1) & 15) * 16 ** r;
  return v;
}

/** Wheel property value for (ring, segment); 0 = none. */
export function wheelValue(ring: number, segment: number): number {
  const n = CABINET.wheelRings[ring];
  if (n === undefined || segment < 0 || segment >= n) throw new RangeError(`wheel ring ${ring} segment ${segment}`);
  return ring * 100 + segment + 1;
}

/** Clamp a Nether multiplier to a plate that exists (plates show ×1…×10; 0 hides the plate). */
export const multPlate = (mult: number): number => Math.max(0, Math.min(CABINET.maxMult, Math.round(mult)));

// ---------------------------------------------------------------------------------------------------------------
// Input: what the slots service (lane B-L8, S-B5) already knows about one presented round

export interface CabinetReelSpin {
  /** 5 stops (landed strip index of the top window cell) */
  stops: readonly number[];
  /** timeline ms when the reels start spinning (SPIN_UP / FS_SPIN beat) */
  startMs: number;
  /** timeline ms of each REEL_LAND (already includes honest anticipation — never computed here) */
  landMs: readonly number[];
  /** win mask of the first evaluation (0 = no win) */
  winMask: number;
  /** timeline ms of WIN_SHOW (defaults to the last land) */
  winShowMs?: number;
  /** Nether tumble steps: each explode beat with that step's win mask and the multiplier after it */
  tumbles?: ReadonlyArray<{ atMs: number; winMask: number; mult: number }>;
  /** Nether: final window after the chain (NICE g0…g2 overlay); omitted when there was no tumble */
  finalWindow?: readonly number[];
  /** End free spins: sticky mask after this spin (bit 0..2 = reels 2..4) and when it grows */
  stickyMask?: number;
  stickyMs?: number;
  /** multiplier shown on the plate from the spin start (Nether free spins start at ×2) */
  baseMult?: number;
}

export interface CabinetSpin {
  machine: MachineId;
  /** base spin first, then every free spin in order */
  spins: readonly CabinetReelSpin[];
  /** feature phases (state `feature` from `featureMs` until `endMs`) */
  featureMs?: number;
  /** Piglin's Hoard steps: the locked-coin mask after each step */
  hoard?: ReadonlyArray<{ atMs: number; holdMask: number }>;
  /** Dragon Wheel spins in order */
  wheel?: ReadonlyArray<{ atMs: number; ring: number; segment: number }>;
  /** celebration state: 'win' for WIN/NICE, 'big' for BIG+, 'jackpot' for any jackpot; none → 'land' */
  celebrate?: { atMs: number; state: 'win' | 'big' | 'jackpot' };
  /** END beat (timeline end) — the cabinet rests on the terminal window with its win frames */
  endMs: number;
}

export interface CabinetCue {
  at: number;
  props: PropWrite;
  /** reel indices whose land animation fires at this cue */
  land?: readonly number[];
  particle?: string;
}

/** The persistent part of the prop between spins (for seq continuity). */
export interface CabinetMemory {
  seq: number;
}

/**
 * PURE planner. Cues are sorted by time; each cue carries only the property values that the step changes.
 * The last cue is the terminal state (== the paid result), so `finish()` can apply it at once.
 */
export function planCabinet(spin: CabinetSpin, mem: CabinetMemory = { seq: 0 }): CabinetCue[] {
  const P = CABINET.prop;
  const cues: CabinetCue[] = [];
  let seq = mem.seq;
  const lastLand = (s: CabinetReelSpin): number => Math.max(...s.landMs);
  for (const s of spin.spins) {
    if (s.stops.length !== 5 || s.landMs.length !== 5) throw new Error('cabinet: a spin needs 5 stops and 5 land times');
    seq = (seq + 1) % CABINET.seqModulo;
    // spin start: stops for all 5 reels in the same tick (≤ 7 writes); the previous win frames / overlay are
    // cleared one tick later (they are hidden while `state` = spin anyway), keeping the start tick in budget
    const start: Record<string, number | string> = { [P.state]: 'spin', [P.seq]: seq };
    s.stops.forEach((t, r) => (start[P.reels[r]!] = t));
    cues.push({ at: s.startMs, props: start });
    const clear: Record<string, number | string> = { [P.win]: 0 };
    if (spin.machine === 'nether') Object.assign(clear, { [P.rows[0]]: 0, [P.rows[1]]: 0, [P.rows[2]]: 0, [P.mult]: multPlate(s.baseMult ?? 0) });
    cues.push({ at: s.startMs + 50, props: clear });
    // staggered stops: the land animation per reel at its REEL_LAND tick
    const byTime = new Map<number, number[]>();
    s.landMs.forEach((t, r) => byTime.set(t, [...(byTime.get(t) ?? []), r]));
    for (const [t, reels] of [...byTime].sort((a, b) => a[0] - b[0])) cues.push({ at: t, props: {}, land: reels });
    const landed = lastLand(s);
    cues.push({ at: landed, props: { [P.state]: 'land' } });
    if (s.winMask) cues.push({ at: s.winShowMs ?? landed, props: { [P.win]: s.winMask & 0x7fff } });
    for (const step of s.tumbles ?? [])
      cues.push({ at: step.atMs, props: { [P.win]: step.winMask & 0x7fff, [P.mult]: multPlate(step.mult) }, particle: CABINET.particles.tumble });
    if (s.finalWindow && (s.tumbles?.length ?? 0) > 0) {
      const t = s.tumbles!.at(-1)!.atMs + 750;
      cues.push({ at: t, props: { [P.rows[0]]: packRow(s.finalWindow, 0), [P.rows[1]]: packRow(s.finalWindow, 1), [P.rows[2]]: packRow(s.finalWindow, 2) } });
    }
    if (s.stickyMask !== undefined) cues.push({ at: s.stickyMs ?? landed, props: { [P.sticky]: s.stickyMask & 7 }, particle: s.stickyMask ? CABINET.particles.sticky : undefined });
  }
  if (spin.featureMs !== undefined) cues.push({ at: spin.featureMs, props: { [P.state]: 'feature' } });
  for (const h of spin.hoard ?? []) cues.push({ at: h.atMs, props: { [P.hold]: h.holdMask & 0x7fff } });
  for (const w of spin.wheel ?? []) cues.push({ at: w.atMs, props: { [P.wheel]: wheelValue(w.ring, w.segment) } });
  if (spin.celebrate) cues.push({ at: spin.celebrate.atMs, props: { [P.state]: spin.celebrate.state } });
  // terminal: rest on the final window; the win frames stay (static after the blink window) until the next spin
  const terminal: Record<string, number | string> = { [P.state]: 'idle' };
  if (spin.hoard?.length) terminal[P.hold] = 0;
  cues.push({ at: spin.endMs, props: terminal });
  const sorted = cues.map((c, i) => ({ c, i })).sort((a, b) => a.c.at - b.c.at || a.i - b.i).map((x) => x.c);
  mem.seq = seq;
  return sorted;
}

/** Everything the cues write, folded in order: the prop's state after `t` (terminal state when t = ∞). */
export function stateAt(cues: readonly CabinetCue[], t = Number.POSITIVE_INFINITY): Record<string, number | string> {
  const out: Record<string, number | string> = {};
  for (const c of cues) if (c.at <= t) Object.assign(out, c.props);
  return out;
}

/** Writes per distinct cue time (budget check helper). */
export function writesPerTick(cues: readonly CabinetCue[], msPerTick = 50): Map<number, number> {
  const m = new Map<number, number>();
  for (const c of cues) {
    const tick = Math.ceil(c.at / msPerTick);
    m.set(tick, (m.get(tick) ?? 0) + Object.keys(c.props).length);
  }
  return m;
}

// ---------------------------------------------------------------------------------------------------------------
// Player (impure edges injected)

export interface CabinetTarget {
  /** write only changed values; returns the write count */
  write(props: PropWrite): number;
  playLand(reel: number): void;
  particle(id: string): void;
  isValid(): boolean;
}

export interface CabinetClock {
  /** ms since the timeline start (shared clock: (currentTick − startTick) × 50) */
  nowMs(): number;
  /** run `fn` after `ms` (rounded up to ticks by the adapter); returns a cancel handle */
  after(ms: number, fn: () => void): () => void;
}

export interface CabinetOptions {
  /** reduce motion (the SPINNING player's setting only affects their own form; the cabinet is shared) */
  particles?: boolean;
}

/**
 * Plays planned cues on one cabinet. Cues already in the past when `play` starts (late start / catch-up) are
 * folded into one write without land animations or particles (no bursts), exactly like the scheduler.
 */
export class CabinetPlayer {
  private cancel: (() => void) | undefined;
  private next = 0;
  private done = false;

  constructor(
    private readonly cues: readonly CabinetCue[],
    private readonly target: CabinetTarget,
    private readonly clock: CabinetClock,
    private readonly opts: CabinetOptions = {},
  ) {}

  play(): this {
    const now = this.clock.nowMs();
    const past: Record<string, number | string> = {};
    while (this.next < this.cues.length && this.cues[this.next]!.at < now - 100) Object.assign(past, this.cues[this.next++]!.props);
    if (Object.keys(past).length) this.target.write(past);
    this.arm();
    return this;
  }

  /** Skip / close / disconnect: jump to the terminal state (F8). */
  finish(): void {
    if (this.done) return;
    this.cancel?.();
    const rest: Record<string, number | string> = {};
    while (this.next < this.cues.length) Object.assign(rest, this.cues[this.next++]!.props);
    if (this.target.isValid() && Object.keys(rest).length) this.target.write(rest);
    this.done = true;
  }

  isDone(): boolean {
    return this.done;
  }

  private arm(): void {
    if (this.next >= this.cues.length) {
      this.done = true;
      return;
    }
    const at = this.cues[this.next]!.at;
    this.cancel = this.clock.after(Math.max(0, at - this.clock.nowMs()), () => this.fire(at));
  }

  private fire(at: number): void {
    if (this.done) return;
    if (!this.target.isValid()) {
      this.done = true;
      return;
    }
    const props: Record<string, number | string> = {};
    const lands: number[] = [];
    const particles: string[] = [];
    while (this.next < this.cues.length && this.cues[this.next]!.at <= at) {
      const c = this.cues[this.next++]!;
      Object.assign(props, c.props);
      if (c.land) lands.push(...c.land);
      if (c.particle) particles.push(c.particle);
    }
    if (Object.keys(props).length) this.target.write(props);
    for (const r of lands) this.target.playLand(r);
    if (this.opts.particles !== false) for (const p of new Set(particles)) this.target.particle(p);
    this.arm();
  }
}

/** Beats → land times, when the service has a built `SlotTimeline` (REEL_LAND beats carry the reel in `lane`). */
export function landTimesFromBeats(beats: ReadonlyArray<{ at: number; kind: string; lane: number }>, from: number, to = Number.POSITIVE_INFINITY): number[] {
  const out: number[] = [];
  for (const b of beats) if (b.kind === 'slots.reel_land' && b.at >= from && b.at < to && b.lane >= 0 && b.lane < 5 && out[b.lane] === undefined) out[b.lane] = b.at;
  return out;
}
