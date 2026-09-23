/**
 * Non-chip ("pawn") stakes and the Soul Wager (GAME_DESIGN.md §4.3, §4.4). PURE.
 * Every stake is converted to a stake value V in chips; the game then treats it as a V-chip bet.
 */

/** XP needed to go from `level` to `level + 1` (vanilla, both editions). */
export function xpToNextLevel(level: number): number {
  if (level < 16) return 2 * level + 7;
  if (level < 31) return 5 * level - 38;
  return 9 * level - 158;
}

/** Total XP points from level 0 to the start of `level`. */
export function xpAtLevel(level: number): number {
  let sum = 0;
  for (let l = 0; l < level; l++) sum += xpToNextLevel(l);
  return sum;
}

/** points(L): XP between level (current − L) and current (level boundaries). */
export function xpPointsForLevels(current: number, levels: number): number {
  return xpAtLevel(current) - xpAtLevel(Math.max(0, current - levels));
}

/** Stake value of L levels: V = floor(points(L) / pointsPerChip). */
export const xpStakeValue = (current: number, levels: number, pointsPerChip: number): number =>
  Math.floor(xpPointsForLevels(current, levels) / Math.max(1, pointsPerChip));

export type PawnCheck = { ok: true } | { ok: false; key: string };

/** XP stake rules: 1 ≤ L ≤ current level, L ≤ maxLevels. */
export function checkXpStake(current: number, levels: number, maxLevels: number): PawnCheck {
  if (!Number.isInteger(levels) || levels < 1 || levels > maxLevels) return { ok: false, key: 'gui.burmaldaholic.error.invalid_amount' };
  if (levels > current) return { ok: false, key: 'gui.burmaldaholic.error.xp_not_enough' };
  return { ok: true };
}

export interface HeartPenalty {
  /** hearts lost (1 heart = 2 HP) */
  hearts: number;
  /** absolute world tick when it expires */
  until: number;
}

export const activeHearts = (penalties: readonly HeartPenalty[], now: number): number =>
  penalties.filter((p) => p.until > now).reduce((s, p) => s + p.hearts, 0);

/**
 * Heart stake rules (§4.3.3): 1 ≤ h ≤ maxPerBet; Σ active + h ≤ maxTotal; resulting max
 * health ≥ 10 HP.
 */
export function checkHeartStake(h: number, o: { maxPerBet: number; maxTotal: number; active: number; baseMaxHealth: number }): PawnCheck {
  if (!Number.isInteger(h) || h < 1 || h > o.maxPerBet) return { ok: false, key: 'gui.burmaldaholic.error.invalid_amount' };
  if (o.active + h > o.maxTotal || o.baseMaxHealth - 2 * (o.active + h) < 10) return { ok: false, key: 'gui.burmaldaholic.error.hearts_cap' };
  return { ok: true };
}

/** Effective max health with active heart penalties (never below 10 HP). */
export const cappedMaxHealth = (baseMax: number, activeHeartsLost: number): number => Math.max(Math.min(10, baseMax), baseMax - 2 * activeHeartsLost);

/** Item appraisal (§4.3.1): V = appraisal × count; only undamaged, unenchanted, unnamed stacks. */
export function checkItemStake(o: { appraisal: number; count: number; damaged: boolean; enchanted: boolean; named: boolean }): PawnCheck & { value?: number } {
  if (!(o.appraisal > 0)) return { ok: false, key: 'gui.burmaldaholic.error.pawn_not_accepted' };
  if (o.damaged || o.enchanted || o.named) return { ok: false, key: 'gui.burmaldaholic.error.pawn_damaged' };
  return { ok: true, value: o.appraisal * o.count };
}

/** Soul Wager value: V = max(minValue, balance). */
export const soulValue = (balance: number, minValue: number): number => Math.max(minValue, balance);

/**
 * Settlement of a pawn stake worth V when the game says the round returns `totalReturn`
 * (in chips, as if V chips had been bet). Win/push (return ≥ V): the pawn comes back and
 * `totalReturn − V` chips are paid. Loss (return < V): the pawn is forfeited and any partial
 * return (e.g. a wheel 0.5× segment) is paid in chips.
 */
export function pawnSettlement(value: number, totalReturn: number): { returnPawn: boolean; chips: number } {
  const r = Math.max(0, Math.floor(totalReturn));
  if (r >= value) return { returnPawn: true, chips: r - value };
  return { returnPawn: false, chips: r };
}
