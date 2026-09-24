/**
 * Entity-based in-world props (docs/architecture/animation.md §2.9): cabinet reels, wheels, dice, coin,
 * cards. A prop is an AI-free entity with `client_sync` properties; the script writes properties at beat
 * ticks (≤ N writes per event, global.md §2.9) and triggers `playAnimation` for one-shot motion; Molang in the
 * generated animation files does the per-frame work client-side.
 * Lifecycle (extras-pvp.md BX1): one entity per machine, linked to its block by a tag, re-linked after chunk loads.
 */
import type { Dimension, Entity, Vector3 } from '@minecraft/server';

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

/** Tag that links a prop entity to its block (`burmaldaholic_prop:<key>`, key = the table/machine key). */
export const propTag = (key: string): string => `burmaldaholic_prop:${key}`;

const near = (dim: Dimension, pos: Vector3, typeId: string, key: string): Entity[] => dim.getEntities({ type: typeId, tags: [propTag(key)], location: pos, maxDistance: 3 });

/** The prop entity of `key` near `pos` (re-link after a chunk load), or undefined. */
export function findProp(dim: Dimension, pos: Vector3, typeId: string, key: string): Entity | undefined {
  try {
    return near(dim, pos, typeId, key)[0];
  } catch {
    return undefined; // unloaded chunk
  }
}

/**
 * Prop lifecycle (extras-pvp.md BX1, docs/architecture/animation.md §2.9): the machine's one entity, found by its tag
 * or spawned at `at`; duplicates left by a crash are removed. Undefined while the chunk is not loaded.
 */
export function ensureProp(dim: Dimension, at: Vector3, typeId: string, key: string): Entity | undefined {
  try {
    const found = near(dim, at, typeId, key);
    for (const extra of found.slice(1)) extra.remove();
    if (found[0]) return found[0];
    const e = dim.spawnEntity(typeId, at);
    e.addTag(propTag(key));
    return e;
  } catch {
    return undefined;
  }
}

/** Removes the prop of `key` (block broken, table removed). */
export function removeProp(dim: Dimension, pos: Vector3, typeId: string, key: string): void {
  try {
    for (const e of near(dim, pos, typeId, key)) e.remove();
  } catch {
    /* unloaded: the next ensureProp re-links it, or the owning game's orphan sweep removes it */
  }
}
