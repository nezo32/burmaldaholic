/**
 * Money-bot funding legs for `economy.transact` (BOTS.md §5.1). Bot chips are escrowed in the BANK like
 * a human buy-in: BANK purse → nothing moves when the bot sits or leaves (the bank mints / sinks);
 * BANKROLL purse → sit = bankroll → bank (must be covered by `bankrollAvailable`), leave = bank → bankroll.
 * In a PvP settle / poker pot the bot's leg is `purseAccount(purse)`.
 */
import type { TxAccount, TxOp } from '../economy';
import type { Purse } from '../logic/bots/types';

export const purseAccount = (p: Purse): TxAccount => (p.kind === 'BANKROLL' ? { bankroll: p.id } : 'bank');

/** Legs that fund a bot sitting down with `amount` (empty for bank / none purses). */
export function fundLegs(p: Purse, amount: number): TxOp[] {
  if (p.kind !== 'BANKROLL' || amount <= 0) return [];
  return [
    { account: { bankroll: p.id }, delta: -amount },
    { account: 'bank', delta: amount },
  ];
}

/** Legs that return what a leaving bot holds. */
export function returnLegs(p: Purse, amount: number): TxOp[] {
  if (p.kind !== 'BANKROLL' || amount <= 0) return [];
  return [
    { account: 'bank', delta: -amount },
    { account: { bankroll: p.id }, delta: amount },
  ];
}
