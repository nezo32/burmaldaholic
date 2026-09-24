/**
 * Owner bankroll solvency (GAME_DESIGN §18.2). PURE.
 *
 * The reservation itself lives in core (`economy.reserve` via `wagers.place({house, worstCase})`).
 * This file knows the worst-case TOTAL return of one round per unit of bet for every game (used
 * for the insolvency rule "bankroll < the smallest worst case of any table at its minimum bet")
 * and how to route a house result into a bankroll when a game did not bank the round itself.
 */
import { type BankrollState, bankrollAvailable } from '../../core/logic/ledger';

/**
 * Worst-case total return (stake included) of one round, per chip of the round's base bet.
 * Keys are table handler ids (block param `game`), optionally `<game>.<variant>`.
 *  - blackjack: 4 split hands, all doubled, all win 1:1 (16×) + insurance ½ bet at 2:1 (1.5×)
 *  - roulette: straight-up 35:1 → 36×
 *  - craps: Pass + 5× odds on 6/8 at 6:5 → 2 + 5 × 2.2 = 13×
 *  - slots (v2, SLOTS.md §8.6): the max-win cap bounds the whole spin incl. features and the fixed owned jackpots:
 *    Overworld Riches (copper) 500×, Nether Inferno (gold) 2 000×, End Void (netherite) 5 000× the bet (the
 *    slots module pushes the configured `slots.<m>.maxWinMultiple` through `setWorstCase`)
 *  - plinko: High bin 0/12 = 170×; wheel: X segment 10×; scratch: 2 500 / 10 = 250×
 *  - coin flip / dice duel: 2×; poker: PvP, the house (owner) only takes rake → 0
 */
export const WORST_CASE_PER_CHIP: Readonly<Record<string, number>> = {
  blackjack: 17.5,
  roulette: 36,
  craps: 13,
  slots: 5000,
  'slots.copper': 500,
  'slots.gold': 2000,
  'slots.netherite': 5000,
  plinko: 170,
  wheel: 10,
  scratch: 250,
  coin_flip: 2,
  dice_duel: 2,
  poker: 0,
};

/** Used for a table whose game is unknown to this table (conservative). */
export const UNKNOWN_WORST_CASE_PER_CHIP = 36;

/** Worst-case multiplier for a table (`game.variant` first, then `game`, then the fallback). */
export function worstCasePerChip(game: string, variant?: string, overrides: Readonly<Record<string, number>> = {}): number {
  const keys = variant ? [`${game}.${variant}`, game] : [game];
  for (const k of keys) {
    const v = overrides[k] ?? WORST_CASE_PER_CHIP[k];
    if (v !== undefined) return v;
  }
  return UNKNOWN_WORST_CASE_PER_CHIP;
}

/** Worst-case total return of one round at `bet` (integer chips, rounded up). */
export const worstCaseAt = (perChip: number, bet: number): number => Math.ceil(Math.max(0, perChip) * Math.max(0, bet));

export interface ExposedTable {
  game: string;
  variant?: string;
  /** effective table minimum bet (≥ 1) */
  minBet: number;
  open: boolean;
}

/**
 * The smallest worst case among open tables that expose the bankroll (poker = 0 is ignored).
 * `undefined` when no open table risks the bankroll.
 */
export function cheapestWorstCase(tables: readonly ExposedTable[], overrides: Readonly<Record<string, number>> = {}): number | undefined {
  let best: number | undefined;
  for (const t of tables) {
    if (!t.open) continue;
    const wc = worstCaseAt(worstCasePerChip(t.game, t.variant, overrides), Math.max(1, t.minBet));
    if (wc <= 0) continue;
    if (best === undefined || wc < best) best = wc;
  }
  return best;
}

/**
 * Insolvency (§18.2): the casino is "broke" while its bankroll is below the smallest worst case
 * of any of its open tables at the table minimum. It reopens as soon as the bankroll covers it.
 */
export function isBroke(bankroll: BankrollState, tables: readonly ExposedTable[], overrides: Readonly<Record<string, number>> = {}): boolean {
  const need = cheapestWorstCase(tables, overrides);
  return need !== undefined && bankroll.balance < need;
}

export interface RoutedResult {
  /** signed change to the bankroll (house profit is positive) */
  bankrollDelta: number;
  /** part of a house loss the bankroll could not cover (paid by the world bank; should be 0) */
  bankCovered: number;
}

/**
 * Route one settled house round into a bankroll AFTER the world bank already settled it with
 * the player (fallback for games that do not pass `house` to `wagers.place`). The bank receives
 * the opposite of `bankrollDelta`. A house loss is limited to the unreserved bankroll so that
 * reservations of properly banked rounds stay intact; the remainder is reported.
 */
export function routeHouseResult(b: BankrollState, staked: number, totalReturn: number): RoutedResult {
  const houseNet = Math.floor(staked) - Math.floor(totalReturn);
  if (houseNet >= 0) return { bankrollDelta: houseNet, bankCovered: 0 };
  const loss = -houseNet;
  const take = Math.min(loss, bankrollAvailable(b));
  return { bankrollDelta: -take, bankCovered: loss - take };
}
