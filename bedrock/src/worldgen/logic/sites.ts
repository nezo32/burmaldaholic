/**
 * Where casinos go. PURE.
 *
 * Bedrock cannot append pieces to vanilla villages / bastions / End Cities, so the add-on detects
 * those structures by their residents (villagers, piglin brutes, shulkers) and builds the casino
 * next to (or on top of) them. Each detected structure is a "site"; the chance roll happens once
 * per site and the decision is persisted, which gives exactly the spec's "≈ 1 per 3 villages"
 * (`worldgen.villageCasino.chance`) etc.
 */
import type { CasinoKind, Vec3 } from './layouts';

export const SITE_RADIUS = 128;

export type SiteState = 'placed' | 'skipped' | 'no_room';

export interface Site {
  readonly kind: CasinoKind;
  readonly dim: string;
  readonly x: number;
  readonly z: number;
  readonly state: SiteState;
}

const STATE_CODE: Readonly<Record<SiteState, string>> = { placed: 'p', skipped: 's', no_room: 'n' };
const KIND_CODE: Readonly<Record<CasinoKind, string>> = { village_casino: 'v', piglin_parlor: 'b', high_roller: 'e' };
const DIM_CODE: Readonly<Record<string, string>> = { 'minecraft:overworld': 'o', 'minecraft:nether': 'n', 'minecraft:the_end': 'e' };
const invert = (r: Readonly<Record<string, string>>): Record<string, string> => Object.fromEntries(Object.entries(r).map(([k, v]) => [v, k]));
const STATE_OF = invert(STATE_CODE) as Record<string, SiteState>;
const KIND_OF = invert(KIND_CODE) as Record<string, CasinoKind>;
const DIM_OF = invert(DIM_CODE);

/** Compact persisted form: `v|o|120|-340|p`. */
export function encodeSite(s: Site): string {
  return [KIND_CODE[s.kind], DIM_CODE[s.dim] ?? s.dim, Math.round(s.x), Math.round(s.z), STATE_CODE[s.state]].join('|');
}

export function decodeSite(raw: string): Site | undefined {
  const [k, d, x, z, st] = raw.split('|');
  const kind = KIND_OF[k ?? ''];
  const state = STATE_OF[st ?? ''];
  const nx = Number(x);
  const nz = Number(z);
  if (!kind || !state || !d || !Number.isFinite(nx) || !Number.isFinite(nz)) return undefined;
  return { kind, dim: DIM_OF[d] ?? d, x: nx, z: nz, state };
}

/** Spatial index of decided sites (per dimension, coarse cells of SITE_RADIUS). */
export class SiteIndex {
  private readonly cells = new Map<string, Site[]>();
  private readonly all: Site[] = [];

  constructor(private readonly radius = SITE_RADIUS) {}

  private cellKey(dim: string, cx: number, cz: number): string {
    return `${dim}|${cx}|${cz}`;
  }

  add(s: Site): void {
    const key = this.cellKey(s.dim, Math.floor(s.x / this.radius), Math.floor(s.z / this.radius));
    const list = this.cells.get(key) ?? [];
    list.push(s);
    this.cells.set(key, list);
    this.all.push(s);
  }

  /** A decided site within `radius` of (x, z) in `dim`, if any. */
  near(dim: string, x: number, z: number): Site | undefined {
    const cx = Math.floor(x / this.radius);
    const cz = Math.floor(z / this.radius);
    const r2 = this.radius * this.radius;
    for (let dx = -1; dx <= 1; dx++)
      for (let dz = -1; dz <= 1; dz++)
        for (const s of this.cells.get(this.cellKey(dim, cx + dx, cz + dz)) ?? []) {
          if ((s.x - x) ** 2 + (s.z - z) ** 2 <= r2) return s;
        }
    return undefined;
  }

  list(): readonly Site[] {
    return this.all;
  }
}

/** Split strings into chunks whose joined length (with '\n') stays <= maxChars. */
export function chunkStrings(items: readonly string[], maxChars: number): string[] {
  const chunks: string[] = [];
  let cur: string[] = [];
  let len = 0;
  for (const s of items) {
    if (s.length + 1 > maxChars) throw new Error('item longer than a chunk');
    if (cur.length && len + s.length + 1 > maxChars) {
      chunks.push(cur.join('\n'));
      cur = [];
      len = 0;
    }
    cur.push(s);
    len += s.length + 1;
  }
  if (cur.length) chunks.push(cur.join('\n'));
  return chunks;
}

// ---- candidate positions ----------------------------------------------------------------------

const DIRS: readonly (readonly [number, number])[] = [
  [0, -1],
  [1, -1],
  [-1, -1],
  [1, 0],
  [-1, 0],
  [1, 1],
  [-1, 1],
  [0, 1],
];

/**
 * Footprint origins (min corner) around `anchor`, ordered so that buildings north of the anchor
 * come first: their entrance (+z face) then looks towards the village / bastion.
 */
export function candidateOrigins(anchor: { x: number; z: number }, size: { x: number; z: number }, distances: readonly number[]): { x: number; z: number }[] {
  const out: { x: number; z: number }[] = [];
  for (const d of distances)
    for (const [dx, dz] of DIRS) {
      const n = Math.hypot(dx, dz);
      const cx = anchor.x + (dx / n) * d;
      const cz = anchor.z + (dz / n) * d;
      out.push({ x: Math.round(cx - size.x / 2), z: Math.round(cz - size.z / 2) });
    }
  return out;
}

/** Sample offsets covering a footprint (edges included), step `step`. */
export function footprintSamples(size: { x: number; z: number }, step: number): { dx: number; dz: number }[] {
  const axis = (n: number): number[] => {
    const a: number[] = [];
    for (let v = 0; v < n - 1; v += step) a.push(v);
    a.push(n - 1);
    return a;
  };
  const out: { dx: number; dz: number }[] = [];
  for (const dx of axis(size.x)) for (const dz of axis(size.z)) out.push({ dx, dz });
  return out;
}

// ---- surface classification (Overworld) -------------------------------------------------------

export type SurfaceKind = 'ground' | 'plant' | 'tree' | 'liquid' | 'built' | 'air';

const GROUND = new Set(
  [
    'grass_block', 'dirt', 'coarse_dirt', 'podzol', 'mycelium', 'rooted_dirt', 'dirt_with_roots', 'sand', 'red_sand', 'gravel',
    'stone', 'granite', 'diorite', 'andesite', 'tuff', 'calcite', 'deepslate', 'clay', 'snow', 'moss_block', 'mud',
    'sandstone', 'red_sandstone', 'terracotta', 'hardened_clay', 'packed_ice', 'blue_ice', 'powder_snow', 'pale_moss_block',
  ].map((n) => `minecraft:${n}`),
);
const PLANT_RE = /(short_grass|tall_grass|^minecraft:grass$|fern|flower|tulip|orchid|allium|bluet|daisy|dandelion|poppy|lily|bush|snow_layer|cactus|sapling|mushroom|sugar_cane|pumpkin|melon|vine|leaf_litter|wildflowers|firefly_bush|dry_grass|pale_moss_carpet|moss_carpet)/;
const TREE_RE = /(_log$|leaves|_wood$|mangrove_roots|bamboo|mushroom_block|mushroom_stem|beehive|bee_nest)/;
const LIQUID_RE = /(water|lava|^minecraft:ice$|frosted_ice|seagrass|kelp|bubble_column)/;
const COLORED_TERRACOTTA = /^minecraft:[a-z_]+_terracotta$/;

export function classifySurface(typeId: string): SurfaceKind {
  if (typeId === 'minecraft:air' || typeId === 'minecraft:cave_air' || typeId === 'minecraft:void_air') return 'air';
  if (GROUND.has(typeId) || (COLORED_TERRACOTTA.test(typeId) && !typeId.includes('glazed'))) return 'ground';
  if (LIQUID_RE.test(typeId)) return 'liquid';
  if (TREE_RE.test(typeId)) return 'tree';
  if (PLANT_RE.test(typeId) && typeId !== 'minecraft:grass_path' && typeId !== 'minecraft:dirt_path') return 'plant';
  return 'built';
}

export interface SurfaceSample {
  /** y of the topmost non-air block */
  readonly y: number;
  readonly kind: SurfaceKind;
}

export interface FootprintVerdict {
  readonly ok: boolean;
  /** world y of the floor layer (= ground level) */
  readonly floorY: number;
  readonly reason?: 'unloaded' | 'built' | 'liquid' | 'trees' | 'steep' | 'no_ground';
}

/**
 * Is this patch of Overworld a good, untouched building lot? No player/village blocks, no water,
 * few trees, height spread <= maxSpread. The floor goes at the median ground height.
 */
export function evaluateFootprint(samples: readonly (SurfaceSample | undefined)[], maxSpread = 4, maxTreeShare = 0.25): FootprintVerdict {
  const heights: number[] = [];
  let trees = 0;
  for (const s of samples) {
    if (!s) return { ok: false, floorY: 0, reason: 'unloaded' };
    if (s.kind === 'built') return { ok: false, floorY: 0, reason: 'built' };
    if (s.kind === 'liquid') return { ok: false, floorY: 0, reason: 'liquid' };
    if (s.kind === 'tree') trees++;
    else if (s.kind === 'ground') heights.push(s.y);
    else if (s.kind === 'plant') heights.push(s.y - 1);
  }
  if (trees > samples.length * maxTreeShare) return { ok: false, floorY: 0, reason: 'trees' };
  if (heights.length === 0) return { ok: false, floorY: 0, reason: 'no_ground' };
  heights.sort((a, b) => a - b);
  if ((heights[heights.length - 1] as number) - (heights[0] as number) > maxSpread) return { ok: false, floorY: 0, reason: 'steep' };
  return { ok: true, floorY: heights[Math.floor(heights.length / 2)] as number };
}

/** Rough "is this a village" check: vanilla villages always have paths or a bell. */
export function looksLikeVillage(surfaceIds: readonly string[], minPaths = 3): boolean {
  let paths = 0;
  for (const id of surfaceIds) {
    if (id === 'minecraft:bell') return true;
    if (id === 'minecraft:grass_path' || id === 'minecraft:dirt_path') paths++;
  }
  return paths >= minPaths;
}

// ---- Nether (Piglin Parlor) -------------------------------------------------------------------

export type NetherKind = 'solid' | 'air' | 'lava' | 'bastion';

const BASTION_RE = /(blackstone|gilded|polished_basalt|chest|gold_block|chain|lantern|spawner|lodestone|ladder|soul_fire)/;

export function classifyNether(typeId: string): NetherKind {
  if (typeId === 'minecraft:air' || typeId === 'minecraft:cave_air') return 'air';
  if (/lava|fire/.test(typeId) && !typeId.includes('soul_fire')) return 'lava';
  if (BASTION_RE.test(typeId)) return 'bastion';
  return 'solid';
}

/**
 * The Parlor is carved into the rock next to the bastion: reject volumes touching the bastion or
 * lava, and volumes that are mostly open air (we want an "extra room", not a floating box).
 */
export function evaluateNetherVolume(samples: readonly (NetherKind | undefined)[], minSolidShare = 0.35): { ok: boolean; reason?: string } {
  let solid = 0;
  for (const s of samples) {
    if (!s) return { ok: false, reason: 'unloaded' };
    if (s === 'bastion') return { ok: false, reason: 'bastion' };
    if (s === 'lava') return { ok: false, reason: 'lava' };
    if (s === 'solid') solid++;
  }
  if (solid < samples.length * minSolidShare) return { ok: false, reason: 'open' };
  return { ok: true };
}

/** Sample offsets inside a box (edges included). */
export function volumeSamples(size: Vec3, step: number): Vec3[] {
  const axis = (n: number): number[] => {
    const a: number[] = [];
    for (let v = 0; v < n - 1; v += step) a.push(v);
    a.push(n - 1);
    return a;
  };
  const out: Vec3[] = [];
  for (const x of axis(size.x)) for (const y of axis(size.y)) for (const z of axis(size.z)) out.push({ x, y, z });
  return out;
}

// ---- End City (High Roller Lounge) ------------------------------------------------------------

const END_CITY_RE = /(purpur|end_bricks|end_stone_brick)/;

export function isEndCityBlock(typeId: string): boolean {
  return END_CITY_RE.test(typeId);
}

export interface TopSample {
  readonly x: number;
  readonly z: number;
  readonly y: number;
  readonly typeId: string;
}

/** Highest End City roof block among the samples (the top floor of the tallest tower). */
export function pickTowerTop(samples: readonly TopSample[]): TopSample | undefined {
  let best: TopSample | undefined;
  for (const s of samples) if (isEndCityBlock(s.typeId) && (!best || s.y > best.y)) best = s;
  return best;
}

/** Lounge fits on the tower if nothing in its footprint reaches above the roof it stands on. */
export function loungeFits(roofY: number, footprintTops: readonly (number | undefined)[], height: number, maxY: number): boolean {
  if (roofY + 1 + height > maxY) return false;
  // undefined = empty column (void), which is fine for an overhang
  return footprintTops.every((y) => y === undefined || y <= roofY);
}

// ---- chance ------------------------------------------------------------------------------------

/** One roll per detected structure. */
export function rollSite(chance: number, r: number): boolean {
  return r < Math.max(0, Math.min(1, chance));
}
