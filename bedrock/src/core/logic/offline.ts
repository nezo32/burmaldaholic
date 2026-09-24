/**
 * Offline settlement (GAME_DESIGN §4.1). PURE.
 *
 * A round may settle (or be refunded) after its player disconnected: the table played it out,
 * the spin landed, the craps bets resolved. Core cannot write an offline player's dynamic
 * properties, so the result is parked in a world-level "offline entry" keyed by player id and
 * applied on the player's next join, BEFORE stale rounds are refunded:
 *
 *   join: apply entry (chips, pawns, heart penalties, messages, deferred onSettled events)
 *         -> drop every stored ticket whose id is in entry.resolved
 *         -> settle the remaining tickets of an earlier server run whose outcome was drawn
 *            (parked here first, then applied), refund the undrawn ones
 *
 * Drawn rounds of an earlier run are also parked here at world load (from the world-level
 * drawn store), so an offline player's drawn round is settled even before they come back.
 *
 * `resolved` is what prevents double payment: the player's persisted ticket list still holds
 * the ticket (it could not be rewritten while offline), so without it a restart between the
 * offline settlement and the join would refund a round that was already paid.
 */

/** A settled round whose onSettled listeners run when the player is back (with a live Player). */
export interface DeferredSettle {
  game: string;
  staked: number;
  totalReturn: number;
  stakeKind: string;
  house: { kind: 'bank' } | { kind: 'bankroll'; id: string };
  tableKey?: string;
  theoreticalLoss: number;
}

export interface OfflineEntry {
  /** chips to credit */
  chips: number;
  /** pawn items to give back */
  items: { typeId: string; amount: number }[];
  /** XP points to give back */
  xp: number;
  /** heart penalties to add ({hearts, until: absolute world tick}) */
  hearts: { hearts: number; until: number; d?: number }[];
  /** lost Soul Wager: the player dies on join */
  soulDeath?: boolean;
  /** Soul Wager cooldown (absolute world tick) */
  soulCooldown?: number;
  /** rawtext messages (JSON) to send on join, oldest first */
  messages: unknown[];
  /** deferred onSettled events */
  settled: DeferredSettle[];
  /** ticket ids settled/refunded while offline */
  resolved: string[];
  /** achievement ids unlocked while offline */
  achievements: string[];
}

/** Keep the entry small: a player away for a long time must not blow the 32 KB property. */
export const OFFLINE_CAPS = { messages: 20, settled: 50, resolved: 200, items: 36 } as const;

export const emptyOffline = (): OfflineEntry => ({ chips: 0, items: [], xp: 0, hearts: [], messages: [], settled: [], resolved: [], achievements: [] });

/** Normalize whatever was stored (older/partial/corrupt JSON). */
export function normalizeOffline(raw: unknown): OfflineEntry {
  const e = emptyOffline();
  if (!raw || typeof raw !== 'object') return e;
  const r = raw as Partial<OfflineEntry>;
  const num = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : 0);
  e.chips = Math.max(0, Math.floor(num(r.chips)));
  e.xp = Math.max(0, Math.floor(num(r.xp)));
  if (Array.isArray(r.items)) e.items = r.items.filter((i) => i && typeof i.typeId === 'string' && num(i.amount) > 0);
  if (Array.isArray(r.hearts)) e.hearts = r.hearts.filter((h) => h && num(h.hearts) > 0 && num(h.until) > 0);
  if (r.soulDeath === true) e.soulDeath = true;
  if (num(r.soulCooldown) > 0) e.soulCooldown = num(r.soulCooldown);
  if (Array.isArray(r.messages)) e.messages = r.messages.slice(-OFFLINE_CAPS.messages);
  if (Array.isArray(r.settled)) e.settled = r.settled.filter((s) => s && typeof s.game === 'string').slice(-OFFLINE_CAPS.settled);
  if (Array.isArray(r.resolved)) e.resolved = r.resolved.filter((x): x is string => typeof x === 'string').slice(-OFFLINE_CAPS.resolved);
  if (Array.isArray(r.achievements)) e.achievements = r.achievements.filter((x): x is string => typeof x === 'string');
  return e;
}

export const isEmptyOffline = (e: OfflineEntry): boolean =>
  e.chips === 0 && e.xp === 0 && !e.items.length && !e.hearts.length && !e.soulDeath && !e.soulCooldown && !e.messages.length && !e.settled.length && !e.resolved.length && !e.achievements.length;

/** Pure update helpers (return a new entry, respecting the caps). */
export function withChips(e: OfflineEntry, amount: number): OfflineEntry {
  return amount > 0 ? { ...e, chips: e.chips + Math.floor(amount) } : e;
}

export function withResolved(e: OfflineEntry, ticketId: string): OfflineEntry {
  if (e.resolved.includes(ticketId)) return e;
  return { ...e, resolved: [...e.resolved, ticketId].slice(-OFFLINE_CAPS.resolved) };
}

export function withMessage(e: OfflineEntry, msg: unknown): OfflineEntry {
  return { ...e, messages: [...e.messages, msg].slice(-OFFLINE_CAPS.messages) };
}

export function withSettled(e: OfflineEntry, s: DeferredSettle): OfflineEntry {
  return { ...e, settled: [...e.settled, s].slice(-OFFLINE_CAPS.settled) };
}

export function withItem(e: OfflineEntry, typeId: string, amount: number): OfflineEntry {
  if (!(amount > 0)) return e;
  return { ...e, items: [...e.items, { typeId, amount }].slice(-OFFLINE_CAPS.items) };
}

export function withAchievement(e: OfflineEntry, id: string): OfflineEntry {
  return e.achievements.includes(id) ? e : { ...e, achievements: [...e.achievements, id] };
}

export interface StoredTicketLike {
  id: string;
  boot: number;
  /** total return of the round's already drawn outcome (see WagerService.draw) */
  drawn?: number;
}

/** The round's outcome was drawn (GAME_DESIGN §4.1: such a round is played out, never refunded). */
export const hasDrawn = (w: { drawn?: unknown }): w is { drawn: number } => typeof w.drawn === 'number' && Number.isFinite(w.drawn) && w.drawn >= 0;

/**
 * What to do with a joining player's persisted tickets: drop the ones resolved while offline,
 * SETTLE those of an earlier server run whose outcome was already drawn (at that drawn result),
 * refund the other ones of an earlier run (no draw yet), keep the rest (still open in this run).
 */
export function planRecovery<T extends StoredTicketLike>(stored: readonly T[], resolved: readonly string[], boot: number): { refund: T[]; settle: T[]; keep: T[]; dropped: T[] } {
  const done = new Set(resolved);
  const refund: T[] = [];
  const settle: T[] = [];
  const keep: T[] = [];
  const dropped: T[] = [];
  for (const w of stored) {
    if (done.has(w.id)) dropped.push(w);
    else if (w.boot === boot) keep.push(w);
    else if (hasDrawn(w)) settle.push(w);
    else refund.push(w);
  }
  return { refund, settle, keep, dropped };
}
