/**
 * Table FX helpers (tables.md B-A0, docs/architecture/animation.md §2.7): one-shot beats that need exact ticks without
 * a presentation session.
 *
 * - `soundTimeline(dim, pos, events)` — a game's in-world sound events (ms from now, from the same pure path the
 *   entity animates) as ONE `system.runTimeout` chain, ≤ 12 calls per spin (tables.md §0.7), positional per viewer.
 * - `revealAfter(ticks, fn)` — text trails the animation (tables.md §0.6.3): results, chat lines, forms at the gate.
 */
import { type Dimension, type Vector3, system } from '@minecraft/server';
import { type SoundEvent, type SoundSlot, TABLE_SOUND_BUDGET, planSoundTimeline } from '../logic/anim/sound-plan';
import { playAt } from './sound';

/** Runs `fn` after `ticks` (at once when ≤ 0). Returns a cancel function (idempotent). */
export function revealAfter(ticks: number, fn: () => void): () => void {
  if (ticks <= 0) {
    fn();
    return () => {};
  }
  let fired = false;
  const id = system.runTimeout(() => {
    fired = true;
    fn();
  }, Math.ceil(ticks));
  return () => {
    if (!fired) system.clearRun(id);
    fired = true;
  };
}

export interface SoundTimelineOptions {
  /** Hearing radius (default 24 blocks). */
  radius?: number;
  /** Call budget (default 12). */
  maxCalls?: number;
  /** Returning false stops the chain (table removed, round cancelled). */
  alive?: () => boolean;
}

export interface SoundTimelineHandle {
  /** The planned slots (after dedupe and budget). */
  readonly slots: readonly SoundSlot[];
  cancel(): void;
}

/** Plays `events` at `pos` with one `runTimeout` chain (no per-tick loop). */
export function soundTimeline(dim: Dimension, pos: Vector3, events: readonly SoundEvent[], opts: SoundTimelineOptions = {}): SoundTimelineHandle {
  const slots = planSoundTimeline(events, opts.maxCalls ?? TABLE_SOUND_BUDGET);
  const start = system.currentTick;
  let i = 0;
  let id: number | undefined;
  let cancelled = false;
  const step = (): void => {
    id = undefined;
    if (cancelled) return;
    if (opts.alive && !opts.alive()) return;
    const tick = slots[i]?.tick;
    while (i < slots.length && slots[i]!.tick === tick) {
      const s = slots[i++]!;
      playAt(dim, pos, s.id, { pitch: s.pitch, volume: s.volume }, opts.radius ?? 24);
    }
    schedule();
  };
  const schedule = (): void => {
    if (i >= slots.length) return;
    const wait = start + slots[i]!.tick - system.currentTick;
    if (wait <= 0) step();
    else id = system.runTimeout(step, wait);
  };
  schedule();
  return {
    slots,
    cancel: () => {
      cancelled = true;
      if (id !== undefined) system.clearRun(id);
    },
  };
}
