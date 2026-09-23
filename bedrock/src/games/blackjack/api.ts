/**
 * Public API of the blackjack module for other modules (types only + service name + ids).
 * Provided in onWorldLoad: ctx.services.provide(BLACKJACK_SERVICE, impl).
 */
export const BLACKJACK_SERVICE = 'blackjack';

/** Table blocks (component `burmaldaholic:table` {game:'blackjack', variant}). */
export const BLACKJACK_TABLE_BLOCK = 'burmaldaholic:blackjack_table';
export const BLACKJACK_HIGH_ROLLER_TABLE_BLOCK = 'burmaldaholic:blackjack_table_high_roller';
/** Dealer NPC: interacting with it opens a table keyed `npc:<entity id>`. */
export const BLACKJACK_DEALER_ENTITY = 'burmaldaholic:blackjack_dealer';
/** Tag a dealer entity with this to make its table a High-Roller table (worldgen: End City). */
export const BLACKJACK_HIGH_ROLLER_TAG = 'burmaldaholic_blackjack_high_roller';

export interface BlackjackApi {
  /** Number of tables with a round in progress or bets placed (for admin/stats). */
  activeTables(): number;
}
