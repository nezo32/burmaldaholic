/**
 * Per-table bot state (BOTS.md §2–§6), owned by a game's table runtime (composition — core/tables.ts is
 * unchanged). Holds the saved defaults, owner/keeper limits, access (private + invites) and the live
 * session (host, pending settings, seated bots with stacks and purses, bot rng). Persisted per table as
 * the world property `burmaldaholic:bots:<tableKey>` (JSON `TableBotsState`, < 2 000 chars; pvp-bots.md §5.3).
 *
 * Game integration contract (same as Java TableBots):
 *  1. `const bots = ctx.bots.table(key, hooks, defaults)` when the table session starts;
 *  2. when a human wants to sit: `bots.admit(player)` (private / BOTS_ONLY / claimant);
 *  3. at the game's safe point: `bots.safePoint()` → joined / left / seated claimants;
 *  4. per bot decision: build the view, `bots.think(bot, big, humanTimer)` ticks later call
 *     `botAct(policy, bot, view, work, bots.rng)` — never the game rng;
 *  5. when the table stops (after the round settled): `bots.endSession()`.
 */
import type { Player } from '@minecraft/server';
import type { Rng } from '../logic/rng';
import type { YieldRule } from '../logic/bots/seating';
import type { BotProfile, BotRole, BotSettings, OwnerControls, Purse, SeatOccupant } from '../logic/bots/types';
import type { Raw } from '../logic/rawtext';

/** Game-side hooks, called only at the game's safe point. */
export interface BotTableHooks {
  /** config family id: poker | chemmy | blackjack | roulette | craps | baccarat | uth */
  readonly game: string;
  readonly role: BotRole;
  readonly yieldRule: YieldRule;
  seats(): number;
  /** humans in sit-down order (player ids) */
  seatedHumans(): string[];
  occupants(): (SeatOccupant | undefined)[];
  seatBot(bot: SeatOccupant & { kind: 'bot' }, stack: number): boolean;
  /** returns what the bot holds (to go back to its purse); atmosphere 0 */
  unseatBot(botKey: string): number;
  /** chips a new money bot sits with (poker buy-in, chemmy bank); 0 for atmosphere */
  buyIn?(): number;
  /** false where decisions cannot matter (difficulty hidden: "luck only") */
  difficultyMatters?: boolean;
}

export interface SeatedBot {
  profile: BotProfile;
  purse: Purse;
  stack: number;
  bankEscrow: number;
}

/** Persisted JSON (pvp-bots.md §5.3). */
export interface TableBotsState {
  v: 1;
  keeper?: string;
  defaults: BotSettings & { private: boolean; guests: string[] };
  limits: OwnerControls;
  session?: { host?: string; pending?: BotSettings; invites: string[]; claimants: string[]; bots: SeatedBot[] };
}

export interface SafePointResult {
  joined: SeatedBot[];
  left: SeatedBot[];
  seatedClaimants: string[];
  settingsApplied: boolean;
}

export type Admit = { ok: true; sitNow: boolean } | { ok: false; error: Raw };

export interface TableBots {
  readonly key: string;
  readonly rng: Rng;
  settings(): BotSettings;
  pending(): BotSettings | undefined;
  host(): string | undefined;
  bots(): readonly SeatedBot[];
  admit(player: Player): Admit;
  requestChange(actor: Player, wanted: BotSettings, asDefaults: boolean): Raw | undefined;
  setLimits(limits: OwnerControls): void;
  safePoint(): SafePointResult;
  think(bot: BotProfile, big: boolean, humanTimer: number): number;
  endSession(): void;
}
