/**
 * Bedrock sound planning (docs/architecture/animation.md §2.4, global.md §2.7, cards.md §7, tables.md §0.8). PURE.
 *
 * - A Bedrock `sound_definitions.json` entry with several files picks ONE at random (variants); a "+" composite of
 *   the specs is therefore a second definition `burmaldaholic.<id>.l<n>` played in the same tick (or a few ticks
 *   later for arpeggios / knocks). `SOUND_LAYERS` lists those extra layers; the definitions live in
 *   `packs/core/RP/sounds/sound_definitions.json` (a test checks both agree).
 * - `planSoundTimeline` turns a game's sound events (ms from a start) into tick slots for one `system.runTimeout`
 *   chain: sorted, at most one call per id per tick, at most `maxCalls` calls (the tables budget is 12 per spin;
 *   the last event — the landing — is always kept).
 */
import { ceilTicks } from './timeline';

export interface SoundLayer {
  /** Layer number (definition `burmaldaholic.<id>.l<n>`). */
  readonly n: number;
  /** Ticks after the primary sound. */
  readonly delayTicks: number;
}

/** Extra layers per core-owned id (the primary `burmaldaholic.<id>` always plays first). */
export const SOUND_LAYERS: Readonly<Record<string, readonly SoundLayer[]>> = {
  win_small: [{ n: 2, delayTicks: 0 }],
  win_nice: [
    { n: 2, delayTicks: 2 },
    { n: 3, delayTicks: 3 },
    { n: 4, delayTicks: 0 },
  ],
  win_big: [{ n: 2, delayTicks: 0 }],
  win_mega: [{ n: 2, delayTicks: 0 }],
  jackpot: [
    { n: 2, delayTicks: 0 },
    { n: 3, delayTicks: 4 },
  ],
  card_sting: [
    { n: 2, delayTicks: 2 },
    { n: 3, delayTicks: 3 },
  ],
  table_knock: [{ n: 2, delayTicks: 2 }],
  chip_push: [{ n: 2, delayTicks: 0 }],
  pot_win: [{ n: 2, delayTicks: 0 }],
  collector_arrive: [{ n: 2, delayTicks: 0 }],
  wheel_stop: [{ n: 2, delayTicks: 0 }],
  burn: [{ n: 2, delayTicks: 30 }],
};

export const layerSoundId = (id: string, n: number): string => `${id}.l${n}`;

/** Every definition id (without namespace) a core sound needs: the id itself plus its layers. */
export function definitionIds(id: string): string[] {
  return [id, ...(SOUND_LAYERS[id] ?? []).map((l) => layerSoundId(id, l.n))];
}

/** One scheduled sound of a game timeline. */
export interface SoundEvent {
  /** ms from the timeline start */
  readonly at: number;
  /** catalog id without namespace (`roulette_ball_bounce`) */
  readonly id: string;
  readonly pitch?: number;
  readonly volume?: number;
}

export interface SoundSlot {
  readonly tick: number;
  readonly id: string;
  readonly pitch: number;
  readonly volume: number;
}

/** Default tables budget: ≤ 12 `playSound` calls per spin (tables.md §0.7). */
export const TABLE_SOUND_BUDGET = 12;

export function planSoundTimeline(events: readonly SoundEvent[], maxCalls = TABLE_SOUND_BUDGET): SoundSlot[] {
  const sorted = events
    .map((e, i) => ({ e, i }))
    .sort((a, b) => a.e.at - b.e.at || a.i - b.i)
    .map(({ e }) => ({ tick: ceilTicks(Math.max(0, e.at)), id: e.id, pitch: e.pitch ?? 1, volume: e.volume ?? 1 }));
  const out: SoundSlot[] = [];
  for (const s of sorted) if (!out.some((o) => o.tick === s.tick && o.id === s.id)) out.push(s);
  if (out.length <= maxCalls) return out;
  if (maxCalls <= 0) return [];
  // keep an even spread plus the final event (the landing / settle is the important one)
  const last = out[out.length - 1]!;
  const kept: SoundSlot[] = [];
  const step = (out.length - 1) / Math.max(1, maxCalls - 1);
  for (let k = 0; k < maxCalls - 1; k++) kept.push(out[Math.floor(k * step)]!);
  kept.push(last);
  return kept.filter((s, i) => kept.indexOf(s) === i);
}

/** Title / action-bar flipbook frames (extras-pvp.md BX1): ≥ 2 t apart, ≤ 18 frames, reduce motion = last only. */
export interface FlipFrame {
  readonly tick: number;
  readonly frame: number;
}

export const MAX_FLIP_FRAMES = 18;

export function planFlipbook(frameCount: number, frameTicks: number, reduceMotion: boolean): FlipFrame[] {
  const n = Math.max(0, Math.min(MAX_FLIP_FRAMES, Math.floor(frameCount)));
  if (n === 0) return [];
  if (reduceMotion) return [{ tick: 0, frame: n - 1 }];
  const step = Math.max(2, Math.floor(frameTicks));
  return Array.from({ length: n }, (_, i) => ({ tick: i * step, frame: i }));
}
