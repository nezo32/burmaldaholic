/**
 * Balance arithmetic and atomic multi-account transactions. PURE.
 * Balances are non-negative safe integers in chips. Scoreboards are int32, so the mirrored
 * scoreboard value is clamped to SCORE_MAX.
 */
export const BALANCE_MAX = Number.MAX_SAFE_INTEGER;
export const SCORE_MAX = 2_147_483_647;

export type LedgerResult = { ok: true; balance: number } | { ok: false; reason: 'insufficient' | 'invalid' };

export function applyDelta(balance: number, delta: number, max = BALANCE_MAX): LedgerResult {
  if (!Number.isSafeInteger(delta) || !Number.isSafeInteger(balance)) return { ok: false, reason: 'invalid' };
  const next = balance + delta;
  if (next < 0) return { ok: false, reason: 'insufficient' };
  return { ok: true, balance: Math.min(max, next) };
}

export const toScore = (balance: number): number => Math.min(SCORE_MAX, Math.max(0, Math.trunc(balance)));

/** One leg of a transaction. `account` is an opaque id ('player:<id>', 'bankroll:<id>', 'bank'). */
export interface TxLeg {
  account: string;
  delta: number;
}

export interface AccountView {
  /** Current balance; `Infinity` for the bank (mints/burns without limit). */
  balance: number;
  /** Chips that may not be spent (bankroll reservations). Default 0. */
  locked?: number;
  /** Upper cap; credits beyond it are dropped (player balances: economy.maxBalance). */
  max?: number;
}

export type TxPlan =
  | { ok: true; balances: Map<string, number>; dropped: Map<string, number> }
  | { ok: false; account: string; reason: 'insufficient' | 'invalid' };

/**
 * Validate every leg against the current balances and compute the new balances. Nothing is
 * applied here; the caller writes the returned balances only when `ok` (all-or-nothing).
 * Debits may not take an account below its `locked` amount. Credits above `max` are dropped
 * (reported in `dropped`) — money never goes negative anywhere.
 */
export function planTransaction(legs: readonly TxLeg[], view: (account: string) => AccountView): TxPlan {
  const sums = new Map<string, number>();
  for (const l of legs) {
    if (!Number.isSafeInteger(l.delta)) return { ok: false, account: l.account, reason: 'invalid' };
    sums.set(l.account, (sums.get(l.account) ?? 0) + l.delta);
  }
  const balances = new Map<string, number>();
  const dropped = new Map<string, number>();
  for (const [acc, delta] of sums) {
    const v = view(acc);
    if (v.balance === Infinity) continue;
    const next = v.balance + delta;
    if (delta < 0 && next < (v.locked ?? 0)) return { ok: false, account: acc, reason: 'insufficient' };
    const max = v.max ?? BALANCE_MAX;
    if (next > max) dropped.set(acc, next - max);
    balances.set(acc, Math.min(max, next));
  }
  return { ok: true, balances, dropped };
}

/** Owner bankroll of a player casino (GAME_DESIGN §18.2). */
export interface BankrollState {
  balance: number;
  reserved: number;
}

/** Owner withdrawals are limited to bankroll − reserved. */
export const bankrollAvailable = (b: BankrollState): number => Math.max(0, b.balance - b.reserved);

/** Reservation rule: accept a stake only if reserved + worstCase ≤ bankroll. */
export function tryReserve(b: BankrollState, worstCase: number): BankrollState | undefined {
  const wc = Math.max(0, Math.ceil(worstCase));
  if (b.reserved + wc > b.balance) return undefined;
  return { balance: b.balance, reserved: b.reserved + wc };
}

/**
 * Settle a round against a bankroll: the stake comes in, the payout goes out, the reservation
 * is released. The reservation guarantees `payout ≤ balance + stake`.
 */
export function settleBankroll(b: BankrollState, o: { reserved: number; stake: number; payout: number }): BankrollState {
  return {
    balance: Math.max(0, b.balance + o.stake - o.payout),
    reserved: Math.max(0, b.reserved - o.reserved),
  };
}

/** Release a reservation without settling (refund / cancel). */
export const releaseBankroll = (b: BankrollState, reserved: number): BankrollState => ({ balance: b.balance, reserved: Math.max(0, b.reserved - reserved) });
