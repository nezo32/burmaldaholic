/**
 * Chemin de fer — the player-banked variant (GAME_DESIGN §20.9). PURE.
 *
 * One seated player holds the bank B (escrowed chips) and owns the Banker hand; every other
 * seated player is a punter betting on the Player hand against the bank. Coverage
 * C = min(B, banker's max) is what punters may bet on one coup; bets are accepted in placement
 * order while Σ ≤ C (the bet crossing C is snapped down to the open coverage). Banco: one
 * punter matches the whole coverage alone (others refunded). The tableau is the fixed §20.3
 * one, so the odds of §20.2 apply. The house only takes a rake on the bank's wins.
 */
import type { Winner } from './rules';

export interface PunterStake {
  readonly id: string;
  readonly name: string;
  amount: number;
}

export interface ChemmyBank {
  readonly ownerId: string;
  readonly ownerName: string;
  /** escrowed chips (the whole bank, including what is not at risk) */
  amount: number;
  /** consecutive winning coups of this bank (bank_holder advancement) */
  wins: number;
}

export const coverage = (bank: number, bankerMax: number): number => Math.max(0, Math.min(Math.floor(bank), Math.floor(bankerMax)));
export const stakedTotal = (stakes: readonly PunterStake[]): number => stakes.reduce((a, s) => a + s.amount, 0);
export const openCoverage = (stakes: readonly PunterStake[], cover: number): number => Math.max(0, cover - stakedTotal(stakes));

export type ChemmyBetError =
  | { code: 'invalid_amount' }
  | { code: 'bet_too_low'; min: number }
  | { code: 'total_max'; max: number }
  | { code: 'coverage'; open: number }
  | { code: 'banco_taken' }
  | { code: 'banco_funds'; need: number };

/**
 * A punter bet of `amount`: returns the accepted amount (snapped down to the open coverage)
 * or an error. `punterMax` = min(table max, the punter's tier max) for their total this coup.
 */
export function acceptBet(
  stakes: readonly PunterStake[],
  cover: number,
  id: string,
  amount: number,
  min: number,
  punterMax: number,
  bancoCalled: boolean,
): { ok: true; amount: number; snapped: boolean } | { ok: false; error: ChemmyBetError } {
  if (!Number.isSafeInteger(amount) || amount <= 0) return { ok: false, error: { code: 'invalid_amount' } };
  if (bancoCalled) return { ok: false, error: { code: 'banco_taken' } };
  const own = stakes.find((s) => s.id === id)?.amount ?? 0;
  if (own + amount < min) return { ok: false, error: { code: 'bet_too_low', min } };
  // The punter's own max is a hard limit; only the coverage snaps a bet down.
  if (own + amount > punterMax) return { ok: false, error: { code: 'total_max', max: punterMax } };
  const open = openCoverage(stakes, cover);
  if (open <= 0) return { ok: false, error: { code: 'coverage', open: 0 } };
  const a = Math.min(amount, open);
  if (own + a < min) return { ok: false, error: { code: 'coverage', open } };
  return { ok: true, amount: a, snapped: a < amount };
}

/** Whether `id` may call Banco: nobody did yet, and balance and max both reach C. */
export function bancoCheck(cover: number, balancePlusOwn: number, punterMax: number, bancoCalled: boolean): ChemmyBetError | undefined {
  if (bancoCalled) return { code: 'banco_taken' };
  if (cover <= 0) return { code: 'coverage', open: 0 };
  if (balancePlusOwn < cover || punterMax < cover) return { code: 'banco_funds', need: cover };
  return undefined;
}

/**
 * Banco: `id` matches the whole coverage alone. Returns the other stakes to refund and the
 * extra chips `id` has to put up (C − own stake).
 */
export function applyBanco(stakes: readonly PunterStake[], id: string, name: string, cover: number): { stakes: PunterStake[]; refunds: PunterStake[]; extra: number } {
  const own = stakes.find((s) => s.id === id)?.amount ?? 0;
  return {
    stakes: [{ id, name, amount: cover }],
    refunds: stakes.filter((s) => s.id !== id).map((s) => ({ ...s })),
    extra: cover - own,
  };
}

export interface ChemmySettlement {
  /** Σ punter stakes matched this coup (the banker's wagered) */
  matched: number;
  /** house rake (banker wins only) */
  rake: number;
  /** change of the bank amount (+W − rake, −Σ or 0) */
  bankDelta: number;
  /** banker's net result this coup (bankDelta) */
  bankerNet: number;
  /** total return per punter (stake included): 2a on a Player win, a on a tie, 0 on a Banker win */
  punterReturns: Map<string, number>;
}

export function settleChemmy(winner: Winner, stakes: readonly PunterStake[], rakePercent: number): ChemmySettlement {
  const matched = stakedTotal(stakes);
  const punterReturns = new Map<string, number>();
  let bankDelta = 0;
  let rake = 0;
  if (winner === 'banker') {
    rake = Math.max(0, Math.floor(matched * rakePercent + 1e-9));
    bankDelta = matched - rake;
    for (const s of stakes) punterReturns.set(s.id, 0);
  } else if (winner === 'player') {
    bankDelta = -matched;
    for (const s of stakes) punterReturns.set(s.id, 2 * s.amount);
  } else {
    for (const s of stakes) punterReturns.set(s.id, s.amount);
  }
  return { matched, rake, bankDelta, bankerNet: bankDelta, punterReturns };
}

/**
 * Next bank candidate clockwise: the first seated id after `after` (by seat order, wrapping)
 * that has not passed this round of offers. `order` = seated ids by seat number.
 */
export function nextCandidate(order: readonly string[], after: string | undefined, passed: ReadonlySet<string>): string | undefined {
  if (!order.length) return undefined;
  const start = after === undefined ? -1 : order.indexOf(after);
  for (let i = 1; i <= order.length; i++) {
    const id = order[(start + i + order.length) % order.length]!;
    if (!passed.has(id)) return id;
  }
  return undefined;
}

/** bank_holder (§19): one bank kept through this many winning coups in a row. */
export const BANK_HOLDER_WINS = 5;

/** Edges per chip (§20.9) from the exact 8-deck probabilities. */
export function chemmyEdges(pBanker: number, pPlayer: number, rakePercent: number): { banker: number; punter: number; house: number } {
  return {
    banker: (1 - rakePercent) * pBanker - pPlayer,
    punter: pPlayer - pBanker,
    house: rakePercent * pBanker,
  };
}
