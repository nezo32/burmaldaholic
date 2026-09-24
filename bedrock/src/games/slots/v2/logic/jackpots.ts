/**
 * Progressive jackpot maths (SLOTS.md §5); twin of Java `Jackpots`. PURE. Integers: safe below 2^53.
 *
 * Pool state per machine and tier: `inc` (chips above the seed) + `rem` (hidden fractional remainder of the
 * contributions, in millionths of a chip). The pool shown/awarded is `seed + inc`.
 */
import type { JackpotDef, MachineDef, MachineId, SpinTape } from './types';

export const MINI = 1;
export const MINOR = 2;
export const MAJOR = 3;
export const GRAND = 4;

/** floor((seed + increment) × min(bet, ref) / ref) */
export const jackpotAward = (seed: number, increment: number, bet: number, ref: number): number => Math.floor(((seed + increment) * Math.min(bet, ref)) / ref);

/** increment × (1 − r), floored */
export const incrementAfter = (increment: number, bet: number, ref: number): number => Math.floor((increment * (ref - Math.min(bet, ref))) / ref);

export interface TierPool {
  inc: number;
  /** millionths of a chip */
  rem: number;
}

/** index 1..4 (0 unused) */
export type MachinePools = TierPool[];
export type AllPools = Record<MachineId, MachinePools>;

const MICRO = 1_000_000;

export const emptyPools = (): MachinePools => [0, 1, 2, 3, 4].map(() => ({ inc: 0, rem: 0 }));

/** Sanitize a persisted pool set (corrupt → empty). */
export function loadPools(raw: unknown): MachinePools {
  const out = emptyPools();
  if (!Array.isArray(raw)) return out;
  for (let t = 1; t <= 4; t++) {
    const p = raw[t] as Partial<TierPool> | undefined;
    const inc = typeof p?.inc === 'number' && Number.isSafeInteger(p.inc) && p.inc >= 0 ? p.inc : 0;
    const rem = typeof p?.rem === 'number' && Number.isInteger(p.rem) && p.rem >= 0 && p.rem < MICRO ? p.rem : 0;
    out[t] = { inc, rem };
  }
  return out;
}

/** Pool values (seed + inc) per tier, index 1..4. */
export const poolValues = (j: JackpotDef, pools: MachinePools): number[] => [0, 1, 2, 3, 4].map((t) => (t === 0 ? 0 : j.seeds[t]! + pools[t]!.inc));

/**
 * Add `contribution × stake` to every tier (SLOTS.md §5.1). Exact: the rate is rounded to millionths once and
 * the fractional chips accumulate in `rem`. Returns the chips added per tier.
 */
export function contribute(j: JackpotDef, pools: MachinePools, stake: number): number[] {
  const added = [0, 0, 0, 0, 0];
  for (let t = 1; t <= 4; t++) {
    const rate = Math.round(j.contribution[t]! * MICRO);
    const acc = pools[t]!.rem + stake * rate;
    const whole = Math.floor(acc / MICRO);
    pools[t] = { inc: pools[t]!.inc + whole, rem: acc - whole * MICRO };
    added[t] = whole;
  }
  return added;
}

/** Apply the progressive awards of a drawn tape (in tape order) to the pools; returns the chips debited. */
export function applyAwards(def: MachineDef, pools: MachinePools, tape: SpinTape): number {
  let paid = 0;
  for (const a of tape.jackpots) {
    if (a.owned) continue;
    const p = pools[a.tier]!;
    const pool = def.jackpot.seeds[a.tier]! + p.inc;
    const award = jackpotAward(def.jackpot.seeds[a.tier]!, p.inc, tape.bet, def.jackpot.ref);
    if (award !== a.chips) throw new Error(`jackpot award mismatch: tape ${a.chips}, pool ${award} (${pool})`);
    pools[a.tier] = { inc: incrementAfter(p.inc, tape.bet, def.jackpot.ref), rem: p.rem };
    paid += award;
  }
  return paid;
}

/**
 * What other players' meters show (SLOTS.md §5.2 timing): a pool debited by a spin that is drawn but not revealed
 * yet keeps showing its value from before the award, so nobody sees a meter drop before the winner does.
 */
export function displayedPools(values: readonly number[], pending: ReadonlyArray<{ tier: number; before: number }>): number[] {
  const out = values.slice();
  for (const p of pending) out[p.tier] = Math.max(out[p.tier] ?? 0, p.before);
  return out;
}

/** Owned casino: fixed prize (× bet) per tier. */
export const ownedPrize = (j: JackpotDef, tier: number, bet: number): number => j.owned[tier]! * bet;

/** Admin reset: the increments leave the economy (SLOTS.md §5.2). Returns the chips removed. */
export function resetPools(pools: MachinePools): number {
  let removed = 0;
  for (let t = 1; t <= 4; t++) {
    removed += pools[t]!.inc;
    pools[t] = { inc: 0, rem: 0 };
  }
  return removed;
}

/**
 * v1 → v2 pool migration (SLOTS.md §5.3): the Golden Reels increment (pool − old seed) goes to the Nether Grand
 * increment, the Netherite increment to the End Grand; old seeds are dropped. Returns the chips moved.
 */
export function migrateV1Pools(
  all: AllPools,
  v1: { gold?: { pool?: number }; netherite?: { pool?: number } } | undefined,
  oldSeeds: { gold: number; netherite: number },
): { nether: number; end: number } {
  const inc = (p: { pool?: number } | undefined, seed: number): number => {
    const v = typeof p?.pool === 'number' && Number.isFinite(p.pool) ? Math.floor(p.pool) : seed;
    return Math.max(0, v - seed);
  };
  const nether = inc(v1?.gold, oldSeeds.gold);
  const end = inc(v1?.netherite, oldSeeds.netherite);
  all.nether[GRAND]!.inc += nether;
  all.end[GRAND]!.inc += end;
  return { nether, end };
}
