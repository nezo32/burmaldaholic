/**
 * Table seat bookkeeping. PURE (the runtime wrapper is core/tables.ts).
 * A player sits at most at one table; a table has `seats` numbered 1..N.
 */
export interface SeatInfo {
  tableKey: string;
  seat: number;
}

export type JoinResult = { ok: true; seat: number; rejoined: boolean } | { ok: false; reason: 'busy' | 'full' };

export class SeatRegistry {
  private readonly byPlayer = new Map<string, SeatInfo>();
  private readonly byTable = new Map<string, Map<number, string>>();

  /** Seat `playerId` at `tableKey`. Re-joining the same table returns the existing seat. */
  join(playerId: string, tableKey: string, seats: number): JoinResult {
    const cur = this.byPlayer.get(playerId);
    if (cur) return cur.tableKey === tableKey ? { ok: true, seat: cur.seat, rejoined: true } : { ok: false, reason: 'busy' };
    let occ = this.byTable.get(tableKey);
    if (!occ) this.byTable.set(tableKey, (occ = new Map()));
    for (let s = 1; s <= seats; s++) {
      if (!occ.has(s)) {
        occ.set(s, playerId);
        this.byPlayer.set(playerId, { tableKey, seat: s });
        return { ok: true, seat: s, rejoined: false };
      }
    }
    return { ok: false, reason: 'full' };
  }

  leave(playerId: string): SeatInfo | undefined {
    const cur = this.byPlayer.get(playerId);
    if (!cur) return undefined;
    this.byPlayer.delete(playerId);
    const occ = this.byTable.get(cur.tableKey);
    occ?.delete(cur.seat);
    if (occ && occ.size === 0) this.byTable.delete(cur.tableKey);
    return cur;
  }

  of(playerId: string): SeatInfo | undefined {
    return this.byPlayer.get(playerId);
  }

  /** Player ids at a table ordered by seat. */
  at(tableKey: string): { seat: number; playerId: string }[] {
    const occ = this.byTable.get(tableKey);
    if (!occ) return [];
    return [...occ.entries()].sort((a, b) => a[0] - b[0]).map(([seat, playerId]) => ({ seat, playerId }));
  }

  tables(): string[] {
    return [...this.byTable.keys()];
  }
}

/** Stable key of a table block: `<dimension>|x,y,z`. */
export const tableKeyOf = (dimensionId: string, l: { x: number; y: number; z: number }): string =>
  `${dimensionId}|${Math.floor(l.x)},${Math.floor(l.y)},${Math.floor(l.z)}`;

/** Horizontal+vertical distance check without sqrt. */
export const within = (a: { x: number; y: number; z: number }, b: { x: number; y: number; z: number }, r: number): boolean =>
  (a.x - b.x) ** 2 + (a.y - b.y) ** 2 + (a.z - b.z) ** 2 <= r * r;

/** Why a table session ended (core/tables.ts passes it to the game's onLeave). */
export type LeaveReason = 'leave' | 'distance' | 'disconnect' | 'broken' | 'casino_off' | 'replaced';

/**
 * What a game does with a round that is already in play when a session ends
 * (GAME_DESIGN §4.1, one rule for every table game):
 *  - 'play_out': the round is auto-completed with the game's default action (blackjack: stand;
 *    craps: bets stay working; roulette: the spin proceeds; poker: auto-fold, the hand is
 *    played out) and settled. No refunds. This covers leave / walk-away / disconnect AND a
 *    broken table (block mined, dealer NPC removed, owner closed it): breaking a table must
 *    never cancel a round whose result is already drawn (review B1, the free-roll exploit).
 *  - 'refund': only when casino mode turns off (the mod goes dormant, §2.1, and nothing may
 *    keep running). A server stop is refunded separately on the next join (core wagers).
 * Bets on a round that has not started yet (roulette betting phase, blackjack pre-deal) may
 * still be handled by the game as it does for a normal leave.
 */
export type LeavePolicy = 'play_out' | 'refund';
export function leavePolicy(reason: LeaveReason): LeavePolicy {
  return reason === 'casino_off' ? 'refund' : 'play_out';
}
