/**
 * Ultimate Texas Hold'em limits, reservations and the player bank (GAME_DESIGN §21.2, §21.6,
 * §21.9). PURE.
 */
import { type Paytable, maxPay } from './paytable';

/** Largest Play bet multiple (Bet ×4 preflop). */
export const MAX_PLAY_MULTIPLE = 4;

/** Worst-case total W = 6 × Ante + Trips (Ante + Blind + ×4 Play + Trips); VIP / table max applies to W. */
export const worstCaseTotal = (ante: number, trips: number): number => 6 * ante + trips;

/** Chips debited at confirmation: Ante + Blind + Trips. */
export const confirmCost = (ante: number, trips: number): number => 2 * ante + trips;

/**
 * House / bank reservation per seat (§21.6): the largest net loss of the house,
 * `Ante × (4 + 1 + max Blind pay) + Trips × max Trips pay` = 505 × Ante + 50 × Trips at defaults.
 */
export function seatReservation(ante: number, trips: number, blindPays: Readonly<Paytable>, tripsPays: Readonly<Paytable>): number {
  return Math.ceil(ante * (MAX_PLAY_MULTIPLE + 1 + maxPay(blindPays)) + trips * maxPay(tripsPays));
}

/** Reservation per chip of Ante (the insolvency test for owned casinos uses Ante 1 → 505). */
export const reservationPerAnte = (blindPays: Readonly<Paytable>): number => MAX_PLAY_MULTIPLE + 1 + maxPay(blindPays);

/** Largest Ante a bank with `available` unreserved chips still covers with `trips` alongside. */
export function maxCoveredAnte(available: number, trips: number, blindPays: Readonly<Paytable>, tripsPays: Readonly<Paytable>): number {
  const per = reservationPerAnte(blindPays);
  return Math.max(0, Math.floor((available - trips * maxPay(tripsPays)) / per));
}

export interface BetLimits {
  /** minimum Ante (and minimum non-zero Trips) */
  minAnte: number;
  /** maximum W = 6 × Ante + Trips (min(table max, tier max × multiplier)) */
  maxTotal: number;
  tripsEnabled: boolean;
}

export type BetError =
  | { key: 'ante_min'; min: number }
  | { key: 'worst_case_max'; max: number }
  | { key: 'trips_off' }
  | { key: 'trips_needs_ante' }
  | { key: 'trips_min'; min: number }
  | { key: 'insufficient'; balance: number }
  | { key: 'keep_for_river'; keep: number };

/** Validate an Ante / Trips pair (§21.2). `balance` must cover 2A + T and leave ≥ A. */
export function validateBets(ante: number, trips: number, l: BetLimits, balance: number): BetError | undefined {
  if (!Number.isSafeInteger(ante) || !Number.isSafeInteger(trips) || trips < 0 || ante < 0) return { key: 'ante_min', min: l.minAnte };
  if (trips > 0 && !l.tripsEnabled) return { key: 'trips_off' };
  if (ante <= 0) return trips > 0 ? { key: 'trips_needs_ante' } : { key: 'ante_min', min: l.minAnte };
  if (ante < l.minAnte) return { key: 'ante_min', min: l.minAnte };
  if (trips > 0 && trips < l.minAnte) return { key: 'trips_min', min: l.minAnte };
  if (worstCaseTotal(ante, trips) > l.maxTotal) return { key: 'worst_case_max', max: l.maxTotal };
  if (confirmCost(ante, trips) > balance) return { key: 'insufficient', balance };
  if (confirmCost(ante, trips) + ante > balance) return { key: 'keep_for_river', keep: ante };
  return undefined;
}

/** Ante range shown in the bet form: [minAnte, floor((maxTotal − trips) / 6)]. */
export function anteRange(l: BetLimits, trips = 0): { min: number; max: number } {
  return { min: l.minAnte, max: Math.max(0, Math.floor((l.maxTotal - trips) / 6)) };
}

// ---- player bank (§21.9) ----------------------------------------------------------------

export interface BankRound {
  /** Σ over seats of (stake − total return): positive = the bank won */
  bankerNet: number;
  rake: number;
  /** what the bank changes by: bankerNet − rake */
  bankDelta: number;
}

/** Rake only on a positive banker net: floor(net × rakePercent). */
export function bankRound(seatNets: readonly number[], rakePercent: number): BankRound {
  const bankerNet = -seatNets.reduce((s, x) => s + x, 0);
  const rake = bankerNet > 0 ? Math.floor(bankerNet * Math.max(0, rakePercent)) : 0;
  return { bankerNet, rake, bankDelta: bankerNet - rake };
}

export interface BankerCheck {
  vipTier: number;
  minVip: number;
  owes: boolean;
  isOwner: boolean;
  seatTaken: boolean;
  betsConfirmed: boolean;
  bank: number;
  minBank: number;
  balance: number;
}

export type BankerError = 'vip' | 'pvp_owing' | 'owner' | 'seat_taken' | 'seat_next_round' | 'min_bank' | 'insufficient';

/** May this player take the dealer seat with bank B now (§21.9)? */
export function checkBanker(c: BankerCheck): BankerError | undefined {
  if (c.seatTaken) return 'seat_taken';
  if (c.vipTier < c.minVip) return 'vip';
  if (c.owes) return 'pvp_owing';
  if (c.isOwner) return 'owner';
  if (c.betsConfirmed) return 'seat_next_round';
  if (!Number.isSafeInteger(c.bank) || c.bank < c.minBank) return 'min_bank';
  if (c.bank > c.balance) return 'insufficient';
  return undefined;
}

/**
 * Banking ends after the round when the bank is below minBank or cannot cover one seat at the
 * minimum Ante (§21.9).
 */
export const bankTooLow = (bank: number, minBank: number, minAnte: number, blindPays: Readonly<Paytable>): boolean =>
  bank < minBank || bank < minAnte * reservationPerAnte(blindPays);

/** Next seat clockwise after `from` among `seats` (sorted ascending), wrapping; undefined if none. */
export function nextClockwise(seats: readonly number[], from: number): number | undefined {
  const sorted = [...seats].sort((a, b) => a - b);
  return sorted.find((s) => s > from) ?? sorted.find((s) => s !== from);
}
