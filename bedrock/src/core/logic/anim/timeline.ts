/**
 * The cross-edition presentation timeline ("PTL", docs/architecture/animation.md §3). Twin of Java
 * `core.anim.{Beat, Timeline, TimelineBuilder, TimingProfile}`: same method names, integer milliseconds, the
 * same canonical JSON — both editions must build byte-identical timelines from the same outcome.
 * Bedrock converts to ticks only when scheduling (`ceilTicks`), never inside the timeline.
 */
export type ClockCode = 'S' | 'L';
/** Shared (server-paced, same tick for every viewer) or local (per viewer: speed, skip). */
export const SHARED: ClockCode = 'S';
export const LOCAL: ClockCode = 'L';

export interface Beat {
  readonly at: number;
  readonly dur: number;
  readonly kind: string;
  readonly lane: number;
  readonly group: number;
  readonly clock: ClockCode;
  readonly args: readonly number[];
}

export const beatEnd = (b: Beat): number => b.at + b.dur;
export const beatActiveAt = (b: Beat, t: number): boolean => t >= b.at && t < b.at + Math.max(b.dur, 1);

/** Progress in [0, 1] (1 after the end or for a cue at/after its time). */
export function beatProgress(b: Beat, t: number): number {
  if (t <= b.at) return b.dur === 0 && t === b.at ? 1 : 0;
  if (b.dur === 0 || t >= b.at + b.dur) return 1;
  return (t - b.at) / b.dur;
}

export function beatJson(b: Beat): string {
  return `{"at":${b.at},"dur":${b.dur},"kind":"${b.kind}","lane":${b.lane},"group":${b.group},"clock":"${b.clock}","args":[${b.args.join(',')}]}`;
}

export const TIMELINE_VERSION = 1;

export class Timeline {
  constructor(
    readonly game: string,
    readonly seed: number,
    readonly beats: readonly Beat[],
  ) {}

  static builder(game: string, seed: number): TimelineBuilder {
    return new TimelineBuilder(game, seed);
  }

  endMs(): number {
    let end = 0;
    for (const b of this.beats) end = Math.max(end, beatEnd(b));
    return end;
  }

  /** Reveal gate: end of the last shared beat. */
  sharedEndMs(): number {
    let end = 0;
    for (const b of this.beats) if (b.clock === SHARED) end = Math.max(end, beatEnd(b));
    return end;
  }

  groupEnd(group: number): number {
    let end = -1;
    for (const b of this.beats) if (b.group === group) end = Math.max(end, beatEnd(b));
    return end;
  }

  groupAt(t: number): number {
    let g = -1;
    for (const b of this.beats) if (beatActiveAt(b, t)) g = b.group;
    return g;
  }

  forEachActive(t: number, visit: (b: Beat) => void): void {
    for (const b of this.beats) {
      if (b.at > t) break;
      if (beatActiveAt(b, t)) visit(b);
    }
  }

  sharedEndTicks(): number {
    return ceilTicks(this.sharedEndMs());
  }

  toCanonicalJson(): string {
    return `{"v":${TIMELINE_VERSION},"game":"${this.game}","seed":${this.seed | 0},"beats":[${this.beats.map(beatJson).join(',')}]}`;
  }
}

export const ceilTicks = (ms: number): number => Math.floor((ms + 49) / 50);

export class TimelineBuilder {
  private readonly beats: Beat[] = [];
  private clockCode: ClockCode = SHARED;
  private groupId = 0;
  private cur = 0;

  constructor(
    private readonly game: string,
    private readonly seed: number,
  ) {}

  clock(c: ClockCode): this {
    this.clockCode = c;
    return this;
  }
  group(g: number): this {
    this.groupId = g;
    return this;
  }
  cursor(): number {
    return this.cur;
  }
  setCursor(ms: number): this {
    if (ms < 0) throw new RangeError('cursor < 0');
    this.cur = ms;
    return this;
  }
  add(at: number, dur: number, kind: string, lane: number, ...args: number[]): this {
    if (at < 0 || dur < 0) throw new RangeError(`negative time in beat ${kind}`);
    this.beats.push({ at, dur, kind, lane, group: this.groupId, clock: this.clockCode, args });
    this.cur = Math.max(this.cur, at + dur);
    return this;
  }
  then(dur: number, kind: string, lane: number, ...args: number[]): this {
    return this.add(this.cur, dur, kind, lane, ...args);
  }
  cue(kind: string, lane: number, ...args: number[]): this {
    this.beats.push({ at: this.cur, dur: 0, kind, lane, group: this.groupId, clock: this.clockCode, args });
    return this;
  }
  pause(ms: number): this {
    this.cur += ms;
    return this;
  }
  build(): Timeline {
    // Array.prototype.sort is stable (ES2019+), like Java List.sort.
    const sorted = this.beats.map((b, i) => ({ b, i })).sort((x, y) => x.b.at - y.b.at || x.i - y.i).map((x) => x.b);
    return new Timeline(this.game, this.seed | 0, sorted);
  }
}

/** Presentation speed/motion preferences (global.md §2.8); integer scaling identical to Java. */
export interface TimingProfile {
  readonly speedPct: number;
  readonly reduceMotion: boolean;
  readonly flashes: boolean;
}

export const SHARED_PROFILE: TimingProfile = { speedPct: 100, reduceMotion: false, flashes: true };

export const scaleMs = (p: TimingProfile, ms: number): number => Math.floor((ms * 100) / p.speedPct);

/** Catch-up rule (tables.md §0.1): arriving after 85 % of the shared part shows the settled state. */
export const showSettled = (elapsedMs: number, sharedEndMs: number): boolean => sharedEndMs <= 0 || elapsedMs >= 0.85 * sharedEndMs;

/**
 * A game's pure frame sampler (docs/architecture/animation.md §3.6); twin of Java `core.anim.FrameModel`.
 * Fidelity tests: `equal(frame(o, tl, tl.endMs()), terminal(o))` for every vector, also for skip / reduce motion.
 */
export interface FrameModel<O, F> {
  frame(outcome: O, timeline: Timeline, tMs: number): F;
  terminal(outcome: O): F;
}
