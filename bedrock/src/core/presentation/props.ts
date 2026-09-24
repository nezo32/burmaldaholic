/**
 * Entity-based in-world props (docs/architecture/animation.md §2.9): cabinet reels, wheels, dice, coin,
 * cards. A prop is an AI-free entity with `client_sync` properties; the script writes properties at beat
 * ticks (≤ N writes per event, global.md §2.9) and triggers `playAnimation` for one-shot motion; Molang in the
 * generated animation files does the per-frame work client-side.
 * SKELETON: typed helpers only.
 */
import type { Entity } from '@minecraft/server';

export type PropValue = number | boolean | string;

/** Writes only changed properties; returns how many writes were made (budget accounting). */
export function writeProps(e: Entity, values: Readonly<Record<string, PropValue>>): number {
  let writes = 0;
  for (const [k, v] of Object.entries(values)) {
    try {
      if (e.getProperty(k) === v) continue;
      e.setProperty(k, v);
      writes++;
    } catch {
      /* entity unloaded or property missing in an old pack: presentation only, never fatal */
    }
  }
  return writes;
}

/** One-shot animation on a controller state (e.g. `land_r` at a reel stop tick). */
export function playPropAnimation(e: Entity, animation: string, controller?: string, nextState?: string): void {
  try {
    e.playAnimation(animation, { controller, nextState });
  } catch {
    /* ignore: decoration */
  }
}
