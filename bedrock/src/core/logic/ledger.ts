/**
 * Balance arithmetic. PURE. Balances are non-negative safe integers in chips.
 * Scoreboards are int32, so the mirrored scoreboard value is clamped to SCORE_MAX.
 */
export const BALANCE_MAX = Number.MAX_SAFE_INTEGER;
export const SCORE_MAX = 2_147_483_647;

export type LedgerResult = { ok: true; balance: number } | { ok: false; reason: 'insufficient' | 'invalid' };

export function applyDelta(balance: number, delta: number): LedgerResult {
  if (!Number.isSafeInteger(delta) || !Number.isSafeInteger(balance)) return { ok: false, reason: 'invalid' };
  const next = balance + delta;
  if (next < 0) return { ok: false, reason: 'insufficient' };
  return { ok: true, balance: Math.min(BALANCE_MAX, next) };
}

export const toScore = (balance: number): number => Math.min(SCORE_MAX, Math.max(0, Math.trunc(balance)));
