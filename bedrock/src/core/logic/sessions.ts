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
