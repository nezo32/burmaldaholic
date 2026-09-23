/**
 * Slot machine engine (GAME_DESIGN §8). PURE: no @minecraft imports.
 *
 * - 3×3 window, every cell drawn independently from the tier's symbol weights (no strips).
 * - Paylines: middle, top, bottom, diagonal TL→BR, diagonal BL→TR (Copper uses 1, Gold 3,
 *   Netherite 5). Every line is 3 independent cells, so per-line RTP = machine RTP.
 * - A line pays its single best combination (§8.1 rules 1–4); payout = floor(mult × lineBet).
 */
import { type Rng, weightedPick } from '../../../core/logic/rng';

export const SYMBOLS = ['berries', 'apple', 'golden_carrot', 'emerald', 'diamond', 'seven', 'wild', 'creeper', 'tnt', 'pearl', 'clock', 'star'] as const;
export type Sym = (typeof SYMBOLS)[number];

/** Symbols Wild substitutes for (fruit, gems, seven). */
export const REGULAR: readonly Sym[] = ['berries', 'apple', 'golden_carrot', 'emerald', 'diamond', 'seven'];
/** Symbols that only pay as three natural of a kind and may trigger chaos. */
export const SPECIAL: readonly Sym[] = ['creeper', 'tnt', 'pearl', 'clock', 'star'];
export type SpecialSym = 'creeper' | 'tnt' | 'pearl' | 'clock' | 'star';

export const isSpecial = (s: Sym): s is SpecialSym => (SPECIAL as readonly Sym[]).includes(s);

export const TIERS = ['copper', 'gold', 'netherite'] as const;
export type Tier = (typeof TIERS)[number];
export const isTier = (v: unknown): v is Tier => typeof v === 'string' && (TIERS as readonly string[]).includes(v);

/** Paylines per tier (§8.2–8.4). */
export const TIER_LINES: Record<Tier, number> = { copper: 1, gold: 3, netherite: 5 };
/** Tiers with a progressive jackpot (§8.5). */
export const PROGRESSIVE: readonly Tier[] = ['gold', 'netherite'];

/** Chaos event fired for three natural specials (§8.1, §13.1 trigger 2/4). */
export const SPECIAL_EVENT = {
  creeper: 'mob_wave',
  tnt: 'mob_wave',
  pearl: 'random_teleport',
  clock: 'golden_hour',
  star: 'jackpot',
} as const satisfies Record<SpecialSym, string>;

/** At most one chaos event per spin: Star > Clock > Pearl > TNT/Creeper (§8.1). */
export const SPECIAL_PRIORITY: readonly SpecialSym[] = ['star', 'clock', 'pearl', 'tnt', 'creeper'];

/**
 * Payline cell coordinates [row, col] (row 0 = top). Line numbers shown to players are the
 * 1-based index in this list: 1 middle, 2 top, 3 bottom, 4 diagonal ↘, 5 diagonal ↗.
 */
export const PAYLINES: readonly (readonly [number, number])[][] = [
  [[1, 0], [1, 1], [1, 2]],
  [[0, 0], [0, 1], [0, 2]],
  [[2, 0], [2, 1], [2, 2]],
  [[0, 0], [1, 1], [2, 2]],
  [[2, 0], [1, 1], [0, 2]],
];

/** One machine's math. Weights/pays for absent symbols are 0. */
export interface SlotTable {
  weights: Record<Sym, number>;
  /** 3-of-a-kind multipliers (stake included: "pays 10×" = 10 × line bet). star = 0 when progressive. */
  pays: Record<Sym, number>;
  /** multipliers for 1 and 2 leading Sweet Berries */
  berryPartial: readonly [number, number];
  lines: number;
  /** progressive machines: 3 stars award the jackpot instead of `pays.star` */
  progressive: boolean;
}

export type Grid = Sym[][]; // [row][col]

export type LineKind = 'three' | 'wild' | 'berry1' | 'berry2' | 'special';
export interface LineResult {
  kind: LineKind;
  /** symbol that pays (berries for partials, wild for three wilds) */
  symbol: Sym;
  /** multiplier of the line bet (0 for zero-pay specials / the progressive star) */
  multiplier: number;
}

const zeroMap = (): Record<Sym, number> => Object.fromEntries(SYMBOLS.map((s) => [s, 0])) as Record<Sym, number>;

/** Build a table from (possibly partial / admin-edited) config maps; invalid entries become 0. */
export function makeTable(o: {
  weights: Partial<Record<string, number>>;
  pays: Partial<Record<string, number>>;
  berryPartial?: readonly number[];
  lines: number;
  progressive: boolean;
}): SlotTable {
  const clean = (m: Partial<Record<string, number>>): Record<Sym, number> => {
    const out = zeroMap();
    for (const s of SYMBOLS) {
      const v = m[s];
      out[s] = typeof v === 'number' && Number.isFinite(v) && v > 0 ? v : 0;
    }
    return out;
  };
  const bp = o.berryPartial ?? [];
  const nz = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) && v > 0 ? v : 0);
  return { weights: clean(o.weights), pays: clean(o.pays), berryPartial: [nz(bp[0]), nz(bp[1])], lines: Math.max(1, Math.min(PAYLINES.length, Math.floor(o.lines))), progressive: o.progressive };
}

/** Defaults from GAME_DESIGN §8.2–8.4 (same as the CONFIG.md catalog defaults). */
export const DEFAULT_WEIGHTS: Record<Tier, Partial<Record<Sym, number>>> = {
  copper: { berries: 24, apple: 20, golden_carrot: 16, emerald: 12, diamond: 8, seven: 5, creeper: 15 },
  gold: { berries: 22, apple: 19, golden_carrot: 16, emerald: 12, diamond: 8, seven: 5, wild: 3, creeper: 8, pearl: 5, star: 2 },
  netherite: { berries: 20, apple: 19, golden_carrot: 16, emerald: 12, diamond: 9, seven: 6, wild: 3, tnt: 6, pearl: 5, clock: 2, star: 2 },
};
export const DEFAULT_PAYS: Record<Tier, Partial<Record<Sym, number>>> = {
  copper: { berries: 10, apple: 10, golden_carrot: 20, emerald: 30, diamond: 60, seven: 150, creeper: 0 },
  gold: { berries: 8, apple: 7, golden_carrot: 11, emerald: 25, diamond: 50, seven: 100, wild: 200, creeper: 0, pearl: 10, star: 0 },
  netherite: { berries: 8, apple: 9, golden_carrot: 14, emerald: 25, diamond: 50, seven: 100, wild: 250, tnt: 0, pearl: 10, clock: 50, star: 0 },
};

export const defaultTable = (tier: Tier, owned = false, ownedStarPays = 1000): SlotTable => {
  const pays = { ...DEFAULT_PAYS[tier] };
  const progressive = !owned && PROGRESSIVE.includes(tier);
  if (owned && PROGRESSIVE.includes(tier)) pays.star = ownedStarPays;
  return makeTable({ weights: DEFAULT_WEIGHTS[tier], pays, berryPartial: [2, 3], lines: TIER_LINES[tier], progressive });
};

/**
 * Evaluate one payline (cells left→right), §8.1:
 *  1. three identical specials → special result;
 *  2. three Wilds → Wild pay;
 *  3. best regular S where every cell is S or Wild;
 *  4. leading Sweet Berries (Wild does not count): 1 → partial[0], 2 → partial[1].
 * Returns undefined for no result.
 */
export function evaluateLine(a: Sym, b: Sym, c: Sym, table: SlotTable): LineResult | undefined {
  if (a === b && b === c && isSpecial(a)) {
    const multiplier = a === 'star' && table.progressive ? 0 : table.pays[a];
    return { kind: 'special', symbol: a, multiplier };
  }
  if (a === 'wild' && b === 'wild' && c === 'wild') return { kind: 'wild', symbol: 'wild', multiplier: table.pays.wild };
  let best: LineResult | undefined;
  for (const s of REGULAR) {
    if ((a === s || a === 'wild') && (b === s || b === 'wild') && (c === s || c === 'wild')) {
      const m = table.pays[s];
      if (!best || m > best.multiplier) best = { kind: 'three', symbol: s, multiplier: m };
    }
  }
  if (best) return best;
  if (a === 'berries') {
    if (b === 'berries') return { kind: 'berry2', symbol: 'berries', multiplier: table.berryPartial[1] };
    return { kind: 'berry1', symbol: 'berries', multiplier: table.berryPartial[0] };
  }
  return undefined;
}

export interface LineWin extends LineResult {
  /** 1-based payline number */
  line: number;
  /** floor(multiplier × lineBet) */
  payout: number;
}

export interface SpinEval {
  grid: Grid;
  wins: LineWin[];
  /** sum of line payouts (jackpot award NOT included) */
  basePayout: number;
  /** three natural Nether Stars on a progressive machine (award once, §8.5) */
  jackpotHit: boolean;
  /** chaos trigger for this spin (highest priority special), if any */
  special?: SpecialSym;
}

export function evaluateGrid(grid: Grid, table: SlotTable, lineBet: number): SpinEval {
  const wins: LineWin[] = [];
  const specials = new Set<SpecialSym>();
  let basePayout = 0;
  let jackpotHit = false;
  for (let i = 0; i < table.lines; i++) {
    const cells = PAYLINES[i]!.map(([r, c]) => grid[r]![c]!) as [Sym, Sym, Sym];
    const r = evaluateLine(cells[0], cells[1], cells[2], table);
    if (!r) continue;
    const payout = Math.floor(r.multiplier * lineBet + 1e-9);
    basePayout += payout;
    wins.push({ ...r, line: i + 1, payout });
    if (r.kind === 'special') {
      specials.add(r.symbol as SpecialSym);
      if (r.symbol === 'star' && table.progressive) jackpotHit = true;
    }
  }
  const special = SPECIAL_PRIORITY.find((s) => specials.has(s));
  return { grid, wins, basePayout, jackpotHit, special };
}

export function weightEntries(table: SlotTable): [Sym, number][] {
  return SYMBOLS.filter((s) => table.weights[s] > 0).map((s) => [s, table.weights[s]]);
}

/** Draw a 3×3 window, every cell independent. */
export function drawGrid(rng: Rng, table: SlotTable, entries = weightEntries(table)): Grid {
  const g: Grid = [];
  for (let r = 0; r < 3; r++) {
    const row: Sym[] = [];
    for (let c = 0; c < 3; c++) row.push(weightedPick(rng, entries));
    g.push(row);
  }
  return g;
}

export const spinBetOf = (lineBet: number, table: Pick<SlotTable, 'lines'>): number => lineBet * table.lines;

/** Largest possible total return of one spin (bankroll reservation), jackpot excluded. */
export function worstCaseReturn(table: SlotTable, lineBet: number): number {
  const best = Math.max(...SYMBOLS.map((s) => table.pays[s]), ...table.berryPartial);
  return Math.ceil(best * lineBet) * table.lines;
}
