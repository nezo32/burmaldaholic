/**
 * Scratch Cards (GAME_DESIGN §11.3). PURE.
 *
 * The outcome is drawn on the FIRST scratch from the card's prize table, then a 3×3 face is
 * built to match it:
 *  - winning card: exactly 3 cells show the prize symbol; the other 6 show other symbols, each
 *    at most twice;
 *  - losing card: no symbol appears 3+ times;
 *  - Creeper card (share `creeperChance` of losing cards): 3 creeper cells, no prize.
 * A symbol is a prize amount (> 0) shown as a number; `CREEPER` (0) is the creeper symbol.
 */
import { type Rng, shuffle } from '../../../core/logic/rng';

export type ScratchKind = 'basic' | 'gold';
export const SCRATCH_KINDS: readonly ScratchKind[] = ['basic', 'gold'];
export const CREEPER = 0;
export const CELLS = 9;

export type PrizeTable = readonly (readonly [prize: number, probability: number])[];

export const DEFAULT_PRIZES: Readonly<Record<ScratchKind, PrizeTable>> = {
  basic: [
    [10, 0.22],
    [20, 0.1],
    [50, 0.03],
    [100, 0.01],
    [500, 0.002],
    [2500, 0.0001],
  ],
  gold: [
    [100, 0.2],
    [200, 0.1],
    [500, 0.05],
    [1000, 0.01],
    [5000, 0.001],
    [25000, 0.0002],
  ],
};

export const DEFAULT_PRICES: Readonly<Record<ScratchKind, number>> = { basic: 10, gold: 100 };
/** Minimum VIP tier to buy (Basic: Bronze, Gold: Silver). */
export const MIN_TIER: Readonly<Record<ScratchKind, number>> = { basic: 0, gold: 1 };

/**
 * Config `extras.scratch.<kind>.prizes` → a clean table: [int prize ≥ 1, p ≥ 0] pairs, merged
 * by prize, sorted ascending; if Σp > 1 the probabilities are scaled down to sum to 1.
 * Malformed input falls back to the default table.
 */
export function prizeTable(value: unknown, kind: ScratchKind): PrizeTable {
  if (!Array.isArray(value)) return DEFAULT_PRIZES[kind];
  const m = new Map<number, number>();
  for (const row of value) {
    if (!Array.isArray(row) || row.length !== 2) return DEFAULT_PRIZES[kind];
    const [prize, p] = row as unknown[];
    if (typeof prize !== 'number' || typeof p !== 'number' || !Number.isFinite(prize) || !Number.isFinite(p)) return DEFAULT_PRIZES[kind];
    const amount = Math.floor(prize);
    if (amount < 1 || p <= 0) continue;
    m.set(amount, (m.get(amount) ?? 0) + p);
  }
  if (!m.size) return DEFAULT_PRIZES[kind];
  const total = [...m.values()].reduce((a, b) => a + b, 0);
  const scale = total > 1 ? 1 / total : 1;
  return [...m.entries()].sort((a, b) => a[0] - b[0]).map(([a, p]) => [a, p * scale] as const);
}

/** RTP of a card: Σ prize × p / price. */
export const scratchRtp = (table: PrizeTable, price: number): number => table.reduce((s, [a, p]) => s + a * p, 0) / price;

export const topPrize = (table: PrizeTable): number => table.reduce((m, [a]) => Math.max(m, a), 0);

export interface ScratchOutcome {
  /** chips won (0 = losing card) */
  prize: number;
  /** losing card with three creepers (chaos mob_wave) */
  creeper: boolean;
}

/** Draw the outcome of one card. */
export function drawScratch(rng: Rng, table: PrizeTable, creeperChance: number): ScratchOutcome {
  let r = rng.next();
  for (const [prize, p] of table) {
    if (r < p) return { prize, creeper: false };
    r -= p;
  }
  return { prize: 0, creeper: rng.next() < creeperChance };
}

/**
 * The symbols a card of this table may show. Tables with fewer than 4 prizes get decoy amounts
 * (never a prize of this table) so a losing card can still avoid any triple.
 */
export function symbolsFor(table: PrizeTable): number[] {
  const s = table.map(([a]) => a);
  const base = Math.max(1, s[0] ?? 1);
  for (let k = 2; s.length < 4; k++) if (!s.includes(base * k)) s.push(base * k);
  return s;
}

/** Take `n` symbols from `pool`, each at most twice, in random order. */
function upToTwice(rng: Rng, pool: readonly number[], n: number): number[] {
  return shuffle(rng, [...pool, ...pool]).slice(0, n);
}

/** Build the 3×3 face (row-major) for an outcome. */
export function buildFace(rng: Rng, table: PrizeTable, o: ScratchOutcome): number[] {
  const symbols = symbolsFor(table);
  let cells: number[];
  if (o.prize > 0) {
    cells = [o.prize, o.prize, o.prize, ...upToTwice(rng, [...symbols.filter((s) => s !== o.prize), CREEPER], CELLS - 3)];
  } else if (o.creeper) {
    cells = [CREEPER, CREEPER, CREEPER, ...upToTwice(rng, symbols, CELLS - 3)];
  } else {
    cells = upToTwice(rng, [...symbols, CREEPER], CELLS);
  }
  return shuffle(rng, cells);
}

/** Counts per symbol (tests + result detection). */
export function symbolCounts(cells: readonly number[]): Map<number, number> {
  const m = new Map<number, number>();
  for (const c of cells) m.set(c, (m.get(c) ?? 0) + 1);
  return m;
}

/** Check a face against the §11.3 rules for its outcome. */
export function faceMatches(cells: readonly number[], o: ScratchOutcome): boolean {
  if (cells.length !== CELLS) return false;
  const counts = symbolCounts(cells);
  const triples = [...counts.entries()].filter(([, n]) => n >= 3);
  if (o.prize > 0) return triples.length === 1 && triples[0]?.[0] === o.prize && triples[0][1] === 3;
  if (o.creeper) return triples.length === 1 && triples[0]?.[0] === CREEPER && triples[0][1] === 3;
  return triples.length === 0;
}

/** A card being scratched (persisted on the player so it survives closing the form). */
export interface ScratchCard {
  kind: ScratchKind;
  price: number;
  cells: number[];
  /** cells revealed so far, in order 0…8 */
  revealed: number;
  prize: number;
  creeper: boolean;
  top: boolean;
}

export function newCard(rng: Rng, kind: ScratchKind, price: number, table: PrizeTable, o: ScratchOutcome): ScratchCard {
  return { kind, price, cells: buildFace(rng, table, o), revealed: 0, prize: o.prize, creeper: o.creeper, top: o.prize > 0 && o.prize === topPrize(table) };
}

/** Reveal one more cell (or all). Returns true when the card is fully scratched. */
export function scratch(card: ScratchCard, all = false): boolean {
  card.revealed = all ? CELLS : Math.min(CELLS, card.revealed + 1);
  return card.revealed >= CELLS;
}

/** Structural check for a card read back from storage. */
export function isScratchCard(x: unknown): x is ScratchCard {
  if (!x || typeof x !== 'object') return false;
  const c = x as Partial<ScratchCard>;
  return (
    (c.kind === 'basic' || c.kind === 'gold') &&
    typeof c.price === 'number' &&
    Array.isArray(c.cells) &&
    c.cells.length === CELLS &&
    c.cells.every((v) => typeof v === 'number') &&
    typeof c.revealed === 'number' &&
    typeof c.prize === 'number' &&
    typeof c.creeper === 'boolean'
  );
}
