/**
 * Casino sound playback (docs/architecture/animation.md §2.4, global.md §2.7 / §2.8 / §2.9):
 * `burmaldaholic.<id>` definitions from `packs/<owner>/RP/sounds/sound_definitions.json`, played with
 * `volume × anim.volume`, composites as same-tick (or short-delay) layers (`SOUND_LAYERS`), at most 20 sounds per
 * second per player. Personal sounds use `player.playSound`; table sounds are positional per viewer (so every viewer's
 * own volume applies) through `playAt`.
 */
import { type Dimension, type Player, type Vector3, system } from '@minecraft/server';
import { SOUND_LAYERS, layerSoundId } from '../logic/anim/sound-plan';
import { soundId } from '../logic/anim/sound-ids';
import { fxSettings } from './settings';

export interface SoundOpts {
  pitch?: number;
  volume?: number;
  /** Positional (heard from this point) instead of at the player. */
  location?: Vector3;
}

/** Per-player rate limit: ≤ 20 sounds per rolling second (global.md §2.9). */
export const SOUNDS_PER_SECOND = 20;
const recent = new Map<string, number[]>();

function allow(playerId: string, tick: number): boolean {
  const list = (recent.get(playerId) ?? []).filter((t) => tick - t < 20);
  if (list.length >= SOUNDS_PER_SECOND) {
    recent.set(playerId, list);
    return false;
  }
  list.push(tick);
  recent.set(playerId, list);
  return true;
}

/** Forget a player's rate-limit history (player left). */
export function forgetSoundHistory(playerId: string): void {
  recent.delete(playerId);
}

function playRaw(p: Player, fullId: string, opts: SoundOpts, volumeFactor: number): void {
  if (!allow(p.id, system.currentTick)) return;
  try {
    p.playSound(fullId, { pitch: opts.pitch ?? 1, volume: (opts.volume ?? 1) * volumeFactor, location: opts.location });
  } catch {
    /* player left / sound missing in an old pack: decoration only */
  }
}

/**
 * Plays a catalog sound (`id` without namespace, e.g. `win_big` or `slots.reel_stop`) for one player, with its
 * Bedrock layers. Returns false when muted by the player's effects volume.
 */
export function playCasinoSound(p: Player, id: string, opts: SoundOpts = {}): boolean {
  const factor = fxSettings(p).volume / 100;
  if (factor <= 0) return false;
  playRaw(p, soundId(id), opts, factor);
  for (const l of SOUND_LAYERS[id] ?? []) {
    const full = soundId(layerSoundId(id, l.n));
    if (l.delayTicks <= 0) playRaw(p, full, opts, factor);
    else
      system.runTimeout(() => {
        if (p.isValid) playRaw(p, full, opts, factor);
      }, l.delayTicks);
  }
  return true;
}

/** Viewers of a positional sound: players within `radius` blocks (default 24). */
export function viewersNear(dim: Dimension, pos: Vector3, radius = 24): Player[] {
  try {
    return dim.getPlayers({ location: pos, maxDistance: radius });
  } catch {
    return [];
  }
}

/** Positional table sound for everyone nearby (each with their own effects volume). Returns the viewer count. */
export function playAt(dim: Dimension, pos: Vector3, id: string, opts: Omit<SoundOpts, 'location'> = {}, radius = 24): number {
  const viewers = viewersNear(dim, pos, radius);
  for (const v of viewers) playCasinoSound(v, id, { ...opts, location: pos });
  return viewers.length;
}
