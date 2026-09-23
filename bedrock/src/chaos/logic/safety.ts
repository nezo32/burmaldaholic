/**
 * Chaos safety rules (GAME_DESIGN §13.4). PURE: the script layer gathers a snapshot of the
 * player / blocks and these functions decide.
 */
import type { ChaosEventId } from './events';

export type SkipReason =
  | 'disabled'
  | 'game_mode'
  | 'dead'
  | 'respawn'
  | 'sleeping'
  | 'cooldown'
  | 'deferred_too_long'
  | 'peaceful'
  | 'boss'
  | 'claim'
  | 'no_spot'
  | 'gliding'
  | 'riding'
  | 'falling'
  | 'in_round'
  | 'dimension'
  | 'golden_hour_cooldown';

export type Verdict = { action: 'run' } | { action: 'defer' } | { action: 'skip'; reason: SkipReason };

export interface PlayerSnapshot {
  /** 'Survival' | 'Adventure' | 'Creative' | 'Spectator' */
  gameMode: string;
  dead: boolean;
  /** ticks since the last (non-initial) respawn; undefined = no recent respawn */
  ticksSinceRespawn?: number;
  sleeping: boolean;
  gliding: boolean;
  riding: boolean;
  /** falling more than ~3 blocks (stable API has no fallDistance: estimated from velocity) */
  falling: boolean;
  /** a casino form is on screen (event is deferred) */
  formOpen: boolean;
  /** an open card/table round (wager ticket or seated at a table game) */
  inRound: boolean;
  /** within bossSafeRadius of a Wither / Warden / Ender Dragon */
  nearBoss: boolean;
  /** inside a claimed casino interior (§18) */
  inClaim: boolean;
  peaceful: boolean;
  dimension: string;
}

export interface SafetyConfig {
  respawnGraceTicks: number;
}

const OK: Verdict = { action: 'run' };
const skip = (reason: SkipReason): Verdict => ({ action: 'skip', reason });

/**
 * Per-player checks for one event. Golden Hour is server-wide and not checked here.
 * `mob_wave` in Peaceful returns skip('peaceful'): the caller rerolls (§13.4).
 */
export function evaluate(event: ChaosEventId, s: PlayerSnapshot, cfg: SafetyConfig): Verdict {
  if (event === 'golden_hour') return OK;
  if (s.gameMode === 'Creative' || s.gameMode === 'Spectator') return skip('game_mode');
  if (s.dead) return skip('dead');
  if (s.ticksSinceRespawn !== undefined && s.ticksSinceRespawn < cfg.respawnGraceTicks) return skip('respawn');
  if (s.sleeping) return skip('sleeping');
  if (s.formOpen) return { action: 'defer' };
  switch (event) {
    case 'mob_wave':
      if (s.peaceful) return skip('peaceful');
      if (s.nearBoss) return skip('boss');
      if (s.inClaim) return skip('claim');
      return OK;
    case 'random_teleport':
      if (s.gliding) return skip('gliding');
      if (s.riding) return skip('riding');
      if (s.falling) return skip('falling');
      if (s.nearBoss) return skip('boss');
      if (s.inRound) return skip('in_round');
      return OK;
    case 'weather_change':
      return s.dimension === 'overworld' ? OK : skip('dimension');
    default:
      return OK;
  }
}

// ---- landing spots -------------------------------------------------------------------------

export interface BlockInfo {
  typeId: string;
  isAir: boolean;
  isLiquid: boolean;
}

/** Top blocks a player must never land on (§13.4), besides air/liquids. */
export const UNSAFE_GROUND: readonly string[] = [
  'minecraft:lava',
  'minecraft:flowing_lava',
  'minecraft:magma',
  'minecraft:fire',
  'minecraft:soul_fire',
  'minecraft:campfire',
  'minecraft:soul_campfire',
  'minecraft:cactus',
  'minecraft:sweet_berry_bush',
  'minecraft:powder_snow',
  'minecraft:pointed_dripstone',
  'minecraft:water',
  'minecraft:flowing_water',
  'minecraft:bedrock',
  'minecraft:barrier',
  'minecraft:structure_void',
  // Bedrock splits the light block by level: minecraft:light_block_0 … _15
  ...Array.from({ length: 16 }, (_, i) => `minecraft:light_block_${i}`),
  'minecraft:end_portal',
  'minecraft:portal',
  'minecraft:end_gateway',
  'minecraft:wither_rose',
  'minecraft:scaffolding',
];

/** Non-solid plants and covers that may stand where the player's feet go (not hazards). */
const PASSABLE = /^minecraft:(short_grass|tall_grass|fern|large_fern|dead_bush|snow_layer|vine|dandelion|poppy|blue_orchid|allium|azure_bluet|oxeye_daisy|cornflower|lily_of_the_valley|[a-z_]+_tulip|sunflower|lilac|rose_bush|peony|seagrass|nether_sprouts|crimson_roots|warped_roots|pink_petals|leaf_litter|short_dry_grass|tall_dry_grass|bush|firefly_bush|wildflowers)$/;

/** A block that is not solid ground but may be stood inside (air or harmless plant). */
export const isPassable = (b: BlockInfo): boolean => b.isAir || (!b.isLiquid && PASSABLE.test(b.typeId));

/** A block that can be solid ground. */
export function isSolidGround(b: BlockInfo): boolean {
  return !b.isAir && !b.isLiquid && !isPassable(b) && !UNSAFE_GROUND.includes(b.typeId) && !/(_slab|_carpet|_pressure_plate|_button|_torch|torch|_sign|_banner|rail|_door|_trapdoor|_fence|_wall|_pane|ladder)$/.test(b.typeId);
}

export interface LandingColumn {
  dimension: string;
  /** y of the ground block */
  y: number;
  ground: BlockInfo;
  feet: BlockInfo;
  head: BlockInfo;
  /** up to 3 blocks under the ground, top first (End rule) */
  below: readonly BlockInfo[];
  minY: number;
}

/** §13.4 teleport target rules for one column. */
export function isSafeLanding(c: LandingColumn): boolean {
  if (!isSolidGround(c.ground)) return false;
  if (!isAirLike(c.feet) || !isAirLike(c.head)) return false;
  if (c.y <= c.minY + 5) return false;
  if (c.dimension === 'nether' && c.y + 1 >= 120) return false;
  if (c.dimension === 'the_end') {
    if (c.ground.typeId !== 'minecraft:end_stone') return false;
    if (c.below.length < 3 || !c.below.slice(0, 3).every(isSolidGround)) return false;
  }
  return true;
}

/** 2 air (non-liquid) blocks above the ground; harmless plants at feet/head are allowed. */
const isAirLike = (b: BlockInfo): boolean => isPassable(b) && !UNSAFE_GROUND.includes(b.typeId);

/** Horizontal offset at distance [min, max] in a uniformly random direction. */
export function randomOffset(r1: number, r2: number, min: number, max: number): { dx: number; dz: number } {
  const lo = Math.min(min, max);
  const hi = Math.max(min, max);
  const dist = lo + r1 * (hi - lo);
  const a = r2 * Math.PI * 2;
  return { dx: Math.round(Math.cos(a) * dist), dz: Math.round(Math.sin(a) * dist) };
}

/** Items may not be dropped into lava/void: the spot's first non-air block below must be safe. */
export function safeDropSurface(firstNonAirBelow: BlockInfo | undefined): boolean {
  if (!firstNonAirBelow) return false; // void
  return !['minecraft:lava', 'minecraft:flowing_lava', 'minecraft:fire', 'minecraft:soul_fire', 'minecraft:cactus', 'minecraft:magma'].includes(firstNonAirBelow.typeId);
}
