/**
 * Chaos event catalog and selection rules (GAME_DESIGN §13.1-§13.2). PURE.
 */
import { type Rng, randInt, weightedPick } from '../../core/logic/rng';

export const CHAOS_EVENTS = [
  'chip_shower',
  'lucky_buff',
  'diamond_rain',
  'xp_fountain',
  'curse',
  'mob_wave',
  'random_teleport',
  'weather_change',
  'golden_hour',
] as const;
export type ChaosEventId = (typeof CHAOS_EVENTS)[number];

export type EventKind = 'good' | 'bad' | 'neutral';

export const EVENT_KIND: Readonly<Record<ChaosEventId, EventKind>> = {
  chip_shower: 'good',
  lucky_buff: 'good',
  diamond_rain: 'good',
  xp_fountain: 'good',
  curse: 'bad',
  mob_wave: 'bad',
  random_teleport: 'neutral',
  weather_change: 'neutral',
  golden_hour: 'good',
};

/** Default ambient weights (§13.2; overridable by chaos.weight.<event>). */
export const DEFAULT_WEIGHTS: Readonly<Record<ChaosEventId, number>> = {
  chip_shower: 18,
  lucky_buff: 20,
  diamond_rain: 4,
  xp_fountain: 12,
  curse: 16,
  mob_wave: 12,
  random_teleport: 8,
  weather_change: 8,
  golden_hour: 2,
};

export const isChaosEvent = (s: string): s is ChaosEventId => (CHAOS_EVENTS as readonly string[]).includes(s);

export type Weights = Partial<Record<ChaosEventId, number>>;

/** Weighted pick among events with weight > 0 that are not excluded; undefined if none. */
export function pickEvent(rng: Rng, weights: Weights, exclude: readonly ChaosEventId[] = [], only?: (e: ChaosEventId) => boolean): ChaosEventId | undefined {
  const entries: [ChaosEventId, number][] = [];
  for (const e of CHAOS_EVENTS) {
    const w = weights[e] ?? 0;
    if (w > 0 && !exclude.includes(e) && (!only || only(e))) entries.push([e, w]);
  }
  return entries.length ? weightedPick(rng, entries) : undefined;
}

/**
 * Event actually run for a requested one, applying the §13.4 reroll rules:
 * - `mob_wave` in Peaceful is rerolled from the remaining events (ambient weights);
 * - `weather_change` outside the Overworld is rerolled once from the good events.
 * Golden Hour is never a reroll result (it is server-wide and has its own cooldown).
 */
export function resolveEvent(
  rng: Rng,
  requested: ChaosEventId,
  env: { peaceful: boolean; overworld: boolean },
  weights: Weights,
): ChaosEventId | undefined {
  const noGh: ChaosEventId[] = ['golden_hour'];
  if (requested === 'mob_wave' && env.peaceful) {
    const e = pickEvent(rng, weights, [...noGh, 'mob_wave', ...(env.overworld ? [] : (['weather_change'] as ChaosEventId[]))]);
    return e;
  }
  if (requested === 'weather_change' && !env.overworld) {
    return pickEvent(rng, weights, noGh, (e) => EVENT_KIND[e] === 'good');
  }
  return requested;
}

// ---- buffs / curses ----------------------------------------------------------------------

export interface EffectSpec {
  /** Bedrock effect id */
  id: string;
  /** 0-based amplifier (Speed II = 1) */
  amplifier: number;
  /** name key suffix: gui.burmaldaholic.chaos.effect.<name> */
  name: string;
}

/**
 * §13.2 lucky_buff. Bedrock has no Luck effect: "Luck I" is replaced by Night Vision (harmless).
 */
export const BUFFS: readonly EffectSpec[] = [
  { id: 'speed', amplifier: 1, name: 'speed_2' },
  { id: 'haste', amplifier: 1, name: 'haste_2' },
  { id: 'regeneration', amplifier: 0, name: 'regeneration_1' },
  { id: 'strength', amplifier: 0, name: 'strength_1' },
  { id: 'night_vision', amplifier: 0, name: 'night_vision_1' },
  { id: 'jump_boost', amplifier: 1, name: 'jump_boost_2' },
  { id: 'fire_resistance', amplifier: 0, name: 'fire_resistance_1' },
];

/**
 * §13.2 curse. Bedrock has no Bad Luck or Glowing effects: Bad Luck is replaced by Nausea,
 * Glowing is dropped. All non-lethal (§13.4).
 */
export const CURSES: readonly EffectSpec[] = [
  { id: 'slowness', amplifier: 0, name: 'slowness_1' },
  { id: 'mining_fatigue', amplifier: 0, name: 'mining_fatigue_1' },
  { id: 'hunger', amplifier: 0, name: 'hunger_1' },
  { id: 'weakness', amplifier: 0, name: 'weakness_1' },
  { id: 'nausea', amplifier: 0, name: 'nausea_1' },
];

/** Effects that may never be applied by chaos (§13.4). */
export const LETHAL_EFFECTS: readonly string[] = ['poison', 'fatal_poison', 'wither', 'instant_damage', 'levitation'];

/** Random integer in [min, max], tolerating min > max (swapped) and negatives (clamped at 0). */
export function rollRange(rng: Rng, min: number, max: number): number {
  const lo = Math.max(0, Math.floor(Math.min(min, max)));
  const hi = Math.max(0, Math.floor(Math.max(min, max)));
  return randInt(rng, lo, hi);
}

export function rollEffect(rng: Rng, pool: readonly EffectSpec[], minTicks: number, maxTicks: number): { effect: EffectSpec; ticks: number } {
  const effect = pool[Math.floor(rng.next() * pool.length)] as EffectSpec;
  return { effect, ticks: Math.max(20, rollRange(rng, minTicks, maxTicks)) };
}

// ---- weather -----------------------------------------------------------------------------

export type Weather = 'Clear' | 'Rain' | 'Thunder';

/** clear → rain, rain → thunder, thunder → clear (§13.2). */
export function nextWeather(w: Weather): Weather {
  return w === 'Clear' ? 'Rain' : w === 'Rain' ? 'Thunder' : 'Clear';
}

// ---- amounts -----------------------------------------------------------------------------

export type DifficultyName = 'Peaceful' | 'Easy' | 'Normal' | 'Hard';

/** Mob wave size (§2.3): Peaceful never; Hardcore uses the Hard value. */
export function mobWaveSize(d: DifficultyName, hardcore: boolean, cfg: { easy: number; normal: number; hard: number }): number {
  if (hardcore) return Math.max(0, cfg.hard);
  switch (d) {
    case 'Peaceful':
      return 0;
    case 'Easy':
      return Math.max(0, cfg.easy);
    case 'Normal':
      return Math.max(0, cfg.normal);
    case 'Hard':
      return Math.max(0, cfg.hard);
  }
}

/** Diamond count: Hard/Hardcore get one less at both ends (§13.2 "Hard/Hardcore: 2-5"). */
export function diamondRainCount(rng: Rng, min: number, max: number, hard: boolean): number {
  const d = hard ? 1 : 0;
  return rollRange(rng, Math.max(0, min - d), Math.max(0, max - d));
}

export type DimensionName = 'overworld' | 'nether' | 'the_end';

/** Mob-wave composition by dimension (§13.2). Never creepers. */
export const WAVE_MOBS: Readonly<Record<DimensionName, readonly string[]>> = {
  overworld: ['minecraft:zombie', 'minecraft:skeleton', 'minecraft:spider'],
  nether: ['minecraft:skeleton', 'minecraft:magma_cube'],
  the_end: ['minecraft:endermite', 'minecraft:skeleton'],
};

/** n mobs, types in equal proportion (round-robin from a random start). */
export function mobWaveComposition(rng: Rng, dim: DimensionName, n: number): string[] {
  const pool = WAVE_MOBS[dim];
  const start = Math.floor(rng.next() * pool.length);
  const out: string[] = [];
  for (let i = 0; i < n; i++) out.push(pool[(start + i) % pool.length] as string);
  return out;
}

/** Split a chip-shower amount into chip_5 and chip_1 items (a random mix, total exact). */
export function chipShowerSplit(rng: Rng, amount: number): { fives: number; ones: number } {
  const total = Math.max(0, Math.floor(amount));
  const maxFives = Math.floor(total / 5);
  const fives = randInt(rng, Math.floor(maxFives / 2), maxFives);
  return { fives, ones: total - fives * 5 };
}

/** Split `total` into `parts` near-equal non-negative integers (XP bursts, rain waves). */
export function splitEven(total: number, parts: number): number[] {
  const n = Math.max(1, Math.floor(parts));
  const t = Math.max(0, Math.floor(total));
  const base = Math.floor(t / n);
  const rest = t - base * n;
  return Array.from({ length: n }, (_, i) => base + (i < rest ? 1 : 0));
}

// ---- triggers ----------------------------------------------------------------------------

/** §13.1.3: a single settlement with net ≥ multiple × stake AND net ≥ minChips. */
export function isBigWin(staked: number, net: number, multiple: number, minChips: number): boolean {
  return staked >= 1 && net > 0 && net >= multiple * staked && net >= minChips;
}

/** Per-player cooldown between events (world ticks). */
export function cooldownReady(lastEventTick: number | undefined, now: number, cooldownTicks: number): boolean {
  return lastEventTick === undefined || now - lastEventTick >= cooldownTicks || now < lastEventTick;
}

/**
 * Sunset roll (§13.1.5): once per Minecraft day when time-of-day crosses 12000. `day` is
 * floor(absoluteTime / 24000); returns true if the roll should happen now.
 */
export function sunsetDue(prevTimeOfDay: number | undefined, timeOfDay: number, day: number, lastRolledDay: number | undefined): boolean {
  if (lastRolledDay === day) return false;
  if (prevTimeOfDay === undefined) return false;
  return prevTimeOfDay <= timeOfDay && prevTimeOfDay < 12000 && timeOfDay >= 12000;
}
