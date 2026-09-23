/**
 * Block / spot helpers for chaos events (thin @minecraft layer over logic/safety.ts).
 * Every block read tolerates unloaded chunks (returns undefined = "not a valid spot").
 */
import type { Block, Dimension, Vector3 } from '@minecraft/server';
import { type BlockInfo, type LandingColumn, isPassable, isSafeLanding, isSolidGround, safeDropSurface } from './logic/safety';

export const dimName = (d: Dimension): string => d.id.replace(/^minecraft:/, '');

export function blockAt(dim: Dimension, loc: Vector3): Block | undefined {
  try {
    return dim.getBlock({ x: Math.floor(loc.x), y: Math.floor(loc.y), z: Math.floor(loc.z) });
  } catch {
    return undefined;
  }
}

export function info(b: Block | undefined): BlockInfo | undefined {
  if (!b) return undefined;
  try {
    return { typeId: b.typeId, isAir: b.isAir, isLiquid: b.isLiquid };
  } catch {
    return undefined;
  }
}

const infoAt = (dim: Dimension, x: number, y: number, z: number): BlockInfo | undefined => info(blockAt(dim, { x, y, z }));

function loaded(dim: Dimension, x: number, y: number, z: number): boolean {
  try {
    return dim.isChunkLoaded({ x, y, z });
  } catch {
    return false;
  }
}

function column(dim: Dimension, x: number, y: number, z: number): LandingColumn | undefined {
  const ground = infoAt(dim, x, y, z);
  const feet = infoAt(dim, x, y + 1, z);
  const head = infoAt(dim, x, y + 2, z);
  if (!ground || !feet || !head) return undefined;
  const below: BlockInfo[] = [];
  for (let i = 1; i <= 3; i++) {
    const b = infoAt(dim, x, y - i, z);
    if (b) below.push(b);
  }
  return { dimension: dimName(dim), y, ground, feet, head, below, minY: dim.heightRange.min };
}

/**
 * Safe teleport landing at column (x, z), §13.4. Overworld/End: the topmost block (stepping
 * down through plants); Nether: scan down from Y 118 (never the roof). Returns the ground y.
 */
export function findLanding(dim: Dimension, x: number, z: number, nearY: number): number | undefined {
  const minY = dim.heightRange.min;
  if (!loaded(dim, x, Math.max(minY, Math.min(nearY, dim.heightRange.max - 1)), z)) return undefined;
  const name = dimName(dim);
  if (name === 'nether') {
    for (let y = 117; y > minY + 5; y--) {
      const c = column(dim, x, y, z);
      if (c && isSafeLanding(c)) return y;
    }
    return undefined;
  }
  let top: Block | undefined;
  try {
    top = dim.getTopmostBlock({ x, z });
  } catch {
    return undefined;
  }
  if (!top) return undefined;
  let y = top.y;
  for (let i = 0; i < 4; i++) {
    const b = infoAt(dim, x, y, z);
    if (!b || !isPassable(b)) break;
    y--;
  }
  const c = column(dim, x, y, z);
  return c && isSafeLanding(c) ? y : undefined;
}

/**
 * Mob spawn spot near (x, z): solid ground with 2 free blocks above, searched within ±6 of
 * `nearY` (closest to it first). Returns the position to spawn at (standing on the ground).
 */
export function findSpawnSpot(dim: Dimension, x: number, z: number, nearY: number): Vector3 | undefined {
  const minY = dim.heightRange.min;
  const maxY = dim.heightRange.max - 3;
  if (!loaded(dim, x, Math.max(minY, Math.min(nearY, maxY)), z)) return undefined;
  const base = Math.floor(nearY);
  for (const d of [0, -1, 1, -2, 2, -3, 3, -4, 4, -5, 5, -6, 6]) {
    const y = base - 1 + d;
    if (y <= minY || y >= maxY) continue;
    const ground = infoAt(dim, x, y, z);
    const feet = infoAt(dim, x, y + 1, z);
    const head = infoAt(dim, x, y + 2, z);
    if (!ground || !feet || !head) continue;
    if (isSolidGround(ground) && feet.isAir && head.isAir) return { x: x + 0.5, y: y + 1, z: z + 0.5 };
  }
  return undefined;
}

/**
 * Where to drop an item near `origin` at horizontal offset (dx, dz), `height` blocks up:
 * falls back to the player's feet if there is no headroom or it would land in lava/void.
 */
export function dropSpot(dim: Dimension, origin: Vector3, dx: number, dz: number, height: number): Vector3 {
  const feet = { x: origin.x, y: origin.y + 0.2, z: origin.z };
  const x = Math.floor(origin.x + dx);
  const z = Math.floor(origin.z + dz);
  const y0 = Math.floor(origin.y);
  for (let h = 0; h <= height; h++) {
    const b = infoAt(dim, x, y0 + h, z);
    if (!b || !isPassable(b)) {
      if (h === 0) return feet;
      height = h - 1;
      break;
    }
  }
  let below: BlockInfo | undefined;
  for (let y = y0 - 1; y >= Math.max(dim.heightRange.min, y0 - 24); y--) {
    const b = infoAt(dim, x, y, z);
    if (!b) break;
    if (!b.isAir) {
      below = b;
      break;
    }
  }
  if (!safeDropSurface(below)) return feet;
  return { x: x + 0.5, y: y0 + Math.max(0, height) + 0.5, z: z + 0.5 };
}
