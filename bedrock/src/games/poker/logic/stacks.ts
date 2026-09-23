/**
 * Persisted poker table stacks (world property `burmaldaholic:poker.stacks`). PURE.
 *
 * Two kinds of entries, both keyed so they can never overwrite each other (review M1):
 *  - LIVE   `<player>|<table>`          the stack of a seat that is still at a live table,
 *    rewritten at every hand start/end (crash safety: a server stop pays it on the next join).
 *  - PARKED `<player>|<table>|#<hand>`  a seat that was removed while its player was offline;
 *    paid on the next join (or right away if the player is online again). Parking onto an
 *    existing key merges (adds) instead of replacing.
 *
 * `buyIn` = chips the player moved from the balance onto the table for that seat (buy-in +
 * top-ups): only `amount − buyIn` is garnishable income at cash-out (loan default).
 * Legacy stores (`{ [playerId]: amount }`) are read as parked entries with buyIn = amount.
 */
export interface StackEntry {
  /** player id */
  p: string;
  /** table key ('' for legacy entries) */
  t: string;
  /** chips */
  a: number;
  /** chips bought in for this seat (buy-in + top-ups) */
  b: number;
}
export type StackStore = Record<string, StackEntry>;

export const liveKey = (playerId: string, tableKey: string): string => `${playerId}|${tableKey}`;
export const parkedKey = (playerId: string, tableKey: string, hand: number): string => `${playerId}|${tableKey}|#${hand}`;

const n = (v: unknown): number => (typeof v === 'number' && Number.isFinite(v) ? Math.max(0, Math.floor(v)) : 0);

/** Read whatever was stored (legacy numbers, partial / corrupt JSON). */
export function normalizeStacks(raw: unknown): StackStore {
  const out: StackStore = {};
  if (!raw || typeof raw !== 'object') return out;
  for (const [k, v] of Object.entries(raw as Record<string, unknown>)) {
    if (typeof v === 'number') {
      const a = n(v);
      if (a > 0) out[k] = { p: k, t: '', a, b: a };
      continue;
    }
    if (!v || typeof v !== 'object') continue;
    const e = v as Partial<StackEntry>;
    if (typeof e.p !== 'string' || n(e.a) <= 0) continue;
    out[k] = { p: e.p, t: typeof e.t === 'string' ? e.t : '', a: n(e.a), b: n(e.b) };
  }
  return out;
}

/** New buy-in / top-up at a table: adds to the live entry (never replaces chips already there). */
export function addBuyIn(store: StackStore, playerId: string, tableKey: string, amount: number): StackStore {
  const k = liveKey(playerId, tableKey);
  const cur = store[k];
  const x = n(amount);
  return { ...store, [k]: { p: playerId, t: tableKey, a: (cur?.a ?? 0) + x, b: (cur?.b ?? 0) + x } };
}

/** Rewrite the live stack of a seat (hand start/end); keeps the recorded buy-in. 0 removes it. */
export function setLive(store: StackStore, playerId: string, tableKey: string, amount: number): StackStore {
  const k = liveKey(playerId, tableKey);
  const next = { ...store };
  const a = n(amount);
  if (a <= 0) delete next[k];
  else next[k] = { p: playerId, t: tableKey, a, b: store[k]?.b ?? 0 };
  return next;
}

/** The live entry of a seat (amount + buy-in), or zeros. */
export function liveEntry(store: StackStore, playerId: string, tableKey: string): { amount: number; buyIn: number } {
  const e = store[liveKey(playerId, tableKey)];
  return { amount: e?.a ?? 0, buyIn: e?.b ?? 0 };
}

/** Remove the live entry of a seat (paid out to an online player). */
export function clearLive(store: StackStore, playerId: string, tableKey: string): StackStore {
  const next = { ...store };
  delete next[liveKey(playerId, tableKey)];
  return next;
}

/**
 * Seat removed while its player is offline: move the live entry to a parked key with the final
 * `amount`, merging into an existing parked entry of the same key.
 */
export function park(store: StackStore, playerId: string, tableKey: string, hand: number, amount: number): StackStore {
  const lk = liveKey(playerId, tableKey);
  const pk = parkedKey(playerId, tableKey, hand);
  const buyIn = store[lk]?.b ?? 0;
  const next = { ...store };
  delete next[lk];
  const a = n(amount);
  if (a <= 0) return next;
  const cur = next[pk];
  next[pk] = { p: playerId, t: tableKey, a: (cur?.a ?? 0) + a, b: (cur?.b ?? 0) + buyIn };
  return next;
}

/**
 * Everything payable to a player now: parked entries always, live entries only when the
 * player no longer has that seat at a live table (server stop / table gone).
 * `seatedAt(tableKey)` = the player still has a seat there. Returns the total, the buy-in part
 * and the store without the paid entries.
 */
export function takePayable(store: StackStore, playerId: string, seatedAt: (tableKey: string) => boolean): { amount: number; buyIn: number; rest: StackStore } {
  let amount = 0;
  let buyIn = 0;
  const rest: StackStore = {};
  for (const [k, e] of Object.entries(store)) {
    const live = k === liveKey(e.p, e.t);
    if (e.p !== playerId || (live && e.t && seatedAt(e.t))) {
      rest[k] = e;
      continue;
    }
    amount += e.a;
    buyIn += Math.min(e.a, e.b);
  }
  return { amount, buyIn, rest };
}

/**
 * Split a cash-out into the returned buy-in (the player's own chips, never garnished) and the
 * net winnings (garnishable while a loan is in default).
 */
export function splitCashOut(amount: number, buyIn: number): { stake: number; winnings: number } {
  const a = n(amount);
  const stake = Math.min(a, n(buyIn));
  return { stake, winnings: a - stake };
}
