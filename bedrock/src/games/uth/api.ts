/**
 * Public API of the Ultimate Texas Hold'em module for other modules (types, ids, service name).
 * Provided in onWorldLoad: ctx.services.provide(UTH_SERVICE, impl).
 */
export const UTH_SERVICE = 'uth';

/** Table blocks (component `burmaldaholic:table` {game: 'uth', variant}). */
export const UTH_TABLE_BLOCK = 'burmaldaholic:uth_table';
export const UTH_HIGH_ROLLER_TABLE_BLOCK = 'burmaldaholic:uth_table_high_roller';
export const UTH_PLAYER_BANKED_TABLE_BLOCK = 'burmaldaholic:uth_table_player_banked';
/** Hold'em Dealer NPC: interacting with it opens a table keyed `npc:<entity id>`. */
export const UTH_DEALER_ENTITY = 'burmaldaholic:uth_dealer';
/** Tag a dealer entity with this to make its table a High-Roller table (worldgen: End City). */
export const UTH_HIGH_ROLLER_TAG = 'burmaldaholic_uth_high_roller';

export interface UthApi {
  /** Number of tables with a round in progress or bets placed. */
  activeTables(): number;
  /** True while the player holds a player seat or the dealer seat at any UTH table. */
  isSeated(playerId: string): boolean;
  /** Chips escrowed in player banks (dealer seats) right now. */
  escrowedBanks(): number;
}
