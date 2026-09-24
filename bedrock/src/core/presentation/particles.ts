/**
 * Casino particles (global.md §2.9, docs/architecture/animation.md §2.3): one `spawnParticle` call per burst (the
 * emitter multiplies client-side from `variable.count`), ≤ 60 particles per burst, ≤ 24 calls per event, reduce
 * motion × 0.3, and per-viewer `anim.celebrations` (others' celebration FX are hidden from viewers who chose
 * "only mine" or "off"). When every viewer accepts the burst it is ONE `dimension.spawnParticle`; otherwise it is
 * sent only to the accepting viewers (`player.spawnParticle`).
 */
import { type Dimension, MolangVariableMap, type Player, type Vector3 } from '@minecraft/server';
import { fxSettings } from './settings';
import { viewersNear } from './sound';

export const MAX_PARTICLES_PER_BURST = 60;
export const MAX_CALLS_PER_EVENT = 24;

export interface BurstOpts {
  /** `variable.count` (clamped to 1–60). */
  count?: number;
  /** Scale the count × 0.3 (the owner's reduce motion; celebration plans already apply it). */
  reduceMotion?: boolean;
  /** `variable.tint` 0–1 RGB (tintable sprites: chips, sparkles, confetti). */
  tint?: readonly [number, number, number];
  /** The player the effect celebrates (viewers with `mine` still see their own). */
  owner?: Player;
  /** Celebration FX obey the viewers' `anim.celebrations`; plain table motion does not. */
  celebration?: boolean;
  /** Viewer radius (default 24 blocks). */
  radius?: number;
}

/** Per-event call budget: create one per event and pass it to every `burst` of that event. */
export class ParticleBudget {
  calls = 0;
  constructor(readonly max = MAX_CALLS_PER_EVENT) {}
  take(n: number): boolean {
    if (this.calls + n > this.max) return false;
    this.calls += n;
    return true;
  }
}

const DEFAULT_TINT: readonly [number, number, number] = [1, 0.84, 0.25];

function vars(count: number, tint: readonly [number, number, number]): MolangVariableMap {
  const m = new MolangVariableMap();
  m.setFloat('variable.count', Math.max(1, Math.min(MAX_PARTICLES_PER_BURST, Math.round(count))));
  m.setColorRGB('variable.tint', { red: tint[0], green: tint[1], blue: tint[2] });
  return m;
}

/** Whether `viewer` wants to see a celebration burst owned by `owner`. */
export function acceptsCelebration(viewer: Player, owner: Player | undefined): boolean {
  const c = fxSettings(viewer).celebrations;
  if (c === 'all') return true;
  if (c === 'off') return false;
  return owner !== undefined && viewer.id === owner.id;
}

/** Spawns one burst; returns the number of `spawnParticle` calls made. */
export function burst(dim: Dimension, particle: string, at: Vector3, opts: BurstOpts = {}, budget = new ParticleBudget()): number {
  let count = opts.count ?? 8;
  if (opts.reduceMotion) count = Math.max(1, Math.round(count * 0.3));
  const v = vars(count, opts.tint ?? DEFAULT_TINT);
  try {
    if (!opts.celebration) {
      if (!budget.take(1)) return 0;
      dim.spawnParticle(particle, at, v);
      return 1;
    }
    const viewers = viewersNear(dim, at, opts.radius ?? 24);
    const ok = viewers.filter((p) => acceptsCelebration(p, opts.owner));
    if (ok.length === 0) return 0;
    if (ok.length === viewers.length) {
      if (!budget.take(1)) return 0;
      dim.spawnParticle(particle, at, v);
      return 1;
    }
    let n = 0;
    for (const p of ok) {
      if (!budget.take(1)) break;
      p.spawnParticle(particle, at, v);
      n++;
    }
    return n;
  } catch {
    return 0; // unloaded chunk / missing particle: decoration only
  }
}
