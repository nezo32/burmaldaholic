/**
 * Casino building layouts (GAME_DESIGN §16). PURE.
 *
 * One source of truth for both sides:
 *  - the shell (vanilla blocks) is exported into `.mcstructure` templates by
 *    tools/gen-structures.mjs (via ./mcstructure.ts);
 *  - the furniture (game tables owned by other modules, NPCs, loot chests) is placed by script
 *    right after the template, so a missing game module never leaves "unknown" blocks and the
 *    table ids come straight from STRINGS.md (`block.burmaldaholic.<id>`).
 *
 * Local coordinates: x/z in [0, size), y = 0 is the floor layer. Every building has its entrance
 * on the +z side (south when placed unrotated).
 */

export interface Vec3 {
  readonly x: number;
  readonly y: number;
  readonly z: number;
}

export type Facing = 'north' | 'south' | 'east' | 'west';
export type CasinoKind = 'village_casino' | 'piglin_parlor' | 'high_roller';
export type VillageStyle = 'plains' | 'desert' | 'savanna' | 'taiga' | 'snowy';
export type LootTableId = 'village_casino' | 'piglin_parlor' | 'high_roller';
export type NpcRole = 'croupier' | 'loan_shark' | 'piglin_dealer' | 'piglin_moneylender' | 'shulker_croupier' | 'baccarat_dealer';
/** Fixed table configs for worldgen tables (GAME_DESIGN §7.1 / §16), exposed through the API. */
export type TablePresetId = 'standard' | 'parlor_poker' | 'high_roller_blackjack' | 'high_roller_roulette' | 'high_roller_baccarat' | 'high_roller_uth';

export interface BlockSpec {
  readonly name: string;
  /** bool -> Byte, number -> Int, string -> String in the NBT palette */
  readonly states?: Readonly<Record<string, string | number | boolean>>;
}

export interface TableSlot {
  /** full block id from STRINGS.md (`burmaldaholic:<id>`) */
  readonly block: string;
  readonly pos: Vec3;
  readonly facing: Facing;
  readonly preset: TablePresetId;
}

export interface NpcSlot {
  readonly role: NpcRole;
  readonly pos: Vec3;
  readonly facing: Facing;
  /** entity tags set on spawn (e.g. the owning module's High-Roller tag for a dealer NPC) */
  readonly tags?: readonly string[];
}

export interface ChestSlot {
  readonly pos: Vec3;
  readonly loot: LootTableId;
}

export interface Layout {
  /** template id; structure `burmaldaholic:worldgen_<id>` */
  readonly id: string;
  readonly kind: CasinoKind;
  readonly size: Vec3;
  readonly grid: Grid;
  readonly tables: readonly TableSlot[];
  readonly npcs: readonly NpcSlot[];
  readonly chests: readonly ChestSlot[];
  /** columns below the floor are filled with this block down to the ground (max `depth`) */
  readonly foundation: { readonly block: string; readonly depth: number };
  /** local door cells (bottom row) on the +z face, used for the Parlor tunnel */
  readonly door: { readonly x0: number; readonly x1: number; readonly height: number };
}

// ---- table / entity ids (other modules own them; ids from STRINGS.md) -------------------------

export const BLOCK = {
  cashier: 'burmaldaholic:cashier',
  netherCashier: 'burmaldaholic:nether_cashier',
  blackjack: 'burmaldaholic:blackjack_table',
  blackjackHighRoller: 'burmaldaholic:blackjack_table_high_roller',
  poker: 'burmaldaholic:poker_table',
  roulette: 'burmaldaholic:roulette_table',
  rouletteHighRoller: 'burmaldaholic:roulette_table_high_roller',
  craps: 'burmaldaholic:craps_table',
  slotsCopper: 'burmaldaholic:slot_machine_copper',
  slotsGold: 'burmaldaholic:slot_machine_gold',
  slotsNetherite: 'burmaldaholic:slot_machine_netherite',
  wheel: 'burmaldaholic:wheel_of_fortune',
  plinko: 'burmaldaholic:plinko_machine',
  baccarat: 'burmaldaholic:baccarat_table',
  baccaratHighRoller: 'burmaldaholic:baccarat_table_high_roller',
  uth: 'burmaldaholic:uth_table',
  uthHighRoller: 'burmaldaholic:uth_table_high_roller',
} as const;

/** Baccarat module's High-Roller tag for its dealer NPC (games/baccarat/api.ts; kept as a string: layouts stay pure). */
export const BACCARAT_HIGH_ROLLER_TAG = 'burmaldaholic_baccarat_high_roller';

export const NPC_ENTITY: Readonly<Record<NpcRole, readonly string[]>> = {
  croupier: ['burmaldaholic:croupier'],
  // owned by the loan module; the moneylender falls back to the plain Loan Shark
  loan_shark: ['burmaldaholic:loan_shark'],
  piglin_moneylender: ['burmaldaholic:piglin_moneylender', 'burmaldaholic:loan_shark'],
  piglin_dealer: ['burmaldaholic:piglin_dealer'],
  shulker_croupier: ['burmaldaholic:shulker_croupier'],
  // owned by the baccarat module (the entity hosts a table itself, §20.6)
  baccarat_dealer: ['burmaldaholic:baccarat_dealer'],
};

/** Minecraft yaw for an entity looking towards `f` (0 = south/+z). */
export function facingYaw(f: Facing): number {
  return { south: 0, west: 90, north: 180, east: -90 }[f];
}

export function structureId(layoutId: string): string {
  return `burmaldaholic:worldgen_${layoutId}`;
}

// ---- grid -------------------------------------------------------------------------------------

/** 3D block grid; `undefined` cells are structure void (the world block is kept). */
export class Grid {
  private readonly cells: (BlockSpec | undefined)[];

  constructor(
    readonly sx: number,
    readonly sy: number,
    readonly sz: number,
  ) {
    this.cells = new Array<BlockSpec | undefined>(sx * sy * sz).fill(undefined);
  }

  /** Bedrock `.mcstructure` order: z fastest, then y, then x. */
  index(x: number, y: number, z: number): number {
    return (x * this.sy + y) * this.sz + z;
  }

  inside(x: number, y: number, z: number): boolean {
    return x >= 0 && y >= 0 && z >= 0 && x < this.sx && y < this.sy && z < this.sz;
  }

  set(x: number, y: number, z: number, b: BlockSpec | undefined): void {
    if (!this.inside(x, y, z)) throw new Error(`grid set out of bounds ${x},${y},${z}`);
    this.cells[this.index(x, y, z)] = b;
  }

  get(x: number, y: number, z: number): BlockSpec | undefined {
    return this.cells[this.index(x, y, z)];
  }

  box(x0: number, y0: number, z0: number, x1: number, y1: number, z1: number, b: BlockSpec | undefined): void {
    for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
      for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) for (let z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) this.set(x, y, z, b);
  }

  /** Hollow walls (no floor/ceiling) of the box x0..x1 × z0..z1 for rows y0..y1. */
  walls(x0: number, y0: number, z0: number, x1: number, y1: number, z1: number, b: BlockSpec): void {
    for (let y = y0; y <= y1; y++) {
      for (let x = x0; x <= x1; x++) {
        this.set(x, y, z0, b);
        this.set(x, y, z1, b);
      }
      for (let z = z0; z <= z1; z++) {
        this.set(x0, y, z, b);
        this.set(x1, y, z, b);
      }
    }
  }

  cellCount(): number {
    return this.cells.length;
  }
}

const B = (name: string, states?: Record<string, string | number | boolean>): BlockSpec => ({ name: `minecraft:${name}`, states });
const AIR = B('air');
const chest = (facing: Facing): BlockSpec => B('chest', { 'minecraft:cardinal_direction': facing });

// ---- "CASINO" pixel font (3×5, I is 1 wide; letters alternate materials, no gaps) -------------

const FONT: Readonly<Record<string, readonly string[]>> = {
  C: ['###', '#..', '#..', '#..', '###'],
  A: ['###', '#.#', '###', '#.#', '#.#'],
  S: ['###', '#..', '###', '..#', '###'],
  I: ['#', '#', '#', '#', '#'],
  N: ['##.', '#.#', '#.#', '#.#', '#.#'],
  O: ['###', '#.#', '#.#', '#.#', '###'],
};

export interface SignPixel {
  /** column from the left edge */
  readonly col: number;
  /** row from the top (0..4) */
  readonly row: number;
  /** letter index (materials alternate by letter) */
  readonly letter: number;
}

/** Lit pixels of a word in the 3×5 font, laid out without gaps. */
export function signPixels(word: string): { pixels: SignPixel[]; width: number } {
  const pixels: SignPixel[] = [];
  let col = 0;
  [...word].forEach((ch, letter) => {
    const glyph = FONT[ch];
    if (!glyph) throw new Error(`no glyph for '${ch}'`);
    glyph.forEach((line, row) => {
      [...line].forEach((c, dx) => {
        if (c === '#') pixels.push({ col: col + dx, row, letter });
      });
    });
    col += glyph[0]!.length;
  });
  return { pixels, width: col };
}

// ---- village casino "Lucky Villager" 17 × 10 × 17 (§16.1) -------------------------------------

interface VillagePalette {
  floor: BlockSpec;
  wall: BlockSpec;
  corner: BlockSpec;
  roof: BlockSpec;
  foundation: BlockSpec;
  carpet: BlockSpec;
  signBack: BlockSpec;
  decor: BlockSpec;
}

export const VILLAGE_STYLES: readonly VillageStyle[] = ['plains', 'desert', 'savanna', 'taiga', 'snowy'];

const VILLAGE_PALETTES: Readonly<Record<VillageStyle, VillagePalette>> = {
  plains: {
    floor: B('oak_planks'),
    wall: B('oak_planks'),
    corner: B('cobblestone'),
    roof: B('dark_oak_planks'),
    foundation: B('cobblestone'),
    carpet: B('red_carpet'),
    signBack: B('black_wool'),
    decor: B('bookshelf'),
  },
  desert: {
    floor: B('cut_sandstone'),
    wall: B('smooth_sandstone'),
    corner: B('chiseled_sandstone'),
    roof: B('sandstone'),
    foundation: B('sandstone'),
    carpet: B('red_carpet'),
    signBack: B('black_wool'),
    decor: B('bookshelf'),
  },
  savanna: {
    floor: B('acacia_planks'),
    wall: B('acacia_planks'),
    corner: B('orange_terracotta'),
    roof: B('orange_terracotta'),
    foundation: B('cobblestone'),
    carpet: B('red_carpet'),
    signBack: B('black_wool'),
    decor: B('bookshelf'),
  },
  taiga: {
    floor: B('spruce_planks'),
    wall: B('spruce_planks'),
    corner: B('mossy_cobblestone'),
    roof: B('dark_oak_planks'),
    foundation: B('cobblestone'),
    carpet: B('green_carpet'),
    signBack: B('black_wool'),
    decor: B('bookshelf'),
  },
  snowy: {
    floor: B('spruce_planks'),
    wall: B('spruce_planks'),
    corner: B('packed_ice'),
    roof: B('snow'),
    foundation: B('cobblestone'),
    carpet: B('red_carpet'),
    signBack: B('black_wool'),
    decor: B('bookshelf'),
  },
};

function villageCasino(style: VillageStyle): Layout {
  const p = VILLAGE_PALETTES[style];
  const g = new Grid(17, 10, 17);
  // Clear the whole volume (terrain, grass, tree canopies) then build.
  g.box(0, 0, 0, 16, 9, 16, AIR);
  g.box(0, 0, 0, 16, 0, 16, p.floor);
  g.walls(0, 0, 0, 16, 0, 16, p.foundation);
  g.walls(0, 1, 0, 16, 4, 16, p.wall);
  for (const [x, z] of [
    [0, 0],
    [16, 0],
    [0, 16],
    [16, 16],
  ] as const)
    g.box(x, 1, z, x, 4, z, p.corner);
  // windows
  for (const z of [3, 4, 11, 12]) for (const x of [0, 16]) g.box(x, 2, z, x, 3, z, B('glass_pane'));
  for (const x of [3, 4, 12, 13]) {
    g.box(x, 2, 0, x, 3, 0, B('glass_pane'));
    g.box(x, 2, 16, x, 3, 16, B('glass_pane'));
  }
  // roof + ceiling lights
  g.box(0, 5, 0, 16, 5, 16, p.roof);
  for (const [x, z] of [
    [4, 3],
    [12, 3],
    [4, 9],
    [12, 9],
    [4, 13],
    [12, 13],
    [8, 11],
  ] as const)
    g.set(x, 5, z, B('glowstone'));
  // back room (z 1..4) behind an inner wall at z = 5 with a doorway
  g.box(1, 1, 5, 15, 4, 5, p.wall);
  g.box(8, 1, 5, 8, 2, 5, AIR);
  g.box(1, 1, 1, 3, 2, 1, p.decor);
  g.box(13, 1, 1, 15, 2, 1, p.decor);
  g.set(8, 1, 1, chest('south'));
  // hall carpet (tables and NPCs are placed on top of / instead of it by script)
  g.box(1, 1, 6, 15, 1, 15, p.carpet);
  // entrance (3 wide, 3 high) on the +z face
  g.box(7, 1, 16, 9, 3, 16, AIR);
  g.box(7, 0, 16, 9, 0, 16, p.floor);
  // "CASINO" sign on the front edge of the roof: glowstone / lit lamps (powered from behind)
  const sign = signPixels('CASINO');
  const left = Math.floor((17 - sign.width) / 2);
  g.box(0, 6, 16, 16, 9, 16, p.signBack);
  g.box(0, 6, 15, 16, 9, 15, p.signBack);
  for (const px of sign.pixels) {
    const x = left + px.col;
    const y = 9 - px.row;
    const lamp = px.letter % 2 === 1;
    g.set(x, y, 16, lamp ? B('lit_redstone_lamp') : B('glowstone'));
    if (lamp) g.set(x, y, 15, B('redstone_block'));
  }

  const f = (x: number, z: number): Vec3 => ({ x, y: 1, z });
  return {
    id: `village_casino_${style}`,
    kind: 'village_casino',
    size: { x: 17, y: 10, z: 17 },
    grid: g,
    tables: [
      { block: BLOCK.slotsCopper, pos: f(2, 6), facing: 'south', preset: 'standard' },
      { block: BLOCK.slotsCopper, pos: f(3, 6), facing: 'south', preset: 'standard' },
      { block: BLOCK.slotsCopper, pos: f(4, 6), facing: 'south', preset: 'standard' },
      { block: BLOCK.slotsGold, pos: f(11, 6), facing: 'south', preset: 'standard' },
      { block: BLOCK.wheel, pos: f(13, 6), facing: 'south', preset: 'standard' },
      { block: BLOCK.blackjack, pos: f(4, 10), facing: 'south', preset: 'standard' },
      { block: BLOCK.roulette, pos: f(12, 10), facing: 'south', preset: 'standard' },
      { block: BLOCK.cashier, pos: f(15, 13), facing: 'west', preset: 'standard' },
      // §16.1 (2026-09): Ultimate Texas Hold'em along the back wall
      { block: BLOCK.uth, pos: f(6, 6), facing: 'south', preset: 'standard' },
    ],
    npcs: [
      { role: 'croupier', pos: f(2, 13), facing: 'east' },
      { role: 'loan_shark', pos: f(8, 7), facing: 'south' },
    ],
    chests: [{ pos: f(8, 1), loot: 'village_casino' }],
    foundation: { block: p.foundation.name, depth: 5 },
    door: { x0: 7, x1: 9, height: 3 },
  };
}

// ---- Piglin Parlor 21 × 12 × 21 (§16.2) -------------------------------------------------------

function piglinParlor(): Layout {
  const g = new Grid(21, 12, 21);
  const bricks = B('polished_blackstone_bricks');
  // rows 0..8 are the room; 9..11 stay structure void so the Nether rock above is kept
  g.box(0, 0, 0, 20, 8, 20, AIR);
  g.box(0, 0, 0, 20, 0, 20, bricks);
  g.walls(0, 1, 0, 20, 7, 20, bricks);
  g.box(0, 8, 0, 20, 8, 20, B('blackstone'));
  // gold pillars every 5 blocks + gilded band
  for (const v of [0, 5, 10, 15, 20]) {
    for (const [x, z] of [
      [v, 0],
      [v, 20],
      [0, v],
      [20, v],
    ] as const)
      g.box(x, 1, z, x, 7, z, B('gold_block'));
  }
  for (let v = 1; v < 20; v++) {
    if (v % 5 === 0) continue;
    for (const [x, z] of [
      [v, 0],
      [v, 20],
      [0, v],
      [20, v],
    ] as const)
      g.set(x, 5, z, B('gilded_blackstone'));
  }
  // crimson floor centre with a carpet runner, shroomlight ceiling grid
  g.box(3, 0, 3, 17, 0, 17, B('crimson_planks'));
  g.box(9, 1, 8, 11, 1, 19, B('red_carpet'));
  for (let x = 2; x <= 18; x += 4) for (let z = 2; z <= 18; z += 4) g.set(x, 8, z, B('shroomlight'));
  // entrance on +z
  g.box(9, 1, 20, 11, 3, 20, AIR);
  g.set(1, 1, 19, chest('east'));

  const f = (x: number, z: number): Vec3 => ({ x, y: 1, z });
  return {
    id: 'piglin_parlor',
    kind: 'piglin_parlor',
    size: { x: 21, y: 12, z: 21 },
    grid: g,
    tables: [
      { block: BLOCK.craps, pos: f(6, 6), facing: 'south', preset: 'standard' },
      { block: BLOCK.poker, pos: f(14, 6), facing: 'south', preset: 'parlor_poker' },
      { block: BLOCK.slotsGold, pos: f(1, 11), facing: 'east', preset: 'standard' },
      { block: BLOCK.slotsGold, pos: f(1, 13), facing: 'east', preset: 'standard' },
      { block: BLOCK.plinko, pos: f(19, 11), facing: 'west', preset: 'standard' },
      { block: BLOCK.netherCashier, pos: f(19, 15), facing: 'west', preset: 'standard' },
      // §16.2 (2026-09): baccarat, one Piglin Dealer behind it (cosmetic)
      { block: BLOCK.baccarat, pos: f(6, 12), facing: 'south', preset: 'standard' },
    ],
    npcs: [
      { role: 'piglin_dealer', pos: f(6, 11), facing: 'south' },
      { role: 'piglin_dealer', pos: f(14, 4), facing: 'south' },
      { role: 'piglin_moneylender', pos: f(10, 2), facing: 'south' },
    ],
    chests: [{ pos: f(1, 19), loot: 'piglin_parlor' }],
    foundation: { block: 'minecraft:blackstone', depth: 6 },
    door: { x0: 9, x1: 11, height: 3 },
  };
}

// ---- End City High Roller Lounge 15 × 9 × 15 (§16.3; 13 × 13 before the 2026-09 games) -------

function highRollerLounge(): Layout {
  const n = 15;
  const m = n - 1;
  const c = Math.floor(n / 2);
  const g = new Grid(n, 9, n);
  const purpur = B('purpur_block', { pillar_axis: 'y' });
  g.box(0, 0, 0, m, 8, m, AIR);
  g.box(0, 0, 0, m, 0, m, purpur);
  g.box(1, 0, 1, m - 1, 0, m - 1, B('obsidian'));
  g.box(c - 1, 0, c - 1, c + 1, 0, c + 1, B('crying_obsidian'));
  g.walls(0, 1, 0, m, 1, m, purpur);
  g.walls(0, 2, 0, m, 4, m, B('magenta_stained_glass'));
  g.walls(0, 5, 0, m, 5, m, purpur);
  for (const [x, z] of [
    [0, 0],
    [m, 0],
    [0, m],
    [m, m],
  ] as const) {
    g.box(x, 1, z, x, 5, z, purpur);
    g.set(x, 7, z, B('end_rod', { facing_direction: 1 }));
  }
  g.box(0, 6, 0, m, 6, m, purpur);
  for (const [x, z] of [
    [3, 3],
    [m - 3, 3],
    [3, m - 3],
    [m - 3, m - 3],
  ] as const)
    g.set(x, 5, z, B('end_rod', { facing_direction: 0 }));
  g.box(1, 1, 1, m - 1, 1, m - 1, B('purple_carpet'));
  g.box(c - 1, 1, m, c + 1, 3, m, AIR);
  g.set(c, 1, 1, chest('south'));

  const f = (x: number, z: number): Vec3 => ({ x, y: 1, z });
  return {
    id: 'high_roller_lounge',
    kind: 'high_roller',
    size: { x: n, y: 9, z: n },
    grid: g,
    tables: [
      { block: BLOCK.slotsNetherite, pos: f(2, 1), facing: 'south', preset: 'standard' },
      { block: BLOCK.slotsNetherite, pos: f(4, 1), facing: 'south', preset: 'standard' },
      { block: BLOCK.blackjackHighRoller, pos: f(4, 5), facing: 'south', preset: 'high_roller_blackjack' },
      { block: BLOCK.rouletteHighRoller, pos: f(10, 5), facing: 'south', preset: 'high_roller_roulette' },
      { block: BLOCK.cashier, pos: f(12, 1), facing: 'south', preset: 'standard' },
      // §16.3 (2026-09): High-Roller Baccarat (dealer NPC behind it) and High-Roller UTH
      { block: BLOCK.baccaratHighRoller, pos: f(4, 10), facing: 'south', preset: 'high_roller_baccarat' },
      { block: BLOCK.uthHighRoller, pos: f(10, 10), facing: 'south', preset: 'high_roller_uth' },
    ],
    npcs: [
      { role: 'shulker_croupier', pos: f(12, 8), facing: 'west' },
      // hosts a High-Roller table of its own (tagged like the module's High-Roller dealers)
      { role: 'baccarat_dealer', pos: f(4, 9), facing: 'south', tags: [BACCARAT_HIGH_ROLLER_TAG] },
    ],
    chests: [{ pos: f(c, 1), loot: 'high_roller' }],
    foundation: { block: 'minecraft:purpur_block', depth: 0 },
    door: { x0: c - 1, x1: c + 1, height: 3 },
  };
}

// ---- registry ---------------------------------------------------------------------------------

let cache: ReadonlyMap<string, Layout> | undefined;

export function allLayouts(): ReadonlyMap<string, Layout> {
  if (!cache) {
    const list = [...VILLAGE_STYLES.map(villageCasino), piglinParlor(), highRollerLounge()];
    cache = new Map(list.map((l) => [l.id, l]));
  }
  return cache;
}

export function layout(id: string): Layout | undefined {
  return allLayouts().get(id);
}

export function villageLayoutId(style: VillageStyle): string {
  return `village_casino_${style}`;
}

export const KIND_LAYOUT: Readonly<Record<Exclude<CasinoKind, 'village_casino'>, string>> = {
  piglin_parlor: 'piglin_parlor',
  high_roller: 'high_roller_lounge',
};

/** Village style from a biome id (`minecraft:snowy_plains`, `minecraft:desert`...). */
export function villageStyleForBiome(biomeId: string): VillageStyle {
  const b = biomeId.replace(/^minecraft:/, '');
  if (/snow|ice|frozen/.test(b)) return 'snowy';
  if (/desert|badlands|mesa/.test(b)) return 'desert';
  if (/savanna/.test(b)) return 'savanna';
  if (/taiga|grove/.test(b)) return 'taiga';
  return 'plains';
}

/** Local position -> world position (templates are always placed unrotated). */
export function toWorld(origin: Vec3, local: Vec3): Vec3 {
  return { x: origin.x + local.x, y: origin.y + local.y, z: origin.z + local.z };
}
