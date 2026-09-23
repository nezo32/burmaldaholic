/**
 * Golden Hour rules (GAME_DESIGN §13.3). PURE. Core keeps the "active until" tick
 * (ctx.goldenHour); chaos keeps the cooldown and the per-player bonus tally here.
 */

export interface GoldenHourState {
  /** sequential id of the current / last Golden Hour */
  id: number;
  /** world tick it started (-1 = never) */
  start: number;
  /** world tick it ends */
  end: number;
  /** a new Golden Hour may start at/after this tick */
  nextAllowed: number;
  /** bonus paid per player id during Golden Hour `id` */
  paid: Record<string, number>;
  /** "ending soon" already announced for `id` */
  warned?: boolean;
  /** end already announced for `id` */
  ended?: boolean;
}

export const INITIAL_GH: GoldenHourState = { id: 0, start: -1, end: -1, nextAllowed: 0, paid: {}, ended: true };

export function isActive(s: GoldenHourState, now: number): boolean {
  return s.start >= 0 && now >= s.start && now < s.end;
}

/**
 * Whether a Golden Hour may start now: not already active and the cooldown (counted from the
 * end of the previous one) has elapsed. A world clock that went backwards resets the cooldown.
 */
export function canStart(s: GoldenHourState, now: number): boolean {
  if (isActive(s, now)) return false;
  if (s.start >= 0 && now < s.start) return true;
  return now >= s.nextAllowed;
}

export function startState(s: GoldenHourState, now: number, durationTicks: number, cooldownTicks: number): GoldenHourState {
  const end = now + Math.max(1, durationTicks);
  return { id: s.id + 1, start: now, end, nextAllowed: end + Math.max(0, cooldownTicks), paid: {}, warned: false, ended: false };
}

/**
 * Bonus for a settled round: floor(netWin × (m − 1)), limited by what is left of the per-player
 * cap. `capReached` is true when this payment makes the player hit the cap (tell them once).
 */
export function goldenBonus(net: number, multiplier: number, paidSoFar: number, cap: number): { bonus: number; capReached: boolean } {
  if (!(net > 0) || !(multiplier > 1)) return { bonus: 0, capReached: false };
  const raw = Math.floor(net * (multiplier - 1));
  const left = Math.max(0, Math.floor(cap) - Math.max(0, paidSoFar));
  const bonus = Math.min(raw, left);
  return { bonus, capReached: bonus > 0 && bonus === left && raw >= left };
}

/** Warn this many ticks before the end (30 s). */
export const GH_WARN_TICKS = 600;
