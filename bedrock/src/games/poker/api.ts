/**
 * Public API of the poker module for other modules (types only + service name).
 * Provided in onWorldLoad: ctx.services.provide(POKER_SERVICE, impl).
 */
export const POKER_SERVICE = 'poker';

export interface PokerApi {
  /** True while the player holds a seat (and chips) at any poker table. */
  isSeated(playerId: string): boolean;
  /** Chips the player currently has on the table (0 when not seated). */
  tableStack(playerId: string): number;
  /** Number of running tables (with at least one human). */
  tableCount(): number;
}
