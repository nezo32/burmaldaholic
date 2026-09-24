/**
 * Public API of the baccarat module (types + service name + ids).
 * Consumers: `ctx.services.get<BaccaratApi>(BACCARAT_SERVICE)?.onCoup(...)` — contracts,
 * statistics, a future bot framework (docs/design/BOTS.md) observing coups.
 * Worldgen: a table preset whose id starts with `high_roller_baccarat` (or with `minTier` /
 * `tierMultiplier`) makes a `baccarat_table` a High Roller table; an entity tagged
 * `BACCARAT_HIGH_ROLLER_TAG` hosts a High Roller table itself.
 */
import type { Player } from '@minecraft/server';

export const BACCARAT_SERVICE = 'baccarat';
export const BACCARAT_DEALER_ENTITY = 'burmaldaholic:baccarat_dealer';
export const BACCARAT_HIGH_ROLLER_TAG = 'burmaldaholic_baccarat_high_roller';
export const BACCARAT_BLOCKS = ['burmaldaholic:baccarat_table', 'burmaldaholic:baccarat_table_high_roller', 'burmaldaholic:baccarat_table_player_banked'] as const;

export type BaccaratBox = 'player' | 'banker' | 'tie' | 'player_pair' | 'banker_pair';

export interface BaccaratCoupEntry {
  playerId: string;
  /** undefined when offline at settlement (core credits them on join) */
  player: Player | undefined;
  /** chips per box (house coup) or `{ player: stake }` for a chemin de fer punter */
  bets: Partial<Record<BaccaratBox, number>>;
  staked: number;
  totalReturn: number;
}

export interface BaccaratCoupEvent {
  /** table key */
  table: string;
  /** 'house' = Punto Banco vs the house; 'chemmy' = a player-banked coup */
  mode: 'house' | 'chemmy';
  winner: 'player' | 'banker' | 'tie';
  playerTotal: number;
  bankerTotal: number;
  playerPair: boolean;
  bankerPair: boolean;
  /** card ids in deal order (P1 B1 P2 B2 [P3] [B3]) */
  cards: readonly string[];
  entries: readonly BaccaratCoupEntry[];
  /** chemin de fer: the banker's id and net result */
  banker?: { id: string; net: number };
}

export interface BaccaratApi {
  /** Fired once per coup after every bettor was settled. */
  onCoup(listener: (e: BaccaratCoupEvent) => void): void;
  /** Tables with a coup running (not idle betting). */
  activeTables(): number;
}
