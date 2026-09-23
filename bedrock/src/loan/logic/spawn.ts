/**
 * Safe collector spawn spots (GAME_DESIGN §5.5 "Spawn"): 24–40 blocks horizontally from the
 * debtor, solid top face, 2 air blocks above, not in water/lava, same dimension. PURE: the
 * script passes a block classifier.
 */
import type { Rng } from '../../core/logic/rng';

export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

/** Classification of one block for spawning. `undefined` = unloaded / outside the world. */
export type Cell = 'solid' | 'air' | 'liquid' | 'passable';

export interface SpawnRules {
  minRadius: number;
  maxRadius: number;
  /** vertical search window around the debtor's feet */
  verticalRange: number;
  /** attempts per member */
  attempts: number;
  /** dimension height range [min, max) */
  minY: number;
  maxY: number;
  /**
   * |x|, |z| limit. Script API 2.8 cannot read the /worldborder, so this is the vanilla hard
   * border (spawns beyond it would fail or strand the squad).
   */
  maxCoord: number;
}

export const WORLD_HARD_BORDER = 29_999_984;
export const DEFAULT_SPAWN_RULES: SpawnRules = { minRadius: 24, maxRadius: 40, verticalRange: 12, attempts: 16, minY: -64, maxY: 320, maxCoord: WORLD_HARD_BORDER };

/** Uniform-area random point on the ring [min, max] around center (integer block coords). */
export function ringPoint(rng: Rng, center: { x: number; z: number }, min: number, max: number): { x: number; z: number } {
  const a = rng.next() * Math.PI * 2;
  const r = Math.sqrt(min * min + rng.next() * (max * max - min * min));
  return { x: Math.floor(center.x + Math.cos(a) * r), z: Math.floor(center.z + Math.sin(a) * r) };
}

/**
 * Standing block y (feet position = y + 1) at column (x, z), searching outward from `nearY`:
 * block at y is solid, y+1 and y+2 are air. Undefined when none in range.
 */
export function standY(cell: (x: number, y: number, z: number) => Cell | undefined, x: number, z: number, nearY: number, rules: SpawnRules): number | undefined {
  const base = Math.floor(nearY);
  for (let d = 0; d <= rules.verticalRange; d++) {
    for (const y of d === 0 ? [base - 1] : [base - 1 - d, base - 1 + d]) {
      if (y < rules.minY || y + 2 >= rules.maxY) continue;
      if (cell(x, y, z) !== 'solid') continue;
      if (cell(x, y + 1, z) === 'air' && cell(x, y + 2, z) === 'air') return y;
    }
  }
  return undefined;
}

/**
 * Up to `attempts` random ring columns; the first standable one wins (feet position, centered).
 * `allowed` rejects spots (e.g. inside a player casino claim).
 */
export function findSpawn(
  rng: Rng,
  center: Vec3,
  cell: (x: number, y: number, z: number) => Cell | undefined,
  rules: SpawnRules = DEFAULT_SPAWN_RULES,
  allowed: (p: Vec3) => boolean = () => true,
): Vec3 | undefined {
  for (let i = 0; i < rules.attempts; i++) {
    const p = ringPoint(rng, center, rules.minRadius, rules.maxRadius);
    if (Math.abs(p.x) > rules.maxCoord || Math.abs(p.z) > rules.maxCoord) continue;
    const y = standY(cell, p.x, p.z, center.y, rules);
    if (y === undefined) continue;
    const spot = { x: p.x + 0.5, y: y + 1, z: p.z + 0.5 };
    if (allowed(spot)) return spot;
  }
  return undefined;
}

/** Lowest free debtor slot (per-debtor target tag) among the slots in use; wraps when all are taken. */
export function freeSlot(used: readonly number[], slots: number): number {
  for (let i = 0; i < slots; i++) if (!used.includes(i)) return i;
  return used.length % slots;
}

/** Block ids that have no solid top face even though they are not air/liquid. */
const PASSABLE_EXACT = /^(grass|short_grass|tall_grass|fern|large_fern|dandelion|poppy|blue_orchid|allium|azure_bluet|oxeye_daisy|cornflower|lily_of_the_valley|wither_rose|torchflower|pink_petals|torch|lantern|soul_lantern|rail|lever|vine|ladder|snow_layer|web|fire|soul_fire|portal|scaffolding|waterlily|lily_pad|deadbush|dead_bush|reeds|sugar_cane|sweet_berry_bush|wheat|carrots|potatoes|beetroot|melon_stem|pumpkin_stem|kelp|seagrass|redstone_wire|tripwire|campfire|soul_campfire|cactus|magma|pointed_dripstone|powder_snow|farmland|glow_lichen|sculk_vein)$/;
const PASSABLE_SUFFIX = /(_sapling|_torch|_sign|_hanging_sign|_banner|_rail|_button|_pressure_plate|_carpet|_tulip|_door|_trapdoor|_fence_gate|_head|_skull|_candle|^candle|_coral|_coral_fan|_vines|_roots|_fungus|_mushroom)$/;

/** Classify a block id (isAir / isLiquid come from the engine). */
export function classify(typeId: string, isAir: boolean, isLiquid: boolean): Cell {
  if (isAir) return 'air';
  if (isLiquid) return 'liquid';
  const id = typeId.replace(/^minecraft:/, '');
  return PASSABLE_EXACT.test(id) || PASSABLE_SUFFIX.test(id) ? 'passable' : 'solid';
}

/** Horizontal distance (spawn ring is horizontal). */
export const horizontalDistance = (a: { x: number; z: number }, b: { x: number; z: number }): number => Math.hypot(a.x - b.x, a.z - b.z);
