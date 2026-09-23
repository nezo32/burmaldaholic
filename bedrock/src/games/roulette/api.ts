/**
 * Public API of the roulette module for other modules (types only + service name).
 * Consumers: `ctx.services.get<RouletteApi>(ROULETTE_SERVICE)?.onSpin(...)` — e.g. contracts
 * (`roulette_red`: winning bets on red) and achievements (`zero_hero`: a straight bet on 0 won).
 */
import type { Player } from '@minecraft/server';

export const ROULETTE_SERVICE = 'roulette';

export type RouletteBetType =
  | 'straight' | 'split' | 'street' | 'trio' | 'corner' | 'first_four' | 'six_line'
  | 'dozen' | 'column' | 'red' | 'black' | 'odd' | 'even' | 'low' | 'high';

export interface RouletteBetResult {
  type: RouletteBetType;
  /** covered numbers, ascending */
  numbers: readonly number[];
  amount: number;
  /** total return of this bet (stake included; 0 = lost) */
  totalReturn: number;
}

export interface RouletteSpinEntry {
  playerId: string;
  /** undefined when the player was offline at settlement (settled on their next join) */
  player: Player | undefined;
  bets: readonly RouletteBetResult[];
  staked: number;
  totalReturn: number;
}

export interface RouletteSpinEvent {
  /** table key (`<dimension>|x,y,z`) */
  table: string;
  /** winning pocket 0..36 */
  result: number;
  entries: readonly RouletteSpinEntry[];
}

export interface RouletteApi {
  /** Fired once per shared spin after every bettor was settled. */
  onSpin(listener: (e: RouletteSpinEvent) => void): void;
  /** Last results of a table, newest first. */
  history(tableKey: string): readonly number[];
}
