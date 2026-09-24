/**
 * Owned-table settings and statistics (GAME_DESIGN §18.2, UI.md §11). PURE.
 */
import { parseAmount } from '../../core/logic/bet';

/** Per-table owner settings. `min`/`max` undefined = the game's own limits. */
export interface OwnedTable {
  /** casino id, or undefined while the table is inactive (charter removed) */
  casinoId?: string;
  ownerId: string;
  game: string;
  variant?: string;
  blockTypeId?: string;
  open: boolean;
  min?: number;
  max?: number;
  /** poker: allow bots */
  bots: boolean;
  /** slots (SLOTS.md §8.6): bonus buy / autoplay allowed at this machine (undefined = on) */
  slotsBuy?: boolean;
  slotsAutoplay?: boolean;
}

export const newOwnedTable = (casinoId: string, ownerId: string, game: string, variant?: string, blockTypeId?: string): OwnedTable => ({
  casinoId,
  ownerId,
  game,
  variant,
  blockTypeId,
  open: true,
  bots: true,
});

export type LimitsInput = { ok: true; min?: number; max?: number } | { ok: false; error: 'invalid' | 'min_over_max' | 'over_global'; limit?: number };

/**
 * Validate the owner's Min/Max text fields. Empty = no owner limit. Max may not exceed the
 * global table max (the highest VIP tier max); the tier max still applies to each player.
 */
export function parseLimits(minText: string, maxText: string, globalMax: number): LimitsInput {
  const read = (s: string): number | undefined | null => {
    const v = s.trim();
    if (!v) return undefined;
    const n = parseAmount(v);
    return n === undefined || !Number.isSafeInteger(n) || n < 1 ? null : n;
  };
  const min = read(minText);
  const max = read(maxText);
  if (min === null || max === null) return { ok: false, error: 'invalid' };
  if (max !== undefined && max > globalMax) return { ok: false, error: 'over_global', limit: globalMax };
  if (min !== undefined && min > globalMax) return { ok: false, error: 'over_global', limit: globalMax };
  if (min !== undefined && max !== undefined && min > max) return { ok: false, error: 'min_over_max' };
  return { ok: true, min, max };
}

export interface BaseLimits {
  min?: number;
  tableMax?: number;
  tierMultiplier?: number;
  minTier?: number;
}

/** Game limits narrowed by the owner's settings (the owner can only tighten them). */
export function applyOwnerLimits<L extends BaseLimits>(base: L, t: Pick<OwnedTable, 'min' | 'max'> | undefined): L {
  if (!t) return base;
  const out: L = { ...base };
  if (t.min !== undefined) out.min = Math.max(base.min ?? 1, t.min);
  if (t.max !== undefined) out.tableMax = base.tableMax === undefined ? t.max : Math.min(base.tableMax, t.max);
  return out;
}

/** Effective minimum bet of an owned table (for the insolvency rule). */
export const effectiveMin = (t: Pick<OwnedTable, 'min'>, gameMin = 1): number => Math.max(1, gameMin, t.min ?? 1);

// ---- statistics ------------------------------------------------------------------------

export interface Tally {
  /** chips staked at owned tables */
  handle: number;
  /** chips paid back to players (stake included) */
  paid: number;
  /** poker rake collected */
  rake: number;
  rounds: number;
}

export interface CasinoStats {
  /** world day index of `today` */
  day: number;
  today: Tally;
  total: Tally;
}

const zero = (): Tally => ({ handle: 0, paid: 0, rake: 0, rounds: 0 });
export const emptyStats = (day: number): CasinoStats => ({ day, today: zero(), total: zero() });

/** Day index of a world tick (24 000 ticks per Minecraft day). */
export const dayOf = (tick: number): number => Math.floor(tick / 24000);

/** Owner profit of a tally: handle − payouts + rake. */
export const profit = (t: Tally): number => t.handle - t.paid + t.rake;

function roll(s: CasinoStats, day: number): CasinoStats {
  return s.day === day ? { day, today: { ...s.today }, total: { ...s.total } } : { day, today: zero(), total: { ...s.total } };
}

/** Normalize possibly-stale stats to `day` (a new day resets `today`). */
export const statsFor = (s: CasinoStats | undefined, day: number): CasinoStats => (s ? roll(s, day) : emptyStats(day));

/** Record one settled house round. */
export function recordRound(s: CasinoStats | undefined, day: number, staked: number, paid: number): CasinoStats {
  const n = statsFor(s, day);
  for (const t of [n.today, n.total]) {
    t.handle += Math.max(0, Math.floor(staked));
    t.paid += Math.max(0, Math.floor(paid));
    t.rounds += 1;
  }
  return n;
}

/** Record poker rake paid into the bankroll. */
export function recordRake(s: CasinoStats | undefined, day: number, rake: number): CasinoStats {
  const n = statsFor(s, day);
  for (const t of [n.today, n.total]) t.rake += Math.max(0, Math.floor(rake));
  return n;
}
