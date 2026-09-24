/**
 * Core particle set (global.md §5.2, docs/architecture/animation.md §2.3) and the layout of the core particle atlas
 * `packs/core/RP/textures/particle/burmaldaholic_fx.png` (128 × 128, 8 × 8 cells, one flipbook row per sprite set,
 * frames left to right). PURE data: the particle JSON files in `packs/core/RP/particles/` use these rows (a test
 * checks them) and the asset generator (`tools/assets/modules/core.mjs`, lane X-L0) draws the sprites into them.
 * Sprites are white/neutral where a `variable.tint` colour is applied (chip denominations, confetti).
 */
export const PARTICLE_ATLAS = { path: 'textures/particle/burmaldaholic_fx', size: 128, cell: 8 } as const;

export interface AtlasRow {
  readonly row: number;
  readonly sprite: string;
  readonly frames: number;
}

/** Row → sprite set. Rows 12–15: 12 extras `foil_flake` (extras-pvp.md §10.4), 13–15 reserve. */
export const ATLAS_ROWS: readonly AtlasRow[] = [
  { row: 0, sprite: 'chip', frames: 4 },
  { row: 1, sprite: 'chip_glint', frames: 4 },
  { row: 2, sprite: 'sparkle', frames: 4 },
  { row: 3, sprite: 'gold_burst', frames: 4 },
  { row: 4, sprite: 'golden_mote', frames: 2 },
  { row: 5, sprite: 'diamond_glint', frames: 4 },
  { row: 6, sprite: 'curse_wisp', frames: 3 },
  { row: 7, sprite: 'summon_rune', frames: 4 },
  { row: 8, sprite: 'teleport_ring', frames: 4 },
  { row: 9, sprite: 'collector_smoke', frames: 4 },
  { row: 10, sprite: 'coin', frames: 8 },
  { row: 11, sprite: 'confetti', frames: 8 },
  { row: 12, sprite: 'foil_flake', frames: 4 },
];

/** Core-owned particle ids (Bedrock `burmaldaholic:<id>`) → the atlas sprite row they draw. */
export const CORE_PARTICLES: Readonly<Record<string, string>> = {
  chip_pop: 'chip',
  chip_fountain: 'chip',
  chip_glint: 'chip_glint',
  sparkle: 'sparkle',
  gold_burst: 'gold_burst',
  golden_mote: 'golden_mote',
  diamond_glint: 'diamond_glint',
  curse_wisp: 'curse_wisp',
  summon_rune: 'summon_rune',
  teleport_ring: 'teleport_ring',
  collector_smoke: 'collector_smoke',
  vip_ring: 'sparkle',
  coin_burst: 'coin',
  confetti: 'confetti',
  jackpot_burst: 'coin',
};

export const atlasRow = (sprite: string): AtlasRow | undefined => ATLAS_ROWS.find((r) => r.sprite === sprite);

/** Chip denomination tints (global.md §2.1 chip colours; value → RGB 0–1). */
export const CHIP_TINTS: Readonly<Record<number, readonly [number, number, number]>> = {
  1: [0.96, 0.93, 0.97],
  5: [0.85, 0.2, 0.25],
  25: [0.2, 0.62, 0.3],
  100: [0.15, 0.15, 0.2],
  500: [0.47, 0.24, 0.75],
};
