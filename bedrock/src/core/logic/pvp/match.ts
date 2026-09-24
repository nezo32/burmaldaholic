/**
 * PvP match record (PVP.md §3.1, §3.6) and its persisted JSON (pvp-bots.md §5.1). PURE.
 * Bedrock: world property `burmaldaholic:pvp:<id>` + index `burmaldaholic:pvp_index`; Java: PvpMatchData.
 */
import type { BotSettings, SeatOccupant } from '../bots/types';
import type { AnchorKind, Outcome, PvpModeId } from './mode';

export type MatchState = 'INVITED' | 'LOBBY' | 'STARTING' | 'DRAWN' | 'SETTLED' | 'CANCELLED' | 'CLOSED';
export const PERSISTED_STATES: readonly MatchState[] = ['LOBBY', 'DRAWN', 'SETTLED'];

export interface Participant {
  index: number;
  occupant: SeatOccupant;
  stake: number;
  allIn: boolean;
  pressed?: boolean;
  taunts?: number;
  wantsRematch?: boolean;
}

export interface MatchRecord {
  v: 1;
  /** 8-char base-36 */
  id: string;
  mode: PvpModeId;
  state: MatchState;
  params: unknown;
  anchor: { kind: AnchorKind; dim: string; x: number; y: number; z: number };
  /** owned-casino bankroll at creation ('' = bank) — rake destination */
  bankroll: string;
  seating: BotSettings;
  inviteOnly: boolean;
  host?: string;
  participants: Participant[];
  createdTick: number;
  /** Coin Flip Duel chain */
  chainOf?: string;
  link?: number;
  /** DRAWN+: the mode's encoded tape — written BEFORE any reveal */
  tape?: unknown;
  drawnTick?: number;
  /** SETTLED: payouts per participant, rake */
  payouts?: number[];
  rake?: number;
  grudge?: boolean;
  outcome?: Outcome;
}

/** 8-char base-36 id from any rng function. */
export function newMatchId(next: () => number): string {
  let s = '';
  for (let i = 0; i < 8; i++) s += Math.floor(next() * 36).toString(36);
  return s;
}
