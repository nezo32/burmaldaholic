/**
 * Presentation runtime (docs/architecture/animation.md §2.7): plays a `Timeline` for a set of viewers with
 * ONE `system.runInterval` per session (never one per player or per beat), under a per-tick time budget.
 *
 * - Beat start ticks are `ceilTicks(beat.at)` from the session start tick (Bedrock rounds up to 50 ms).
 * - Every `periodTicks` (default 2, the DDUI/action-bar cadence) the sink gets `frame(tMs)` for continuous
 *   beats (reel scroll, roll-up) and `beat(b)` once when a beat starts.
 * - `skip()` jumps to the end of the current LOCAL skip group; shared beats are never skipped by a viewer.
 * - The session stops itself when the timeline ends, `alive()` returns false (form closed, player left) or
 *   `stop()` is called; `onEnd` then receives the terminal time (fidelity F8: interrupt = reveal).
 *
 * SKELETON: complete and dependency-light, not used by any game yet.
 */
import { system } from '@minecraft/server';
import { type Beat, LOCAL, type Timeline, ceilTicks } from '../logic/anim/timeline';

export interface TimelineSink {
  /** A beat starts (sounds, particles, entity property writes, titles). */
  beat?(b: Beat, tMs: number): void;
  /** Continuous frame at timeline time `tMs` (write only what changed). */
  frame?(tMs: number): void;
  /** Terminal state: the timeline ended, was skipped to the end, or was interrupted. */
  end?(tMs: number, interrupted: boolean): void;
}

export interface PlayOptions {
  /** Frame cadence in ticks (default 2). */
  periodTicks?: number;
  /** Returning false stops the session (e.g. `() => form.isShowing()`). */
  alive?: () => boolean;
  /** Warn when a frame exceeds this many ms (default 0.3, global.md §2.9). */
  budgetMs?: number;
  /** Start `startMs` into the timeline (late join / catch-up). */
  startMs?: number;
}

export interface PresentationSession {
  readonly timeline: Timeline;
  skip(): void;
  stop(): void;
  isRunning(): boolean;
}

export function playTimeline(timeline: Timeline, sink: TimelineSink, opts: PlayOptions = {}): PresentationSession {
  const period = Math.max(1, opts.periodTicks ?? 2);
  const budget = opts.budgetMs ?? 0.3;
  const end = timeline.endMs();
  const t0 = system.currentTick - ceilTicks(opts.startMs ?? 0);
  let offsetMs = 0;
  let next = 0; // index of the next beat to start
  let running = true;

  const finish = (interrupted: boolean): void => {
    if (!running) return;
    running = false;
    system.clearRun(runId);
    sink.end?.(end, interrupted);
  };

  const tick = (): void => {
    if (!running) return;
    if (opts.alive && !opts.alive()) return finish(true);
    const started = Date.now();
    const t = Math.min(end, (system.currentTick - t0) * 50 + offsetMs);
    const beats = timeline.beats;
    while (next < beats.length && beats[next]!.at <= t) {
      const b = beats[next++]!;
      // beats passed over by a skip or a late start are not replayed (no sound bursts): only recent ones fire
      if (t - b.at <= 100) sink.beat?.(b, t);
    }
    sink.frame?.(t);
    if (Date.now() - started > budget) console.warn(`[presentation] ${timeline.game}: frame over budget`);
    if (t >= end) finish(false);
  };

  const runId = system.runInterval(tick, period);
  return {
    timeline,
    skip(): void {
      const t = (system.currentTick - t0) * 50 + offsetMs;
      const g = timeline.groupAt(t);
      if (g < 0) return;
      if (timeline.beats.some((b) => b.group === g && b.clock !== LOCAL && b.at + b.dur > t)) return;
      offsetMs += Math.max(0, timeline.groupEnd(g) - t);
    },
    stop: () => finish(true),
    isRunning: () => running,
  };
}
