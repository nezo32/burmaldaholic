/**
 * Progressive jackpot pool (GAME_DESIGN §8.5). PURE.
 *
 * - One pool per progressive tier per world, seeded (minted by the bank).
 * - Every spin adds floor(spinBet × contribution); the fractional part accumulates in a hidden
 *   remainder so the long-run rate is exact.
 * - Win: award = floor(pool × min(1, spinBet / machineMaxSpinBet)); pool −= award; if the pool
 *   falls below the seed it is topped back up to the seed.
 */

export interface PoolState {
  pool: number;
  /** hidden fractional remainder in [0, 1) */
  rem: number;
}

export const newPool = (seed: number): PoolState => ({ pool: Math.max(0, Math.floor(seed)), rem: 0 });

/** Sanitize persisted state (missing/corrupt → seed). */
export function loadPool(raw: unknown, seed: number): PoolState {
  const o = raw as Partial<PoolState> | undefined;
  if (!o || typeof o.pool !== 'number' || !Number.isFinite(o.pool) || o.pool < 0) return newPool(seed);
  const rem = typeof o.rem === 'number' && o.rem >= 0 && o.rem < 1 ? o.rem : 0;
  return { pool: Math.floor(o.pool), rem };
}

/** Add one spin's contribution. Returns the new state and the whole chips added. */
export function contribute(s: PoolState, spinBet: number, rate: number): { state: PoolState; added: number } {
  if (!(rate > 0) || !(spinBet > 0)) return { state: s, added: 0 };
  const exact = s.rem + spinBet * rate;
  // tolerate float noise (e.g. 100 × 0.015 = 1.4999999)
  const added = Math.floor(exact + 1e-9);
  const rem = Math.max(0, exact - added);
  return { state: { pool: s.pool + added, rem: rem < 1 - 1e-9 ? rem : 0 }, added };
}

/** Jackpot award for a spin bet (share of the pool proportional to the bet, capped at 1). */
export function awardFor(s: PoolState, spinBet: number, machineMaxSpinBet: number): number {
  const share = machineMaxSpinBet > 0 ? Math.min(1, spinBet / machineMaxSpinBet) : 1;
  return Math.floor(s.pool * share);
}

/** Pay a jackpot: returns the award and the new state (topped up to the seed). */
export function payJackpot(s: PoolState, spinBet: number, machineMaxSpinBet: number, seed: number): { state: PoolState; award: number; toppedUp: number } {
  const award = awardFor(s, spinBet, machineMaxSpinBet);
  const left = s.pool - award;
  const floorSeed = Math.max(0, Math.floor(seed));
  const pool = Math.max(left, floorSeed);
  return { state: { pool, rem: s.rem }, award, toppedUp: pool - left };
}
