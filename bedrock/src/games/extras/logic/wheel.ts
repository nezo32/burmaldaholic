/**
 * Wheel of Fortune (GAME_DESIGN §11.2, Appendix B). PURE.
 * 54 segments in a fixed order (index 0 at the pointer, clockwise); a spin lands uniformly on
 * one index and pays floor(stake × multiplier). The Creeper segment pays 0 and triggers the
 * chaos `mob_wave` event.
 */
import { type Rng } from '../../../core/logic/rng';
import { floorPay } from './payout';

export type WheelCode = 'B' | 'C' | 'H' | 'M' | 'D' | 'T' | 'E' | 'X';
export const WHEEL_CODES: readonly WheelCode[] = ['B', 'C', 'H', 'M', 'D', 'T', 'E', 'X'];

/** Appendix B, index 0…53 clockwise from the pointer at rest. */
export const DEFAULT_SEGMENTS: readonly WheelCode[] = (
  'X B M B D B M B H B D B M B T B M B D B H B M B D B E ' + 'B M B D B H B M B T B M B D B H B M B T B C M H D M B'
).split(' ') as WheelCode[];

export const DEFAULT_MULTIPLIERS: Readonly<Record<WheelCode, number>> = { B: 0, C: 0, H: 0.5, M: 1, D: 2, T: 3, E: 5, X: 10 };

/** Lang key suffix per code: `gui.burmaldaholic.extras.wheel.segment.<name>`. */
export const SEGMENT_NAMES: Readonly<Record<WheelCode, string>> = {
  B: 'bust',
  C: 'creeper',
  H: 'half',
  M: 'money_back',
  D: 'double',
  T: 'triple',
  E: 'emerald',
  X: 'diamond',
};
export const segmentKey = (c: WheelCode): string => `gui.burmaldaholic.extras.wheel.segment.${SEGMENT_NAMES[c]}`;

/** Color per segment (UI.md: loss §c, push §7, win §a, jackpot §6). */
export const SEGMENT_COLORS: Readonly<Record<WheelCode, string>> = { B: '§c', C: '§2', H: '§c', M: '§7', D: '§a', T: '§a', E: '§6', X: '§b' };

export interface WheelSetup {
  segments: readonly WheelCode[];
  multipliers: Readonly<Record<WheelCode, number>>;
}

export const DEFAULT_WHEEL: WheelSetup = { segments: DEFAULT_SEGMENTS, multipliers: DEFAULT_MULTIPLIERS };

const isCode = (x: unknown): x is WheelCode => typeof x === 'string' && (WHEEL_CODES as readonly string[]).includes(x);

/**
 * Build a wheel from config values (`extras.wheel.segments`, `extras.wheel.multipliers`).
 * Unknown codes are dropped; a wheel with fewer than 2 valid segments falls back to Appendix B.
 * Missing multipliers use the defaults; negative / non-finite ones become 0.
 */
export function wheelFromConfig(segments: unknown, multipliers: unknown): WheelSetup {
  const segs = Array.isArray(segments) ? segments.filter(isCode) : [];
  const mult: Record<WheelCode, number> = { ...DEFAULT_MULTIPLIERS };
  if (multipliers && typeof multipliers === 'object') {
    for (const [k, v] of Object.entries(multipliers as Record<string, unknown>)) {
      if (isCode(k)) mult[k] = typeof v === 'number' && Number.isFinite(v) && v > 0 ? v : 0;
    }
  }
  return { segments: segs.length >= 2 ? segs : DEFAULT_SEGMENTS, multipliers: mult };
}

export interface WheelSpin {
  index: number;
  code: WheelCode;
  multiplier: number;
}

export function spinWheel(rng: Rng, w: WheelSetup = DEFAULT_WHEEL): WheelSpin {
  const index = Math.floor(rng.next() * w.segments.length);
  const code = w.segments[index] as WheelCode;
  return { index, code, multiplier: w.multipliers[code] };
}

/** Total return (stake included). */
export const wheelReturn = (stake: number, spin: Pick<WheelSpin, 'multiplier'>): number => floorPay(stake, spin.multiplier);

/** Segment counts in wheel order of first appearance. */
export function segmentCounts(w: WheelSetup = DEFAULT_WHEEL): Map<WheelCode, number> {
  const m = new Map<WheelCode, number>();
  for (const c of w.segments) m.set(c, (m.get(c) ?? 0) + 1);
  return m;
}

/** Legend rows ordered by multiplier (bust first), for the form body. */
export function wheelLegend(w: WheelSetup = DEFAULT_WHEEL): { code: WheelCode; multiplier: number; count: number }[] {
  const counts = segmentCounts(w);
  return WHEEL_CODES.filter((c) => counts.has(c))
    .map((c) => ({ code: c, multiplier: w.multipliers[c], count: counts.get(c) ?? 0 }))
    .sort((a, b) => a.multiplier - b.multiplier || (a.code === 'C' ? 1 : 0) - (b.code === 'C' ? 1 : 0));
}

/** Exact RTP (before flooring): mean multiplier over the segments. */
export function wheelRtp(w: WheelSetup = DEFAULT_WHEEL): number {
  return w.segments.reduce((s, c) => s + w.multipliers[c], 0) / w.segments.length;
}

export const maxWheelMultiplier = (w: WheelSetup = DEFAULT_WHEEL): number => Math.max(...w.segments.map((c) => w.multipliers[c]));

/**
 * Pointer path for the spin animation: `turns` full turns then decelerate onto `target`.
 * Returns the indices to show (last = target) and the tick delay before each frame
 * (growing, so the wheel slows down). Deterministic.
 */
export function spinFrames(target: number, size: number, frames = 24, turns = 1): { index: number; delay: number }[] {
  const n = Math.max(2, size);
  const total = turns * n + (((target % n) + n) % n);
  const out: { index: number; delay: number }[] = [];
  for (let f = 1; f <= frames; f++) {
    // ease-out: position = total × (1 − (1 − f/frames)²)
    const x = f / frames;
    const pos = Math.round(total * (1 - (1 - x) * (1 - x)));
    out.push({ index: pos % n, delay: 1 + Math.floor(5 * x * x) });
  }
  out[out.length - 1] = { index: ((target % n) + n) % n, delay: out[out.length - 1]?.delay ?? 6 };
  return out;
}
