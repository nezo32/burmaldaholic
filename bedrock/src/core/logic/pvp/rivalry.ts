/** Head-to-head records, win streaks, grudge matches (PVP.md §3.8). PURE. Bots never have records. */

/** Record of A→B. run: +n = A won the last n vs B, −n = lost the last n. */
export interface HeadToHead {
  wins: number;
  losses: number;
  net: number;
  run: number;
}

export const EMPTY_H2H: HeadToHead = { wins: 0, losses: 0, net: 0, run: 0 };
export const h2hWin = (r: HeadToHead, chips: number): HeadToHead => ({ wins: r.wins + 1, losses: r.losses, net: r.net + chips, run: r.run > 0 ? r.run + 1 : 1 });
export const h2hLoss = (r: HeadToHead, chips: number): HeadToHead => ({ wins: r.wins, losses: r.losses + 1, net: r.net - chips, run: r.run < 0 ? r.run - 1 : -1 });
export const isGrudge = (r: HeadToHead, grudgeLosses: number): boolean => r.run <= -grudgeLosses;

/** Tier reached exactly at `streak` (0 heating, 1 rampage, 2 legendary) or -1. */
export function announceTier(streak: number, thresholds: readonly number[]): number {
  for (let i = thresholds.length - 1; i >= 0; i--) if (streak === thresholds[i]) return i;
  return -1;
}

export const brokenCallout = (previous: number, thresholds: readonly number[]): boolean => thresholds.length > 0 && previous >= thresholds[0]!;

/** Player record JSON (Bedrock property `burmaldaholic:pvp_record`, Java PvpRecordData) — pvp-bots.md §5.2. */
export interface PvpPlayerRecord {
  v: 1;
  wins: number;
  losses: number;
  net: number;
  streak: number;
  acceptInvites: boolean;
  /** rival player id → record, most recent first, ≤ 50 (LRU) */
  rivals: { id: string; name: string; r: HeadToHead }[];
  /** "vs bots" statistics line (BOTS.md §5.3) */
  botRounds: number;
  botNet: number;
}

export const EMPTY_RECORD: PvpPlayerRecord = { v: 1, wins: 0, losses: 0, net: 0, streak: 0, acceptInvites: true, rivals: [], botRounds: 0, botNet: 0 };
