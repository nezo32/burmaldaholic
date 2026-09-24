/** Pure seating rules (BOTS.md §2.2, §2.4, §3). Same functions as Java SeatingMath. */
import { type BotRole, type BotSettings, type OwnerControls, modeAllows } from './types';

/** Bot seats wanted at a safe point before purse / global / heat limits. No human → 0. */
export function wantedBots(s: BotSettings, seats: number, humans: number, claimants: number): number {
  if (humans + claimants <= 0) return 0;
  switch (s.policy) {
    case 'HUMANS_ONLY':
      return 0;
    case 'BOTS_ONLY':
      return Math.max(1, Math.min(s.count, seats - 1));
    case 'MIXED':
      return Math.max(0, Math.min(s.count, seats - humans - claimants - (s.keepFree ? 1 : 0)));
  }
}

/** Hard caps: owner maxBots + Bots mode, world budget left, affordable money bots. */
export function cappedBots(wanted: number, owner: OwnerControls, role: BotRole, activeBudgetLeft: number, affordable: number): number {
  if (!modeAllows(owner.botsMode, role)) return 0;
  let n = Math.min(wanted, owner.maxBots, Math.max(0, activeBudgetLeft));
  if (role === 'MONEY') n = Math.min(n, Math.max(0, affordable));
  return Math.max(0, n);
}

export const botsOnlyAllowed = (humansSeated: number): boolean => humansSeated <= 1;

/** Host: the keeper if seated, else the longest-seated human (`seatedOrder` = sit-down order). */
export function hostOf(keeper: string | undefined, seatedOrder: readonly string[]): string | undefined {
  if (keeper && seatedOrder.includes(keeper)) return keeper;
  return seatedOrder[0];
}

/** Which bot yields its seat to a claimant (BOTS.md §3.3). */
export type YieldRule = 'POKER_BIG_BLIND' | 'CHEMMY_PUNTER_FIRST' | 'HIGHEST_SEAT' | 'LAST_JOINED';
