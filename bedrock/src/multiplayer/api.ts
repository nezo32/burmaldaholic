/**
 * Public API of the multiplayer module (player-owned casinos, GAME_DESIGN §18.2).
 * Provided in onWorldLoad as `ctx.services.provide(MULTIPLAYER_SERVICE, impl)`.
 *
 * Games need NOTHING to join the owned-casino economy: the module registers core hooks
 * (`wagers.setHouseResolver / setLimitsResolver / addVeto`), so every `ctx.wagers.place` at an
 * owned table (the player's table session, or `tableKey` passed explicitly) is banked by the
 * owner's bankroll with core's reservation rule, narrowed by the owner's min/max and refused
 * for the owner / closed tables / a broke house. Useful extras:
 *
 *   const mp = ctx.services.get<MultiplayerApi>(MULTIPLAYER_SERVICE);
 *   canJoin: (p, table) => ctx.wagers.check(p, game, table.key),   // same vetoes, before seating
 *   const limits = ctx.wagers.limitsFor(p, game, base, s.table.key); // prompts show owner limits
 *   // poker rake at an owned table: house = ctx.wagers.resolveHouse(p, 'poker', key) (== houseFor)
 *   //   economy.transact([... {account: {bankroll: house.id}, delta: rake} ...]); mp.recordRake(key, rake)
 *
 * A round a game explicitly banks with `house: BANK` at an owned table is still routed to the
 * owner on `wagers.onSettled` (without the up-front reservation; see ownership.ts).
 */
import type { Player } from '@minecraft/server';
import type { HouseRef, Raw, TableLimits } from '../core';

export const MULTIPLAYER_SERVICE = 'multiplayer';

export interface OwnedTableInfo {
  casinoId: string;
  ownerId: string;
  ownerName: string;
  open: boolean;
  /** owner minimum / maximum bet (undefined = game default) */
  min?: number;
  max?: number;
  /** poker: bots allowed */
  bots: boolean;
  /** slots (SLOTS.md §8.6): the owner allows the bonus buy / autoplay at this machine */
  slotsBuy: boolean;
  slotsAutoplay: boolean;
  /** the casino cannot cover its cheapest table */
  broke: boolean;
}

export interface MultiplayerApi {
  /** Who banks rounds at this table: the owner's bankroll inside an active casino, else the bank. */
  houseFor(tableKey: string): HouseRef;
  /**
   * Refusal text for seating/betting at this table (owner can't play, closed by owner, house
   * broke, unlicensed/inactive table), or undefined when the player may play.
   */
  checkTable(player: Player, tableKey: string): Raw | undefined;
  /** The game's limits narrowed by the owner's min/max settings. */
  limitsFor(tableKey: string, base: TableLimits): TableLimits;
  /** Owned-table info, undefined for house tables. */
  tableInfo(tableKey: string): OwnedTableInfo | undefined;
  /** Poker: whether bots may sit at this table (true for house tables). */
  botsAllowed(tableKey: string): boolean;
  /** Record poker rake that the game already paid into the bankroll (stats only). */
  recordRake(tableKey: string, amount: number): void;
  /**
   * Override the worst-case TOTAL return per chip of base bet used by the insolvency rule for a
   * table game (`game` or `game.variant`), e.g. `setWorstCase('slots.copper', 500)` (the Overworld max-win cap, SLOTS.md §8.6).
   */
  setWorstCase(key: string, perChip: number): void;
  /** Worst-case total return of one round at `bet` (a sane default for `wagers.place({worstCase})`). */
  worstCaseFor(game: string, bet: number, variant?: string): number;
  /**
   * Whether a position lies inside any player casino claim (e.g. chaos: no mob waves there).
   * `dimension` is a dimension id or a Dimension object.
   */
  isInsideClaim(dimension: string | { readonly id: string }, location: { x: number; y: number; z: number }): boolean;
  /** Owner of the claim containing a position, if any (`dimension`: id or Dimension). */
  ownerAt(dimension: string | { readonly id: string }, location: { x: number; y: number; z: number }): { id: string; name: string } | undefined;
  /** Casino whose claim contains a position, if any. */
  casinoAt(dimensionId: string, location: { x: number; y: number; z: number }): { id: string; ownerId: string; ownerName: string } | undefined;
}
