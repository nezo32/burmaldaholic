/**
 * Per-player FX settings (global.md §2.8) on player dynamic properties `burmaldaholic:anim.*`.
 * SKELETON: read-only accessors with defaults; the Casino Menu → Settings form (task B14 / lane B-L1) writes
 * them. Nothing reads them yet, so behaviour is unchanged.
 */
import type { Player } from '@minecraft/server';
import type { TimingProfile } from '../logic/anim/timeline';

export type Celebrations = 'all' | 'mine' | 'off';

export interface FxSettings {
  readonly reduceMotion: boolean;
  readonly flashes: boolean;
  /** 50 slow / 100 normal / 150 turbo */
  readonly speedPct: number;
  readonly celebrations: Celebrations;
  /** 0–100 */
  readonly volume: number;
  /** styled JSON UI HUD panel (Bedrock only) */
  readonly hudPanel: boolean;
}

export const FX_DEFAULTS: FxSettings = { reduceMotion: false, flashes: true, speedPct: 100, celebrations: 'all', volume: 100, hudPanel: true };

export const FX_PROP = {
  reduceMotion: 'burmaldaholic:anim.reduceMotion',
  flashes: 'burmaldaholic:anim.flashes',
  speed: 'burmaldaholic:anim.speed',
  celebrations: 'burmaldaholic:anim.celebrations',
  volume: 'burmaldaholic:anim.volume',
  hudPanel: 'burmaldaholic:anim.hudPanel',
} as const;

function read<T extends boolean | number | string>(p: Player, id: string, fallback: T): T {
  try {
    const v = p.getDynamicProperty(id);
    return typeof v === typeof fallback ? (v as T) : fallback;
  } catch {
    return fallback;
  }
}

export function fxSettings(p: Player): FxSettings {
  const reduceMotion = read(p, FX_PROP.reduceMotion, FX_DEFAULTS.reduceMotion);
  const speed = read(p, FX_PROP.speed, FX_DEFAULTS.speedPct);
  const cel = read(p, FX_PROP.celebrations, FX_DEFAULTS.celebrations as string);
  return {
    reduceMotion,
    // reduce motion forces flashes off (global.md §2.8)
    flashes: !reduceMotion && read(p, FX_PROP.flashes, FX_DEFAULTS.flashes),
    speedPct: speed === 50 || speed === 150 ? speed : 100,
    celebrations: cel === 'mine' || cel === 'off' ? cel : 'all',
    volume: Math.max(0, Math.min(100, read(p, FX_PROP.volume, FX_DEFAULTS.volume))),
    hudPanel: read(p, FX_PROP.hudPanel, FX_DEFAULTS.hudPanel),
  };
}

/** Profile for LOCAL beats built for this player (shared beats use `SHARED_PROFILE`). */
export function localProfile(s: FxSettings): TimingProfile {
  return { speedPct: s.speedPct, reduceMotion: s.reduceMotion, flashes: s.flashes };
}
