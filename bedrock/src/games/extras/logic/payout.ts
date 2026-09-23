/**
 * Shared payout helpers for the extras games. PURE.
 */

/**
 * floor(stake × mult), robust to binary floating point (1.4 × 15 = 20.999999999999996 → 21).
 * Multipliers in the spec have at most 2 decimals, so a tiny epsilon is safe.
 */
export function floorPay(stake: number, mult: number): number {
  if (!(stake > 0) || !(mult > 0)) return 0;
  return Math.floor(stake * mult + 1e-9);
}

/** Expected return per unit staked for a discrete distribution of (probability, multiplier). */
export function expectedMultiplier(entries: readonly (readonly [number, number])[]): number {
  return entries.reduce((s, [p, m]) => s + p * m, 0);
}
