/**
 * Presentation runtime (docs/architecture/animation.md §2.7): plays a `Timeline` for a set of viewers with
 * ONE `system.runInterval` per session (never one per player or per beat), under a per-tick time budget.
 *
 * - Beat start ticks are `ceilTicks(beat.at)` from the session start tick (Bedrock rounds up to 50 ms).
 * - Every `periodTicks` (default 2, the DDUI/action-bar cadence) the sink gets `beat(b)` once for every beat that
 *   started since the last frame (in order, also when the server lagged), then `frame(tMs)`.
 * - Beats passed over by a skip or a late start are NOT replayed (no sound bursts); the sink's `frame`/`end` draw the
 *   state for the new time instead.
 * - `skip()` jumps to the end of the current LOCAL skip group; shared beats are never skipped by a viewer.
 *   `finishNow()` jumps to the terminal state (overrun rule: a new round finishes the old player first).
 * - The session stops itself when the timeline ends, `alive()` returns false (form closed, player left) or
 *   `stop()` is called; `end` then receives the terminal time (fidelity F8: interrupt = reveal).
 */
import { system } from '@minecraft/server';
import { type Beat, LOCAL, type Timeline, ceilTicks } from '../logic/anim/timeline';

export interface TimelineSink {
  /** A beat starts (sounds, particles, entity property writes, titles). */
  beat?(b: Beat, tMs: number): void;
  /** Continuous frame at timeline time `tMs` (write only what changed). */
  frame?(tMs: number): void;
  /** Terminal state: the timeline ended, was skipped to the end, or was interrupted. Called exactly once. */
  end?(tMs: number, interrupted: boolean): void;
}

export interface PlayOptions {
  /** Frame cadence in ticks (default 2). */
  periodTicks?: number;
  /** Returning false stops the session (e.g. `() => form.isShowing()`). */
  alive?: () => boolean;
  /** Called every frame before the beats; returning true skips (e.g. `() => player.isSneaking`). */
  skipWhen?: () => boolean;
  /** Warn when a frame exceeds this many ms (default 0.3, global.md §2.9). */
  budgetMs?: number;
  /** Start `startMs` into the timeline (late join / catch-up): earlier beats do not fire. */
  startMs?: number;
}

export interface PresentationSession {
  readonly timeline: Timeline;
  /** Current timeline time (ms). */
  now(): number;
  /** Skip the current LOCAL group (no-op while a shared beat of the group still runs). */
  skip(): void;
  /** Jump to the end: `end(t, false)` with the terminal state, no replay of the skipped beats. */
  finishNow(): void;
  /** Interrupt: `end(t, true)` (the sink still draws the terminal state). */
  stop(): void;
  isRunning(): boolean;
}

export function playTimeline(timeline: Timeline, sink: TimelineSink, opts: PlayOptions = {}): PresentationSession {
  const period = Math.max(1, opts.periodTicks ?? 2);
  const budget = opts.budgetMs ?? 0.3;
  const end = timeline.endMs();
  const t0 = system.currentTick - ceilTicks(opts.startMs ?? 0);
  const beats = timeline.beats;
  let offsetMs = 0;
  let next = 0; // index of the next beat to start
  let running = true;
  let runId = 0;

  const now = (): number => Math.min(end, (system.currentTick - t0) * 50 + offsetMs);
  /** Beats before `t` are passed over silently (skip / late start). */
  const passOver = (t: number): void => {
    while (next < beats.length && beats[next]!.at < t) next++;
  };
  passOver(opts.startMs ?? 0);

  const finish = (interrupted: boolean): void => {
    if (!running) return;
    running = false;
    if (runId) system.clearRun(runId);
    try {
      sink.end?.(end, interrupted);
    } catch (e) {
      console.warn(`[presentation] ${timeline.game}: end failed ${String(e)}`);
    }
  };

  const tick = (): void => {
    if (!running) return;
    const started = Date.now();
    try {
      if (opts.alive && !opts.alive()) return finish(true);
      if (opts.skipWhen?.()) skip();
      const t = now();
      while (next < beats.length && beats[next]!.at <= t) {
        const b = beats[next++]!; // outside the optional call: `sink.beat?.(x++)` would not evaluate x++
        sink.beat?.(b, t);
      }
      sink.frame?.(t);
      if (t >= end) finish(false);
    } catch (e) {
      console.warn(`[presentation] ${timeline.game}: frame failed ${String(e)}`);
      finish(true);
    }
    if (Date.now() - started > budget) console.warn(`[presentation] ${timeline.game}: frame over budget`);
  };

  function skip(): void {
    if (!running) return;
    const t = now();
    const g = timeline.groupAt(t);
    if (g < 0) return;
    if (beats.some((b) => b.group === g && b.clock !== LOCAL && b.at + b.dur > t)) return;
    const target = timeline.groupEnd(g);
    offsetMs += Math.max(0, target - t);
    passOver(target);
  }

  runId = system.runInterval(tick, period);
  return {
    timeline,
    now,
    skip,
    finishNow: () => {
      passOver(end + 1);
      finish(false);
    },
    stop: () => finish(true),
    isRunning: () => running,
  };
}
