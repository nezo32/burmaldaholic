/**
 * Slot Showdown v2 scoring (SLOTS.md §9). Plugs into the PvP engine contract (`core/logic/pvp/mode.ts`
 * on the PvP-bots branch) after the merge; points = 10 × win / bet = 2 × fifths. Lane S-B8.
 */
export type Hazard = 'none' | 'kaboom' | 'swap' | 'time_warp';

export interface ShowdownSpin {
  stops: number[];
  hazard: Hazard;
  points: number;
  /** 0 none, 1 free spins, 2 hunt, 3 hoard, 4 wheel */
  featureCode: number;
  featureArg: number;
}

export const showdownPoints = (totalFifths: number): number => 2 * totalFifths;
